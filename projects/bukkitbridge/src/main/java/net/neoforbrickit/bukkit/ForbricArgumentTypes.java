/*
 * Forbric Bukkit bridge — the argument types a plugin's command can take.
 */
package net.neoforbrickit.bukkit;

import java.util.List;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.AxisSet;
import io.papermc.paper.command.brigadier.argument.range.DoubleRangeProvider;
import io.papermc.paper.command.brigadier.argument.range.IntegerRangeProvider;
import io.papermc.paper.command.brigadier.argument.resolvers.AngleResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.ColumnBlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.ColumnFinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.RotationResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.predicate.BlockInWorldPredicate;
import io.papermc.paper.command.brigadier.argument.predicate.ItemStackPredicate;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;

/**
 * Builds the argument types a Paper command can take, on top of the game's own.
 *
 * <p><b>Delegation, not reimplementation.</b> An argument type is a parser, and the client's tab-completion is
 * driven by the same object. A parser written here would accept a slightly different language from the one the
 * client suggests — {@code @e[type=!zombie,distance=..5]} is a grammar, not a method call — so every type this
 * class returns is one of the game's, wrapped only where the API's declared type differs from the game's.
 *
 * <p><b>The wrapping that is actually needed.</b> {@code EntityArgument.players()} parses to an
 * {@code EntitySelector}; the API promises a {@code PlayerSelectorArgumentResolver}. A resolver is a lazy query:
 * it holds the selector and looks the source up when the plugin asks, which is what lets a plugin's executor
 * call {@code resolve(ctx.getSource())} and get entities resolved against the world the command ran in. So the
 * wrapper is a small adapter, not a parser.
 *
 * <p><b>One instance per server, held statically.</b> The provider class lives in Paper's package and is
 * instantiated by {@code ServiceLoader} with no arguments, so it cannot be given a server; this is where the
 * server is kept instead, set once when the bridge starts.
 */
public final class ForbricArgumentTypes {

    /** The instance for the running server, or null before the bridge starts. */
    private static volatile ForbricArgumentTypes instance;

    private final ServerImpl server;

    private ForbricArgumentTypes(ServerImpl server) {
        this.server = server;
    }

    /**
     * Publishes the instance for the running server.
     *
     * <p>Called once by the bridge before any plugin loads. Overwriting is allowed on purpose: an integrated
     * server can be started, stopped and started again in one JVM, and the second server's arguments must resolve
     * against the second server.
     */
    public static void install(ServerImpl server) {
        ForbricArgumentTypes created = new ForbricArgumentTypes(server);
        instance = created;
        // Published for the OTHER copy of this class, which is the one a plugin reaches. Paper instantiates the
        // VanillaArgumentProvider through ServiceLoader -- using the interface's own loader, FML's plugins layer
        // -- and a plugin building its Brigadier tree calls ArgumentTypes.players() from inside its COMMANDS
        // handler. That copy cannot see this static field, so it answered "asked for before the bridge started"
        // and THREW; the plugin's whole registration died with it, leaving a command that was absent from tab
        // completion and did nothing when typed while the plugin logged "enabled".
        //
        // A system property is process-wide -- the same channel ForbricRegistries uses. The published value is
        // this object, and the other layer calls its methods through reflection: the methods build
        // net.minecraft.* types, so they must run on the class that can see Minecraft, and the object carries
        // that class with it.
        System.getProperties().put(SHARED_INSTANCE_KEY, created);
        // Before any plugin can build a command: a selector argument that is not in the game's
        // argument-type map makes the command packet throw when a player joins, and the join dies
        // with "Invalid player data".
        ForbricSelectorArguments.install();
    }

    /** Where the instance is published for the bridge's other class-loader layer. */
    private static final String SHARED_INSTANCE_KEY = "neofobric.bukkit.argumentTypes";

    /** The instance for the running server, or null when the bridge has not started. */
    public static ForbricArgumentTypes current() {
        return instance;
    }

