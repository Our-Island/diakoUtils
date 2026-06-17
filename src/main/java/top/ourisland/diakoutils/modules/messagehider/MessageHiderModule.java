package top.ourisland.diakoutils.modules.messagehider;

import com.electronwill.nightconfig.core.Config;
import top.ourisland.diakoutils.AbstractModule;

public final class MessageHiderModule extends AbstractModule {

    public static final String ID = "message_hider";

    private boolean hidePublicChat = true;
    private boolean hideJoinLeaveMessages = false;

    @Override
    public String description() {
        return "Hide public chat broadcasts and, optionally, join/leave messages.";
    }

    @Override
    public void loadConfig(Config config, String path) {
        var enabledValue = config.get(path + ".enabled");
        setEnabled(enabledValue instanceof Boolean enabled && enabled);

        var hidePublicChatValue = config.get(path + ".hide_public_chat");
        if (hidePublicChatValue instanceof Boolean value) {
            hidePublicChat = value;
        }

        var hideJoinLeaveValue = config.get(path + ".hide_join_leave_messages");
        if (hideJoinLeaveValue instanceof Boolean value) {
            hideJoinLeaveMessages = value;
        }
    }

    @Override
    public void saveConfig(Config config, String path) {
        config.set(path + ".enabled", enabled());
        config.set(path + ".hide_public_chat", hidePublicChat);
        config.set(path + ".hide_join_leave_messages", hideJoinLeaveMessages);
    }

    @Override
    public String statusLine() {
        return "Status: %s | Public chat: %s | Join/leave messages: %s".formatted(
                enabled() ? "§aEnabled§r" : "§cDisabled§r",
                shouldHidePublicChat() ? "§ahidden§r" : "§cvisible§r",
                shouldHideJoinLeaveMessages() ? "§ahidden§r" : "§cvisible§r"
        );
    }

    public boolean shouldHidePublicChat() {
        return enabled() && hidePublicChat;
    }

    public boolean shouldHideJoinLeaveMessages() {
        return enabled() && hideJoinLeaveMessages;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Message Hider";
    }

}
