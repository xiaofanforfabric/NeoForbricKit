/*
 * Forbric Bukkit bridge — one item kind, as a plugin sees it.
 */
package org.cn.xiaofan.bukkit;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * One entry of the item registry, as an {@code org.bukkit.inventory.ItemType}.
 *
 * <p><b>What stands on this class.</b> {@code ItemStack.of(material, amount)} calls
 * {@code Material.asItemType().createItemStack(amount)}, and {@code createItemStack} is here. So
 * every {@code new ItemStack(...)} a plugin writes lands in this file — and what it must return is
 * an item the rest of the bridge can actually read, which is why it returns a
 * {@link ForbricItemStack} rather than an object of the API's own class: that class's methods all
 * forward to a CraftBukkit delegate this bridge does not have.
 *
 * <p><b>Identity is the registry id.</b> One instance per id, cached, so {@code getType() ==} and
 * the equals a plugin does on two {@code ItemType}s both behave the way the API describes. The id
 * is the only thing kept: everything else is read from the game's item when asked, because the
 * game's item is what actually decides a stack size or a burn duration.
 */
final class ForbricItemType implements org.bukkit.inventory.ItemType {

    private final Identifier id;

    private ForbricItemType(Identifier id) {
        this.id = id;
    }

    /**
     * The one instance for an id.
     *
     * <p>Cached because two {@code ItemType}s for the same id must be equal and ideally identical —
     * a plugin compares them and puts them in sets. The cache is bounded by the item registry, which
     * is fixed once the world has loaded.
     */
    static ForbricItemType of(Identifier id) {
        return CACHE.computeIfAbsent(id, ForbricItemType::new);
    }

    private static final java.util.Map<Identifier, ForbricItemType> CACHE =
                    java.util.Collections.synchronizedMap(new java.util.HashMap<>());

    /** The game's item, looked up fresh: the registry is complete only after the world loads. */
    private Item item() {
        return BuiltInRegistries.ITEM.getValue(id);
    }

    /** The registry id behind this type, for {@link ForbricItemStack}. */
    Identifier identifier() {
        return id;
    }

    /** This entry's key, e.g. {@code minecraft:stone}. */
    @Override
    public org.bukkit.NamespacedKey getKey() {
        return new org.bukkit.NamespacedKey(id.getNamespace(), id.getPath());
    }

    /**
     * A stack of this item — the endpoint of {@code ItemStack.of(material, amount)}.
     *
     * <p>An amount of zero or less gives an empty stack, which is what the API's own contract says:
     * a stack of nothing is air, not an error.
     */
    @Override
    public org.bukkit.inventory.ItemStack createItemStack(int amount) {
        if (amount <= 0) {
            return ForbricItemStack.plain(org.bukkit.Material.AIR, 0);
        }
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(id.toString());
        if (material == null) {
            // No Bukkit constant for this item — a mod's. The bridge can still hold it, named by
            // its id, which is better than refusing: the plugin asked for a specific item and got
            // that item.
            return ForbricItemStack.ofItem(id, amount);
        }
        return ForbricItemStack.plain(material, amount);
    }

    /** A stack of one. */
    @Override
    public org.bukkit.inventory.ItemStack createItemStack() {
        return createItemStack(1);
    }

    /** The material with the same id, or null when this bridge's API has no constant for it. */
    @Override
    public org.bukkit.Material asMaterial() {
        return org.bukkit.Material.matchMaterial(id.toString());
    }

    /** How large a stack of this item is, asked of the game. */
    @Override
    public int getMaxStackSize() {
        return item().getDefaultMaxStackSize();
    }

    /**
     * This item's tooltip key, e.g. {@code block.minecraft.stone}.
     *
     * <p>Read from the game so a mod-added item answers with the key its own code declares, rather
     * than one this bridge invented from the id.
     */
    @Override
    public String getTranslationKey() {
        return item().getDescriptionId();
    }

    /** The same key, in the shape Adventure's translatable asks for it. */
    @Override
    public String translationKey() {
        return getTranslationKey();
    }

    /** Whether this item can be placed, as the game decides it. */
    @Override
    public boolean hasBlockType() {
        return item() instanceof net.minecraft.world.item.BlockItem;
    }

    /**
     * The block this item places, when it places one.
     *
     * <p>Refuses. {@code BlockType} is a large interface with its own implementation behind it, and
     * this bridge has not built that yet — the item path (which is what a plugin storing items
     * needs) does not go through it. Returning a stand-in would look like it worked and give a
     * plugin an object whose every other method is missing.
     */
    @Override
    public org.bukkit.block.BlockType getBlockType() {
        if (!(item() instanceof net.minecraft.world.item.BlockItem)) {
            throw new IllegalStateException(
                            "[Forbric/Bukkit] " + id + " is not a block item, so it has no BlockType");
        }
        throw notYet("getBlockType");
    }

    /**
     * Whether the game says this item can be eaten.
     *
     * <p>Asked of the item's food component, which is how the game decides it — the {@code Item}
     * class has no {@code isEdible()}.
     */
    @Override
    public boolean isEdible() {
        return item().components().has(net.minecraft.core.component.DataComponents.FOOD);
    }