    /**
     * The same instance as an opaque object, for the layer whose {@code ForbricArgumentTypes} is a different
     * class.
     *
     * <p>Typed {@code Object} on purpose: the two layers define one class of this name each, so the other layer
     * cannot cast the value to its own copy (a ClassCastException) and could not call these methods even if it
     * could, because they link {@code net.minecraft.commands.arguments.*}. It reflects instead.
     */
    public static Object sharedInstance() {
        if (instance != null) {
            return instance;
        }
        return System.getProperties().get(SHARED_INSTANCE_KEY);
    }

    /** The server these arguments resolve against. */
    ServerImpl server() {
        return server;
    }

    // ---- Entity selectors: the four forms differ only in what the game's parser accepts -------------

    /**
     * The players or entities an {@code @} selector names.
     *
     * <p>Two booleans from the API's four methods, because the game models it that way: {@code EntityArgument}'s
     * factories differ only in whether multiple entities and non-players are allowed. Passing them through keeps
     * the four API methods one implementation without making any of them accept more than it promises.
     */
    /**
     * The players or entities an {@code @} selector names.
     *
     * <p>Four methods rather than one with two booleans, because the API has four and each returns a different
     * resolver type. The game's factories are chosen by the same two facts -- whether several entities are
     * allowed, and whether only players are -- so each method is one line plus the wrapper.
     */
    public ArgumentType<EntitySelectorArgumentResolver> entity() {
        return ForbricSelectorType.of(net.minecraft.commands.arguments.EntityArgument.entity(), false, false);
    }

    public ArgumentType<EntitySelectorArgumentResolver> entities() {
        return ForbricSelectorType.of(net.minecraft.commands.arguments.EntityArgument.entities(), true, false);
    }

    public ArgumentType<PlayerSelectorArgumentResolver> player() {
        return ForbricSelectorType.of(net.minecraft.commands.arguments.EntityArgument.player(), false, true);
    }

    public ArgumentType<PlayerSelectorArgumentResolver> players() {
        return ForbricSelectorType.of(net.minecraft.commands.arguments.EntityArgument.players(), true, true);
    }

    /**
     * Players named by profile, without a world to resolve against.
     *
     * <p>Refuses: the game's parser for this is {@code GameProfileArgument}, which resolves names through the
     * server's profile cache — a lookup this bridge would have to drive itself, and one that fails differently
     * for an offline-mode server than an online one.
     */
    public ArgumentType<PlayerProfileListResolver> playerProfiles() {
        throw notYet("playerProfiles()");
    }

    // ---- Positions and rotations -------------------------------------------------------------------

    /** Refuses: the game's block-position parser needs a coordinate-normalisation context this bridge has not built. */
    public ArgumentType<BlockPositionResolver> blockPosition() {
        throw notYet("blockPosition()");
    }

    public ArgumentType<ColumnBlockPositionResolver> columnBlockPosition() {
        throw notYet("columnBlockPosition()");
    }

    public ArgumentType<FinePositionResolver> finePosition(boolean centerIntegers) {
        throw notYet("finePosition()");
    }

    /** Refuses with its sibling: both are the game's fine-position parser under a different name. */
    public ArgumentType<ColumnFinePositionResolver> columnFinePosition(boolean centerIntegers) {
        throw notYet("columnFinePosition()");
    }

    public ArgumentType<RotationResolver> rotation() {
        throw notYet("rotation()");
    }

    /**
     * An angle in degrees.
     *
     * <p>The game's parser is {@code AngleArgument}, and its parsed value is already a {@code float} in degrees,
     * so the resolver is a plain accessor rather than a query.
     */
    public ArgumentType<AngleResolver> angle() {
        throw notYet("angle()");
    }

    // ---- The rest ----------------------------------------------------------------------------------

    public ArgumentType<org.bukkit.World> world() {
        throw notYet("world()");
    }

    public ArgumentType<org.bukkit.GameMode> gameMode() {
        throw notYet("gameMode()");
    }

