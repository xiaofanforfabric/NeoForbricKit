/*
 * Forbric Bukkit bridge — the command source a Brigadier command sees.
 */
package org.cn.xiaofan.bukkit;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/**
 * Where a command came from, as a plugin sees it.
 *
 * <p><b>One field carries the whole design.</b> {@link #nms()} is the game's own source, kept because the two
 * sides of a command are different objects on either side of the bridge: a plugin's executor receives this
 * wrapper and reads {@code getSender()}, while the game hands its own {@code CommandSourceStack} to an argument
 * resolver — {@code EntityArgument.findEntities(...)} takes the NMS one. Anything that loses the original cannot
 * resolve an argument, so the wrapper keeps it and hands it back through a package-private accessor.
 *
 * <p><b>Why the accessors are lazy.</b> A console command has no entity and no player, and a command block has no
 * player either. Building every view eagerly would mean constructing a {@code Player} for a source that is a
 * command block and then throwing it away — and, worse, would need a player wrapper that may not be reachable at
 * the time the stack is created. Each accessor is asked only when a plugin asks for that particular view.
 *
 * <p><b>The sender is the one view always available.</b> Every source has a {@code CommandSender} — the console,
 * a player, a command block's proxy — because the game itself models the sender as a {@code CommandSource}, and
 * Bukkit models it as a {@code CommandSender}. This is the only accessor with no failure mode.
 */
final class ForbricCommandSourceStack implements CommandSourceStack {

    // Brigadier's own LiteralMessage, not an Adventure Component. The two look interchangeable and are not:
    // brigadier's Message is its own one-method interface ({@code getString()}) that Adventure's Component does
    // not implement, so passing a Component is a compile error naming neither brigadier nor the missing method.
    private static final com.mojang.brigadier.Message NOT_PLAYER_MESSAGE =
                    new com.mojang.brigadier.LiteralMessage("This command can only be run by a player");
    private static final com.mojang.brigadier.Message NOT_ENTITY_MESSAGE =
                    new com.mojang.brigadier.LiteralMessage("This command requires an entity to run");

    /** Thrown when a plugin asks a console-sourced command for the player who ran it. */
    private static final SimpleCommandExceptionType NO_PLAYER =
                    new SimpleCommandExceptionType(NOT_PLAYER_MESSAGE);

    /** Thrown when a plugin asks for an entity and the source is the console. */
    private static final SimpleCommandExceptionType NO_ENTITY =
                    new SimpleCommandExceptionType(NOT_ENTITY_MESSAGE);

    private final net.minecraft.commands.CommandSourceStack nms;
    private final ServerImpl server;
    private final org.bukkit.command.CommandSender sender;

    private ForbricCommandSourceStack(net.minecraft.commands.CommandSourceStack nms, ServerImpl server,
                    org.bukkit.command.CommandSender sender) {
        this.nms = nms;
        this.server = server;
        this.sender = sender;
    }

    /**
     * Wraps a game source. The one place the game's {@code CommandSourceStack} becomes Paper's.
     *
     * <p>The sender is built here and not lazily, unlike the entity views, because every path needs it: a plugin's
     * executor almost always starts with {@code ctx.getSource().getSender()}, and a stack whose sender had to be
     * constructed on demand would be able to fail at the one call that never should.
     */
    static ForbricCommandSourceStack of(net.minecraft.commands.CommandSourceStack nms, ServerImpl server) {
        return new ForbricCommandSourceStack(nms, server, ForbricCommandSender.senderFor(nms, server));
    }

    /**
     * The game's own source, for the bridge's use — arguments resolve against it.
     *
     * <p>Package-private on purpose: it is an internal seam, and a plugin reaching for it would be reaching past
     * the API it is written against.
     */
    net.minecraft.commands.CommandSourceStack nms() {
        return nms;
    }

    /** The server this source came from, for the bridge's argument resolvers. */
    ServerImpl server() {
        return server;
    }

