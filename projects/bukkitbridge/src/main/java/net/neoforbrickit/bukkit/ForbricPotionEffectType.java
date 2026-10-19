/*
 * Forbric Bukkit bridge — one mob effect, as {@code org.bukkit.potion.PotionEffectType}.
 */
package net.neoforbrickit.bukkit;

import net.minecraft.core.registries.BuiltInRegistries;

/**
 * One registered mob effect, as Bukkit's {@code org.bukkit.potion.PotionEffectType} sees it.
 *
 * <p><b>Why this class is in the game layer.</b> The wrapper body needs
 * {@code net.minecraft.core.registries.BuiltInRegistries} to answer anything, and the API layer
 * ({@code ForbricRegistryAccess}) cannot link that. So the same split the item registry uses applies
 * here: the API layer holds the {@code Registry}, and every answer comes from the game layer — which,
 * for this class, is the class's own static half.
 *
 * <p>The class is public with static entry points precisely so the API layer can call it by name
 * through {@code Class.forName(..., gameLayer)}, the way {@code ForbricItemLookup} is called.
 *
 * <h2>Why it has to answer at all</h2>
 *
 * <p>{@code PotionEffectType}'s static initialiser resolves every constant through
 * {@code Registry.EFFECT.getOrThrow(key)}. A registry that answers nothing raises out of that
 * initialiser, leaves the class permanently unusable, and every later user — BlueMap on player join,
 * for one — dies with {@code NoClassDefFoundError}. A null answer here is instead the ordinary "no such
 * effect", and a caller that asked for a constant gets it.
 *
 * <h2>Identity is the key</h2>
 *
 * <p>Two wrappers for one effect are equal and hash alike. {@code getId()} is left unanswerable rather
 * than faked: a numeric id is an internal the game no longer exposes, and a made-up one would collide
 * with no meaning.
 */
public final class ForbricPotionEffectType implements org.bukkit.potion.PotionEffectType {

    private final org.bukkit.NamespacedKey key;

    private ForbricPotionEffectType(org.bukkit.NamespacedKey key) {
        this.key = key;
    }

    /** Wraps an effect id. The one place a key becomes a {@code PotionEffectType}. */
    static ForbricPotionEffectType of(org.bukkit.NamespacedKey key) {
        return new ForbricPotionEffectType(key);
    }

    /**
     * One effect, by id, or null when the game has none.
     *
     * <p>Static and public so the API layer can reach it; the lookup is against the game's own registry,
     * so a mod's effect is a real answer and a datapack's removal is honoured.
     */
    public static org.bukkit.potion.PotionEffectType of(org.bukkit.NamespacedKey key) {
        if (key == null) {
            return null;
        }
        var id = net.minecraft.resources.Identifier.fromNamespaceAndPath(key.getNamespace(), key.getKey());
        var effect = BuiltInRegistries.MOB_EFFECT.get(id);
        return effect == null ? null : new ForbricPotionEffectType(key);
    }

    /** Every effect id the game has, in its own order. */
    public static java.util.List<org.bukkit.NamespacedKey> gameKeys() {
        java.util.List<org.bukkit.NamespacedKey> keys = new java.util.ArrayList<>();
        for (var holder : BuiltInRegistries.MOB_EFFECT.holders().toList()) {
            var id = holder.key().location();
            keys.add(new org.bukkit.NamespacedKey(id.getNamespace(), id.getPath()));
        }
        return keys;
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
     * Bukkit no longer exposes a numeric effect id, and the game has none to read.
     *
     * <p>{@code -1} rather than a fabricated number: plugins that switch on {@code getId()} are written
     * against ids that no longer exist in this API generation, and answering with an invented one would
     * make them take the wrong branch silently.
     */
    @Override
    public int getId() {
        return -1;
    }

    @Override
    public String getName() {
        return key.getKey();
    }

    /**
     * Appearance, which this bridge does not model.
     *
     * <p>Refused by name rather than answered with a plausible default: a plugin drawing a HUD icon from
     * this would render the wrong one and no exception would say why.
     */
    @Override
    public org.bukkit.potion.PotionEffectTypeCategory getCategory() {
        throw notYet("getCategory");
    }

    @Override
    public org.bukkit.Color getColor() {
        throw notYet("getColor");
    }

    private static UnsupportedOperationException notYet(String what) {
        return new UnsupportedOperationException(
                        "[Forbric/Bukkit] PotionEffectType." + what + "() is not implemented by this bridge yet");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ForbricPotionEffectType effect && effect.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "ForbricPotionEffectType[" + key + "]";
    }
}
