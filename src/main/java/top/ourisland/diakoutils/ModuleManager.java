package top.ourisland.diakoutils;

import net.minecraft.server.MinecraftServer;
import top.ourisland.diakoutils.discovery.ModuleScanner;
import top.ourisland.diakoutils.property.ModulePropertyRegistry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ModuleManager {

    private final Map<String, IModule> modules = new LinkedHashMap<>();
    private final Map<Class<? extends IModule>, IModule> modulesByType = new LinkedHashMap<>();
    private final ModulePropertyRegistry properties;
    private MinecraftServer currentServer;

    public ModuleManager(ModulePropertyRegistry properties) {
        this.properties = properties;
    }

    @SuppressWarnings("UnusedReturnValue")
    public int discoverAndRegister(String modId, String basePackage) {
        var discovered = ModuleScanner.discover(modId, basePackage);
        discovered.forEach(this::register);
        return discovered.size();
    }

    public void register(IModule module) {
        var moduleId = module.id();
        if (modules.containsKey(moduleId)) {
            throw new IllegalArgumentException("Duplicate module id: " + moduleId);
        }

        if (modulesByType.containsKey(module.getClass())) {
            throw new IllegalArgumentException(
                    "Duplicate module type: " + module.getClass().getName()
            );
        }

        properties.register(module);
        modules.put(moduleId, module);
        modulesByType.put(module.getClass(), module);
    }

    public Collection<IModule> all() {
        return modules.values();
    }

    public Set<String> ids() {
        return modules.keySet();
    }

    public IModule get(String id) {
        return modules.get(id);
    }

    public <T extends IModule> T get(Class<T> moduleType) {
        return moduleType.cast(modulesByType.get(moduleType));
    }

    public boolean isEnabled(String id) {
        var module = modules.get(id);
        return module != null && module.enabled();
    }

    public boolean enable(String id, MinecraftServer server) {
        var module = modules.get(id);

        if (module == null) {
            return false;
        }

        if (!module.enabled()) {
            module.setEnabled(true);
            try {
                module.onEnable(server);
            } catch (RuntimeException e) {
                module.setEnabled(false);
                throw e;
            }
        }

        return true;
    }

    public boolean disable(String id, MinecraftServer server) {
        var module = modules.get(id);

        if (module == null) {
            return false;
        }

        if (module.enabled()) {
            module.onDisable(server);
            module.setEnabled(false);
        }

        return true;
    }

    public void onServerStarted(MinecraftServer server) {
        currentServer = server;

        modules.values().stream()
                .filter(IModule::enabled)
                .forEach(module -> {
                    try {
                        module.onEnable(server);
                    } catch (RuntimeException e) {
                        module.setEnabled(false);
                        DiakoUtils.LOGGER.error(
                                "[{}] Failed to enable configured module {} during server startup",
                                DiakoUtils.MOD_ID,
                                module.id(),
                                e
                        );
                    }
                });
    }

    public void onServerStopping(MinecraftServer server) {
        modules.values().stream()
                .filter(IModule::enabled)
                .forEach(module -> {
                    try {
                        module.onDisable(server);
                    } catch (RuntimeException e) {
                        DiakoUtils.LOGGER.error(
                                "[{}] Failed to stop module {} cleanly",
                                DiakoUtils.MOD_ID,
                                module.id(),
                                e
                        );
                    }
                });

        currentServer = null;
    }

    public void onEndServerTick(MinecraftServer server) {
        modules.values().stream()
                .filter(module -> module.enabled()
                        && module instanceof TickingModule
                )
                .map(module -> (TickingModule) module)
                .forEach(tickingModule -> tickingModule.onEndServerTick(server));
    }

    public MinecraftServer currentServer() {
        return currentServer;
    }

}
