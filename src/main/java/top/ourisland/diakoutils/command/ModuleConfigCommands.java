package top.ourisland.diakoutils.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.permissions.DiakoPermissions;
import top.ourisland.diakoutils.property.PropertyOperationResult;

import static top.ourisland.diakoutils.command.DiakoCommandComponents.*;

final class ModuleConfigCommands {

    private ModuleConfigCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("config")
                .requires(source -> DiakoPermissions.hasAny(
                        source,
                        DiakoPermissions.ROOT,
                        DiakoPermissions.CONFIG,
                        DiakoPermissions.CONFIG_GET,
                        DiakoPermissions.CONFIG_SET,
                        DiakoPermissions.CONFIG_RESET
                ))
                .then(Commands.argument("module", StringArgumentType.word())
                        .suggests(DiakoCommandSuggestions.MODULE_IDS)
                        .executes(ModuleConfigCommands::listPropertiesWithPermission)
                        .then(Commands.literal("list")
                                .requires(ModuleConfigCommands::canReadConfig)
                                .executes(context -> listProperties(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "module")
                                ))
                        )
                        .then(Commands.literal("get")
                                .requires(ModuleConfigCommands::canReadConfig)
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .suggests(DiakoCommandSuggestions.PROPERTY_IDS)
                                        .executes(context -> getProperty(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "module"),
                                                StringArgumentType.getString(context, "property")
                                        ))
                                )
                        )
                        .then(Commands.literal("set")
                                .requires(ModuleConfigCommands::canSetConfig)
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .suggests(DiakoCommandSuggestions.PROPERTY_IDS)
                                        .then(Commands.argument(
                                                                "value",
                                                                StringArgumentType.greedyString()
                                                        )
                                                        .suggests(DiakoCommandSuggestions.PROPERTY_VALUES)
                                                        .executes(context -> setProperty(
                                                                context.getSource(),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "module"
                                                                ),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "property"
                                                                ),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "value"
                                                                )
                                                        ))
                                        )
                                )
                        )
                        .then(Commands.literal("reset")
                                .requires(ModuleConfigCommands::canResetConfig)
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .suggests(DiakoCommandSuggestions.PROPERTY_IDS)
                                        .executes(context -> resetProperty(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "module"),
                                                StringArgumentType.getString(context, "property")
                                        ))
                                )
                        )
                        .then(Commands.literal("reset-all")
                                .requires(ModuleConfigCommands::canResetConfig)
                                .executes(context -> resetAllProperties(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "module")
                                ))
                        )
                );
    }

    private static int listPropertiesWithPermission(CommandContext<CommandSourceStack> context) {
        if (!canReadConfig(context.getSource())) {
            context.getSource().sendFailure(error(
                    "You do not have permission to view module properties."
            ));
            return 0;
        }

        return listProperties(
                context.getSource(),
                StringArgumentType.getString(context, "module")
        );
    }

    private static boolean canReadConfig(CommandSourceStack source) {
        return DiakoPermissions.hasAny(
                source,
                DiakoPermissions.ROOT,
                DiakoPermissions.CONFIG,
                DiakoPermissions.CONFIG_GET
        );
    }

    private static int listProperties(CommandSourceStack source, String moduleId) {
        var module = DiakoUtils.MODULES.get(moduleId);
        if (module == null) {
            source.sendFailure(error("Unknown module: " + moduleId));
            return 0;
        }

        var descriptors = DiakoUtils.PROPERTIES.all(moduleId);
        source.sendSuccess(
                () -> header(module.displayName() + " Properties"),
                false
        );
        if (descriptors.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("No configurable properties.")
                            .withStyle(ChatFormatting.GRAY),
                    false
            );
            return 1;
        }

        descriptors.forEach(descriptor -> {
            var value = get(descriptor, module);
            source.sendSuccess(
                    () -> propertyTitle(
                            descriptor
                    ),
                    false
            );
            source.sendSuccess(
                    () -> indentedLabelValue(
                            "Value",
                            format(descriptor, value),
                            valueColor(value)
                    ),
                    false
            );
            source.sendSuccess(
                    () -> indentedLabelValue(
                            "Default",
                            format(descriptor, descriptor.defaultValue())
                    ),
                    false
            );
            source.sendSuccess(
                    () -> indentedLabelValue("Type", descriptor.typeName()),
                    false
            );
            if (!descriptor.rangeDescription().isEmpty()) {
                source.sendSuccess(
                        () -> indentedLabelValue("Range", descriptor.rangeDescription()),
                        false
                );
            }
            if (!descriptor.description().isBlank()) {
                source.sendSuccess(
                        () -> indentedLabelValue("Description", descriptor.description()),
                        false
                );
            }
        });
        return descriptors.size();
    }

    private static int getProperty(
            CommandSourceStack source,
            String moduleId,
            String propertyId
    ) {
        var module = DiakoUtils.MODULES.get(moduleId);
        if (module == null) {
            source.sendFailure(error("Unknown module: " + moduleId));
            return 0;
        }

        var descriptor = DiakoUtils.PROPERTIES.get(moduleId, propertyId);
        if (descriptor == null) {
            source.sendFailure(error("Unknown property: " + moduleId + "." + propertyId));
            return 0;
        }

        var value = get(descriptor, module);
        source.sendSuccess(
                () -> header(
                        descriptor.displayName()
                ),
                false
        );
        source.sendSuccess(
                () -> labelValue(
                        "Property",
                        moduleId + "." + propertyId,
                        ChatFormatting.AQUA
                ),
                false
        );
        source.sendSuccess(
                () -> labelValue(
                        "Value",
                        format(descriptor, value),
                        valueColor(value)
                ),
                false
        );
        source.sendSuccess(
                () -> labelValue(
                        "Default",
                        format(descriptor, descriptor.defaultValue())
                ),
                false
        );
        source.sendSuccess(
                () -> labelValue(
                        "Type",
                        descriptor.typeName()
                ),
                false
        );
        if (!descriptor.rangeDescription().isEmpty()) {
            source.sendSuccess(
                    () -> labelValue(
                            "Range",
                            descriptor.rangeDescription()
                    ),
                    false
            );
        }
        if (!descriptor.description().isBlank()) {
            source.sendSuccess(
                    () -> labelValue(
                            "Description",
                            descriptor.description()
                    ),
                    false
            );
        }
        return 1;
    }

    private static boolean canSetConfig(CommandSourceStack source) {
        return DiakoPermissions.hasAny(
                source,
                DiakoPermissions.ROOT,
                DiakoPermissions.CONFIG,
                DiakoPermissions.CONFIG_SET
        );
    }

    private static int setProperty(
            CommandSourceStack source,
            String moduleId,
            String propertyId,
            String value
    ) {
        var result = DiakoUtils.PROPERTY_SERVICE.set(
                moduleId,
                propertyId,
                value,
                source.getServer()
        );
        return sendPropertyResult(source, moduleId, "Updated", result);
    }

    private static boolean canResetConfig(CommandSourceStack source) {
        return DiakoPermissions.hasAny(
                source,
                DiakoPermissions.ROOT,
                DiakoPermissions.CONFIG,
                DiakoPermissions.CONFIG_RESET
        );
    }

    private static int resetProperty(
            CommandSourceStack source,
            String moduleId,
            String propertyId
    ) {
        var result = DiakoUtils.PROPERTY_SERVICE.reset(
                moduleId,
                propertyId,
                source.getServer()
        );
        return sendPropertyResult(source, moduleId, "Reset", result);
    }

    private static int resetAllProperties(CommandSourceStack source, String moduleId) {
        var result = DiakoUtils.PROPERTY_SERVICE.resetAll(
                moduleId,
                source.getServer()
        );

        if (!result.successful()) {
            source.sendFailure(error(result.message()));
            return 0;
        }

        if (!result.changed()) {
            source.sendSuccess(() -> notice(result.message()), false);
            return 1;
        }

        source.sendSuccess(
                () -> success("Reset %d properties for %s.".formatted(
                        result.changes().size(),
                        moduleId
                )),
                true
        );
        result.changes().forEach(change -> source.sendSuccess(
                () -> propertyChangeLine(moduleId, change),
                false
        ));
        return result.changes().size();
    }

    private static int sendPropertyResult(
            CommandSourceStack source,
            String moduleId,
            String action,
            PropertyOperationResult result
    ) {
        if (!result.successful()) {
            source.sendFailure(error(result.message()));
            return 0;
        }

        if (!result.changed()) {
            source.sendSuccess(() -> notice(result.message()), false);
            return 1;
        }

        var change = result.changes().getFirst();
        source.sendSuccess(
                () -> propertyUpdate(action, moduleId, change),
                true
        );
        return 1;
    }

}
