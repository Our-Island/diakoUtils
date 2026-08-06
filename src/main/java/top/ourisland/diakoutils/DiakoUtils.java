package top.ourisland.diakoutils;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import top.ourisland.diakoutils.command.DiakoCommand;
import top.ourisland.diakoutils.config.ConfigManager;
import top.ourisland.diakoutils.property.ModulePropertyRegistry;
import top.ourisland.diakoutils.property.ModulePropertyService;

public final class DiakoUtils implements ModInitializer {

    public static final String MOD_ID = "diakoutils";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ModulePropertyRegistry PROPERTIES = new ModulePropertyRegistry();
    public static final ModuleManager MODULES = new ModuleManager(PROPERTIES);
    public static final ConfigManager CONFIG = new ConfigManager(MODULES, PROPERTIES);
    public static final ModulePropertyService PROPERTY_SERVICE = new ModulePropertyService(
            MODULES,
            PROPERTIES,
            CONFIG
    );
    private static final String MODULE_PACKAGE = "top.ourisland.diakoutils.modules";

    @Override
    public void onInitialize() {
        MODULES.discoverAndRegister(MOD_ID, MODULE_PACKAGE);

        CONFIG.loadOrCreate();
        DiakoCommand.register();

        ServerLifecycleEvents.SERVER_STARTED.register(MODULES::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(MODULES::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(MODULES::onEndServerTick);

        LOGGER.info("[{}] Loaded {} modules", MOD_ID, MODULES.all().size());
    }

}
