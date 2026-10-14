/*
 * Forbric Bukkit bridge — the wire form of a Forbric selector argument.
 */
package org.cn.xiaofan.bukkit;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.network.FriendlyByteBuf;
import net.forbric.kernel.util.ForbricLog;

import com.google.gson.JsonObject;

/**
 * How {@link ForbricSelectorType} travels in a command packet.
 *
 * <h2>Why this is not {@code SingletonArgumentInfo}</h2>
 *
 * <p>It was, and it disconnected every joining player. {@code SingletonArgumentInfo} is for a type
 * that carries nothing and can be rebuilt from nothing: its template calls a no-argument supplier.
 * A selector is not that. {@code entity()}, {@code entities()}, {@code player()} and {@code
 * players()} are four grammars, and the packet describes only the one that was used — so rebuilding
 * it from nothing either throws or produces the wrong grammar on the far side.
 *
 * <h2>What is sent, and why one byte</h2>
 *
 * <p>The game's own {@code EntityArgument$Info} sends one byte for the same reason: {@code
 * EntityArgument} is {@code (boolean single, boolean playersOnly)} and nothing else, so two bits say
 * everything about how it parses. {@code ForbricSelectorType} wraps the same class and differs in
 * the same two bits, so this is that encoding.
 *
 * <p><b>Rebuilt through the game's factories, not by constructing its class.</b> {@code
 * EntityArgument}'s constructor is {@code protected}, and its factories are the API's own statement
 * of what the four combinations mean. Rebuilding through them keeps this file from having to know
 * whether "single, players only" is a valid selector, which is a rule the game owns.
 *
 * <h2>Where instantiate is called from</h2>
 *
 * <p>Not only on the client. {@code VanillaConnectionNetworkFilter.filterCommandList} — NeoForge's
 * filter for a client that is not NeoForge — rebuilds the command tree from the serialized stubs
 * <em>on the server</em> before writing the packet, and that rebuild calls {@code instantiate}. So a
 * template that throws does not fail on a client: it fails on the server, inside Netty's encoder,
 * and the write is abandoned with {@code EncoderException}. That is exactly what happened when this
 * was a {@code SingletonArgumentInfo}, and it is why the failure named a lambda in this package
 * rather than anything about the client.
 */
final class ForbricSelectorArguments {

    /** {@code single} — one target only, as {@code entity()} and {@code player()} promise. */
    static final byte FLAG_SINGLE = 1;
    /** {@code playersOnly} — {@code player()} and {@code players()}. */
    static final byte FLAG_PLAYERS_ONLY = 2;

    private static final ForbricSelectorArgumentInfo INFO = new ForbricSelectorArgumentInfo();
    private static volatile boolean installed;

    private ForbricSelectorArguments() {
    }

