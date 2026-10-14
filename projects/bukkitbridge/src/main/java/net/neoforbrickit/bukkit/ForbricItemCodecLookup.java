/*
 * Forbric Bukkit bridge — the item codec's game-layer half.
 */
package net.neoforbrickit.bukkit;

/**
 * The item codec's cross-layer entry point, defined in the layer that can see Minecraft.
 *
 * <h2>Why this class exists separately</h2>
 *
 * <p>{@link ForbricItemCodec} names {@code net.minecraft.world.item.ItemStack},
 * {@code RegistryFriendlyByteBuf} and {@code RegistryAccess} in its own signatures and bodies, so only
 * the game layer can ever define it. It has no service declaration of its own, but
 * {@link ForbricApiBridge} — which <em>is</em> a {@code ServiceLoader} provider for
 * {@code io.papermc.paper.InternalAPIBridge} — calls it, and that call is resolved by whichever layer
 * loads the caller. That is FML's "FML Plugins" layer, which cannot link {@code net.minecraft.*}.
 *
 * <p>The failure is delivered on the first {@code ItemStack.deserializeBytes} a plugin performs, as
 * {@code NoClassDefFoundError: net/minecraft/core/RegistryAccess} raised from inside a command — a
 * crash that reads as a broken plugin while the plugin did nothing wrong.
 *
 * <p>So the crossing is narrowed to this one class: its methods take and return only types both layers
 * can see ({@code byte[]}, {@code List<String>}), and everything needing Minecraft stays behind it. The
 * API-layer half reaches it by name through {@link #bridge}.
 */
public final class ForbricItemCodecLookup {

    private ForbricItemCodecLookup() {
    }

    /** One item, encoded in Paper's own byte format. */
    public static byte[] encode(org.bukkit.inventory.ItemStack stack) {
        return ForbricItemCodec.encode(stack);
    }

    /** One item, decoded from Paper's own byte format. */
    public static org.bukkit.inventory.ItemStack decode(byte[] data) {
        return ForbricItemCodec.decode(data);
    }

    /** A list of items, in the array format {@code serializeItemsAsBytes} writes. */
    public static byte[] encodeList(java.util.List<org.bukkit.inventory.ItemStack> stacks) {
        return ForbricItemCodec.encodeList(stacks);
    }

    /** A list of items, read back from the array format. */
    public static org.bukkit.inventory.ItemStack[] decodeList(byte[] data) {
        return ForbricItemCodec.decodeList(data);
    }

    /**
     * An empty stack, for {@code InternalAPIBridge.createEmptyStack()}.
     *
     * <p>Here rather than reached directly because {@link ForbricItemStack}'s constructor takes a
     * {@code net.minecraft.world.item.ItemStack}, which its descriptor names — and a descriptor is
     * resolved when the call site links, so the plugins layer cannot name that class at all.
     */
    public static org.bukkit.inventory.ItemStack emptyStack() {
        return ForbricItemStack.plain(org.bukkit.Material.AIR, 0);
    }
}
