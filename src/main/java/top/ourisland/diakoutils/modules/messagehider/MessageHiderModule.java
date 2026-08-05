package top.ourisland.diakoutils.modules.messagehider;

import top.ourisland.diakoutils.AbstractModule;
import top.ourisland.diakoutils.annotation.DiakoModule;
import top.ourisland.diakoutils.annotation.ModuleProperty;

@DiakoModule(
        id = "message_hider",
        displayName = "Message Hider",
        description = "Hide public chat broadcasts and, optionally, join/leave messages."
)
public final class MessageHiderModule extends AbstractModule {

    @ModuleProperty(
            id = "hide_public_chat",
            displayName = "Hide Public Chat",
            description = "Hide public player chat broadcasts.",
            order = 10
    )
    private final boolean hidePublicChat = true;

    @ModuleProperty(
            id = "hide_join_leave_messages",
            displayName = "Hide Join/Leave Messages",
            description = "Hide player join and leave messages.",
            order = 20
    )
    private final boolean hideJoinLeaveMessages = false;

    @Override
    public String statusLine() {
        return "Status: %s | Public chat: %s | Join/leave messages: %s".formatted(
                enabled()
                        ? "§aEnabled§r"
                        : "§cDisabled§r",
                shouldHidePublicChat()
                        ? "§ahidden§r"
                        : "§cvisible§r",
                shouldHideJoinLeaveMessages()
                        ? "§ahidden§r"
                        : "§cvisible§r"
        );
    }

    public boolean shouldHidePublicChat() {
        return enabled() && hidePublicChat;
    }

    public boolean shouldHideJoinLeaveMessages() {
        return enabled() && hideJoinLeaveMessages;
    }

}
