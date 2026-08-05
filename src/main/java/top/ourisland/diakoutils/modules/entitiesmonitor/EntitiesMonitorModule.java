package top.ourisland.diakoutils.modules.entitiesmonitor;

import com.electronwill.nightconfig.core.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import top.ourisland.diakoutils.AbstractModule;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.TickingModule;
import top.ourisland.diakoutils.annotation.DiakoModule;

@DiakoModule(
        id = "entities_monitor",
        displayName = "Entities Monitor",
        description = "Monitor total entity count and notify players when it exceeds a threshold."
)
public final class EntitiesMonitorModule extends AbstractModule implements TickingModule {

    private int threshold = 800;
    private int checkIntervalTicks = 100;
    private int cooldownTicks = 200;
    private boolean overlay = false;
    private String messageTemplate = "[EntitiesMonitor] TOO MANY ENTITIES!!!!! {count} (Threshold {threshold})";

    private long tickCounter = 0;
    private long lastNotifyTick = -1;
    private boolean lastWasOver = false;

    @Override
    public void loadConfig(Config config, String path) {
        var enabledValue = config.get(path + ".enabled");
        setEnabled(enabledValue instanceof Boolean enabled && enabled);

        threshold = intValue(
                config.get(path + ".threshold"),
                threshold,
                0
        );
        checkIntervalTicks = intValue(
                config.get(path + ".check_interval_ticks"),
                checkIntervalTicks,
                1
        );
        cooldownTicks = intValue(
                config.get(path + ".cooldown_ticks"),
                cooldownTicks,
                0
        );

        var overlayValue = config.get(path + ".overlay");
        if (overlayValue instanceof Boolean value) {
            overlay = value;
        }

        var messageValue = config.get(path + ".message_template");
        if (messageValue instanceof String value && !value.isBlank()) {
            messageTemplate = value;
        }
    }

    @Override
    public void saveConfig(Config config, String path) {
        config.set(path + ".enabled", enabled());
        config.set(path + ".threshold", threshold);
        config.set(path + ".check_interval_ticks", checkIntervalTicks);
        config.set(path + ".cooldown_ticks", cooldownTicks);
        config.set(path + ".overlay", overlay);
        config.set(path + ".message_template", messageTemplate);
    }

    @Override
    public void onEnable(MinecraftServer server) {
        tickCounter = 0;
        lastNotifyTick = -1;
        lastWasOver = false;
    }

    @Override
    public String statusLine() {
        return "Status: %s | Threshold: %d | Check interval: %d ticks | Cooldown: %d ticks | Overlay: %s".formatted(
                enabled()
                        ? "§aEnabled§r"
                        : "§cDisabled§r",
                threshold,
                checkIntervalTicks,
                cooldownTicks,
                overlay
                        ? "§aon§r"
                        : "§coff§r"
        );
    }

    private static int intValue(
            Object value,
            int fallback,
            int min
    ) {
        var parsed = value instanceof Number number
                ? number.intValue()
                : fallback;
        return Math.max(min, parsed);
    }

    @Override
    public void onEndServerTick(MinecraftServer server) {
        tickCounter++;

        if (tickCounter % checkIntervalTicks != 0) {
            return;
        }

        var total = countAllEntities(server);
        var over = total > threshold;
        var cooldownReady = lastNotifyTick < 0 || tickCounter - lastNotifyTick >= cooldownTicks;
        var shouldNotify = over && (!lastWasOver || cooldownReady);

        if (shouldNotify) {
            var message = messageTemplate
                    .replace("{count}", String.valueOf(total))
                    .replace("{threshold}", String.valueOf(threshold));

            var text = Component.literal(message);
            var playerList = server.getPlayerList();
            playerList.broadcastSystemMessage(text, _ -> text, overlay);

            DiakoUtils.LOGGER.info("[{}] {}", id(), message);
            lastNotifyTick = tickCounter;
        }

        lastWasOver = over;
    }

    private int countAllEntities(MinecraftServer server) {
        var counter = new CountingList<Entity>();
        var anyEntity = EntityTypeTest.forClass(Entity.class);

        for (var level : server.getAllLevels()) {
            level.getEntities(
                    anyEntity,
                    _ -> true,
                    counter
            );
        }

        return counter.getCount();
    }

}
