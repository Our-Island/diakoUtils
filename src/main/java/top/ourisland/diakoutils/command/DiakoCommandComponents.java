package top.ourisland.diakoutils.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.IModule;
import top.ourisland.diakoutils.property.ModulePropertyChange;
import top.ourisland.diakoutils.property.ModulePropertyDescriptor;

import java.util.Arrays;

final class DiakoCommandComponents {

    private DiakoCommandComponents() {
    }

    static Component moduleLine(IModule module) {
        var stateColor = module.enabled()
                ? ChatFormatting.GREEN
                : ChatFormatting.RED;
        var state = module.enabled()
                ? "Enabled"
                : "Disabled";

        return Component.empty()
                .append(Component.literal(" • ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(module.id())
                        .withStyle(ChatFormatting.AQUA)
                )
                .append(Component.literal(" — ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(module.displayName())
                        .withStyle(ChatFormatting.WHITE)
                )
                .append(Component.literal(" [")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(state)
                        .withStyle(stateColor)
                )
                .append(Component.literal("]")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal("  " + module.description())
                        .withStyle(ChatFormatting.GRAY)
                );
    }

    static Component propertyTitle(ModulePropertyDescriptor<?> descriptor) {
        return Component.empty()
                .append(Component.literal(" • ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(descriptor.id())
                        .withStyle(ChatFormatting.AQUA)
                )
                .append(Component.literal(" — ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(descriptor.displayName())
                        .withStyle(ChatFormatting.WHITE)
                );
    }

    static Component propertyUpdate(
            String action,
            String moduleId,
            ModulePropertyChange<?> change
    ) {
        var descriptor = DiakoUtils.PROPERTIES.get(moduleId, change.propertyId());
        return prefix()
                .append(Component.literal(action + " ")
                        .withStyle(ChatFormatting.GREEN)
                )
                .append(Component.literal(moduleId + "." + change.propertyId())
                        .withStyle(ChatFormatting.AQUA)
                )
                .append(Component.literal(": ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(format(descriptor, change.oldValue()))
                        .withStyle(valueColor(change.oldValue()))
                )
                .append(Component.literal(" -> ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(format(descriptor, change.newValue()))
                        .withStyle(valueColor(change.newValue()))
                );
    }

    private static MutableComponent prefix() {
        return Component.empty()
                .append(Component.literal("diakoUtils")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                )
                .append(Component.literal(" › ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                );
    }

    @SuppressWarnings("unchecked")
    static <T> String format(ModulePropertyDescriptor<T> descriptor, Object value) {
        return descriptor.format((T) value);
    }

    static ChatFormatting valueColor(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue
                    ? ChatFormatting.GREEN
                    : ChatFormatting.RED;
        }
        return ChatFormatting.WHITE;
    }

    static Component propertyChangeLine(
            String moduleId,
            ModulePropertyChange<?> change
    ) {
        var descriptor = DiakoUtils.PROPERTIES.get(moduleId, change.propertyId());
        return Component.empty()
                .append(Component.literal(" • ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(change.propertyId())
                        .withStyle(ChatFormatting.AQUA)
                )
                .append(Component.literal(": ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(format(descriptor, change.oldValue()))
                        .withStyle(valueColor(change.oldValue()))
                )
                .append(Component.literal(" -> ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(format(descriptor, change.newValue()))
                        .withStyle(valueColor(change.newValue()))
                );
    }

    static Component header(String title) {
        return prefix().append(
                Component.literal(title)
                        .withStyle(ChatFormatting.YELLOW)
        );
    }

    static Component success(String message) {
        return prefix().append(Component.literal(message)
                .withStyle(ChatFormatting.GREEN)
        );
    }

    static Component notice(String message) {
        return prefix().append(Component.literal(message)
                .withStyle(ChatFormatting.YELLOW)
        );
    }

    static Component error(String message) {
        return prefix().append(Component.literal(message)
                .withStyle(ChatFormatting.RED)
        );
    }

    static Component labelValue(String label, String value) {
        return labelValue(label, value, ChatFormatting.WHITE);
    }

    static Component labelValue(
            String label,
            String value,
            ChatFormatting valueColor
    ) {
        return Component.empty()
                .append(Component.literal(label + ": ")
                        .withStyle(ChatFormatting.GRAY)
                )
                .append(Component.literal(value)
                        .withStyle(valueColor)
                );
    }

    static Component indentedLabelValue(String label, String value) {
        return indentedLabelValue(label, value, ChatFormatting.WHITE);
    }

    static Component indentedLabelValue(
            String label,
            String value,
            ChatFormatting valueColor
    ) {
        return Component.empty()
                .append(Component.literal("   " + label + ": ")
                        .withStyle(ChatFormatting.GRAY)
                )
                .append(Component.literal(value)
                        .withStyle(valueColor)
                );
    }

    static Component line(String... parts) {
        var result = Component.empty().withStyle(ChatFormatting.GRAY);
        Arrays.stream(parts).forEach(part -> {
            var command = part.startsWith("/");
            result.append(Component.literal(part)
                    .withStyle(command
                            ? ChatFormatting.AQUA
                            : ChatFormatting.GRAY
                    )
            );
        });
        return result;
    }

    static <T> T get(ModulePropertyDescriptor<T> descriptor, IModule module) {
        return descriptor.get(module);
    }

}
