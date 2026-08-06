package top.ourisland.diakoutils.modules.itemcleaner;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import top.ourisland.diakoutils.AbstractModule;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.TickingModule;
import top.ourisland.diakoutils.annotation.DiakoModule;
import top.ourisland.diakoutils.annotation.ModuleProperty;
import top.ourisland.diakoutils.property.ModulePropertyChange;
import top.ourisland.diakoutils.property.PropertyValidationResult;
import top.ourisland.diakoutils.text.DiakoText;

import java.util.*;

@DiakoModule(
        id = "item_cleaner",
        displayName = "Item Cleaner",
        description = "Remove excessive dropped items using configurable filters."
)
public final class ItemCleanerModule extends AbstractModule implements TickingModule {

    private static final List<String> DEFAULT_ITEMS = List.of(
            "#diakoutils:item_cleaner/stone_like",
            "minecraft:stone",
            "minecraft:cobblestone",
            "minecraft:netherrack",
            "minecraft:end_stone"
    );

    @ModuleProperty(
            id = "mode",
            displayName = "Filter Mode",
            description = "Whitelist, blacklist, or all dropped items.",
            order = 10
    )
    private ItemCleanerMode mode = ItemCleanerMode.WHITELIST;

    @ModuleProperty(
            id = "items",
            displayName = "Item List",
            description = "Item IDs or item tags used by whitelist and blacklist modes.",
            order = 20
    )
    private List<String> items = DEFAULT_ITEMS;

    @ModuleProperty(
            id = "threshold",
            displayName = "Item Threshold",
            description = "Start a cleanup countdown when matching item count exceeds this value. Notice this is NOT item entity count!",
            min = "1",
            max = "9223372036854775807",
            order = 30
    )
    private long threshold = 2500 * 64;

    @ModuleProperty(
            id = "warning_duration_seconds",
            displayName = "Warning Duration",
            description = "Seconds between the warning and item cleanup.",
            min = "1",
            max = "300",
            order = 40
    )
    private int warningDurationSeconds = 10;

    @ModuleProperty(
            id = "check_interval_ticks",
            displayName = "Check Interval",
            description = "Number of server ticks between dropped-item scans.",
            min = "1",
            max = "1200",
            order = 50
    )
    private int checkIntervalTicks = 20;

    private CleanupState state = CleanupState.IDLE;
    private ItemFilter compiledFilter;
    private long tickCounter;
    private long nextCheckTick;
    private long cleanupAtTick;
    private long nextCountdownScanTick;
    private int lastDisplayedSecond = -1;

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static void broadcastChat(
            MinecraftServer server,
            Component message
    ) {
        server.getPlayerList().broadcastSystemMessage(
                message,
                _ -> message,
                false
        );
    }

    private static void broadcastActionBar(
            MinecraftServer server,
            Component message
    ) {
        server.getPlayerList().broadcastSystemMessage(
                message,
                _ -> message,
                true
        );
    }

    @Override
    public void onEnable(MinecraftServer server) {
        compiledFilter = ItemFilterCompiler.compile(mode, items);
        if (mode == ItemCleanerMode.ALL) {
            logAllModeWarning();
        }
        resetRuntimeState();
    }

    @Override
    public void onDisable(MinecraftServer server) {
        resetRuntimeState();
    }

    @Override
    public PropertyValidationResult validateProperties(Map<String, Object> candidateValues) {
        var candidateMode = (ItemCleanerMode) candidateValues.get("mode");
        @SuppressWarnings("unchecked")
        var candidateItems = (List<String>) candidateValues.get("items");

        if (candidateMode != ItemCleanerMode.ALL && candidateItems.isEmpty()) {
            return PropertyValidationResult.failure(
                    "items must not be empty in whitelist or blacklist mode."
            );
        }

        //noinspection resource
        if (DiakoUtils.MODULES.currentServer() != null) {
            try {
                ItemFilterCompiler.compile(candidateMode, candidateItems);
            } catch (IllegalArgumentException e) {
                return PropertyValidationResult.failure(e.getMessage());
            }
        }

        return PropertyValidationResult.success();
    }