    public ArgumentType<org.bukkit.inventory.ItemStack> itemStack() {
        throw notYet("itemStack()");
    }

    public ArgumentType<org.bukkit.block.BlockState> blockState() {
        throw notYet("blockState()");
    }

    public ArgumentType<net.kyori.adventure.text.format.NamedTextColor> namedColor() {
        throw notYet("namedColor()");
    }

    public ArgumentType<net.kyori.adventure.text.format.TextColor> hexColor() {
        throw notYet("hexColor()");
    }

    public ArgumentType<net.kyori.adventure.text.Component> component() {
        throw notYet("component()");
    }

    public ArgumentType<net.kyori.adventure.text.format.Style> style() {
        throw notYet("style()");
    }

    public ArgumentType<io.papermc.paper.command.brigadier.argument.SignedMessageResolver> signedMessage() {
        throw notYet("signedMessage()");
    }

    public ArgumentType<org.bukkit.scoreboard.DisplaySlot> scoreboardDisplaySlot() {
        throw notYet("scoreboardDisplaySlot()");
    }

    public ArgumentType<org.bukkit.NamespacedKey> namespacedKey() {
        throw notYet("namespacedKey()");
    }

    public ArgumentType<net.kyori.adventure.key.Key> key() {
        throw notYet("key()");
    }

    /** Refuses: the range providers expose a query over two bounds, which is a type this bridge has not built. */
    public ArgumentType<IntegerRangeProvider> integerRange() {
        throw notYet("integerRange()");
    }

    public ArgumentType<DoubleRangeProvider> doubleRange() {
        throw notYet("doubleRange()");
    }

    public ArgumentType<org.bukkit.HeightMap> heightMap() {
        throw notYet("heightMap()");
    }

    /**
     * A UUID, in the form the game's {@code UuidArgument} accepts.
     *
     * <p>The parsed value needs no resolution: the game hands back a {@code UUID}, which is already what the API
     * promises. Whole-string or one of {@code ~}/{@code *} for a default, both of which the game's parser knows.
     */
    public ArgumentType<java.util.UUID> uuid() {
        return net.minecraft.commands.arguments.UuidArgument.uuid();
    }

    public ArgumentType<org.bukkit.scoreboard.Criteria> objectiveCriteria() {
        throw notYet("objectiveCriteria()");
    }

    public ArgumentType<io.papermc.paper.entity.LookAnchor> entityAnchor() {
        throw notYet("entityAnchor()");
    }

    /**
     * A time in ticks or a duration.
     *
     * <p>The {@code int} argument selects the unit set the parser accepts, which the API passes through from
     * {@code ArgumentTypes.time()}. Refuses until the mapping from that flag to the game's mode is verified —
     * guessing it would accept "1h" where the caller asked for ticks.
     */
    public ArgumentType<Integer> time(int unit) {
        throw notYet("time()");
    }

    public ArgumentType<org.bukkit.block.structure.Mirror> templateMirror() {
        throw notYet("templateMirror()");
    }

    public ArgumentType<org.bukkit.block.structure.StructureRotation> templateRotation() {
        throw notYet("templateRotation()");
    }

    public <T> ArgumentType<TypedKey<T>> resourceKey(RegistryKey<T> registryKey) {
        throw notYet("resourceKey()");
    }

    public <T> ArgumentType<T> resource(RegistryKey<T> registryKey) {
        throw notYet("resource()");
    }

    public ArgumentType<AxisSet> axes() {
        throw notYet("axes()");
    }

    public ArgumentType<BlockInWorldPredicate> blockInWorldPredicate() {
        throw notYet("blockInWorldPredicate()");
    }

    public ArgumentType<ItemStackPredicate> itemStackPredicate() {
        throw notYet("itemStackPredicate()");
    }

    private static UnsupportedOperationException notYet(String what) {
        return new UnsupportedOperationException(
                        "[Forbric/Bukkit] ArgumentTypes." + what + " is not implemented by this bridge yet");
    }
}
