/*
 * Forbric Bukkit bridge -- the vanilla argument types, in Paper's shape.
 */
package io.papermc.paper.command.brigadier.argument;

import net.neoforbrickit.bukkit.ForbricArgumentTypes;

/**
 * The argument types {@code ArgumentTypes} hands out.
 *
 * <p><b>Why this class is in Paper's package.</b> {@code VanillaArgumentProvider} is not public, and
 * {@code ServiceLoader} resolves a provider through the interface's own class loader, so an implementation has
 * to sit in {@code io.papermc.paper.command.brigadier.argument} to be found at all. Paper's server jar puts its
 * provider here for the same reason; this bridge stands in for it.
 *
 * <p><b>What was failing without it.</b> Every {@code ArgumentTypes.xxx()} call ends in
 * {@code VanillaArgumentProvider.provider()}, whose lookup returned empty, so the {@code orElseThrow} threw
 * {@code NoSuchElementException}. A plugin reached it the moment it built a command with an argument -- INSIDE
 * the COMMANDS lifecycle handler, after {@code onEnable} had already logged success. That is the worst shape a
 * failure can take: the plugin reports itself enabled and then dies on the first line of its registration.
 *
 * <p><b>Every method here forwards, and that is the implementation.</b> The types come from
 * {@link ForbricArgumentTypes}, which wraps the game's own argument types -- {@code ArgumentTypes.players()} on a
 * real server wraps {@code EntityArgument.players()}, and so does this. They are not reimplemented because an
 * argument type IS the parser, and a parser written here would accept a slightly different language from the one
 * the client's tab-completion suggests. The generated signatures are copied from the interface rather than
 * recalled, so a method Paper adds appears as a compile error instead of an InvocationTargetException.
 */
public final class ForbricVanillaArgumentProvider implements VanillaArgumentProvider {

    /**
     * Required by {@code ServiceLoader}, which instantiates a provider reflectively with no arguments.
     *
     * <p>Stateless: the per-server factory is reached through {@link ForbricArgumentTypes#current()}, set once
     * when the bridge starts. A provider that took the server in its constructor could not be discovered at all,
     * and the lookup would come back empty again -- the failure this class exists to fix.
     */
    public ForbricVanillaArgumentProvider() {
    }

    /**
     * The bridge's argument factory, as an opaque object.
     *
     * <p><b>Not typed as {@code ForbricArgumentTypes}, and that is the fix.</b> Paper instantiates this provider
     * through {@code ServiceLoader}, using the interface's own loader -- FML's plugins layer -- while the factory
     * was created by the game layer. The two layers define one class of that name each, so the value cannot be
     * cast to this layer's copy (ClassCastException), and calling the methods would fail anyway: they build
     * {@code net.minecraft.commands.arguments.*} values, which this layer cannot link.
     *
     * <p>So it crosses as {@code Object} and the calls go through {@link #ask}: the method body that runs is the
     * game layer's, where Minecraft is visible.
     */
    private static Object factory() {
        Object current = ForbricArgumentTypes.sharedInstance();
        if (current == null) {
            throw new IllegalStateException("[Forbric/Bukkit] a command argument type was asked for before the "
                            + "plugin bridge started; there is no server to build one against");
        }
        return current;
    }

    /**
     * One argument type from the game-layer factory, by method name.
     *
     * <p>The generic return is what keeps the thirty-odd methods below forwarders rather than a wall of casts:
     * {@code return ask("players")} is an {@code ArgumentType<PlayerSelectorArgumentResolver>} to the compiler,
     * taken from the method's own declared type.
     */
    @SuppressWarnings("unchecked")
    private static <T> T ask(String method, Object... args) {
        Object factory = factory();
        for (java.lang.reflect.Method candidate : factory.getClass().getMethods()) {
            if (!candidate.getName().equals(method) || candidate.getParameterCount() != args.length) {
                continue;
            }
            try {
                return (T) candidate.invoke(factory, args);
            } catch (java.lang.reflect.InvocationTargetException wrapped) {
                Throwable cause = wrapped.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(cause);
            } catch (ReflectiveOperationException failed) {
                throw new IllegalStateException(
                                "[Forbric/Bukkit] could not build the argument type " + method, failed);
            }
        }
        throw new UnsupportedOperationException("[Forbric/Bukkit] the bridge's argument factory has no method "
                        + method + " taking " + args.length + " argument(s)");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver> entity() {
        return ask("entity");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver> player() {
        return ask("player");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver> entities() {
        return ask("entities");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver> players() {
        return ask("players");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver> playerProfiles() {
        return ask("playerProfiles");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver> blockPosition() {
        return ask("blockPosition");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.ColumnBlockPositionResolver> columnBlockPosition() {
        return ask("columnBlockPosition");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver> finePosition(boolean arg0) {
        return ask("finePosition", arg0);
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.ColumnFinePositionResolver> columnFinePosition(boolean arg0) {
        return ask("columnFinePosition", arg0);
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.RotationResolver> rotation() {
        return ask("rotation");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.AngleResolver> angle() {
        return ask("angle");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.AxisSet> axes() {
        return ask("axes");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.block.BlockState> blockState() {
        return ask("blockState");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.predicate.BlockInWorldPredicate> blockInWorldPredicate() {
        return ask("blockInWorldPredicate");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.inventory.ItemStack> itemStack() {
        return ask("itemStack");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.predicate.ItemStackPredicate> itemStackPredicate() {
        return ask("itemStackPredicate");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<net.kyori.adventure.text.format.NamedTextColor> namedColor() {
        return ask("namedColor");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<net.kyori.adventure.text.format.TextColor> hexColor() {
        return ask("hexColor");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<net.kyori.adventure.text.Component> component() {
        return ask("component");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<net.kyori.adventure.text.format.Style> style() {
        return ask("style");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.SignedMessageResolver> signedMessage() {
        return ask("signedMessage");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.scoreboard.DisplaySlot> scoreboardDisplaySlot() {
        return ask("scoreboardDisplaySlot");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.NamespacedKey> namespacedKey() {
        return ask("namespacedKey");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<net.kyori.adventure.key.Key> key() {
        return ask("key");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.range.IntegerRangeProvider> integerRange() {
        return ask("integerRange");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.range.DoubleRangeProvider> doubleRange() {
        return ask("doubleRange");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.World> world() {
        return ask("world");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.GameMode> gameMode() {
        return ask("gameMode");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.HeightMap> heightMap() {
        return ask("heightMap");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<java.util.UUID> uuid() {
        return ask("uuid");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.scoreboard.Criteria> objectiveCriteria() {
        return ask("objectiveCriteria");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.entity.LookAnchor> entityAnchor() {
        return ask("entityAnchor");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<java.lang.Integer> time(int arg0) {
        return ask("time", arg0);
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.block.structure.Mirror> templateMirror() {
        return ask("templateMirror");
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.block.structure.StructureRotation> templateRotation() {
        return ask("templateRotation");
    }

    @Override
    public <T> com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.registry.TypedKey<T>> resourceKey(io.papermc.paper.registry.RegistryKey<T> arg0) {
        return ask("resourceKey", arg0);
    }

    @Override
    public <T> com.mojang.brigadier.arguments.ArgumentType<T> resource(io.papermc.paper.registry.RegistryKey<T> arg0) {
        return ask("resource", arg0);
    }

}
