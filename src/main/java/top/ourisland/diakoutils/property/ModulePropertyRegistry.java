package top.ourisland.diakoutils.property;

import com.electronwill.nightconfig.core.Config;
import top.ourisland.diakoutils.IModule;
import top.ourisland.diakoutils.annotation.ModuleProperty;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ModulePropertyRegistry {

    private static final Pattern PROPERTY_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]*");
    private static final String RESERVED_PROPERTY_ID = "enabled";

    private final Map<String, Map<String, ModulePropertyDescriptor<?>>> propertiesByModule =
            new LinkedHashMap<>();

    public void register(IModule module) {
        var descriptors = discover(module);
        var byId = new LinkedHashMap<String, ModulePropertyDescriptor<?>>();
        descriptors.stream()
                .filter(descriptor ->
                        byId.putIfAbsent(descriptor.id(), descriptor) != null
                )
                .forEach(descriptor -> {
                    throw new IllegalArgumentException(
                            "Duplicate property id " + module.id() + "." + descriptor.id()
                    );
                });

        var defaultSnapshot = new LinkedHashMap<String, Object>();
        descriptors.forEach(descriptor -> defaultSnapshot.put(
                descriptor.id(),
                descriptor.defaultValue()
        ));

        var moduleValidation = module.validateProperties(Collections.unmodifiableMap(defaultSnapshot));
        if (!moduleValidation.valid()) {
            throw new IllegalArgumentException(
                    "Invalid default properties for module " + module.id() + ": "
                            + moduleValidation.message()
            );
        }

        propertiesByModule.put(module.id(), Collections.unmodifiableMap(byId));
    }

    private List<ModulePropertyDescriptor<?>> discover(IModule module) {
        return Stream
                .<Class<?>>iterate(
                        module.getClass(),
                        type -> type != null && type != Object.class,
                        Class::getSuperclass
                )
                .flatMap(type ->
                        Arrays.stream(type.getDeclaredFields())
                )
                .<ModulePropertyDescriptor<?>>mapMulti((field, downstream) -> {
                    var annotation = field.getAnnotation(ModuleProperty.class);
                    if (annotation != null) {
                        downstream.accept(createDescriptor(module, field, annotation));
                    }
                })
                .sorted(Comparator
                        .comparingInt(ModulePropertyDescriptor<?>::order)
                        .thenComparing(ModulePropertyDescriptor::id)
                        .thenComparing(ModulePropertyDescriptor::fieldName)
                )
                .toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ModulePropertyDescriptor<?> createDescriptor(
            IModule module,
            Field field,
            ModuleProperty annotation
    ) {
        validateField(module, field, annotation);

        var codec = (PropertyCodec) PropertyCodecs.find(field);
        if (codec == null) {
            throw invalidField(
                    module,
                    field,
                    "unsupported property type " + field.getType().getName()
            );
        }

        if (!field.trySetAccessible()) {
            throw invalidField(module, field, "field is not accessible");
        }

        var accessor = new PropertyAccessor<>() {
            @Override
            public Object get(IModule target) {
                try {
                    return field.get(target);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot read property field " + field, e);
                }
            }

            @Override
            public void set(IModule target, Object value) {
                try {
                    field.set(target, value);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot write property field " + field, e);
                }
            }
        };

        var defaultValue = accessor.get(module);
        if (defaultValue == null) {
            throw invalidField(module, field, "default value must not be null");
        }

        PropertyConstraints constraints;
        try {
            constraints = PropertyConstraints.from(annotation, field.getType(), codec);
        } catch (IllegalArgumentException e) {
            throw invalidField(module, field, e.getMessage());
        }

        var defaultValidation = constraints.validate(annotation.id(), defaultValue);
        if (!defaultValidation.valid()) {
            throw invalidField(
                    module,
                    field,
                    "invalid default value: " + defaultValidation.message()
            );
        }

        return new ModulePropertyDescriptor<>(
                annotation.id(),
                annotation.displayName(),
                annotation.description(),
                annotation.order(),
                field.getName(),
                field.getType(),
                defaultValue,
                codec,
                constraints,
                annotation.applyMode(),
                accessor,
                List.of(annotation.suggestions())
        );
    }

    private static void validateField(
            IModule module,
            Field field,
            ModuleProperty annotation
    ) {
        if (!PROPERTY_ID_PATTERN.matcher(annotation.id()).matches()) {
            throw invalidField(module, field, "invalid property id: " + annotation.id());
        }

        if (RESERVED_PROPERTY_ID.equals(annotation.id())) {
            throw invalidField(module, field, "property id 'enabled' is reserved");
        }

        if (annotation.displayName().isBlank()) {
            throw invalidField(module, field, "displayName must not be blank");
        }

        if (Modifier.isStatic(field.getModifiers())) {
            throw invalidField(module, field, "property field must not be static");
        }

        if (Modifier.isFinal(field.getModifiers())) {
            throw invalidField(module, field, "property field must not be final");
        }

        if (field.isSynthetic()) {
            throw invalidField(module, field, "property field must not be synthetic");
        }
    }

    private static IllegalArgumentException invalidField(
            IModule module,
            Field field,
            String message
    ) {
        return new IllegalArgumentException(
                "Invalid module property " + module.id() + "." + field.getName() + ": " + message
        );
    }

    public ModulePropertyDescriptor<?> get(String moduleId, String propertyId) {
        var properties = propertiesByModule.get(moduleId);
        return properties == null
                ? null
                : properties.get(propertyId);
    }

    public Set<String> ids(String moduleId) {
        var properties = propertiesByModule.get(moduleId);
        return properties == null
                ? Set.of()
                : properties.keySet();
    }

    public Map<String, Object> snapshot(IModule module) {
        var values = new LinkedHashMap<String, Object>();
        all(module.id()).forEach(descriptor -> values.put(
                descriptor.id(),
                getValue(descriptor, module)
        ));
        return values;
    }

    public Collection<ModulePropertyDescriptor<?>> all(String moduleId) {
        var properties = propertiesByModule.get(moduleId);
        return properties == null
                ? List.of()
                : properties.values();
    }

    private static <T> T getValue(ModulePropertyDescriptor<T> descriptor, IModule module) {
        return descriptor.get(module);
    }

    public void restoreSnapshot(IModule module, Map<String, Object> snapshot) {
        all(module.id()).stream()
                .filter(descriptor ->
                        snapshot.containsKey(descriptor.id())
                )
                .forEach(descriptor ->
                        setValue(descriptor, module, snapshot.get(descriptor.id()))
                );
    }

    @SuppressWarnings("unchecked")
    private static <T> void setValue(
            ModulePropertyDescriptor<T> descriptor,
            IModule module,
            Object value
    ) {
        descriptor.set(module, (T) value);
    }

    public LoadResult loadProperties(
            IModule module,
            Config config,
            String modulePath,
            boolean reload
    ) {
        var candidates = new LinkedHashMap<String, Object>();
        var warnings = new ArrayList<String>();
        var repaired = false;

        for (var descriptor : all(module.id())) {
            var fallback = reload
                    ? getValue(descriptor, module)
                    : descriptor.defaultValue();
            var propertyPath = modulePath + "." + descriptor.id();

            Object candidate;
            if (config.get(propertyPath) == null) {
                candidate = descriptor.defaultValue();
            } else {
                try {
                    candidate = readValue(descriptor, config, propertyPath, fallback);
                } catch (PropertyParseException e) {
                    candidate = fallback;
                    repaired = true;
                    warnings.add("%s. Using %s.".formatted(
                            e.getMessage(),
                            formatValue(descriptor, fallback)
                    ));
                }
            }

            var validation = validateValue(descriptor, candidate);
            if (!validation.valid()) {
                candidate = fallback;
                repaired = true;
                warnings.add(
                        "Invalid config value at " + propertyPath + ": "
                                + validation.message() + " Using "
                                + formatValue(descriptor, fallback) + "."
                );
            }
            candidates.put(descriptor.id(), candidate);
        }

        var moduleValidation = module.validateProperties(Collections.unmodifiableMap(candidates));
        if (!moduleValidation.valid()) {
            candidates.clear();
            all(module.id()).forEach(descriptor -> candidates.put(
                    descriptor.id(),
                    reload
                            ? getValue(descriptor, module)
                            : descriptor.defaultValue()
            ));
            repaired = true;
            warnings.add(
                    "Invalid property set for module %s: %s. Keeping %s".formatted(
                            module.id(),
                            moduleValidation.message(),
                            reload
                                    ? "current values."
                                    : "defaults."
                    )
            );
        }

        var changes = new ArrayList<ModulePropertyChange<?>>();
        all(module.id()).forEach(descriptor -> {
            var oldValue = getValue(descriptor, module);
            var newValue = candidates.get(descriptor.id());

            if (!Objects.equals(oldValue, newValue)) {
                setValue(
                        descriptor,
                        module,
                        newValue
                );
                changes.add(change(
                        descriptor.id(),
                        oldValue,
                        newValue
                ));
            }
        });

        return new LoadResult(
                List.copyOf(changes),
                repaired,
                List.copyOf(warnings)
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T readValue(
            ModulePropertyDescriptor<T> descriptor,
            Config config,
            String path,
            Object fallback
    ) throws PropertyParseException {
        return descriptor.read(config, path, (T) fallback);
    }

    @SuppressWarnings("unchecked")
    private static <T> String formatValue(ModulePropertyDescriptor<T> descriptor, Object value) {
        return descriptor.format((T) value);
    }

    @SuppressWarnings("unchecked")
    private static <T> PropertyValidationResult validateValue(
            ModulePropertyDescriptor<T> descriptor,
            Object value
    ) {
        return descriptor.validate((T) value);
    }

    private static <T> ModulePropertyChange<T> change(
            String id,
            Object oldValue,
            Object newValue
    ) {
        @SuppressWarnings("unchecked") var typedOld = (T) oldValue;
        @SuppressWarnings("unchecked") var typedNew = (T) newValue;
        return new ModulePropertyChange<>(id, typedOld, typedNew);
    }

    public void saveProperties(
            IModule module,
            Config config,
            String modulePath
    ) {
        all(module.id()).forEach(descriptor -> writeValue(
                descriptor,
                config,
                modulePath + "." + descriptor.id(),
                getValue(descriptor, module)
        ));
    }

    @SuppressWarnings("unchecked")
    private static <T> void writeValue(
            ModulePropertyDescriptor<T> descriptor,
            Config config,
            String path,
            Object value
    ) {
        descriptor.write(config, path, (T) value);
    }

    public record LoadResult(
            List<ModulePropertyChange<?>> changes,
            boolean repaired,
            List<String> warnings
    ) {

    }

}
