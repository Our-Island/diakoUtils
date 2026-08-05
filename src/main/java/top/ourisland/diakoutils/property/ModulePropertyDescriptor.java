package top.ourisland.diakoutils.property;

import com.electronwill.nightconfig.core.Config;
import top.ourisland.diakoutils.IModule;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public final class ModulePropertyDescriptor<T> {

    private final String id;
    private final String displayName;
    private final String description;
    private final int order;
    private final String fieldName;
    private final Class<?> valueType;
    private final T defaultValue;
    private final PropertyCodec<T> codec;
    private final PropertyConstraints<T> constraints;
    private final PropertyApplyMode applyMode;
    private final PropertyAccessor<T> accessor;
    private final List<String> declaredSuggestions;

    public ModulePropertyDescriptor(
            String id,
            String displayName,
            String description,
            int order,
            String fieldName,
            Class<?> valueType,
            T defaultValue,
            PropertyCodec<T> codec,
            PropertyConstraints<T> constraints,
            PropertyApplyMode applyMode,
            PropertyAccessor<T> accessor,
            List<String> declaredSuggestions
    ) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.order = order;
        this.fieldName = fieldName;
        this.valueType = valueType;
        this.defaultValue = defaultValue;
        this.codec = codec;
        this.constraints = constraints;
        this.applyMode = applyMode;
        this.accessor = accessor;
        this.declaredSuggestions = List.copyOf(declaredSuggestions);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public int order() {
        return order;
    }

    public String fieldName() {
        return fieldName;
    }

    public Class<?> valueType() {
        return valueType;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public PropertyApplyMode applyMode() {
        return applyMode;
    }

    public T get(IModule module) {
        return accessor.get(module);
    }

    public void set(IModule module, T value) {
        accessor.set(module, value);
    }

    public T parse(String input) throws PropertyParseException {
        return codec.parse(input);
    }

    public T read(
            Config config,
            String path,
            T fallback
    ) throws PropertyParseException {
        return codec.read(config, path, fallback);
    }

    public void write(
            Config config,
            String path,
            T value
    ) {
        codec.write(config, path, value);
    }

    public String typeName() {
        return codec.typeName();
    }

    public PropertyValidationResult validate(T value) {
        return constraints.validate(id, value);
    }

    public String rangeDescription() {
        return constraints.rangeDescription(codec);
    }

    public Collection<String> suggestions() {
        var suggestions = new LinkedHashSet<String>();

        codec.suggestions().stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(suggestions::add);
        declaredSuggestions.stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(suggestions::add);
        addSuggestion(suggestions, format(defaultValue));

        if (constraints.minimum() != null) {
            addSuggestion(suggestions, format(constraints.minimum()));
        }
        if (constraints.maximum() != null) {
            addSuggestion(suggestions, format(constraints.maximum()));
        }

        return List.copyOf(suggestions);
    }

    private static void addSuggestion(Collection<String> suggestions, String value) {
        if (value != null && !value.isBlank()) {
            suggestions.add(value);
        }
    }

    public String format(T value) {
        return codec.format(value);
    }

}
