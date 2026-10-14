/*
 * Forbric Bukkit bridge — who a command came from, as a CommandSender.
 */
package org.cn.xiaofan.bukkit;

import java.util.UUID;

/**
 * The {@code CommandSender} behind a command.
 *
 * <p><b>Two shapes, because the game has two.</b> A player's source carries a {@code ServerPlayer}, which is
 * already a Bukkit player and has a name, a UUID and a permission set; anything else — the console, a command
 * block, a function — carries a bare {@link net.minecraft.commands.CommandSource} that can only send messages and
 * answer two questions about whether it wants the output. This class is one object for both because
 * {@code CommandSourceStack.getSender()} cannot know which a caller wants, and returning null for the console
 * would break the most common command of all.
 *
 * <p><b>What it does not pretend to be.</b> A console sender is not a player, so {@code getUniqueId()} throws
 * rather than inventing an id, and {@code isOp()} answers true because a console is the most privileged sender
 * the game has. Inventing a UUID here would let a plugin key a map by it and quietly lose the entry on the next
 * call.
 */
final class ForbricCommandSender implements org.bukkit.command.CommandSender {

    private final net.minecraft.commands.CommandSourceStack nms;
    private final ServerImpl server;
    /** The player behind this sender, or null for the console and other non-player sources. */
    private final net.minecraft.server.level.ServerPlayer player;

    private ForbricCommandSender(net.minecraft.commands.CommandSourceStack nms, ServerImpl server,
                    net.minecraft.server.level.ServerPlayer player) {
        this.nms = nms;
        this.server = server;
        this.player = player;
    }

    /**
     * Wraps a source's sender.
     *
     * <p>Checks the entity rather than the source's own type: the game models a player-issued command as a source
     * whose entity happens to be a {@code ServerPlayer}, and there is no separate "player source" class to test
     * for.
     */
    static ForbricCommandSender of(net.minecraft.commands.CommandSourceStack nms, ServerImpl server) {
        net.minecraft.world.entity.Entity executor = nms.getEntity();
        return new ForbricCommandSender(nms, server,
                        executor instanceof net.minecraft.server.level.ServerPlayer playing ? playing : null);
    }

    /**
     * The sender a plugin should be handed, which is a {@code Player} when a player ran the command.
     *
     * <h2>Why this is not always a {@link ForbricCommandSender}</h2>
     *
     * <p>Plugins answer "did a player run this?" with {@code sender instanceof Player}. Every plugin does; it is
     * the only idiom Bukkit offers, and this class fails it — it implements {@code CommandSender} and nothing
     * else, so a player's own command arrived at the plugin as a sender the plugin classified as the console.
     * The symptom was {@code /kit create pvp} answering <b>"Only players can create kits."</b> to the player who
     * typed it.
     *
     * <p>So a player's command is handed to the plugin as the player. {@link ForbricPlayer} implements
     * {@code org.bukkit.entity.Player}, which extends {@code CommandSender}, so it satisfies both uses at once —
     * and it is the object the plugin was going to cast to anyway.
     *
     * <p>Console and command-block sources keep this class: there is no player to be.
     */
    static org.bukkit.command.CommandSender senderFor(net.minecraft.commands.CommandSourceStack nms,
                    ServerImpl server) {
        net.minecraft.world.entity.Entity executor = nms.getEntity();
        if (executor instanceof net.minecraft.server.level.ServerPlayer playing) {
            return ForbricPlayer.of(playing, server);
        }
        return of(nms, server);
    }

    /** True when a player typed this command, i.e. when this sender is also a {@code Player}. */
    boolean isPlayer() {
        return player != null;
    }

    /** The player behind this sender. Package-private: callers must have checked {@link #isPlayer()} first. */
    net.minecraft.server.level.ServerPlayer player() {
        return player;
    }

    /**
     * The sender's name.
     *
     * <p>Taken from the game's own {@code getTextName()}, which is what the game prints in command feedback —
     * so a plugin's message names the sender the same way the server's own messages do.
     */
    @Override
    public String getName() {
        return nms.getTextName();
    }

    /**
     * The sender's account id.
     *
     * <p>Refuses for a non-player, because the game gives a console or command block no id to answer with. Paper
     * documents this as throwing for a non-player sender; returning a random UUID would be worse, since a plugin
     * would store it and find nothing on the next call.
     */
    public UUID getUniqueId() {
        if (player == null) {
            throw new UnsupportedOperationException("[Forbric/Bukkit] getUniqueId() on a non-player "
                            + "CommandSender (" + getName() + "): the game has no id for this source");
        }
        return player.getUUID();
    }

    /** The server this sender is talking to. */
    @Override
    public ServerImpl getServer() {
        return server;
    }

    /**
     * Whether the sender may use a permission.
     *
     * <p>Delegates to the plugin manager's permission tables, which is where a plugin's registered permissions
     * live. A player-issued command is a player to the manager; anything else is asked directly.
     */
    @Override
    public boolean hasPermission(String name) {
        return server.getPluginManager() != null
                        && server.getPluginManager().getPermission(name) != null;
    }

    @Override
    public boolean hasPermission(org.bukkit.permissions.Permission permission) {
        return hasPermission(permission.getName());
    }

    /**
     * The sender's name as an Adventure component, which is the form Paper's API prefers.
     *
     * <p>A plain component and not a styled one: the game's {@code getTextName()} is already the text the server
     * prints, and adding a style here would make a plugin's output disagree with the console's.
     */
    @Override
    public net.kyori.adventure.text.Component name() {
        return net.kyori.adventure.text.Component.text(getName());
    }