    @Override
    public void onPropertiesChanged(
            MinecraftServer server,
            Collection<ModulePropertyChange<?>> changes
    ) {
        var rebuildFilter = changes.stream()
                .map(ModulePropertyChange::propertyId)
                .anyMatch(id -> id.equals("mode") || id.equals("items"));

        if (rebuildFilter) {
            compiledFilter = ItemFilterCompiler.compile(mode, items);

            if (changes.stream().anyMatch(change ->
                    change.propertyId().equals("mode")
                            && change.newValue() == ItemCleanerMode.ALL
            )) {
                logAllModeWarning();
            }
        }

        resetRuntimeState();
    }

    @Override
    public String statusLine() {
        return "Mode: %s | Threshold: %d items | Warning: %d seconds | Check interval: %d ticks".formatted(
                mode.name().toLowerCase(Locale.ROOT),
                threshold,
                warningDurationSeconds,
                checkIntervalTicks
        );
    }

    private void logAllModeWarning() {
        DiakoUtils.LOGGER.warn(
                "[{}] ALL mode is enabled; every loaded dropped item may be removed",
                id()
        );
    }

    private void resetRuntimeState() {
        state = CleanupState.IDLE;
        nextCheckTick = tickCounter + checkIntervalTicks;
        cleanupAtTick = 0L;
        nextCountdownScanTick = 0L;
        lastDisplayedSecond = -1;
    }

    @Override
    public void onEndServerTick(MinecraftServer server) {
        tickCounter++;

        if (compiledFilter == null) {
            compiledFilter = ItemFilterCompiler.compile(mode, items);
        }

        switch (state) {
            case IDLE -> tickIdle(server);
            case COUNTDOWN -> tickCountdown(server);
        }
    }

    private void tickIdle(MinecraftServer server) {
        if (tickCounter < nextCheckTick) {
            return;
        }
        nextCheckTick = tickCounter + checkIntervalTicks;

        var result = countMatchingItems(server);
        if (result.itemCount() <= threshold) {
            return;
        }

        state = CleanupState.COUNTDOWN;
        cleanupAtTick = tickCounter + warningDurationSeconds * 20L;
        nextCountdownScanTick = tickCounter;
        lastDisplayedSecond = -1;

        broadcastChat(server, countdownStarted(result.itemCount()));
        broadcastActionBar(server, countdownActionBar(result.itemCount(), warningDurationSeconds));
        lastDisplayedSecond = warningDurationSeconds;
        nextCountdownScanTick = tickCounter + 20L;

        DiakoUtils.LOGGER.warn(
                "[{}] Cleanup scheduled: {} items, threshold {}, delay {}s",
                id(),
                result.itemCount(),
                threshold,
                warningDurationSeconds
        );
    }

    private void tickCountdown(MinecraftServer server) {
        if (tickCounter >= cleanupAtTick) {
            finishCountdown(server);
            return;
        }

        if (tickCounter < nextCountdownScanTick) {
            return;
        }
        nextCountdownScanTick = tickCounter + 20L;

        var result = countMatchingItems(server);
        if (result.itemCount() <= threshold) {
            cancelCountdown(server, result.itemCount());
            return;
        }

        var remainingSeconds = (int) Math.max(
                1L,
                (cleanupAtTick - tickCounter + 19L) / 20L
        );
        if (remainingSeconds != lastDisplayedSecond) {
            broadcastActionBar(
                    server,
                    countdownActionBar(result.itemCount(), remainingSeconds)
            );
            lastDisplayedSecond = remainingSeconds;
        }
    }

    private void finishCountdown(MinecraftServer server) {
        var result = collectMatchingItems(server);
        if (result.itemCount() <= threshold) {
            cancelCountdown(server, result.itemCount());
            return;
        }

        var removedItems = 0L;
        var removedStacks = 0;
        for (var entity : result.entities()) {
            if (entity.isRemoved()) {
                continue;
            }

            var stack = entity.getItem();
            if (stack.isEmpty() || !compiledFilter.matches(mode, stack)) {
                continue;
            }

            removedItems += stack.getCount();
            removedStacks++;
            entity.discard();
        }

        broadcastChat(server, cleanupCompleted(removedItems, removedStacks));
        DiakoUtils.LOGGER.info(
                "[{}] Cleanup completed: {} items across {} stacks",
                id(),
                removedItems,
                removedStacks
        );
        resetRuntimeState();
    }

