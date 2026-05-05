package top.ourisland.diakoutils;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import top.ourisland.diakoutils.command.DiakoCommand;
import top.ourisland.diakoutils.config.ConfigManager;
import top.ourisland.diakoutils.modules.entitiesmonitor.EntitiesMonitorModule;
import top.ourisland.diakoutils.modules.messagehider.MessageHiderModule;

public final class DiakoUtils implements ModInitializer {

    public static final String MOD_ID = "diakoutils";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final ModuleManager MODULES = new ModuleManager();
    public static final ConfigManager CONFIG = new ConfigManager(MODULES);

    @Override
    public void onInitialize() {
        MODULES.register(new MessageHiderModule());
        MODULES.register(new EntitiesMonitorModule());

        CONFIG.loadOrCreate();
        DiakoCommand.register();

        ServerTickEvents.END_SERVER_TICK.register(MODULES::onEndServerTick);

        LOGGER.info("[{}] Loaded {} modules", MOD_ID, MODULES.all().size());
    }

}
