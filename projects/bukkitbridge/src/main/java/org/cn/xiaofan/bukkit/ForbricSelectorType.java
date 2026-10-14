/*
 * Forbric Bukkit bridge — an entity selector, parsed by the game and resolved lazily.
 */
package org.cn.xiaofan.bukkit;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

/**
 * {@code @e} and friends, as Paper's API declares them.
 *
 * <p><b>The parse is the game's; the resolution is ours.</b> {@code EntityArgument} already knows the selector
 * grammar — the predicates, the ranges, the sorting — and its result is an {@code EntitySelector}, which is a
 * query rather than a list: it has not touched a world yet. Paper's API instead promises a <i>resolver</i>, an
 * object a plugin calls with a command source to get entities. So this class parses by delegating and returns a
 * resolver that holds the selector until asked.
 *
 * <p><b>Why lazy matters.</b> The selector resolves against the source's level and position, which are only
 * known when the command runs. A wrapper that resolved eagerly at parse time would need a source it does not
 * have, and one that cached a list would answer a question about the wrong world after a teleport.
 *
 * <p><b>Why the four API methods share one class.</b> {@code entity()}, {@code entities()}, {@code player()} and
 * {@code players()} differ only in the game's factory and in whether the result is a player list — differences
 * the two booleans carry. Four classes would be four copies of the same delegating parse.
 */
final class ForbricSelectorType<T> implements ArgumentType<T> {

    private final net.minecraft.commands.arguments.EntityArgument game;
    private final boolean multiple;
    private final boolean playersOnly;

    private ForbricSelectorType(net.minecraft.commands.arguments.EntityArgument game, boolean multiple,
                    boolean playersOnly) {
        this.game = game;
        this.multiple = multiple;
        this.playersOnly = playersOnly;
        // Registered here, not at the call sites: every wrapper is built through this constructor, and the
        // reverse lookup a running command needs must not depend on each caller remembering to record it.
        BY_GAME_TYPE.put(game, this);
    }

    /**
     * The four selector forms.
     *
     * <p>The unchecked cast is the API's own shape, not a shortcut: {@code entity()} and {@code entities()} both
     * return {@code ArgumentType<EntitySelectorArgumentResolver>} while {@code player()} and {@code players()}
     * return the player-specific type, and the compiler cannot tell which this instance is from the booleans.
     * The caller in {@link ForbricArgumentTypes#selectors} knows, because it chose them.
     */
    @SuppressWarnings("unchecked")
    static <T> ArgumentType<T> of(net.minecraft.commands.arguments.EntityArgument game, boolean multiple,
                    boolean playersOnly) {
        return (ArgumentType<T>) new ForbricSelectorType<Object>(game, multiple, playersOnly);
    }

    /**
     * Rebuilds one of the four forms from the two bits {@link ForbricSelectorArguments} sends.
     *
     * <p>Called by the template's {@code instantiate}, which on this server runs when NeoForge's
     * {@code VanillaConnectionNetworkFilter} re-creates the command tree for a client that is not
     * NeoForge — before the packet is written, which is why the rebuild has to succeed here and not
     * only on a client.
     *
     * <p>The game's factories rather than its constructor: {@code EntityArgument}'s is
     * {@code protected}, and "single + players only" is a combination the game names. Going through
     * them means the grammar is whatever {@code EntityArgument} says it is.
     */
    @SuppressWarnings("unchecked")
    static ForbricSelectorType<?> fromFlags(byte flags) {
        boolean single = (flags & ForbricSelectorArguments.FLAG_SINGLE) != 0;
        boolean playersOnly = (flags & ForbricSelectorArguments.FLAG_PLAYERS_ONLY) != 0;
        net.minecraft.commands.arguments.EntityArgument game = playersOnly
                        ? (single ? net.minecraft.commands.arguments.EntityArgument.player()
                                        : net.minecraft.commands.arguments.EntityArgument.players())
                        : (single ? net.minecraft.commands.arguments.EntityArgument.entity()
                                        : net.minecraft.commands.arguments.EntityArgument.entities());
        return new ForbricSelectorType<Object>(game, !single, playersOnly);
    }

    /**
     * Records every wrapper this bridge builds, so one can be found from the game argument it wrapped.
     *
     * <p>Needed because the type a context's node carries is the GAME's ({@code wireTypeOf} substitutes it for
     * the packet's sake), and the resolver factory lives on the bridge's wrapper. Keyed by the game argument
     * instance, which is what the node holds and what {@code selectorFor} can read off it.
     */
    private static final java.util.Map<net.minecraft.commands.arguments.EntityArgument, ForbricSelectorType<?>>
                    BY_GAME_TYPE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * The bridge's wrapper for a game argument, or null when the type is not one of ours.
     *
     * <p>A {@code WeakHashMap} because the game's argument instances are held by the command trees, and a tree
     * is rebuilt on every reload; strong keys here would keep every reload's arguments alive for the life of
     * the process, one entry per command per plugin per reload.
     *
     * <p>The lookup is by the game argument the wrapper was built from, not by the argument's class: the four
     * selector forms share one class and differ only in the two booleans, so the class alone could not say
     * which resolver to build.
     */
    static ForbricSelectorType<?> wrapperFor(Object gameType) {
        if (gameType instanceof net.minecraft.commands.arguments.EntityArgument argument) {
            return BY_GAME_TYPE.get(argument);
        }
        return null;
    }

