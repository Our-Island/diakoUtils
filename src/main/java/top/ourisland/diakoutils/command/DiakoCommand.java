package top.ourisland.diakoutils.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.IModule;
import top.ourisland.diakoutils.permissions.DiakoPermissions;

import java.util.Arrays;

public final class DiakoCommand {

    private static final SuggestionProvider<CommandSourceStack> MODULE_ID_SUGGESTIONS = (_, builder) ->
            SharedSuggestionProvider.suggest(DiakoUtils.MODULES.ids(), builder);

    private DiakoCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((
                dispatcher,
                _,
                _
        ) -> dispatcher.register(
                Commands.literal("diako")
                        .requires(source -> DiakoPermissions.hasAny(
                                source,
                                DiakoPermissions.ROOT,
                                DiakoPermissions.LIST,
                                DiakoPermissions.STATUS,
                                DiakoPermissions.ENABLE,
                                DiakoPermissions.DISABLE,
                                DiakoPermissions.RELOAD
                        ))
                        .executes(context -> overview(context.getSource()))
                        .then(listCommand())
                        .then(statusCommand())
                        .then(enableCommand())
                        .then(disableCommand())
                        .then(reloadCommand())
        ));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> listCommand() {
        return Commands.literal("list")
                .requires(source -> DiakoPermissions.hasAny(
                        source,
                        DiakoPermissions.ROOT,
                        DiakoPermissions.LIST
                ))
                .executes(context -> list(context.getSource()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> statusCommand() {
        return Commands.literal("status")
                .requires(source -> DiakoPermissions.hasAny(
                        source,
                        DiakoPermissions.ROOT,
                        DiakoPermissions.STATUS
                ))
                .then(Commands.argument("module", StringArgumentType.word())
                        .suggests(MODULE_ID_SUGGESTIONS)
                        .executes(context -> status(
                                context.getSource(),
                                StringArgumentType.getString(context, "module")
                        ))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> enableCommand() {
        return Commands.literal("enable")
                .requires(source -> DiakoPermissions.hasAny(
                        source,
                        DiakoPermissions.ROOT,
                        DiakoPermissions.ENABLE
                ))
                .then(Commands.argument("module", StringArgumentType.word())
                        .suggests(MODULE_ID_SUGGESTIONS)
                        .executes(context -> setEnabled(
                                context.getSource(),
                                StringArgumentType.getString(context, "module"),
                                true
                        ))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> disableCommand() {
        return Commands.literal("disable")
                .requires(source -> DiakoPermissions.hasAny(
                        source,
                        DiakoPermissions.ROOT,
                        DiakoPermissions.DISABLE
                ))
                .then(Commands.argument("module", StringArgumentType.word())
                        .suggests(MODULE_ID_SUGGESTIONS)
                        .executes(context -> setEnabled(
                                context.getSource(),
                                StringArgumentType.getString(context, "module"),
                                false
                        ))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reloadCommand() {
        return Commands.literal("reload")
                .requires(source -> DiakoPermissions.hasAny(
                        source,
                        DiakoPermissions.ROOT,
                        DiakoPermissions.RELOAD
                ))
                .executes(context -> {
                    DiakoUtils.CONFIG.reload(context.getSource().getServer());
                    context.getSource().sendSuccess(
                            () -> success("Configuration reloaded."),
                            true
                    );
                    return 1;
                });
    }

    private static int overview(CommandSourceStack source) {
        source.sendSuccess(
                () -> header(
                        "diakoUtils"
                ),
                false
        );
        source.sendSuccess(
                () -> line(
                        "Use ",
                        "/diako list",
                        " to view modules."
                ),
                false
        );
        source.sendSuccess(
                () -> line(
                        "Use ",
                        "/diako enable <module>",
                        " or ",
                        "/diako disable <module>",
                        " to change a module."
                ),
                false
        );
        return 1;
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> header("Available Modules"), false);
        DiakoUtils.MODULES.all().forEach(module -> source.sendSuccess(
                        () -> moduleLine(module),
                        false
                )
        );
        return 1;
    }

    private static int status(CommandSourceStack source, String id) {
        var module = DiakoUtils.MODULES.get(id);
        if (module == null) {
            source.sendFailure(error("Unknown module: " + id));
            return 0;
        }

        source.sendSuccess(
                () -> header(
                        module.displayName()
                ),
                false
        );
        source.sendSuccess(
                () -> labelValue(
                        "Module ID",
                        module.id()
                ),
                false
        );
        source.sendSuccess(
                () -> labelValue(
                        "State",
                        module.enabled()
                                ? "Enabled"
                                : "Disabled",
                        module.enabled()
                                ? ChatFormatting.GREEN
                                : ChatFormatting.RED
                ),
                false
        );
        source.sendSuccess(
                () -> labelValue(
                        "Description",
                        module.description()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(module.statusLine())
                        .withStyle(ChatFormatting.GRAY),
                false
        );
        return 1;
    }

    private static int setEnabled(
            CommandSourceStack source,
            String id,
            boolean enabled
    ) {
        var found = enabled
                ? DiakoUtils.MODULES.enable(id, source.getServer())
                : DiakoUtils.MODULES.disable(id, source.getServer());

        if (!found) {
            source.sendFailure(error("Unknown module: " + id));
            return 0;
        }

        DiakoUtils.CONFIG.save();
        source.sendSuccess(
                () -> success("%s%s.".formatted(
                        enabled
                                ? "Enabled "
                                : "Disabled ",
                        id
                )),
                true
        );
        return 1;
    }

    private static Component moduleLine(IModule module) {
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

    private static Component header(String title) {
        return Component.empty()
                .append(Component.literal("diakoUtils")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                )
                .append(Component.literal(" › ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(title)
                        .withStyle(ChatFormatting.YELLOW)
                );
    }

    private static Component success(String message) {
        return Component.empty()
                .append(Component.literal("diakoUtils")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                )
                .append(Component.literal(" › ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(message)
                        .withStyle(ChatFormatting.GREEN)
                );
    }

    private static Component error(String message) {
        return Component.empty()
                .append(Component.literal("diakoUtils")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                )
                .append(Component.literal(" › ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
                .append(Component.literal(message)
                        .withStyle(ChatFormatting.RED)
                );
    }

    private static Component labelValue(String label, String value) {
        return labelValue(label, value, ChatFormatting.WHITE);
    }

    private static Component labelValue(
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

    private static Component line(String... parts) {
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

}
