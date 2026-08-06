package top.ourisland.diakoutils.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import top.ourisland.diakoutils.permissions.DiakoPermissions;

public final class DiakoCommand {

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
                        .executes(context -> ModuleCommands.overview(context.getSource()))
                        .then(ModuleCommands.listCommand())
                        .then(ModuleCommands.statusCommand())
                        .then(ModuleCommands.enableCommand())
                        .then(ModuleCommands.disableCommand())
                        .then(ModuleConfigCommands.command())
                        .then(ModuleCommands.reloadCommand())
        ));
    }

}
