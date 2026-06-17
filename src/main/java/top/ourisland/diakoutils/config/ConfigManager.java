package top.ourisland.diakoutils.config;

import com.electronwill.nightconfig.core.file.FileConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.IModule;
import top.ourisland.diakoutils.ModuleManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {

    private final ModuleManager modules;
    private final Path path;

    public ConfigManager(ModuleManager modules) {
        this.modules = modules;
        this.path = FabricLoader.getInstance().getConfigDir().resolve("diakoutils.toml");
    }

    public Path path() {
        return path;
    }

    public void loadOrCreate() {
        loadOrCreate(null);
    }

    private void loadOrCreate(MinecraftServer server) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            DiakoUtils.LOGGER.error("[{}] Failed to create config directory for {}", DiakoUtils.MOD_ID, path, e);
            return;
        }

        var configExists = Files.exists(path);
        try (var config = FileConfig.builder(path).build()) {
            if (configExists) {
                config.load();
            }

            for (var module : modules.all()) {
                var wasEnabled = module.enabled();
                var modulePath = modulePath(module.id());

                module.loadConfig(config, modulePath);
                module.saveConfig(config, modulePath);
                fireLifecycleIfNeeded(server, module, wasEnabled, module.enabled());
            }

            config.save();
        } catch (RuntimeException e) {
            DiakoUtils.LOGGER.error("[{}] Failed to load/save TOML config {}", DiakoUtils.MOD_ID, path, e);
        }
    }

    private static String modulePath(String moduleId) {
        return "modules." + moduleId;
    }

    private static void fireLifecycleIfNeeded(
            MinecraftServer server,
            IModule module,
            boolean wasEnabled,
            boolean isEnabled
    ) {
        if (server == null || wasEnabled == isEnabled) {
            return;
        }

        if (isEnabled) {
            module.onEnable(server);
        } else {
            module.onDisable(server);
        }
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            DiakoUtils.LOGGER.error("[{}] Failed to create config directory for {}", DiakoUtils.MOD_ID, path, e);
            return;
        }

        var configExists = Files.exists(path);
        try (var config = FileConfig.builder(path).build()) {
            if (configExists) {
                config.load();
            }

            for (var module : modules.all()) {
                module.saveConfig(config, modulePath(module.id()));
            }

            config.save();
        } catch (RuntimeException e) {
            DiakoUtils.LOGGER.error("[{}] Failed to save TOML config {}", DiakoUtils.MOD_ID, path, e);
        }
    }

    public void reload(MinecraftServer server) {
        loadOrCreate(server);
    }

}