    /**
     * The two bits that describe this argument, as {@link ForbricSelectorArguments} writes them.
     *
     * <p>Derived from the game factory that was chosen rather than stored: {@code EntityArgument}
     * holds {@code (single, playersOnly)} and exposes no getter, so the stored {@code multiple}/
     * {@code playersOnly} are the same facts and are what this instance was built with.
     */
    byte flags() {
        return (byte) ((multiple ? 0 : ForbricSelectorArguments.FLAG_SINGLE)
                        | (playersOnly ? ForbricSelectorArguments.FLAG_PLAYERS_ONLY : 0));
    }

    /**
     * The game's own argument this one wraps.
     *
     * <p>What the dispatcher is given in this type's place. A class only this bridge has cannot be
     * described to a client, so handing the game's own argument over is what makes the parameter
     * survive to a client at all; the plugin still receives a resolver, because the parse that runs
     * is this class's and {@code reargument} converts its result. See
     * {@code ForbricCommands.wireTypeOf}.
     */
    net.minecraft.commands.arguments.EntityArgument gameType() {
        return game;
    }

    /**
     * The resolver for a value this type parsed.
     *
     * <p>Called when a plugin looks an argument up. The value on a context comes from whichever type
     * the dispatcher's node carried, and for a selector that is the game's {@code EntityArgument} —
     * so what arrives is an {@code EntitySelector}, not the resolver {@code parse} here would have
     * returned. This is where it becomes one, so
     * {@code context.getArgument(name, PlayerSelectorArgumentResolver.class)} answers what the API
     * documents instead of throwing.
     */
    Object resolverFor(Object parsed) {
        if (parsed instanceof net.minecraft.commands.arguments.selector.EntitySelector selector) {
            return playersOnly ? new PlayerResolver(selector) : new EntityResolver(selector);
        }
        return parsed;
    }

    /**
     * Parses the selector with the game's parser and returns a resolver for it.
     *
     * <p>The type check the game does at execution time — "only one entity", "players only" — is repeated here
     * at parse time by way of the factory that was chosen, because the factories parse different grammars. A
     * {@code player()} argument rejects {@code @e[type=zombie]} while reading it, which is the error a user
     * should see rather than one after the command has already decided to run.
     */
    @Override
    public T parse(StringReader reader) throws CommandSyntaxException {
        net.minecraft.commands.arguments.selector.EntitySelector selector = game.parse(reader);
        if (selector == null) {
            return null;
        }
        return (T) (playersOnly ? new PlayerResolver(selector) : new EntityResolver(selector));
    }

    /**
     * The two resolvers, as two classes.
     *
     * <p>They cannot be one class: {@code EntitySelectorArgumentResolver} and {@code PlayerSelectorArgumentResolver}
     * both declare {@code resolve(CommandSourceStack)}, differing only in the list's element type, so erasure makes
     * them the same method with two return types and javac refuses. The split costs one small class and buys the
     * API's own shape.
     */
    private abstract static class Base {

        final net.minecraft.commands.arguments.selector.EntitySelector selector;

        Base(net.minecraft.commands.arguments.selector.EntitySelector selector) {
            this.selector = selector;
        }

        /**
         * The game's source behind a Paper one.
         *
         * <p>Only this bridge's wrapper kept it, and the game's query needs it: finding entities means reading a
         * level and a position, which an interface with a sender and no game object cannot supply. A source from
         * elsewhere is refused with the game's own message rather than resolved against the wrong world.
         */
        final net.minecraft.commands.CommandSourceStack nmsOf(CommandSourceStack source)
                        throws CommandSyntaxException {
            if (source instanceof ForbricCommandSourceStack ours) {
                return ours.nms();
            }
            throw net.minecraft.commands.arguments.EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.create();
        }

        final ServerImpl serverOf(CommandSourceStack source) {
            return ((ForbricCommandSourceStack) source).server();
        }
    }

    /** Entities, as {@code entity()} and {@code entities()} promise. */
    private static final class EntityResolver extends Base implements EntitySelectorArgumentResolver {

        EntityResolver(net.minecraft.commands.arguments.selector.EntitySelector selector) {
            super(selector);
        }

        @Override
        public List<org.bukkit.entity.Entity> resolve(CommandSourceStack source) throws CommandSyntaxException {
            List<org.bukkit.entity.Entity> resolved = new ArrayList<>();
            for (net.minecraft.world.entity.Entity entity : selector.findEntities(nmsOf(source))) {
                resolved.add(ForbricEntity.of(entity, serverOf(source)));
            }
            return resolved;
        }
    }

    /**
     * Players, as {@code player()} and {@code players()} promise.
     *
     * <p>The game's player-specific query rather than a filter over the entity list: its "no players matched"
     * error is the one the game prints and its ordering is what the selector asked for.
     */
    private static final class PlayerResolver extends Base implements PlayerSelectorArgumentResolver {

        PlayerResolver(net.minecraft.commands.arguments.selector.EntitySelector selector) {
            super(selector);
        }

        @Override
        public List<org.bukkit.entity.Player> resolve(CommandSourceStack source) throws CommandSyntaxException {
            List<org.bukkit.entity.Player> resolved = new ArrayList<>();
            for (net.minecraft.server.level.ServerPlayer player : selector.findPlayers(nmsOf(source))) {
                resolved.add(ForbricPlayer.of(player, serverOf(source)));
            }
            return resolved;
        }
    }
}
