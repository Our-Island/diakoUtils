package top.ourisland.diakoutils.permissions;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import top.ourisland.diakoutils.DiakoUtils;

public final class DiakoPermissions {

    public static final String ROOT = "diakoutils.command";
    public static final String LIST = "diakoutils.command.list";
    public static final String STATUS = "diakoutils.command.status";
    public static final String ENABLE = "diakoutils.command.enable";
    public static final String DISABLE = "diakoutils.command.disable";
    public static final String RELOAD = "diakoutils.command.reload";

    private static final String LUCKPERMS_MOD_ID = "luckperms";
    private static boolean warnedLuckPermsFailure = false;

    private DiakoPermissions() {
    }

    public static boolean has(CommandSourceStack source, String permission) {
        return hasAny(source, permission);
    }

    public static boolean hasAny(CommandSourceStack source, String... permissions) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }

        Boolean luckPermsResult = luckPermsResult(player, permissions);
        if (luckPermsResult != null) {
            return luckPermsResult;
        }

        return hasVanillaGamemasterPermission(source);
    }

    private static Boolean luckPermsResult(ServerPlayer player, String... permissions) {
        if (!FabricLoader.getInstance().isModLoaded(LUCKPERMS_MOD_ID)) {
            return null;
        }

        try {
            return LuckPermsPermissionBridge.hasAnyPermission(player, permissions);
        } catch (IllegalStateException | LinkageError error) {
            warnLuckPermsFailure(error);
            return null;
        }
    }

    private static boolean hasVanillaGamemasterPermission(CommandSourceStack source) {
        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
    }

    private static void warnLuckPermsFailure(Throwable error) {
        if (warnedLuckPermsFailure) {
            return;
        }

        warnedLuckPermsFailure = true;
        DiakoUtils.LOGGER.warn(
                "[{}] LuckPerms is installed, but its API is not available yet. Falling back to vanilla command level checks.",
                DiakoUtils.MOD_ID,
                error
        );
    }

}
