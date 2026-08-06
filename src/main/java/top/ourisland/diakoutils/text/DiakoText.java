package top.ourisland.diakoutils.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class DiakoText {

    private DiakoText() {
    }

    public static Component warning(String message) {
        return prefix().append(
                Component.literal(message).withStyle(ChatFormatting.YELLOW)
        );
    }

    public static MutableComponent prefix() {
        return Component.empty()
                .append(Component.literal("diakoUtils")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                )
                .append(separator());
    }

    private static MutableComponent separator() {
        return Component.literal(" › ")
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    public static Component success(String message) {
        return prefix().append(
                Component.literal(message).withStyle(ChatFormatting.GREEN)
        );
    }

    public static Component error(String message) {
        return prefix().append(
                Component.literal(message).withStyle(ChatFormatting.RED)
        );
    }

    public static MutableComponent moduleBody(String moduleName) {
        return modulePrefix(moduleName)
                .append(Component.empty().withStyle(ChatFormatting.GRAY));
    }

    public static MutableComponent modulePrefix(String moduleName) {
        return prefix()
                .append(Component.literal(moduleName)
                        .withStyle(ChatFormatting.AQUA)
                )
                .append(separator());
    }

    public static Component moduleTemplate(
            String moduleName,
            String template,
            Map<String, Component> replacements
    ) {
        var result = modulePrefix(moduleName);
        var remaining = template;

        while (!remaining.isEmpty()) {
            String nextToken = null;
            var nextIndex = Integer.MAX_VALUE;

            for (var token : replacements.keySet()) {
                var index = remaining.indexOf(token);
                if (index >= 0 && index < nextIndex) {
                    nextIndex = index;
                    nextToken = token;
                }
            }

            if (nextToken == null) {
                result.append(Component.literal(remaining)
                        .withStyle(ChatFormatting.GRAY)
                );
                break;
            }

            if (nextIndex > 0) {
                result.append(Component.literal(remaining.substring(0, nextIndex))
                        .withStyle(ChatFormatting.GRAY)
                );
            }
            result.append(replacements.get(nextToken));
            remaining = remaining.substring(nextIndex + nextToken.length());
        }

        return result;
    }

    public static Map<String, Component> replacements(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Replacement entries must be token/component pairs");
        }

        return IntStream.iterate(
                        0,
                        index -> index < entries.length,
                        index -> index + 2
                )
                .boxed()
                .collect(Collectors.toMap(
                        index -> (String) entries[index],
                        index -> (Component) entries[index + 1],
                        (_, b) -> b,
                        LinkedHashMap::new
                ));
    }

}
