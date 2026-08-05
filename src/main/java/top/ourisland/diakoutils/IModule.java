package top.ourisland.diakoutils;

import com.electronwill.nightconfig.core.Config;
import net.minecraft.server.MinecraftServer;

public interface IModule {

    default String description() {
        return "";
    }

    default void loadConfig(Config config, String path) {
        var value = config.get(path + ".enabled");
        setEnabled(value instanceof Boolean enabled && enabled);
    }

    void setEnabled(boolean enabled);

    default void saveConfig(Config config, String path) {
        config.set(path + ".enabled", enabled());
    }

    boolean enabled();

    default void onEnable(MinecraftServer server) {
    }

    default void onDisable(MinecraftServer server) {
    }

    default String statusLine() {
        return "%s (%s): %s".formatted(
                id(),
                displayName(),
                enabled()
                        ? "enabled"
                        : "disabled"
        );
    }

    String id();

    String displayName();

}
