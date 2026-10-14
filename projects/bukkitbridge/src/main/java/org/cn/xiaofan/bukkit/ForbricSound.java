/*
 * Forbric Bukkit bridge — one sound, as {@code org.bukkit.Sound}.
 */
package org.cn.xiaofan.bukkit;

/**
 * One registered sound event, as Bukkit's {@code org.bukkit.Sound} sees it.
 *
 * <p><b>Why this has to exist at all.</b> {@code org.bukkit.Sound} is an interface whose every constant
 * is resolved in its static initialiser: {@code Sound.AMBIENT_CAVE}, {@code Sound.BLOCK_STONE_BREAK} and
 * about two thousand more each call {@code Registry.SOUNDS.getOrThrow(key)} and store the result in a
 * static field. {@code getOrThrow} raises {@code NoSuchElementException} when the lookup finds nothing,
 * so a registry that cannot answer leaves {@code Sound} permanently unusable —
 * {@code ExceptionInInitializerError} on first touch, and then every {@code playSound} in every plugin
 * fails with a stack trace naming the plugin.
 *
 * <p>An earlier version of this bridge answered unknown registries with an empty one, which was enough
 * for {@code Art} and {@code Attribute} — nothing resolves those eagerly. It is NOT enough here, which
 * is why sounds are answered from the game's own registry rather than left empty.
 *
 * <h2>Identity is the key</h2>
 *
 * <p>Two wrappers for one sound are equal and hash alike, and the value is not cached anywhere: the game
 * owns the sound registry and this is a handle onto an id. That also means a sound a mod adds is a real
 * entry here, because the id came from the game's registry and not from a table in this bridge.
 *
 * <p>{@code name()} reports Bukkit's constant-name spelling (upper case with underscores) even though
 * this wrapper is not one of those constants — a plugin that logs {@code sound.name()} gets something
 * it recognises, and {@code ordinal()} is left unanswerable rather than faked, because a fabricated
 * ordinal would sort incompatibly with the real enum constants.
 */
final class ForbricSound implements org.bukkit.Sound {

    private final org.bukkit.NamespacedKey key;

    private ForbricSound(org.bukkit.NamespacedKey key) {
        this.key = key;
    }

    /** Wraps a sound id. The one place a key becomes an {@code org.bukkit.Sound}. */
    static ForbricSound of(org.bukkit.NamespacedKey key) {
        return new ForbricSound(key);
    }

    /** The game-side id, for the bridge's own use by the sound bridge and the event layer. */
    org.bukkit.NamespacedKey namespacedKey() {
        return key;
    }

    @Override
    public org.bukkit.NamespacedKey getKey() {
        return key;
    }

    /** Adventure's accessor, which is a different type for the same value. */
    @Override
    public net.kyori.adventure.key.Key key() {
        return net.kyori.adventure.key.Key.key(key.getNamespace(), key.getKey());
    }

    /**
     * Bukkit's constant-name spelling: {@code minecraft:block.stone.break} becomes
     * {@code BLOCK_STONE_BREAK}.
     *
     * <p>Derived rather than stored because this wrapper is built from an id, and there is no constant
     * list to look a name up in — the enum's own values are the ones being constructed. A plugin that
     * formats a message with {@code name()} gets the same shape it would from a real constant.
     */
    @Override
    public String name() {
        return key.getKey().toUpperCase(java.util.Locale.ROOT).replace('.', '_').replace('/', '_');
    }

    /**
     * Refused rather than invented.
     *
     * <p>An ordinal is only meaningful compared against other ordinals from the same enum, and the real
     * {@code Sound} constants get theirs from declaration order in the API. A number made up here would
     * sort a mod's sound against vanilla ones by nothing at all, so the honest answer is that this
     * wrapper has none. Code that compares sounds should use {@link #getKey()}.
     */
    @Override
    public int ordinal() {
        throw new UnsupportedOperationException(
                        "[Forbric/Bukkit] a sound obtained from the registry has no ordinal; compare by getKey()"
                                        + " instead");
    }

    /** Ordered by id, which is the only order two registry sounds have in common. */
    @Override
    public int compareTo(org.bukkit.Sound other) {
        return key.toString().compareTo(String.valueOf(other == null ? null : other.getKey()));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof org.bukkit.Keyed keyed && key.equals(keyed.getKey());
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "ForbricSound[" + key + "]";
    }
}
