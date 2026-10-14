/*
 * Forbric Bukkit bridge -- the vanilla argument types, in Paper's shape.
 */
package io.papermc.paper.command.brigadier.argument;

import org.cn.xiaofan.bukkit.ForbricArgumentTypes;

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

    /** The bridge's argument factory, or a named failure when nothing has started. */
    private static ForbricArgumentTypes factory() {
        ForbricArgumentTypes current = ForbricArgumentTypes.current();
        if (current == null) {
            throw new IllegalStateException("[Forbric/Bukkit] a command argument type was asked for before the "
                            + "plugin bridge started; there is no server to build one against");
        }
        return current;
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver> entity() {
        return factory().entity();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver> player() {
        return factory().player();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver> entities() {
        return factory().entities();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver> players() {
        return factory().players();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver> playerProfiles() {
        return factory().playerProfiles();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver> blockPosition() {
        return factory().blockPosition();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.ColumnBlockPositionResolver> columnBlockPosition() {
        return factory().columnBlockPosition();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver> finePosition(boolean arg0) {
        return factory().finePosition(arg0);
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.ColumnFinePositionResolver> columnFinePosition(boolean arg0) {
        return factory().columnFinePosition(arg0);
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.RotationResolver> rotation() {
        return factory().rotation();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.AngleResolver> angle() {
        return factory().angle();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.AxisSet> axes() {
        return factory().axes();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.block.BlockState> blockState() {
        return factory().blockState();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.predicate.BlockInWorldPredicate> blockInWorldPredicate() {
        return factory().blockInWorldPredicate();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.inventory.ItemStack> itemStack() {
        return factory().itemStack();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.predicate.ItemStackPredicate> itemStackPredicate() {
        return factory().itemStackPredicate();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<net.kyori.adventure.text.format.NamedTextColor> namedColor() {
        return factory().namedColor();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<net.kyori.adventure.text.format.TextColor> hexColor() {
        return factory().hexColor();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<net.kyori.adventure.text.Component> component() {
        return factory().component();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<net.kyori.adventure.text.format.Style> style() {
        return factory().style();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.SignedMessageResolver> signedMessage() {
        return factory().signedMessage();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.scoreboard.DisplaySlot> scoreboardDisplaySlot() {
        return factory().scoreboardDisplaySlot();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.NamespacedKey> namespacedKey() {
        return factory().namespacedKey();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<net.kyori.adventure.key.Key> key() {
        return factory().key();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.range.IntegerRangeProvider> integerRange() {
        return factory().integerRange();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.range.DoubleRangeProvider> doubleRange() {
        return factory().doubleRange();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.World> world() {
        return factory().world();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.GameMode> gameMode() {
        return factory().gameMode();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.HeightMap> heightMap() {
        return factory().heightMap();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<java.util.UUID> uuid() {
        return factory().uuid();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.scoreboard.Criteria> objectiveCriteria() {
        return factory().objectiveCriteria();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.entity.LookAnchor> entityAnchor() {
        return factory().entityAnchor();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<java.lang.Integer> time(int arg0) {
        return factory().time(arg0);
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.block.structure.Mirror> templateMirror() {
        return factory().templateMirror();
    }

    @Override
    public com.mojang.brigadier.arguments.ArgumentType<org.bukkit.block.structure.StructureRotation> templateRotation() {
        return factory().templateRotation();
    }

    @Override
    public <T> com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.registry.TypedKey<T>> resourceKey(io.papermc.paper.registry.RegistryKey<T> arg0) {
        return factory().resourceKey(arg0);
    }

    @Override
    public <T> com.mojang.brigadier.arguments.ArgumentType<T> resource(io.papermc.paper.registry.RegistryKey<T> arg0) {
        return factory().resource(arg0);
    }

}
