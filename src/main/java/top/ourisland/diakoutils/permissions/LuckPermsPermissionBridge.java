package top.ourisland.diakoutils.permissions;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.level.ServerPlayer;

final class LuckPermsPermissionBridge {

    private LuckPermsPermissionBridge() {
    }

    static Boolean hasAnyPermission(
            ServerPlayer player,
            String... permissions
    ) {
        var luckPerms = LuckPermsProvider.get();
        var user = luckPerms.getUserManager().getUser(player.getUUID());

        if (user == null) {
            return null;
        }

        var hasUndefined = false;
        var hasFalse = false;

        for (var permission : permissions) {
            var result = user.getCachedData()
                    .getPermissionData()
                    .checkPermission(permission);

            if (result == Tristate.TRUE) {
                return true;
            }

            if (result == Tristate.FALSE) {
                hasFalse = true;
            }

            if (result == Tristate.UNDEFINED) {
                hasUndefined = true;
            }
        }

        if (hasFalse) {
            return false;
        }

        return hasUndefined
                ? null
                : false;
    }

}
