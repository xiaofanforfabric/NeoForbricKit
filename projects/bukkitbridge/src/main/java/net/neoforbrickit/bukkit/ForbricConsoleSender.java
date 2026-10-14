/*
 * Forbric Bukkit bridge — the console, in the type Bukkit announces it with.
 */
package net.neoforbrickit.bukkit;

/**
 * {@code Bukkit.getConsoleSender()}, as a {@code ConsoleCommandSender}.
 *
 * <p>A subclass rather than a delegate, because everything a console sender does is already
 * {@link ForbricCommandSender}'s behaviour for a non-player source: it names itself, answers {@code isOp()} with
 * true, and checks a permission against the registered tables. What was missing is the marker interface — and
 * plugins do test for it, since {@code sender instanceof ConsoleCommandSender} is how one decides to print a
 * message rather than send it to a player.
 *
 * <p>{@code getServer().getConsoleSender()} used to throw "not implemented", which also left a test harness with
 * no sender to dispatch a command as.
 *
 * <p>{@code ConsoleCommandSender} also extends {@code Conversable}, the old conversation API. Raw messages are
 * supported — that is what {@code sendMessage} already does — and the conversation lifecycle is refused by name,
 * because a conversation is driven by the server's scheduler and this bridge hosts none (the same reason
 * {@code addAttachment(..., ticks)} is refused rather than accepted and ignored).
 */
final class ForbricConsoleSender extends ForbricCommandSender implements org.bukkit.command.ConsoleCommandSender {

    /** The console source is the game's own, so output lands where a command typed on stdin prints. */
    ForbricConsoleSender(net.minecraft.commands.CommandSourceStack nms, ServerImpl server) {
        super(nms, server, null);
    }

    /**
     * A raw message is what the console prints: one line, no prefix, no styling.
     *
     * <p>The {@code UUID} overload is Bukkit's newer spelling of the same thing, where the sender's identity
     * travels with the message so a formatter can prefix it. A console has no identity worth showing, so the
     * message goes out exactly as written.
     */
    @Override
    public void sendRawMessage(String message) {
        sendMessage(message);
    }

    @Override
    public void sendRawMessage(java.util.UUID sender, String message) {
        sendMessage(message);
    }

    /** Never: nothing can start a conversation here, so nothing can be in one. */
    @Override
    public boolean isConversing() {
        return false;
    }

    @Override
    public void acceptConversationInput(String input) {
        throw consoleNotYet("acceptConversationInput");
    }

    @Override
    public boolean beginConversation(org.bukkit.conversations.Conversation conversation) {
        throw consoleNotYet("beginConversation");
    }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation) {
        throw consoleNotYet("abandonConversation");
    }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation,
                    org.bukkit.conversations.ConversationAbandonedEvent details) {
        throw consoleNotYet("abandonConversation");
    }

    private static UnsupportedOperationException consoleNotYet(String what) {
        return new UnsupportedOperationException(
                        "[Forbric/Bukkit] ConsoleCommandSender." + what + "() is not implemented by this bridge"
                                        + " yet");
    }
}
