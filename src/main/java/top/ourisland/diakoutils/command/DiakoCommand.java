package top.ourisland.diakoutils.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.IModule;
import top.ourisland.diakoutils.config.ConfigPersistenceException;
import top.ourisland.diakoutils.permissions.DiakoPermissions;
import top.ourisland.diakoutils.property.ModulePropertyChange;
import top.ourisland.diakoutils.property.ModulePropertyDescriptor;
import top.ourisland.diakoutils.property.PropertyOperationResult;

import java.util.Arrays;
import java.util.LinkedHashSet;

public final class DiakoCommand {

    private static final SuggestionProvider<CommandSourceStack> MODULE_ID_SUGGESTIONS = (_, builder) ->
            SharedSuggestionProvider.suggest(DiakoUtils.MODULES.ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> PROPERTY_ID_SUGGESTIONS = (context, builder) -> {
        var moduleId = StringArgumentType.getString(context, "module");
        return SharedSuggestionProvider.suggest(DiakoUtils.PROPERTIES.ids(moduleId), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> PROPERTY_VALUE_SUGGESTIONS = (context, builder) -> {
        var moduleId = StringArgumentType.getString(context, "module");
        var propertyId = StringArgumentType.getString(context, "property");
        var module = DiakoUtils.MODULES.get(moduleId);
        var descriptor = DiakoUtils.PROPERTIES.get(moduleId, propertyId);

        if (module == null || descriptor == null) {
            return builder.buildFuture();
        }

        var suggestions = new LinkedHashSet<>(descriptor.suggestions());
        suggestions.add(format(descriptor, get(descriptor, module)));
        return SharedSuggestionProvider.suggest(suggestions, builder);
    };

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
                                DiakoPermissions.RELOAD,
                                DiakoPermissions.CONFIG,
                                DiakoPermissions.CONFIG_GET,
                                DiakoPermissions.CONFIG_SET,
                                DiakoPermissions.CONFIG_RESET
                        ))
                        .executes(context -> overview(context.getSource()))
                        .then(listCommand())
                        .then(statusCommand())
                        .then(enableCommand())
                        .then(disableCommand())
                        .then(configCommand())
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

    private static LiteralArgumentBuilder<CommandSourceStack> configCommand() {
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
                        .suggests(MODULE_ID_SUGGESTIONS)
                        .executes(DiakoCommand::listPropertiesWithPermission)
                        .then(Commands.literal("list")
                                .requires(DiakoCommand::canReadConfig)
                                .executes(context -> listProperties(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "module")
                                ))
                        )
                        .then(Commands.literal("get")
                                .requires(DiakoCommand::canReadConfig)
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .suggests(PROPERTY_ID_SUGGESTIONS)
                                        .executes(context -> getProperty(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "module"),
                                                StringArgumentType.getString(context, "property")
                                        ))
                                )
                        )
                        .then(Commands.literal("set")
                                .requires(DiakoCommand::canSetConfig)
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .suggests(PROPERTY_ID_SUGGESTIONS)
                                        .then(Commands.argument(
                                                                "value",
                                                                StringArgumentType.greedyString()
                                                        )
                                                        .suggests(PROPERTY_VALUE_SUGGESTIONS)
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
                                .requires(DiakoCommand::canResetConfig)
                                .then(Commands.argument("property", StringArgumentType.word())
                                        .suggests(PROPERTY_ID_SUGGESTIONS)
                                        .executes(context -> resetProperty(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "module"),
                                                StringArgumentType.getString(context, "property")
                                        ))
                                )
                        )
                        .then(Commands.literal("reset-all")
                                .requires(DiakoCommand::canResetConfig)
                                .executes(context -> resetAllProperties(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "module")
                                ))
                        )
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

        if (enabled) {
            DiakoUtils.MODULES.enable(id, source.getServer());
        } else {
            DiakoUtils.MODULES.disable(id, source.getServer());
        }

        try {
            DiakoUtils.CONFIG.saveOrThrow();
        } catch (ConfigPersistenceException e) {
            if (wasEnabled) {
                DiakoUtils.MODULES.enable(id, source.getServer());
            } else {
                DiakoUtils.MODULES.disable(id, source.getServer());
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
            source.sendSuccess(
                    () -> propertyTitle(
                            descriptor
                    ),
                    false
            );
            source.sendSuccess(
                    () -> indentedLabelValue(
                            "Value",
                            format(descriptor, get(descriptor, module)),
                            valueColor(get(descriptor, module))
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
                    () -> indentedLabelValue(
                            "Type",
                            descriptor.typeName()
                    ),
                    false
            );
            if (!descriptor.rangeDescription().isEmpty()) {
                source.sendSuccess(
                        () -> indentedLabelValue(
                                "Range",
                                descriptor.rangeDescription()
                        ),
                        false
                );
            }
            if (!descriptor.description().isBlank()) {
                source.sendSuccess(
                        () -> indentedLabelValue(
                                "Description",
                                descriptor.description()
                        ),
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
                        format(descriptor, get(descriptor, module)),
                        valueColor(get(descriptor, module))
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
            source.sendSuccess(
                    () -> notice(result.message()),
                    false
            );
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

    private static boolean canReadConfig(CommandSourceStack source) {
        return DiakoPermissions.hasAny(
                source,
                DiakoPermissions.ROOT,
                DiakoPermissions.CONFIG,
                DiakoPermissions.CONFIG_GET
        );
    }

    private static boolean canSetConfig(CommandSourceStack source) {
        return DiakoPermissions.hasAny(
                source,
                DiakoPermissions.ROOT,
                DiakoPermissions.CONFIG,
                DiakoPermissions.CONFIG_SET
        );
    }

    private static boolean canResetConfig(CommandSourceStack source) {
        return DiakoPermissions.hasAny(
                source,
                DiakoPermissions.ROOT,
                DiakoPermissions.CONFIG,
                DiakoPermissions.CONFIG_RESET
        );
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

    private static Component propertyTitle(ModulePropertyDescriptor<?> descriptor) {
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

    private static Component propertyUpdate(
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

    private static Component propertyChangeLine(
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

    private static Component header(String title) {
        return prefix().append(
                Component.literal(title)
                        .withStyle(ChatFormatting.YELLOW)
        );
    }

    private static Component success(String message) {
        return prefix().append(Component.literal(message)
                .withStyle(ChatFormatting.GREEN)
        );
    }

    private static Component notice(String message) {
        return prefix().append(Component.literal(message)
                .withStyle(ChatFormatting.YELLOW)
        );
    }

    private static Component error(String message) {
        return prefix().append(Component.literal(message)
                .withStyle(ChatFormatting.RED)
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

    private static Component indentedLabelValue(String label, String value) {
        return indentedLabelValue(label, value, ChatFormatting.WHITE);
    }

    private static Component indentedLabelValue(
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

    private static ChatFormatting valueColor(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue
                    ? ChatFormatting.GREEN
                    : ChatFormatting.RED;
        }
        return ChatFormatting.WHITE;
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(ModulePropertyDescriptor<T> descriptor, IModule module) {
        return descriptor.get(module);
    }

    @SuppressWarnings("unchecked")
    private static <T> String format(ModulePropertyDescriptor<T> descriptor, Object value) {
        return descriptor.format((T) value);
    }

}
