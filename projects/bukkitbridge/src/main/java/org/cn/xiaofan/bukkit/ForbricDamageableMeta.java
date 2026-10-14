/*
 * Forbric Bukkit bridge — an item meta a plugin can set a damage value on.
 * *** GENERATED FILE — DO NOT EDIT ***
 *
 * Produced by tools/gen_stub.py from tools/stubs/ForbricDamageableMeta.table. Edits belong in the table or
 * this template.
 */
package org.cn.xiaofan.bukkit;

/**
 * An {@code ItemMeta} that carries the item's damage value, which is how a plugin sets durability.
 *
 * <h2>Why this is a subclass rather than a flag on the parent</h2>
 *
 * <p>Bukkit's own shape: {@code Damageable extends ItemMeta}, and a plugin reaches the damage value by
 * checking for it:
 *
 * <pre>{@code
 * ItemMeta meta = item.getItemMeta();
 * if (meta instanceof Damageable damageable) {
 *     damageable.setDamage(64);
 * }
 * }</pre>
 *
 * <p>That guard is not a formality — it is how a plugin asks "is this item a tool at all", because a
 * {@code Damageable} is a thing that can wear out. {@code ForbricItemStack.getItemMeta()} therefore hands
 * back this class for an item whose game components include a {@code max_damage} or {@code damage}, and
 * the plain {@link ForbricItemMeta} for one that cannot be damaged. An item of stone answering
 * {@code instanceof Damageable} would make a plugin write a damage value the game then ignores.
 *
 * <h2>Where the number lives</h2>
 *
 * <p>In the game's {@code minecraft:damage} component. The value here is a snapshot taken when the meta
 * was read, and {@code ForbricItemStack.setItemMeta} writes it back — the same snapshot rule the parent
 * documents, and the reason a plugin must call {@code setItemMeta} for the durability to change.
 */
class ForbricDamageableMeta extends ForbricItemMeta implements org.bukkit.inventory.meta.Damageable {

    /** The damage value as read, and the one written back. Zero means undamaged. */
    private int damage;

    /** Whether the item had a damage component at all, which is what {@code hasDamageValue} reports. */
    private final boolean hadDamage;

    /** The item's maximum damage, or null when the game has no {@code max_damage} for it. */
    private Integer maxDamage;

    ForbricDamageableMeta(net.kyori.adventure.text.Component displayName,
            java.util.List<net.kyori.adventure.text.Component> lore,
            ForbricPersistentDataContainer persistentData, int version, String itemName,
            int damage, boolean hadDamage, Integer maxDamage) {
        super(displayName, lore, persistentData, version, itemName);
        this.damage = damage;
        this.hadDamage = hadDamage;
        this.maxDamage = maxDamage;
    }

    private static UnsupportedOperationException notYet(String what) {
        return new UnsupportedOperationException(
                "[Forbric/Bukkit] Damageable." + what + "() is not implemented by this bridge yet");
    }

    /** Whether the item carries a damage value at all, which is not the same as a non-zero one. */
    @Override
    public boolean hasDamage() {
        return hadDamage;
    }

    /**
     * How damaged the item is.
     *
     * <p>Bukkit measures damage, not durability: a pickaxe with a maximum damage of 100 and a damage of 30
     * has 70 uses left. That is the opposite of the number shown in the durability bar, and the reason a
     * plugin sets {@code maxDamage - 1} to mean "one use left".
     */
    @Override
    public int getDamage() {
        return damage;
    }

    /**
     * Sets how damaged the item is.
     *
     * <p>The value is kept as given, including one outside {@code 0..maxDamage}: the game clamps its own
     * application of it, and this bridge clamping here would silently change what a plugin asked for.
     */
    @Override
    public void setDamage(int damage) {
        this.damage = damage;
    }

    /** The same question as {@link #hasDamage()}, under the name the interface also declares. */
    @Override
    public boolean hasDamageValue() {
        return hadDamage;
    }

    /**
     * Clears the damage value.
     *
     * <p>Sets the stored number to zero and marks the value as present, because that is what "reset" means
     * on an item that has the component: the item stays repairable, it is just no longer damaged.
     */
    @Override
    public void resetDamage() {
        this.damage = 0;
    }

    /** Whether the game records a maximum damage for this item. */
    @Override
    public boolean hasMaxDamage() {
        return maxDamage != null;
    }

    /**
     * The item's maximum damage, or zero when it has none.
     *
     * <p>Zero rather than an exception: this accessor's companion {@code hasMaxDamage} exists precisely so
     * a caller can ask first, and a plugin that calls this without asking gets the same zero a
     * non-damageable item would report anywhere else.
     */
    @Override
    public int getMaxDamage() {
        return maxDamage == null ? 0 : maxDamage;
    }

    /** Overrides the maximum, or clears it when given null. */
    @Override
    public void setMaxDamage(Integer maxDamage) {
        this.maxDamage = maxDamage;
    }

    /**
     * A copy, with the damage state carried over.
     *
     * <p>Overridden rather than inherited because the parent's clone builds a plain {@code ForbricItemMeta}
     * — it cannot know about this subclass — and a plugin that clones a tool's meta and then sets its
     * damage would find the result was not {@code Damageable} at all.
     */
    @Override
    public ForbricDamageableMeta clone() {
        return new ForbricDamageableMeta(nameSnapshot(), loreSnapshot(),
                new ForbricPersistentDataContainer(container().tag().copy()), getVersion(), getItemName(),
                damage, hadDamage, maxDamage);
    }

}