    /**
     * Who ran the command.
     *
     * <p>Paper's contract lets this return the sender for any source, so it never fails. A command block's sender
     * is a proxy object the API supplies; a player's is the player.
     */
    @Override
    public org.bukkit.command.CommandSender getSender() {
        return sender;
    }

    /**
     * Where the command was run from.
     *
     * <p>The NMS position is already world-absolute — the game resolves {@code ~ ~ ~} before a command executes —
     * so this is a plain copy, not a relative-coordinate calculation. The level the number belongs to comes from
     * the source's own dimension, which is why a command run from the console (which has a position but no level)
     * still reports the overworld rather than failing.
     */
    @Override
    public org.bukkit.Location getLocation() {
        net.minecraft.world.phys.Vec3 position = nms.getPosition();
        return new org.bukkit.Location(ForbricWorld.of(nms.getLevel(), server),
                        position.x(), position.y(), position.z(),
                        nms.getRotation().y, nms.getRotation().x);
    }

    /**
     * The entity that ran the command, or null.
     *
     * <p>Null rather than a throw, because Paper's contract says so: this is the "might not be an entity"
     * accessor, and a plugin that wants a failure uses {@link #getEntityOrThrow()}.
     */
    @Override
    public org.bukkit.entity.Entity getExecutor() {
        net.minecraft.world.entity.Entity executor = nms.getEntity();
        return executor == null ? null : ForbricEntity.of(executor, server);
    }

    /**
     * The player who ran the command.
     *
     * <p>Falls back to the executor when the source is not a player: a command forwarded from a player's context
     * carries the player as {@code getEntity()} in every case the game produces, so going through it is what
     * makes a {@code /kit} typed by a player distinguishable from the same command run from the console.
     */
    @Override
    public org.bukkit.entity.Player getPlayerOrThrow() throws CommandSyntaxException {
        net.minecraft.world.entity.Entity executor = nms.getEntity();
        if (executor instanceof net.minecraft.server.level.ServerPlayer player) {
            return ForbricPlayer.of(player, server);
        }
        throw NO_PLAYER.create();
    }

    /** The entity that ran the command, or a named failure. */
    @Override
    public org.bukkit.entity.Entity getEntityOrThrow() throws CommandSyntaxException {
        net.minecraft.world.entity.Entity executor = nms.getEntity();
        if (executor == null) {
            throw NO_ENTITY.create();
        }
        return ForbricEntity.of(executor, server);
    }

    /**
     * The same source, at a different place.
     *
     * <p>{@code withPosition} rather than a new stack: the game models this as a copy carrying the same sender,
     * and rebuilding the whole stack would drop {@code EntityArgument}'s view of who is executing.
     */
    @Override
    public CommandSourceStack withLocation(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException(
                            "[Forbric/Bukkit] CommandSourceStack.withLocation() needs a location in a world");
        }
        net.minecraft.server.level.ServerLevel level =
                        ((ForbricWorld) location.getWorld()).level();
        return ForbricCommandSourceStack.of(nms.withPosition(new net.minecraft.world.phys.Vec3(
                        location.getX(), location.getY(), location.getZ())).withLevel(level), server);
    }

    /**
     * The same source, run by a different entity.
     *
     * <p>Only an entity that is one of ours can be substituted: the game's source holds a real
     * {@code net.minecraft.world.entity.Entity}, and a Bukkit entity this bridge did not wrap has no NMS object
     * to put there. Refusing by name beats accepting and silently keeping the old executor.
     */
    @Override
    public CommandSourceStack withExecutor(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof ForbricEntity forbric)) {
            throw new IllegalArgumentException("[Forbric/Bukkit] CommandSourceStack.withExecutor() needs an "
                            + "entity this bridge produced, got " + entity);
        }
        return ForbricCommandSourceStack.of(nms.withEntity(forbric.entity()), server);
    }

    /** Names the source, so a failed assertion about a command says who ran it. */
    @Override
    public String toString() {
        return "ForbricCommandSourceStack[" + nms.getTextName() + "]";
    }
}
