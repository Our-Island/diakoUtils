package top.ourisland.diakoutils.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.config.ConfigPersistenceException;
import top.ourisland.diakoutils.permissions.DiakoPermissions;

import static top.ourisland.diakoutils.command.DiakoCommandComponents.*;

final class ModuleCommands {

    private ModuleCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> listCommand() {
        return Commands.literal("list")
                .requires(source -> DiakoPermissions.hasAny(
                        source,
                        DiakoPermissions.ROOT,
                        DiakoPermissions.LIST
                ))
                .executes(context -> list(context.getSource()));
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> header("Available Modules"), false);
        DiakoUtils.MODULES.all().forEach(module ->
                source.sendSuccess(
                        () -> moduleLine(module),
                        false
                )
        );
        return 1;
    }

    static LiteralArgumentBuilder<CommandSourceStack> statusCommand() {
        return Commands.literal("status")
                .requires(source -> DiakoPermissions.hasAny(
                        source,
                        DiakoPermissions.ROOT,
                        DiakoPermissions.STATUS
                ))
                .then(Commands.argument("module", StringArgumentType.word())
                        .suggests(DiakoCommandSuggestions.MODULE_IDS)
                        .executes(context -> status(
                                context.getSource(),
                                StringArgumentType.getString(context, "module")
                        ))
                );
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
                () -> labelValue(
                        "Properties",
                        String.valueOf(DiakoUtils.PROPERTIES.all(module.id()).size())
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(module.statusLine())
                        .withStyle(ChatFormatting.GRAY),
                false
        );
        source.sendSuccess(
                () -> line(
                        "Use ",
                        "/diako config " + module.id() + " list",
                        " to view or modify properties."
                ),
                false
        );
        return 1;
    }

    static LiteralArgumentBuilder<CommandSourceStack> enableCommand() {
        return Commands.literal("enable")
                .requires(source -> DiakoPermissions.hasAny(
                        source,
                        DiakoPermissions.ROOT,
                        DiakoPermissions.ENABLE
                ))
                .then(Commands.argument("module", StringArgumentType.word())
                        .suggests(DiakoCommandSuggestions.MODULE_IDS)
                        .executes(context -> setEnabled(
                                context.getSource(),
                                StringArgumentType.getString(context, "module"),
                                true
                        ))
                );
    }

    private static int setEnabled(
            CommandSourceStack source,
            String id,
            boolean enabled
    ) {
        var module = DiakoUtils.MODULES.get(id);
        if (module == null) {
            source.sendFailure(error("Unknown module: " + id));
            return 0;
        }

        var wasEnabled = module.enabled();
        if (wasEnabled == enabled) {
            source.sendSuccess(
                    () -> notice("%s is already %s".formatted(
                            id,
                            enabled
                                    ? "enabled."
                                    : "disabled."
                    )),
                    false
            );
            return 1;
        }

        try {
            if (enabled) {
                DiakoUtils.MODULES.enable(id, source.getServer());
            } else {
                DiakoUtils.MODULES.disable(id, source.getServer());
            }
        } catch (RuntimeException e) {
            DiakoUtils.LOGGER.error(
                    "[{}] Failed to change module state for {}",
                    DiakoUtils.MOD_ID,
                    id,
                    e
            );
            source.sendFailure(error(
                    "Module could not be %s: %s".formatted(
                            enabled
                                    ? "enabled"
                                    : "disabled",
                            e.getMessage() == null
                                    ? "module lifecycle failed"
                                    : e.getMessage()
                    )
            ));
            return 0;
        }

        try {
            DiakoUtils.CONFIG.saveOrThrow();
        } catch (ConfigPersistenceException e) {
            try {
                if (wasEnabled) {
                    DiakoUtils.MODULES.enable(id, source.getServer());
                } else {
                    DiakoUtils.MODULES.disable(id, source.getServer());
                }
            } catch (RuntimeException rollbackError) {
                e.addSuppressed(rollbackError);
                module.setEnabled(wasEnabled);
                DiakoUtils.LOGGER.error(
                        "[{}] Failed to restore module state for {} after a persistence error",
                        DiakoUtils.MOD_ID,
                        id,
                        rollbackError
                );
            }

            DiakoUtils.LOGGER.error(
                    "[{}] Failed to persist module state for {}",
                    DiakoUtils.MOD_ID,
                    id,
                    e
            );
            source.sendFailure(error(
                    "Module state was not changed because the configuration file could not be saved."
            ));
            return 0;
        }

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

    static LiteralArgumentBuilder<CommandSourceStack> disableCommand() {
        return Commands.literal("disable")
                .requires(source -> DiakoPermissions.hasAny(
                        source,
                        DiakoPermissions.ROOT,
                        DiakoPermissions.DISABLE
                ))
                .then(Commands.argument("module", StringArgumentType.word())
                        .suggests(DiakoCommandSuggestions.MODULE_IDS)
                        .executes(context -> setEnabled(
                                context.getSource(),
                                StringArgumentType.getString(context, "module"),
                                false
                        ))
                );
    }

    static LiteralArgumentBuilder<CommandSourceStack> reloadCommand() {
        return Commands.literal("reload")
                .requires(source -> DiakoPermissions.hasAny(
                        source,
                        DiakoPermissions.ROOT,
                        DiakoPermissions.RELOAD
                ))
                .executes(context -> {
                    var reloaded = DiakoUtils.CONFIG.reload(context.getSource().getServer());
                    if (!reloaded) {
                        context.getSource().sendFailure(error(
                                "Configuration could not be reloaded. Check the server log."
                        ));
                        return 0;
                    }

                    context.getSource().sendSuccess(
                            () -> success("Configuration reloaded."),
                            true
                    );
                    return 1;
                });
    }

    static int overview(CommandSourceStack source) {
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
        source.sendSuccess(
                () -> line(
                        "Use ",
                        "/diako config <module>",
                        " to view or modify module properties."
                ),
                false
        );
        return 1;
    }

}