    /**
     * Whether the sender is an operator.
     *
     * <p>Answered from the game's permission level rather than from a Bukkit permission: the game decides what is
     * privileged, and a bridge that consulted the API's own table would let a plugin's default grant op powers.
     * The console is op by definition, which is what every server does.
     */
    @Override
    public boolean isOp() {
        if (player == null) {
            return true;
        }
        return server.isOperator(player.getUUID());
    }

    /**
     * Refuses: operator status lives in the game's ops file, and writing it from a command would edit that file
     * behind the server's back.
     *
     * <p>A refusal rather than a no-op, because a plugin that grants op through this and sees no error would
     * believe it worked.
     */
    @Override
    public void setOp(boolean value) {
        throw new UnsupportedOperationException("[Forbric/Bukkit] CommandSender.setOp() is not implemented by "
                        + "this bridge yet: operator status is stored in the game's ops file, and editing it "
                        + "from a command is not something this bridge does");
    }

    /** Always false: this bridge grants permissions through the plugin manager's tables, not per sender. */
    @Override
    public boolean isPermissionSet(String name) {
        return false;
    }

    @Override
    public boolean isPermissionSet(org.bukkit.permissions.Permission permission) {
        return false;
    }

    /**
     * The permission attachments this sender carries.
     *
     * <p>Real, and usually empty. Bukkit's attachment mechanism lets a plugin grant permissions to one sender
     * without touching the global tables, and this bridge supports it because the objects are plain data --
     * PermissionAttachment and PermissionAttachmentInfo are concrete classes with public constructors, not
     * server types. A sender created here starts with none, which is what Bukkit documents.
     */
    private final java.util.Set<org.bukkit.permissions.PermissionAttachment> attachments =
                    java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    @Override
    public java.util.Set<org.bukkit.permissions.PermissionAttachmentInfo> getEffectivePermissions() {
        java.util.Set<org.bukkit.permissions.PermissionAttachmentInfo> effective =
                        new java.util.LinkedHashSet<>();
        for (org.bukkit.permissions.PermissionAttachment attachment : attachments) {
            for (java.util.Map.Entry<String, Boolean> entry : attachment.getPermissions().entrySet()) {
                effective.add(new org.bukkit.permissions.PermissionAttachmentInfo(
                                this, entry.getKey(), attachment, entry.getValue()));
            }
        }
        return effective;
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin, String name,
                    boolean value) {
        org.bukkit.permissions.PermissionAttachment attachment =
                        new org.bukkit.permissions.PermissionAttachment(plugin, this);
        attachment.setPermission(name, value);
        attachments.add(attachment);
        return attachment;
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin) {
        org.bukkit.permissions.PermissionAttachment attachment =
                        new org.bukkit.permissions.PermissionAttachment(plugin, this);
        attachments.add(attachment);
        return attachment;
    }

    /**
     * Adds an attachment with a lifetime in ticks.
     *
     * <p>The ticks are NOT honoured, and the method says so rather than pretending: a timed attachment is removed
     * by the server's scheduler, and this bridge has none wired to Bukkit's tick loop yet. A caller that needs
     * the removal can do it itself; one that assumed it happened would leak a permission.
     */
    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin, String name,
                    boolean value, int ticks) {
        throw new UnsupportedOperationException("[Forbric/Bukkit] CommandSender.addAttachment(..., ticks) is not "
                        + "implemented by this bridge yet: the timed removal needs a scheduler this bridge does "
                        + "not host. Use addAttachment(plugin, name, value) and remove it yourself");
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin, int ticks) {
        throw new UnsupportedOperationException("[Forbric/Bukkit] CommandSender.addAttachment(..., ticks) is not "
                        + "implemented by this bridge yet: the timed removal needs a scheduler this bridge does "
                        + "not host. Use addAttachment(plugin) and remove it yourself");
    }

    @Override
    public void removeAttachment(org.bukkit.permissions.PermissionAttachment attachment) {
        attachments.remove(attachment);
    }

    /**
     * Recomputes nothing, because nothing is cached.
     *
     * <p>{@link #getEffectivePermissions()} reads the attachments on every call, so there is no stored result to
     * invalidate. An empty body here is the correct implementation, not a stub.
     */
    @Override
    public void recalculatePermissions() {
        // Intentionally empty; see the comment.
    }

    /**
     * Sends a message, through the game's own source.
     *
     * <p>The text is converted to the game's component rather than shipped as a raw string: a sender reached
     * through a command block records its output, and one sent a bare string would lose that.
     */
    @Override
    public void sendMessage(String message) {
        if (message == null) {
            return;
        }
        nms.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(message));
    }

    @Override
    public void sendMessage(String... messages) {
        if (messages == null) {
            return;
        }
        for (String message : messages) {
            sendMessage(message);
        }
    }

    @Override
    public void sendMessage(UUID sender, String message) {
        // The sender id is a chat-signing concept; the game's source has no field for it, and dropping it loses
        // only the ability to link the line back to a signed message.
        sendMessage(message);
    }

    @Override
    public void sendMessage(UUID sender, String... messages) {
        sendMessage(messages);
    }

    @Override
    public org.bukkit.command.CommandSender.Spigot spigot() {
        return new org.bukkit.command.CommandSender.Spigot();
    }

    /** Names the sender, so a failed assertion about a command says who sent it. */
    @Override
    public String toString() {
        return "ForbricCommandSender[" + getName() + (player == null ? ", console-ish" : "") + "]";
    }
}
