/*
 * Forbric Bukkit bridge — the item registry's game-layer half.
 */
package net.neoforbrickit.bukkit;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * The item registry's questions, answered in the layer that can see Minecraft.
 *
 * <h2>Why this class exists separately</h2>
 *
 * <p>Paper instantiates the {@code RegistryAccess} provider ({@link ForbricRegistryAccess}) through
 * {@code ServiceLoader}, using the interface's own class loader — and that is FML's "FML Plugins" layer,
 * which cannot link {@code net.minecraft.*}. {@link ForbricItemType} names
 * {@code BuiltInRegistries} in its own signatures, so it can only ever be defined by the game layer. A
 * provider running in the plugins layer therefore cannot build one: the attempt fails with
 * {@code NoClassDefFoundError}, and every lookup comes back null.
 *
 * <p>That failure mode is nasty enough to be worth this file: the registry answering null for every
 * id looks exactly like "no such item", so {@code Material.asItemType()} returned null,
 * {@code ItemStack.of} threw {@code IllegalArgumentException: PAPER isn't an item}, and the stack trace
 * pointed at the plugin — while the plugin had done nothing wrong.
 *
 * <p>So the provider asks for the answer instead of computing it: this class is public and its methods
 * are static, which makes it callable by name from the other layer. Everything that needs
 * {@code BuiltInRegistries} stays here.
 */
public final class ForbricItemLookup {

    private ForbricItemLookup() {
    }

    /** Every item id the game has, in the game's own order. */
    public static List<org.bukkit.NamespacedKey> keys() {
        return BuiltInRegistries.ITEM.keySet().stream()
                .map(id -> new org.bukkit.NamespacedKey(id.getNamespace(), id.getPath()))
                .toList();
    }

    /**
     * The {@code ItemType} for an id, or null when the game has no such item.
     *
     * <p>"Not present" is answered from the game's registry rather than from a table here, so an item a
     * mod adds is as real as a vanilla one.
     */
    public static org.bukkit.inventory.ItemType of(String namespace, String path) {
        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        return ForbricItemType.of(id);
    }

    /** The number of items the game has, for {@code Registry.size()}. */
    public static int size() {
        return BuiltInRegistries.ITEM.size();
    }
}
