package top.ourisland.diakoutils.config;

import com.electronwill.nightconfig.core.file.FileConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.IModule;
import top.ourisland.diakoutils.ModuleManager;
import top.ourisland.diakoutils.property.ModulePropertyChange;
import top.ourisland.diakoutils.property.ModulePropertyRegistry;
import top.ourisland.diakoutils.property.PropertyApplyMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigManager {

    private final ModuleManager modules;
    private final ModulePropertyRegistry properties;
    private final Path path;

    public ConfigManager(
            ModuleManager modules,
            ModulePropertyRegistry properties
    ) {
        this.modules = modules;
        this.properties = properties;
        this.path = FabricLoader.getInstance().getConfigDir().resolve("diakoutils.toml");
    }

    public Path path() {
        return path;
    }

    public void loadOrCreate() {
        loadOrCreate(null);
    }

    private synchronized boolean loadOrCreate(MinecraftServer server) {
        try {
            ensureConfigDirectory();
        } catch (ConfigPersistenceException e) {
            logPersistenceFailure("create config directory for", e);
            return false;
        }

        var enabledSnapshots = new LinkedHashMap<IModule, Boolean>();
        var propertySnapshots = new LinkedHashMap<IModule, Map<String, Object>>();
        var propertyChanges = new LinkedHashMap<IModule, List<ModulePropertyChange<?>>>();

        modules.all().forEach(module -> {
            enabledSnapshots.put(module, module.enabled());
            propertySnapshots.put(module, properties.snapshot(module));
        });

        var configExists = Files.exists(path);
        try (var config = FileConfig.builder(path).build()) {
            if (configExists) {
                config.load();
            }

            modules.all().forEach(module -> {
                var modulePath = modulePath(module.id());
                module.loadConfig(config, modulePath);

                var loadResult = properties.loadProperties(
                        module,
                        config,
                        modulePath,
                        server != null
                );
                propertyChanges.put(module, loadResult.changes());
                loadResult.warnings().forEach(warning -> DiakoUtils.LOGGER.warn(
                        "[{}] {}",
                        DiakoUtils.MOD_ID,
                        warning
                ));

                module.saveConfig(config, modulePath);
                properties.saveProperties(module, config, modulePath);
            });

            config.save();
        } catch (RuntimeException e) {
            restoreSnapshots(enabledSnapshots, propertySnapshots);
            DiakoUtils.LOGGER.error(
                    "[{}] Failed to load/save TOML config {}",
                    DiakoUtils.MOD_ID,
                    path,
                    e
            );
            return false;
        }

        if (server != null) {
            fireRuntimeChanges(server, enabledSnapshots, propertyChanges);
        }

        return true;
    }

    private void ensureConfigDirectory() throws ConfigPersistenceException {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new ConfigPersistenceException(
                    "Failed to create config directory for " + path,
                    e
            );
        }
    }

    private void logPersistenceFailure(String action, Exception e) {
        DiakoUtils.LOGGER.error(
                "[{}] Failed to {} {}",
                DiakoUtils.MOD_ID,
                action,
                path,
                e
        );
    }

    private static String modulePath(String moduleId) {
        return "modules." + moduleId;
    }

    private void restoreSnapshots(
            Map<IModule, Boolean> enabledSnapshots,
            Map<IModule, Map<String, Object>> propertySnapshots
    ) {
        modules.all().forEach(module -> {
            module.setEnabled(enabledSnapshots.getOrDefault(module, false));
            properties.restoreSnapshot(
                    module,
                    propertySnapshots.getOrDefault(module, Map.of())
            );
        });
    }

    private void fireRuntimeChanges(
            MinecraftServer server,
            Map<IModule, Boolean> enabledSnapshots,
            Map<IModule, List<ModulePropertyChange<?>>> propertyChanges
    ) {
        modules.all().forEach(module -> {
            var changes = propertyChanges.getOrDefault(module, List.of());
            var wasEnabled = enabledSnapshots.getOrDefault(module, false);
            var remainsEnabled = wasEnabled && module.enabled();
            var requiresReenable = remainsEnabled
                    && changes.stream()
                    .map(change ->
                            properties.get(module.id(), change.propertyId())
                    )
                    .anyMatch(descriptor -> descriptor != null
                            && descriptor.applyMode() == PropertyApplyMode.REENABLE_MODULE
                    );

            if (requiresReenable) {
                try {
                    module.onDisable(server);
                } catch (RuntimeException e) {
                    DiakoUtils.LOGGER.error(
                            "[{}] Module {} failed while preparing reloaded properties",
                            DiakoUtils.MOD_ID,
                            module.id(),
                            e
                    );
                }
            }

            if (!changes.isEmpty()) {
                try {
                    module.onPropertiesChanged(server, changes);
                } catch (RuntimeException e) {
                    DiakoUtils.LOGGER.error(
                            "[{}] Module {} failed while applying reloaded properties",
                            DiakoUtils.MOD_ID,
                            module.id(),
                            e
                    );
                }
            }

            try {
                if (wasEnabled != module.enabled()) {
                    if (module.enabled()) {
                        module.onEnable(server);
                    } else {
                        module.onDisable(server);
                    }
                } else if (requiresReenable) {
                    module.onEnable(server);
                }
            } catch (RuntimeException e) {
                DiakoUtils.LOGGER.error(
                        "[{}] Module {} failed during reload lifecycle transition",
                        DiakoUtils.MOD_ID,
                        module.id(),
                        e
                );
            }
        });
    }

    public boolean reload(MinecraftServer server) {
        return loadOrCreate(server);
    }

    public boolean save() {
        try {
            saveOrThrow();
            return true;
        } catch (ConfigPersistenceException e) {
            logPersistenceFailure("save TOML config", e);
            return false;
        }
    }

    public synchronized void saveOrThrow() throws ConfigPersistenceException {
        ensureConfigDirectory();

        var configExists = Files.exists(path);
        try (var config = FileConfig.builder(path).build()) {
            if (configExists) {
                config.load();
            }

            modules.all().forEach(module -> {
                var modulePath = modulePath(module.id());
                module.saveConfig(config, modulePath);
                properties.saveProperties(module, config, modulePath);
            });

            config.save();
        } catch (RuntimeException e) {
            throw new ConfigPersistenceException(
                    "Failed to save TOML config " + path,
                    e
            );
        }
    }

}
