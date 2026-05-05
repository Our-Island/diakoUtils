package top.ourisland.diakoutils;

import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ModuleManager {

    private final Map<String, IModule> modules = new LinkedHashMap<>();
    private MinecraftServer currentServer;

    public void register(IModule module) {
        if (modules.containsKey(module.id())) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
        modules.put(module.id(), module);
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
            module.onEnable(server);
        }

        return true;
    }

    public boolean disable(String id, MinecraftServer server) {
        var module = modules.get(id);
        if (module == null) {
            return false;
        }

        if (module.enabled()) {
            module.setEnabled(false);
            module.onDisable(server);
        }

        return true;
    }

    public void onEndServerTick(MinecraftServer server) {
        currentServer = server;

        for (IModule module : modules.values()) {
            if (module.enabled() && module instanceof TickingModule tickingModule) {
                tickingModule.onEndServerTick(server);
            }
        }
    }

    public MinecraftServer currentServer() {
        return currentServer;
    }

}
