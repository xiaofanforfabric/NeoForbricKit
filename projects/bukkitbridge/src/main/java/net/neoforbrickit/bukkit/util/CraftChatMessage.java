/*
 * Forbric Bukkit bridge — the CraftBukkit-shaped name for chat-message conversion, in the package
 * a plugin derives from the server's own class.
 */
package net.neoforbrickit.bukkit.util;

/**
 * The same conversion as {@link org.bukkit.craftbukkit.util.CraftChatMessage}, under the spelling
 * a derived package lookup produces.
 *
 * <p>Simple Voice Chat computes {@code Bukkit.getServer().getClass().getPackage().getName() + "."}
 * and then asks for {@code util.CraftChatMessage} — which lands in
 * {@code net.neoforbrickit.bukkit.entity}. Plugins that hardcode the upstream path land on the
 * other one. Both spellings have to work, and neither may disagree about the result, so this class
 * holds no logic of its own: it forwards, and the conversion lives in exactly one place.
 *
 * <p>Not an abstract base as the entity classes are, because nothing in the bridge descends from
 * it — it is a name to resolve, so the methods are simply present.
 */
public final class CraftChatMessage {

	private CraftChatMessage() {
	}

	/** See {@link org.bukkit.craftbukkit.util.CraftChatMessage#fromJSON}. */
	public static net.minecraft.network.chat.Component fromJSON(String jsonMessage) {
		return org.bukkit.craftbukkit.util.CraftChatMessage.fromJSON(jsonMessage);
	}

	/** See {@link org.bukkit.craftbukkit.util.CraftChatMessage#fromJSONOrNull}. */
	public static net.minecraft.network.chat.Component fromJSONOrNull(String jsonMessage) {
		return org.bukkit.craftbukkit.util.CraftChatMessage.fromJSONOrNull(jsonMessage);
	}

	/** See {@link org.bukkit.craftbukkit.util.CraftChatMessage#asJsonString}. */
	public static String asJsonString(net.minecraft.network.chat.Component component) {
		return org.bukkit.craftbukkit.util.CraftChatMessage.asJsonString(component);
	}
}