    /**
     * Whether the game says this item is a music disc.
     *
     * <p>Asked of the {@code JUKEBOX_PLAYABLE} component rather than of the item's class: 26.2 no
     * longer has a {@code RecordItem}, and a class check would be wrong for a mod's disc anyway.
     */
    @Override
    public boolean isRecord() {
        return item().components().has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE);
    }

    /** Whether the game says this item burns. */
    @Override
    public boolean isFuel() {
        return getBurnDuration() > 0;
    }

    /**
     * How long this item burns, asked of the game's fuel table.
     *
     * <p>{@code FuelValues} reports the burn time of a STACK, so one item is built and asked. The
     * table is created per call rather than cached: it is built from the game's own registries,
     * which are not complete until the world has loaded, and a cached one would be the empty table
     * from before that.
     */
    /**
     * How long this item burns, asked of the game's fuel table.
     *
     * <p>{@code FuelValues} reports the burn time of a STACK, so one item is built and asked. The
     * table is rebuilt per call rather than cached: it is derived from the game's registries, which
     * are not complete until the world has loaded, and a cached one would be the empty table from
     * before that.
     *
     * <p>An empty feature set means every fuel counts, including ones behind a flag this server has
     * off — the honest answer to "how long would this burn", which is what the API asks.
     */
    @Override
    public int getBurnDuration() {
        net.minecraft.core.RegistryAccess registries = ForbricRegistries.access();
        if (registries == null) {
            return 0;
        }
        return net.minecraft.world.level.block.entity.FuelValues
                        .vanillaBurnTimes(registries, net.minecraft.world.flag.FeatureFlags.VANILLA_SET)
                        .burnDuration(new net.minecraft.world.item.ItemStack(item()));
    }

    /** Equal by registry id — the identity the API describes. */
    @Override
    public boolean equals(Object other) {
        return other instanceof ForbricItemType && ((ForbricItemType) other).id.equals(id);
    }

    /** Hashes by the registry id, matching {@link #equals}. */
    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Names the id, so a failed assertion says which item. */
    @Override
    public String toString() {
        return "ForbricItemType[" + id + "]";
    }

    // ---- Not implemented ------------------------------------------------------------------------
    //
    // Each belongs to a Paper subsystem this bridge does not build. Written out rather than
    // inherited so the failure carries the missing feature's name.

    private static UnsupportedOperationException notYet(String what) {
        return new UnsupportedOperationException(
                        "[Forbric/Bukkit] ItemType." + what + "() is not implemented by this bridge yet");
    }

    @Override
    public org.bukkit.inventory.ItemType.Typed<org.bukkit.inventory.meta.ItemMeta> typed() {
        throw notYet("typed");
    }

    @Override
    public <M extends org.bukkit.inventory.meta.ItemMeta> org.bukkit.inventory.ItemType.Typed<M> typed(
                    Class<M> metaClass) {
        throw notYet("typed");
    }

    @Override
    public Class<? extends org.bukkit.inventory.meta.ItemMeta> getItemMetaClass() {
        throw notYet("getItemMetaClass");
    }

    /**
     * How much damage this item can take before breaking.
     *
     * <p>Read from a stack of it, because that is where the game keeps the number in 26.2 — the
     * {@code Item} class has no {@code getMaxDamage()} any more.
     */
    @Override
    public short getMaxDurability() {
        return (short) new net.minecraft.world.item.ItemStack(item()).getMaxDamage();
    }

    @Override
    public boolean isCompostable() {
        throw notYet("isCompostable");
    }

    @Override
    public float getCompostChance() {
        throw notYet("getCompostChance");
    }

    @Override
    public org.bukkit.inventory.ItemType getCraftingRemainingItem() {
        throw notYet("getCraftingRemainingItem");
    }

    @Override
    public com.google.common.collect.Multimap<org.bukkit.attribute.Attribute,
                    org.bukkit.attribute.AttributeModifier> getDefaultAttributeModifiers() {
        throw notYet("getDefaultAttributeModifiers");
    }

    @Override
    public com.google.common.collect.Multimap<org.bukkit.attribute.Attribute,
                    org.bukkit.attribute.AttributeModifier> getDefaultAttributeModifiers(
                                    org.bukkit.inventory.EquipmentSlot slot) {
        throw notYet("getDefaultAttributeModifiers");
    }

    @Override
    public org.bukkit.inventory.CreativeCategory getCreativeCategory() {
        throw notYet("getCreativeCategory");
    }

    @Override
    public boolean isEnabledByFeature(org.bukkit.World world) {
        return true;
    }

    @Override
    public org.bukkit.inventory.ItemRarity getItemRarity() {
        throw notYet("getItemRarity");
    }

    @Override
    public <T> T getDefaultData(io.papermc.paper.datacomponent.DataComponentType.Valued<T> type) {
        throw notYet("getDefaultData");
    }

    @Override
    public boolean hasDefaultData(io.papermc.paper.datacomponent.DataComponentType type) {
        throw notYet("hasDefaultData");
    }

    @Override
    public java.util.Set<io.papermc.paper.datacomponent.DataComponentType> getDefaultDataTypes() {
        throw notYet("getDefaultDataTypes");
    }
}
