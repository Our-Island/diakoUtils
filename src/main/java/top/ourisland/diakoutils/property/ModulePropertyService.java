package top.ourisland.diakoutils.property;

import net.minecraft.server.MinecraftServer;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.IModule;
import top.ourisland.diakoutils.ModuleManager;
import top.ourisland.diakoutils.config.ConfigManager;
import top.ourisland.diakoutils.config.ConfigPersistenceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ModulePropertyService {

    private final ModuleManager modules;
    private final ModulePropertyRegistry registry;
    private final ConfigManager configManager;

    public ModulePropertyService(
            ModuleManager modules,
            ModulePropertyRegistry registry,
            ConfigManager configManager
    ) {
        this.modules = modules;
        this.registry = registry;
        this.configManager = configManager;
    }

    public PropertyOperationResult set(
            String moduleId,
            String propertyId,
            String rawValue,
            MinecraftServer server
    ) {
        var module = modules.get(moduleId);
        if (module == null) {
            return PropertyOperationResult.failure("Unknown module: " + moduleId);
        }

        var descriptor = registry.get(moduleId, propertyId);
        if (descriptor == null) {
            return PropertyOperationResult.failure(
                    "Unknown property: " + moduleId + "." + propertyId
            );
        }

        final Object newValue;
        try {
            newValue = parse(descriptor, rawValue);
        } catch (PropertyParseException e) {
            return PropertyOperationResult.failure(e.getMessage());
        }

        var validation = validate(descriptor, newValue);
        if (!validation.valid()) {
            return PropertyOperationResult.failure(validation.message());
        }

        var oldValue = get(descriptor, module);
        if (Objects.equals(oldValue, newValue)) {
            return PropertyOperationResult.noChange(
                    moduleId + "." + propertyId + " is already "
                            + format(descriptor, newValue) + "."
            );
        }

        var candidateValues = registry.snapshot(module);
        candidateValues.put(propertyId, newValue);
        var moduleValidation = module.validateProperties(Collections.unmodifiableMap(candidateValues));
        if (!moduleValidation.valid()) {
            return PropertyOperationResult.failure(
                    "Cannot update property: " + moduleValidation.message()
            );
        }

        return apply(
                module,
                List.of(new PendingChange(descriptor, oldValue, newValue)),
                server
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T parse(
            ModulePropertyDescriptor<T> descriptor,
            String rawValue
    ) throws PropertyParseException {
        return descriptor.parse(rawValue);
    }

    @SuppressWarnings("unchecked")
    private static <T> PropertyValidationResult validate(
            ModulePropertyDescriptor<T> descriptor,
            Object value
    ) {
        return descriptor.validate((T) value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(ModulePropertyDescriptor<T> descriptor, IModule module) {
        return descriptor.get(module);
    }

    @SuppressWarnings("unchecked")
    private static <T> String format(
            ModulePropertyDescriptor<T> descriptor,
            Object value
    ) {
        return descriptor.format((T) value);
    }

    private PropertyOperationResult apply(
            IModule module,
            List<PendingChange> pending,
            MinecraftServer server
    ) {
        var changes = pending.stream()
                .<ModulePropertyChange<?>>map(change -> propertyChange(
                        change.descriptor().id(),
                        change.oldValue(),
                        change.newValue()
                ))
                .toList();
        var requiresReenable = module.enabled() && pending.stream()
                .anyMatch(change -> change.descriptor().applyMode()
                        == PropertyApplyMode.REENABLE_MODULE
                );
        var lifecycleSuspended = false;
        var valuesApplied = false;
        var callbackAttempted = false;

        try {
            if (requiresReenable) {
                module.onDisable(server);
                lifecycleSuspended = true;
            }

            pending.forEach(change -> set(
                    change.descriptor(),
                    module,
                    change.newValue()
            ));
            valuesApplied = true;
            configManager.saveOrThrow();

            callbackAttempted = true;
            module.onPropertiesChanged(server, changes);
            if (requiresReenable) {
                module.onEnable(server);
                lifecycleSuspended = false;
            }

            return PropertyOperationResult.success(changes);
        } catch (ConfigPersistenceException e) {
            rollback(
                    module,
                    pending,
                    changes,
                    requiresReenable,
                    lifecycleSuspended,
                    valuesApplied,
                    callbackAttempted,
                    server
            );
            DiakoUtils.LOGGER.error(
                    "[{}] Failed to persist property changes for module {}",
                    DiakoUtils.MOD_ID,
                    module.id(),
                    e
            );
            return PropertyOperationResult.failure(
                    "Property was not updated because the configuration file could not be saved."
            );
        } catch (RuntimeException e) {
            rollback(
                    module,
                    pending,
                    changes,
                    requiresReenable,
                    lifecycleSuspended,
                    valuesApplied,
                    callbackAttempted,
                    server
            );
            DiakoUtils.LOGGER.error(
                    "[{}] Failed to apply property changes for module {}",
                    DiakoUtils.MOD_ID,
                    module.id(),
                    e
            );
            return PropertyOperationResult.failure(
                    "Property was not updated because the module failed to apply the change."
            );
        }
    }

    private static <T> ModulePropertyChange<T> propertyChange(
            String propertyId,
            Object oldValue,
            Object newValue
    ) {
        @SuppressWarnings("unchecked") var typedOld = (T) oldValue;
        @SuppressWarnings("unchecked") var typedNew = (T) newValue;
        return new ModulePropertyChange<>(propertyId, typedOld, typedNew);
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(
            ModulePropertyDescriptor<T> descriptor,
            IModule module,
            Object value
    ) {
        descriptor.set(module, (T) value);
    }

    private void rollback(
            IModule module,
            List<PendingChange> pending,
            List<ModulePropertyChange<?>> changes,
            boolean requiresReenable,
            boolean lifecycleSuspended,
            boolean valuesApplied,
            boolean callbackAttempted,
            MinecraftServer server
    ) {
        pending.forEach(change -> set(
                change.descriptor(),
                module,
                change.oldValue()
        ));

        if (valuesApplied) {
            try {
                configManager.saveOrThrow();
            } catch (ConfigPersistenceException rollbackError) {
                DiakoUtils.LOGGER.error(
                        "[{}] Failed to persist rolled back properties for module {}",
                        DiakoUtils.MOD_ID,
                        module.id(),
                        rollbackError
                );
            }
        }

        if (callbackAttempted) {
            try {
                var reversed = changes.stream()
                        .<ModulePropertyChange<?>>map(change -> propertyChange(
                                change.propertyId(),
                                change.newValue(),
                                change.oldValue()
                        ))
                        .toList();
                module.onPropertiesChanged(server, reversed);
            } catch (RuntimeException rollbackError) {
                DiakoUtils.LOGGER.error(
                        "[{}] Module {} failed while rolling back property callbacks",
                        DiakoUtils.MOD_ID,
                        module.id(),
                        rollbackError
                );
            }
        }

        if (requiresReenable && lifecycleSuspended) {
            try {
                module.onEnable(server);
            } catch (RuntimeException rollbackError) {
                DiakoUtils.LOGGER.error(
                        "[{}] Module {} failed to resume after property rollback",
                        DiakoUtils.MOD_ID,
                        module.id(),
                        rollbackError
                );
            }
        }
    }

    public PropertyOperationResult reset(
            String moduleId,
            String propertyId,
            MinecraftServer server
    ) {
        var module = modules.get(moduleId);
        if (module == null) {
            return PropertyOperationResult.failure("Unknown module: " + moduleId);
        }

        var descriptor = registry.get(moduleId, propertyId);
        if (descriptor == null) {
            return PropertyOperationResult.failure(
                    "Unknown property: " + moduleId + "." + propertyId
            );
        }

        var oldValue = get(descriptor, module);
        var defaultValue = descriptor.defaultValue();
        if (Objects.equals(oldValue, defaultValue)) {
            return PropertyOperationResult.noChange(
                    moduleId + "." + propertyId + " already uses its default value."
            );
        }

        var candidateValues = registry.snapshot(module);
        candidateValues.put(propertyId, defaultValue);
        var moduleValidation = module.validateProperties(Collections.unmodifiableMap(candidateValues));
        if (!moduleValidation.valid()) {
            return PropertyOperationResult.failure(
                    "Cannot reset property: " + moduleValidation.message()
            );
        }

        return apply(
                module,
                List.of(new PendingChange(descriptor, oldValue, defaultValue)),
                server
        );
    }

    public PropertyOperationResult resetAll(
            String moduleId,
            MinecraftServer server
    ) {
        var module = modules.get(moduleId);
        if (module == null) {
            return PropertyOperationResult.failure("Unknown module: " + moduleId);
        }

        var pending = new ArrayList<PendingChange>();
        var candidates = registry.snapshot(module);
        registry.all(moduleId).forEach(descriptor -> {
            var oldValue = get(descriptor, module);
            var defaultValue = descriptor.defaultValue();
            candidates.put(descriptor.id(), defaultValue);

            if (!Objects.equals(oldValue, defaultValue)) {
                pending.add(new PendingChange(descriptor, oldValue, defaultValue));
            }
        });

        if (pending.isEmpty()) {
            return PropertyOperationResult.noChange(
                    moduleId + " already uses all default property values."
            );
        }

        var moduleValidation = module.validateProperties(Collections.unmodifiableMap(candidates));
        if (!moduleValidation.valid()) {
            return PropertyOperationResult.failure(
                    "Cannot reset properties: " + moduleValidation.message()
            );
        }

        return apply(module, pending, server);
    }

    private record PendingChange(
            ModulePropertyDescriptor<?> descriptor,
            Object oldValue,
            Object newValue
    ) {

    }

}