    private void cancelCountdown(MinecraftServer server, long itemCount) {
        broadcastChat(server, cleanupCancelled(itemCount));
        DiakoUtils.LOGGER.info(
                "[{}] Cleanup cancelled: count dropped to {}",
                id(),
                itemCount
        );
        resetRuntimeState();
    }

    private ItemScanResult countMatchingItems(MinecraftServer server) {
        return scanMatchingItems(server, false);
    }

    private ItemScanResult collectMatchingItems(MinecraftServer server) {
        return scanMatchingItems(server, true);
    }

    private ItemScanResult scanMatchingItems(
            MinecraftServer server,
            boolean retainEntities
    ) {
        var itemCount = 0L;
        var stackCount = 0;
        var retained = retainEntities
                ? new ArrayList<ItemEntity>()
                : null;

        for (var level : server.getAllLevels()) {
            var levelItems = new ArrayList<ItemEntity>();
            level.getEntities(
                    EntityTypeTest.forClass(ItemEntity.class),
                    entity -> !entity.isRemoved(),
                    levelItems
            );

            for (var entity : levelItems) {
                var stack = entity.getItem();
                if (stack.isEmpty() || !compiledFilter.matches(mode, stack)) {
                    continue;
                }

                itemCount += stack.getCount();
                stackCount++;
                if (retained != null) {
                    retained.add(entity);
                }
            }
        }

        return new ItemScanResult(
                itemCount,
                stackCount,
                retained == null
                        ? List.of()
                        : retained
        );
    }

    private Component countdownStarted(long itemCount) {
        return DiakoText.modulePrefix(displayName())
                .append(Component.literal(formatNumber(itemCount))
                        .withStyle(ChatFormatting.YELLOW)
                )
                .append(Component.literal(" matching dropped items exceed the threshold of ")
                        .withStyle(ChatFormatting.GRAY)
                )
                .append(Component.literal(formatNumber(threshold))
                        .withStyle(ChatFormatting.YELLOW)
                )
                .append(Component.literal(". Cleanup starts in ")
                        .withStyle(ChatFormatting.GRAY)
                )
                .append(Component.literal(warningDurationSeconds + " seconds")
                        .withStyle(ChatFormatting.RED)
                )
                .append(Component.literal(".")
                        .withStyle(ChatFormatting.GRAY)
                );
    }

    private Component countdownActionBar(long itemCount, int seconds) {
        return DiakoText.modulePrefix(displayName())
                .append(Component.literal("Clearing ")
                        .withStyle(ChatFormatting.GRAY)
                )
                .append(Component.literal(formatNumber(itemCount))
                        .withStyle(ChatFormatting.YELLOW)
                )
                .append(Component.literal(" items in ")
                        .withStyle(ChatFormatting.GRAY)
                )
                .append(Component.literal(seconds + " seconds")
                        .withStyle(ChatFormatting.RED)
                )
                .append(Component.literal(".")
                        .withStyle(ChatFormatting.GRAY)
                );
    }

    private Component cleanupCancelled(long itemCount) {
        return DiakoText.modulePrefix(displayName())
                .append(Component.literal("Cleanup cancelled")
                        .withStyle(ChatFormatting.YELLOW)
                )
                .append(Component.literal(". Matching item count is now ")
                        .withStyle(ChatFormatting.GRAY)
                )
                .append(Component.literal(formatNumber(itemCount))
                        .withStyle(ChatFormatting.GREEN)
                )
                .append(Component.literal(".")
                        .withStyle(ChatFormatting.GRAY)
                );
    }

    private Component cleanupCompleted(long itemCount, int stackCount) {
        return DiakoText.modulePrefix(displayName())
                .append(Component.literal("Cleared ")
                        .withStyle(ChatFormatting.GREEN)
                )
                .append(Component.literal(formatNumber(itemCount))
                        .withStyle(ChatFormatting.YELLOW)
                )
                .append(Component.literal(" items across ")
                        .withStyle(ChatFormatting.GRAY)
                )
                .append(Component.literal(formatNumber(stackCount))
                        .withStyle(ChatFormatting.YELLOW)
                )
                .append(Component.literal(" dropped stacks.")
                        .withStyle(ChatFormatting.GRAY)
                );
    }

    private enum CleanupState {

        IDLE,
        COUNTDOWN

    }

}
