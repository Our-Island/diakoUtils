package top.ourisland.diakoutils.modules.entitiesmonitor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import top.ourisland.diakoutils.AbstractModule;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.TickingModule;
import top.ourisland.diakoutils.annotation.DiakoModule;
import top.ourisland.diakoutils.annotation.ModuleProperty;
import top.ourisland.diakoutils.property.ModulePropertyChange;
import top.ourisland.diakoutils.property.PropertyValidationResult;
import top.ourisland.diakoutils.text.DiakoText;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

@DiakoModule(
        id = "entities_monitor",
        displayName = "Entities Monitor",
        description = "Monitor total entity count and notify players when it exceeds a threshold."
)
public final class EntitiesMonitorModule extends AbstractModule implements TickingModule {

    @ModuleProperty(
            id = "threshold",
            displayName = "Entity Threshold",
            description = "Entity count used to trigger a warning.",
            min = "0",
            max = "2147483647",
            order = 10
    )
    private int threshold = 2000;

    @ModuleProperty(
            id = "check_interval_ticks",
            displayName = "Check Interval",
            description = "Number of server ticks between entity checks.",
            min = "1",
            max = "72000",
            order = 20
    )
    private int checkIntervalTicks = 100;

    @ModuleProperty(
            id = "cooldown_ticks",
            displayName = "Warning Cooldown",
            description = "Minimum ticks between repeated warnings.",
            min = "0",
            max = "720000",
            order = 30
    )
    private int cooldownTicks = 200;

    @ModuleProperty(
            id = "overlay",
            displayName = "Overlay Output",
            description = "Send warnings through the overlay/actionbar.",
            order = 40
    )
    private boolean overlay = false;

    @ModuleProperty(
            id = "message_template",
            displayName = "Message Template",
            description = "Warning body. Supports {count} and {threshold}.",
            maxLength = 512,
            order = 50
    )
    private String messageTemplate = "Entity count {count} exceeded the threshold of {threshold}.";

    private long tickCounter;
    private long lastNotifyTick = -1;
    private boolean lastWasOver;

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Override
    public void onEnable(MinecraftServer server) {
        resetRuntimeState();
    }

    @Override
    public void onDisable(MinecraftServer server) {
        resetRuntimeState();
    }

    @Override
    public PropertyValidationResult validateProperties(Map<String, Object> candidateValues) {
        var template = (String) candidateValues.get("message_template");
        if (!template.contains("{count}")) {
            return PropertyValidationResult.failure(
                    "message_template must contain {count}."
            );
        }

        return PropertyValidationResult.success();
    }

    @Override
    public void onPropertiesChanged(
            MinecraftServer server,
            Collection<ModulePropertyChange<?>> changes
    ) {
        var shouldResetCounters = changes.stream()
                .map(ModulePropertyChange::propertyId)
                .anyMatch(id -> switch (id) {
                    case "threshold", "check_interval_ticks", "cooldown_ticks" -> true;
                    default -> false;
                });

        if (shouldResetCounters) {
            resetRuntimeState();
        }
    }

    @Override
    public String statusLine() {
        return "Threshold: %d | Check interval: %d ticks | Cooldown: %d ticks | Overlay: %s".formatted(
                threshold,
                checkIntervalTicks,
                cooldownTicks,
                overlay
                        ? "on"
                        : "off"
        );
    }

    private void resetRuntimeState() {
        tickCounter = 0;
        lastNotifyTick = -1;
        lastWasOver = false;
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
            var text = DiakoText.moduleTemplate(
                    displayName(),
                    messageTemplate,
                    DiakoText.replacements(
                            "{count}",
                            Component.literal(formatNumber(total))
                                    .withStyle(ChatFormatting.YELLOW),
                            "{threshold}",
                            Component.literal(formatNumber(threshold))
                                    .withStyle(ChatFormatting.YELLOW)
                    )
            );
            server.getPlayerList().broadcastSystemMessage(
                    text,
                    _ -> text,
                    overlay
            );

            var logMessage = messageTemplate
                    .replace("{count}", formatNumber(total))
                    .replace("{threshold}", formatNumber(threshold));
            DiakoUtils.LOGGER.warn("[{}] {}", id(), logMessage);
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
