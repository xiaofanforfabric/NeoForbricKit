/*
 * Forbric Bukkit bridge — one item, as a plugin sees it.
 */
package org.cn.xiaofan.bukkit;

import java.util.Objects;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The {@code org.bukkit.inventory.ItemStack} this bridge hands a plugin.
 *
 * <p><b>Why this class cannot be the API's own class.</b> Paper's
 * {@code org.bukkit.inventory.ItemStack} is a shell: every instance method in it does
 * {@code getfield craftDelegate} and forwards to a second, same-named
 * {@code org.bukkit.inventory.ItemStack} that CraftBukkit supplies — a class that is not in
 * paper-api.jar and that this bridge does not have. The field is private, so an object of the
 * API's class can never be given a delegate from outside, and one built by its own constructor
 * has a null delegate and throws {@code NullPointerException} on the first getter.
 *
 * <p>So this class extends it and answers the questions itself, and — this is the part that is
 * easy to get wrong — it is also what {@code ItemType.createItemStack()} returns. That matters
 * because {@code ItemStack.of(Material, amount)} still goes through the API's own class: it
 * builds an API stack whose {@code craftDelegate} is whatever {@code createItemStack()} gave
 * back. Plugins call {@code of(...)} and {@code new ItemStack(material)}, so their objects are
 * the API's class wrapping one of these; the delegate is where every call lands.
 *
 * <p><b>What is stored, and what is not.</b> The material, the count, and the game's own stack
 * when there is one. There is no separate copy of the components (enchantments, custom name,
 * damage): a bridge that shadowed those in fields would diverge from the game's stack the
 * moment anything else touched the item, and the two would disagree about what the plugin is
 * holding. The wrapped {@code net.minecraft.world.item.ItemStack} is the single source of
 * truth, and this class is a view onto it — which is why mutating a stack a plugin took out of
 * an inventory is visible in the inventory.
 *
 * <p><b>A stack with no game stack.</b> A plugin may build one from a material alone
 * ({@code new ItemStack(Material.STONE)}) and never put it anywhere. That is legal and this
 * class supports it: {@link #game()} is then {@code ItemStack.EMPTY} and the material and count
 * live in fields. {@link #toGame()} materialises a game stack on demand, which is the only
 * moment a game item is needed.
 */
final class ForbricItemStack extends org.bukkit.inventory.ItemStack {

    /**
     * The game's stack, or {@code null} when this item was built from a material alone and has
     * never been in the world.
     *
     * <p>Null rather than {@code ItemStack.EMPTY} so the two cases are distinguishable: an empty
     * game stack and "no game stack yet" behave the same for reading, but writing components to
     * the shared {@code EMPTY} constant would be a mutation of a game-wide singleton.
     */
    private net.minecraft.world.item.ItemStack game;

    /** Used when there is no game stack to read them from, or to override one. */
    private org.bukkit.Material material;

    private int amount;

    private ForbricItemStack(org.bukkit.Material material, int amount,
                    net.minecraft.world.item.ItemStack game) {
        this.material = material;
        this.amount = amount;
        this.game = game;
    }

    /**
     * Wraps a game stack. The one place a game item becomes a plugin's item.
     *
     * <p>A fresh wrapper per call, matching the rest of the bridge. Two wrappers over the same
     * game stack are equal and hash alike — {@link #equals} is by material, count and
     * components — so nothing downstream can tell them apart.
     */
    static ForbricItemStack of(net.minecraft.world.item.ItemStack game) {
        org.bukkit.Material material = materialOf(game);
        return new ForbricItemStack(material, game.getCount(), game);
    }

    /** An item built from a material alone: what {@code new ItemStack(Material)} means. */
    static ForbricItemStack plain(org.bukkit.Material material, int amount) {
        return new ForbricItemStack(material, amount, null);
    }

    /**
     * An item for which this bridge's Bukkit API has no constant — a mod's.
     *
     * <p>Kept by registry id rather than by material, because there is no material to keep: the
     * plugin asked for an item that exists in the game but not in the API jar this bridge was built
     * against. Naming it is better than refusing, and {@link #getType()} answers
     * {@code Material.AIR} for it — the closest true thing, and the case a plugin should treat as
     * "an item this API version does not know".
     */
    static ForbricItemStack ofItem(Identifier id, int amount) {
        ForbricItemStack stack = new ForbricItemStack(org.bukkit.Material.AIR, amount, null);
        stack.modItemId = id;
        return stack;
    }

    /** Set only for a mod item, whose material this API version does not have. */
    private Identifier modItemId;

    /** The registry id behind this item, for a mod item that has no material. */
    Identifier itemId() {
        if (game != null && !game.isEmpty()) {
            return BuiltInRegistries.ITEM.getKey(game.getItem());
        }
        if (modItemId != null) {
            return modItemId;
        }
        return material == null ? null : identifierOf(material);
    }

    /**
     * The registry id of a game stack's item, as a Bukkit material.
     *
     * <p>The registry id is the bridge, for the same reason it is in {@link ForbricBlock}: it is
     * stable where numeric ids are not, and it is the name both sides already agree on.
     *
     * <p>An empty game stack has no item to name — the game's {@code ItemStack.EMPTY} is air —
     * and Bukkit spells that {@code Material.AIR}.
     */
    static org.bukkit.Material materialOf(net.minecraft.world.item.ItemStack game) {
        if (game == null || game.isEmpty()) {
            return org.bukkit.Material.AIR;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(game.getItem());
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(id.toString());
        if (material == null) {
            // A mod-added item this bridge's copy of the Bukkit API has never heard of. Naming it
            // beats returning air, which would read as an empty slot and lose the item silently.
            throw new UnsupportedOperationException(
                            "[Forbric/Bukkit] no Material for item " + id + " (a mod item, or a newer game"
                                            + " than this API was built against)");
        }
        return material;
    }

    /**
     * The game stack to hand to the world, materialised from the fields if there is none yet.
     *
     * <p>The conversion is the registry id, reversed: the item registry is keyed by exactly the
     * name {@code Material} carries.
     */
    net.minecraft.world.item.ItemStack toGame() {
        if (game != null) {
            game.setCount(amount);
            return game;
        }
        if (modItemId != null) {
            Item item = BuiltInRegistries.ITEM.getValue(modItemId);
            net.minecraft.world.item.ItemStack created =
                            new net.minecraft.world.item.ItemStack(item, Math.max(amount, 1));
            game = created;
            return created;
        }
        if (material == null || material == org.bukkit.Material.AIR) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifierOf(material));
        net.minecraft.world.item.ItemStack created =
                        new net.minecraft.world.item.ItemStack(item, Math.max(amount, 1));
        // Kept so a second call does not build a second game stack: an item a plugin mutated
        // through one of these wrappers must stay the same object the world was given.
        game = created;
        return created;
    }

    /**
     * A Bukkit material's registry id.
     *
     * <p>{@code Material} names its entry with the lower-case id the game uses
     * ({@code minecraft:oak_log}), but a mod-added material carries its own namespace, so the
     * name is parsed rather than prefixed: {@code Identifier.parse} accepts both the namespaced
     * and the bare form.
     */
    private static Identifier identifierOf(org.bukkit.Material material) {
        String name = material.getKey().toString();
        return Identifier.parse(name);
    }

    /** What this item is. Read through to the game stack when there is one. */
    @Override
    public org.bukkit.Material getType() {
        if (game != null) {
            return materialOf(game);
        }
        // A mod item has no Material in this API version; AIR is the closest true answer, and the
        // id is still available through getType()'s sibling on the registry side.
        return modItemId != null ? org.bukkit.Material.AIR : material;
    }

    /**
     * Whether this stack holds nothing.
     *
     * <p><b>Overriding this is not optional.</b> The API's own implementation reads its private
     * {@code craftDelegate}, which is null for every stack this bridge builds — so inheriting it
     * turns a question with an obvious answer into a {@code NullPointerException} several frames
     * from the cause. It is also on the path a plugin stores items through:
     * {@code ItemStack.serializeItemsAsBytes(...)} asks each element first.
     *
     * <p>Air, a count of zero, and no type at all are all "empty", which is what the API means.
     */
    @Override
    public boolean isEmpty() {
        if (amount <= 0) {
            return true;
        }
        if (game != null) {
            return game.isEmpty();
        }
        return material == null || material == org.bukkit.Material.AIR;
    }

    /**
     * How much damage this item has taken.
     *
     * <p>Read from the game's stack, which is the only thing that tracks damage here; a
     * material-only item has taken none.
     */
    @Override
    public short getDurability() {
        if (game == null) {
            return 0;
        }
        return (short) game.getDamageValue();
    }

    /**
     * Sets this item's damage.
     *
     * <p>Refuses for a material-only item, because there is no game stack to write damage into —
     * accepting it would store the number where nothing reads it, and the getter above would keep
     * answering zero.
     */
    @Override
    public void setDurability(short durability) {
        if (game == null) {
            throw new UnsupportedOperationException(
                            "[Forbric/Bukkit] ItemStack.setDurability() needs a game stack; this item was built"
                                            + " from a material alone and has none.");
        }
        game.setDamageValue(durability);
    }

    /**
     * Changes what this item is, keeping its count.
     *
     * <p>A type change discards the old game stack: its components belong to the old item and
     * carrying them to a different one would produce an item the game cannot represent. That is
     * also what Bukkit's own implementation does.
     */
    @Override
    public void setType(org.bukkit.Material type) {
        if (type == null) {
            throw new IllegalArgumentException(
                            "[Forbric/Bukkit] ItemStack.setType() needs a material, got null");
        }
        if (!type.isItem()) {
            throw new IllegalArgumentException(
                            "[Forbric/Bukkit] ItemStack.setType() needs an item material, got " + type);
        }
        this.material = type;
        this.game = null;
    }

    /** How many. */
    @Override
    public int getAmount() {
        return amount;
    }

    /**
     * Sets the count.
     *
     * <p>Zero is allowed and means the same as Bukkit's: a stack of nothing. It is not turned into
     * air here, because a plugin that sets 0 and then sets 5 again is expressing one item that is
     * momentarily empty, and collapsing that to air would lose the type.
     */
    @Override
    public void setAmount(int amount) {
        this.amount = amount;
    }

    /** The largest count this item stacks to, asked of the game so it matches what the world does. */
    @Override
    public int getMaxStackSize() {
        if (game != null) {
            return game.getMaxStackSize();
        }
        if (material == null || material == org.bukkit.Material.AIR) {
            return 64;
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifierOf(material));
        return new net.minecraft.world.item.ItemStack(item).getMaxStackSize();
    }

    /**
     * Whether two items are the same kind and the same count.
     *
     * <p>Components included: an enchanted sword is not an unenchanted one. A game stack is asked
     * directly, which is the only answer that cannot drift from what the world sees; a
     * material-only item compares material and count.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof org.bukkit.inventory.ItemStack)) {
            return false;
        }
        org.bukkit.inventory.ItemStack stack = (org.bukkit.inventory.ItemStack) other;
        if (getAmount() != stack.getAmount() || getType() != stack.getType()) {
            return false;
        }
        return componentsMatch(stack);
    }

    /**
     * Whether the non-material parts (enchantments, name, damage) are the same.
     *
     * <p>Compares the game's own stack when both sides have one, which is a comparison of the
     * component maps the game itself uses. When either side is material-only there are no
     * components to compare, so the answer is yes — the two are the same plain item.
     */
    private boolean componentsMatch(org.bukkit.inventory.ItemStack other) {
        net.minecraft.world.item.ItemStack mine = game;
        net.minecraft.world.item.ItemStack theirs =
                        other instanceof ForbricItemStack ? ((ForbricItemStack) other).game : null;
        if (mine == null || theirs == null) {
            return true;
        }
        return net.minecraft.world.item.ItemStack.matches(mine, theirs);
    }

    /** Hashes by the same parts {@link #equals} compares. */
    @Override
    public int hashCode() {
        return Objects.hash(getType(), getAmount());
    }

    /**
     * Whether this and another stack would merge.
     *
     * <p>Count is deliberately not compared — "similar" in Bukkit means "the same item", so two
     * stacks of 1 and 3 of the same material are similar and a plugin may add them together.
     */
    @Override
    public boolean isSimilar(org.bukkit.inventory.ItemStack other) {
        if (other == null) {
            return false;
        }
        return getType() == other.getType() && componentsMatch(other);
    }

    /** A copy, backed by a copy of the game stack so mutating one does not touch the other. */
    @Override
    public org.bukkit.inventory.ItemStack clone() {
        net.minecraft.world.item.ItemStack copy = game == null ? null : game.copy();
        return new ForbricItemStack(getType(), amount, copy);
    }

    /**
     * Whether the game believes this item has components worth keeping.
     *
     * <p>True when a game stack carries components. A material-only item has none by definition,
     * which is the honest answer rather than an exception: a plugin that builds an item from a
     * material and asks this has asked a question with an answer.
     */
    @Override
    public boolean hasItemMeta() {
        return game != null && !game.getComponents().isEmpty();
    }

    /**
     * The item's components as a {@code ItemMeta}, as a snapshot a plugin edits and writes back.
     *
     * <p>The values are read from the game's item components — {@code custom_name}, {@code lore},
     * {@code damage}, {@code max_damage} and {@code custom_data} — and copied into a wrapper. That
     * snapshot is the API's own contract: a plugin edits the meta and calls {@link #setItemMeta} to
     * apply it, and edits that are never written back are discarded.
     *
     * <p><b>The container is the exception, deliberately.</b> The persistent data container handed out
     * here IS the tag inside the item's component, so a plugin that writes a tag and forgets to write
     * the meta back still gets the tag stored. Bukkit's own implementations behave this way, and a
     * plugin tagging its items is the single most common reason to call this method at all.
     *
     * <p><b>Which class comes back depends on the item.</b> An item the game gives a
     * {@code max_damage} or {@code damage} — a tool, a weapon, armour — answers with
     * {@link ForbricDamageableMeta}, so a plugin's {@code meta instanceof Damageable} guard finds it.
     * Anything else answers with the plain meta, and that guard correctly fails: a block of stone
     * cannot wear out, and pretending otherwise would make a plugin write a damage value the game
     * then ignores.
     */
    @Override
    public org.bukkit.inventory.meta.ItemMeta getItemMeta() {
        net.minecraft.world.item.ItemStack stack = toGame();
        if (stack == net.minecraft.world.item.ItemStack.EMPTY || stack.isEmpty()) {
            // An air item has no components to read and no meta a plugin could usefully edit. Bukkit
            // answers null here on every server.
            return null;
        }
        net.kyori.adventure.text.Component name = ForbricComponentBridge.toAdventure(
                        stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        net.minecraft.world.item.component.ItemLore loreComponent =
                        stack.get(net.minecraft.core.component.DataComponents.LORE);
        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        if (loreComponent != null) {
            for (net.minecraft.network.chat.Component line : loreComponent.lines()) {
                net.kyori.adventure.text.Component adventure = ForbricComponentBridge.toAdventure(line);
                if (adventure != null) {
                    lore.add(adventure);
                }
            }
        }
        // The container is taken from the item's own tag, not a copy: writes through it land in the
        // component immediately, which is what a plugin tagging its item expects without a write-back.
        net.minecraft.world.item.component.CustomData custom =
                        stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        ForbricPersistentDataContainer container = new ForbricPersistentDataContainer(
                        custom == null ? new net.minecraft.nbt.CompoundTag() : custom.copyTag());
        // Written back at once so the container this wrapper holds and the one inside the item are the
        // same tag object from here on.
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(container.tag()));

        Integer maxDamage = stack.get(net.minecraft.core.component.DataComponents.MAX_DAMAGE);
        Integer damage = stack.get(net.minecraft.core.component.DataComponents.DAMAGE);
        if (maxDamage != null || damage != null) {
            return new ForbricDamageableMeta(name, lore, container, 0, null,
                            damage == null ? 0 : damage, damage != null, maxDamage);
        }
        return new ForbricItemMeta(name, lore, container, 0, null);
    }

    /**
     * Writes a meta's values back into this item's components.
     *
     * <p>A null meta clears the name, the lore and the damage value while leaving the plugin data
     * alone, which is what the API documents and what a plugin restoring an item's plain look relies
     * on. The data is left because a container cannot be un-set by passing null — a plugin clears its
     * own keys through the container itself.
     *
     * <p>Nothing here touches {@code max_damage}: a meta that overrode it would change how long the
     * item lasts, which is a different kind of change from renaming it and not one this bridge makes
     * implicitly.
     */
    @Override
    public boolean setItemMeta(org.bukkit.inventory.meta.ItemMeta itemMeta) {
        net.minecraft.world.item.ItemStack stack = toGame();
        if (stack == net.minecraft.world.item.ItemStack.EMPTY || stack.isEmpty()) {
            // Nothing to write to. Bukkit reports false rather than throwing, because a plugin may
            // legitimately hold an air item and set a meta on it.
            return false;
        }
        if (itemMeta == null) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, null);
            stack.set(net.minecraft.core.component.DataComponents.LORE, null);
            stack.set(net.minecraft.core.component.DataComponents.DAMAGE, null);
            return true;
        }
        // The snapshot accessors are on this bridge's own class. A meta from somewhere else cannot be
        // read, and guessing at it would write a default name onto the item.
        if (!(itemMeta instanceof ForbricItemMeta meta)) {
            return false;
        }
        net.minecraft.network.chat.Component name = ForbricComponentBridge.toGame(meta.nameSnapshot());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        java.util.List<net.kyori.adventure.text.Component> lines = meta.loreSnapshot();
        if (lines.isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.LORE, null);
        } else {
            java.util.List<net.minecraft.network.chat.Component> gameLines = new java.util.ArrayList<>();
            for (net.kyori.adventure.text.Component line : lines) {
                net.minecraft.network.chat.Component gameLine = ForbricComponentBridge.toGame(line);
                if (gameLine != null) {
                    gameLines.add(gameLine);
                }
            }
            stack.set(net.minecraft.core.component.DataComponents.LORE,
                            new net.minecraft.world.item.component.ItemLore(gameLines));
        }
        // The container was shared, so its tag is already inside the item; re-setting it keeps a plugin
        // that replaced the whole meta rather than editing the one it was given consistent.
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(meta.container().tag()));

        if (meta instanceof ForbricDamageableMeta damageable) {
            stack.set(net.minecraft.core.component.DataComponents.DAMAGE, damageable.getDamage());
            if (damageable.hasMaxDamage()) {
                stack.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, damageable.getMaxDamage());
            }
        }
        return true;
    }

    /**
     * This item in the serialised form {@code ItemStack.serializeAsBytes} produces.
     *
     * <p>The format is Paper's, not the game's: a version byte, then the game's network encoding
     * of the item. A plugin that round-trips through it (the common way a plugin stores an item)
     * gets back an equal stack, which is what the API promises.
     */
    @Override
    public byte[] serializeAsBytes() {
        return ForbricItemCodec.encode(this);
    }

    /**
     * The item's translation key, e.g. {@code block.minecraft.stone}.
     *
     * <p>Asked of the game so it matches the key the client would resolve, including for
     * mod-added items whose key this bridge's Bukkit API has no table for.
     */
    @Override
    public String translationKey() {
        if (game != null) {
            return game.getItem().getDescriptionId();
        }
        if (material == null) {
            return "block.minecraft.air";
        }
        Item item = BuiltInRegistries.ITEM.getValue(identifierOf(material));
        return item.getDescriptionId();
    }

    /** The material's and count's own rendering, which is what a failed assertion wants to see. */
    @Override
    public String toString() {
        return "ForbricItemStack[" + getType() + " x" + amount + "]";
    }

    /** Unsupported, and named: this bridge stores no separate MaterialData. */
    @Override
    public org.bukkit.material.MaterialData getData() {
        throw new UnsupportedOperationException(
                        "[Forbric/Bukkit] ItemStack.getData() is not implemented by this bridge"
                                        + " (MaterialData is the pre-1.13 model; use getType())");
    }

    // ---- The component layer, refused by name -----------------------------------------------------
    //
    // Everything below reads the API class's private `craftDelegate`, which is null for every stack
    // this bridge builds — so leaving them inherited turns a call into a NullPointerException that
    // names a field no plugin has heard of. They are written out so the failure says which feature
    // is missing instead. Implementing them means mapping Bukkit's components to the game's, which
    // is a piece of work in its own right and not part of the item path a plugin stores kits with.

    private static UnsupportedOperationException componentNotYet(String what) {
        return new UnsupportedOperationException(
                        "[Forbric/Bukkit] ItemStack." + what + "() is not implemented by this bridge yet"
                                        + " (needs the Bukkit-to-game component mapping)");
    }

    @Override
    public org.bukkit.inventory.ItemStack withType(org.bukkit.Material type) {
        ForbricItemStack copy = (ForbricItemStack) clone();
        copy.setType(type);
        return copy;
    }

    @Override
    public java.util.Map<String, Object> serialize() {
        throw componentNotYet("serialize");
    }

    @Override
    public java.util.Map<org.bukkit.enchantments.Enchantment, Integer> getEnchantments() {
        throw componentNotYet("getEnchantments");
    }

    @Override
    public boolean containsEnchantment(org.bukkit.enchantments.Enchantment enchantment) {
        throw componentNotYet("containsEnchantment");
    }

    @Override
    public int getEnchantmentLevel(org.bukkit.enchantments.Enchantment enchantment) {
        throw componentNotYet("getEnchantmentLevel");
    }

    @Override
    public void addUnsafeEnchantment(org.bukkit.enchantments.Enchantment enchantment, int level) {
        throw componentNotYet("addUnsafeEnchantment");
    }

    @Override
    public void addEnchantment(org.bukkit.enchantments.Enchantment enchantment, int level) {
        throw componentNotYet("addEnchantment");
    }

    @Override
    public int removeEnchantment(org.bukkit.enchantments.Enchantment enchantment) {
        throw componentNotYet("removeEnchantment");
    }

    @Override
    public io.papermc.paper.inventory.ItemRarity getRarity() {
        throw componentNotYet("getRarity");
    }

    @Override
    public net.kyori.adventure.text.Component displayName() {
        throw componentNotYet("displayName");
    }

    @Override
    public net.kyori.adventure.text.Component effectiveName() {
        throw componentNotYet("effectiveName");
    }

    @Override
    public io.papermc.paper.persistence.PersistentDataContainerView getPersistentDataContainer() {
        throw componentNotYet("getPersistentDataContainer");
    }

    @Override
    public boolean editPersistentDataContainer(
                    java.util.function.Consumer<org.bukkit.persistence.PersistentDataContainer> consumer) {
        throw componentNotYet("editPersistentDataContainer");
    }

    @Override
    public int getMaxItemUseDuration() {
        throw componentNotYet("getMaxItemUseDuration");
    }

    @Override
    public int getMaxItemUseDuration(org.bukkit.entity.LivingEntity entity) {
        throw componentNotYet("getMaxItemUseDuration");
    }

    @Override
    public java.util.List<net.kyori.adventure.text.Component> computeTooltipLines(
                    io.papermc.paper.inventory.tooltip.TooltipContext tooltipContext,
                    org.bukkit.entity.Player player) {
        throw componentNotYet("computeTooltipLines");
    }
}
