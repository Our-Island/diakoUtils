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
    private boolean hidePublicChat = true;

    @ModuleProperty(
            id = "hide_join_leave_messages",
            displayName = "Hide Join/Leave Messages",
            description = "Hide player join and leave messages.",
            order = 20
    )
    private boolean hideJoinLeaveMessages = false;

    @Override
    public String statusLine() {
        return "Public chat: %s | Join/leave messages: %s".formatted(
                shouldHidePublicChat()
                        ? "hidden"
                        : "visible",
                shouldHideJoinLeaveMessages()
                        ? "hidden"
                        : "visible"
        );
    }

    public boolean shouldHidePublicChat() {
        return enabled() && hidePublicChat;
    }

    public boolean shouldHideJoinLeaveMessages() {
        return enabled() && hideJoinLeaveMessages;
    }

}
