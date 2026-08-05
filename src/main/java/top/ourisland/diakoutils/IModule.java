package top.ourisland.diakoutils;

import com.electronwill.nightconfig.core.Config;
import net.minecraft.server.MinecraftServer;
import top.ourisland.diakoutils.annotation.ModuleMetadata;
import top.ourisland.diakoutils.property.ModulePropertyChange;
import top.ourisland.diakoutils.property.PropertyValidationResult;

import java.util.Collection;
import java.util.Map;

public interface IModule {

    default String description() {
        return ModuleMetadata.require(getClass()).description();
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

    default PropertyValidationResult validateProperties(Map<String, Object> candidateValues) {
        return PropertyValidationResult.success();
    }

    default void onPropertiesChanged(
            MinecraftServer server,
            Collection<ModulePropertyChange<?>> changes
    ) {
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

    default String id() {
        return ModuleMetadata.require(getClass()).id();
    }

    default String displayName() {
        return ModuleMetadata.require(getClass()).displayName();
    }

}
