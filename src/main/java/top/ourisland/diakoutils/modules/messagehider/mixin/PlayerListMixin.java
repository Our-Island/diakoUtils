package top.ourisland.diakoutils.modules.messagehider.mixin;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ourisland.diakoutils.DiakoUtils;
import top.ourisland.diakoutils.modules.messagehider.MessageHiderModule;

import java.util.function.Function;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void diakoutils$hidePublicChat(
            PlayerChatMessage message,
            ServerPlayer sender,
            ChatType.Bound chatType,
            CallbackInfo ci
    ) {
        MessageHiderModule module = messageHider();
        if (module != null && module.shouldHidePublicChat()) {
            ci.cancel();
        }
    }

    @Unique
    private static MessageHiderModule messageHider() {
        return (MessageHiderModule) DiakoUtils.MODULES.get(MessageHiderModule.ID);
    }

    @Inject(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void diakoutils$hideJoinLeaveMessage(
            Component message,
            boolean overlay,
            CallbackInfo ci
    ) {
        if (shouldHideJoinLeaveMessage(message)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean shouldHideJoinLeaveMessage(Component message) {
        MessageHiderModule module = messageHider();
        if (module == null || !module.shouldHideJoinLeaveMessages()) {
            return false;
        }

        ComponentContents contents = message.getContents();
        if (!(contents instanceof TranslatableContents translatable)) {
            return false;
        }

        String key = translatable.getKey();
        return key.equals("multiplayer.player.joined")
                || key.equals("multiplayer.player.joined.renamed")
                || key.equals("multiplayer.player.left");
    }

    @Inject(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void diakoutils$hideJoinLeaveMessageWithPerPlayerText(
            Component message,
            Function<ServerPlayer, Component> playerMessages,
            boolean overlay,
            CallbackInfo ci
    ) {
        if (shouldHideJoinLeaveMessage(message)) {
            ci.cancel();
        }
    }

}