    /**
     * Installs the map entry, once.
     *
     * <p>{@code registerByClass} is {@code synchronized} and throws when the class is already bound,
     * so this is guarded by a flag rather than by catching the throw: the flag is the intent, and a
     * second call is not an error.
     *
     * <p><b>The cast, and why it is not a shortcut.</b> {@code registerByClass} is declared
     * {@code <A extends ArgumentType<?>, T extends Template<A>, I extends ArgumentTypeInfo<A, T>>},
     * so it wants the argument class and the info to agree on a type parameter. {@code
     * ForbricSelectorType<T>} is generic in the <em>resolver</em> it parses to, which is a narrowing
     * the game's declaration cannot express: {@code ArgumentType<EntitySelectorArgumentResolver>} and
     * {@code ArgumentType<PlayerSelectorArgumentResolver>} are the same class here. The erased forms
     * are what the map is keyed and queried by, and the map only ever stores the pair, so the cast
     * states the erasure the game is already using.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void install() {
        if (installed) return;
        synchronized (ForbricSelectorArguments.class) {
            if (installed) return;
            ArgumentTypeInfos.registerByClass((Class) ForbricSelectorType.class, (ArgumentTypeInfo) INFO);
            installed = true;
            ForbricLog.info("[Forbric/Bukkit] registered org.cn.xiaofan.bukkit.ForbricSelectorType with "
                    + "ArgumentTypeInfos — without it any plugin command taking an @e/@p selector made "
                    + "PlayerList.placeNewPlayer throw \"Unrecognized argument type\" and the joining "
                    + "player was disconnected with \"Invalid player data\"");
        }
    }

    /** {@code ArgumentTypeInfo} for {@link ForbricSelectorType}: one flag byte. */
    private static final class ForbricSelectorArgumentInfo
            implements ArgumentTypeInfo<ForbricSelectorType<?>, ForbricSelectorArgumentInfo.SelectorTemplate> {

        /**
         * Whether the game can name this argument type, which is what decides if it survives to a client.
         *
         * <p>Two separate facts, and the failure mode differs:
         *
         * <ul>
         *   <li>{@code byClass} throws when the type is unknown. That was the original crash, and it is
         *       what {@link ForbricSelectorArguments#install()} fixes.
         *   <li>The registry id is what the client reads back. {@code -1} means NeoForge's
         *       {@code CommandTreeCleaner} removes the node for a client that is not NeoForge — correct,
         *       because a client that never heard of this type cannot describe the node — but if the node is
         *       <em>not</em> removed, the packet carries {@code -1} and the client's {@code byId(-1)} answers
         *       null. Its {@code ArgumentNodeStub} is then built with a null argument type, which is the
         *       client-side failure that logs nothing on the server.
         * </ul>
         *
         * <p>Both are reported so the two can be told apart from the log instead of from the symptom.
         */
        private static String classify(net.minecraft.commands.synchronization.ArgumentTypeInfo<?, ?> info) {
            boolean named;
            try {
                named = net.minecraft.commands.synchronization.ArgumentTypeInfos
                        .isClassRecognized(ForbricSelectorType.class);
            } catch (RuntimeException | LinkageError unknown) {
                named = false;
            }
            int id;
            try {
                var registry = net.minecraft.core.registries.BuiltInRegistries.COMMAND_ARGUMENT_TYPE;
                // getId is looked up reflectively: the registry's element type is a wildcard and the method does
                // not accept one, so a direct call does not compile against this parameter type.
                id = (int) registry.getClass().getMethod("getId", Object.class).invoke(registry, info);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                id = Integer.MIN_VALUE;
            }
            return "byClass=" + named + " registryId=" + id
                    + (id < 0 ? " (not in the registry: NeoForge removes this node for a non-NeoForge client, "
                                    + "and if it is ever sent anyway the client reads it as an unknown type)"
                                    : "");
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void serializeToNetwork(SelectorTemplate template, FriendlyByteBuf buffer) {
            buffer.writeByte(template.flags());
            ForbricLog.info("[Forbric/Bukkit] selector argument serialized for a command packet (flags=%d, %s)",
                    template.flags(), classify(this));
        }

        @Override
        public SelectorTemplate deserializeFromNetwork(FriendlyByteBuf buffer) {
            return new SelectorTemplate(this, buffer.readByte());
        }

        @Override
        public void serializeToJson(SelectorTemplate template, JsonObject json) {
            // The four forms are distinguished by the command's own syntax, so a data pack that names
            // this argument has nothing extra to say about it. The node's name is the whole story.
        }

        @Override
        public SelectorTemplate unpack(ForbricSelectorType<?> type) {
            return new SelectorTemplate(this, type.flags());
        }

        /** One selector argument, as the two bits that describe it. */
        static final class SelectorTemplate implements ArgumentTypeInfo.Template<ForbricSelectorType<?>> {

            private final ForbricSelectorArgumentInfo owner;
            private final byte flags;

            SelectorTemplate(ForbricSelectorArgumentInfo owner, byte flags) {
                this.owner = owner;
                this.flags = flags;
            }

            byte flags() {
                return flags;
            }

            @Override
            public ForbricSelectorType<?> instantiate(CommandBuildContext context) {
                // Must return a ForbricSelectorType: the interface fixes that return type, so
                // returning the game's EntityArgument here would be a ClassCastException at the
                // method's own return, not a clever substitution. The rebuilt tree therefore keeps a
                // ForbricSelectorType, and NeoForge's cleaner then removes it for a vanilla client —
                // which is the outcome the class comment describes and the reason the parameter is
                // absent from that client's tree.
                ForbricSelectorType<?> rebuilt = ForbricSelectorType.fromFlags(flags);
                ForbricLog.info("[Forbric/Bukkit] rebuilt a selector argument for a command packet "
                        + "(flags=%d) — this runs on the server while NeoForge re-creates the tree for "
                        + "a client that is not NeoForge", flags);
                return rebuilt;
            }

            @Override
            public ArgumentTypeInfo<ForbricSelectorType<?>, ?> type() {
                return owner;
            }
        }
    }
}
