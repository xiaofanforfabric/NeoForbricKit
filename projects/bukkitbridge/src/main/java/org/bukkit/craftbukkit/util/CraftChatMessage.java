/*
 * Forbric Bukkit bridge — the class a plugin reaches for when it wants to turn a JSON chat
 * message into something the game will send.
 */
package org.bukkit.craftbukkit.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Chat-message conversion under the name CraftBukkit gives it.
 *
 * <p>This is a class a plugin looks up <b>by name</b>: Simple Voice Chat resolves
 * {@code <server package>.util.CraftChatMessage} and calls {@code fromJSON(String)} on it to turn
 * a JSON message — the format its own translation system produces — into a game component. The
 * lookup is the whole reason the class exists here rather than the bridge keeping this logic
 * private: a name that does not resolve is a compatibility layer that refuses to load.
 *
 * <p><b>The conversion is real, not a placeholder.</b> The game's own component codec does the
 * parsing, so anything the game accepts a message is accepted here, and a message the game cannot
 * read fails loudly rather than turning into an empty component that would look like a plugin bug.
 */
public final class CraftChatMessage {

	private CraftChatMessage() {
	}

	/**
	 * Reads a JSON chat message into a game component.
	 *
	 * <p>A plain string is accepted too — JSON and plain text are the same to the parser — and an
	 * input that is empty or all whitespace yields {@code null}, matching what the upstream method
	 * documents: there is no component to send.
	 *
	 * @throws com.google.gson.JsonParseException if the message is neither valid JSON nor a plain
	 *         string the game can read
	 */
	public static net.minecraft.network.chat.Component fromJSON(String jsonMessage) {
		if (jsonMessage == null) {
			return null;
		}
		JsonElement json = JsonParser.parseString(jsonMessage);
		if (json == null || (json.isJsonPrimitive() && json.getAsString().isBlank())) {
			return null;
		}
		return net.minecraft.network.chat.ComponentSerialization.CODEC
				.parse(com.mojang.serialization.JsonOps.INSTANCE, json)
				.getOrThrow(error -> new com.google.gson.JsonParseException(
						"[Forbric/Bukkit] a chat message could not be read by the game's component codec: "
								+ error));
	}

	/**
	 * {@link #fromJSON} that answers {@code null} instead of throwing on unreadable input.
	 *
	 * <p>Upstream offers both and plugins rely on the difference: this one is for a message that
	 * arrived from a client, where unreadable input is an expected condition rather than a defect.
	 */
	public static net.minecraft.network.chat.Component fromJSONOrNull(String jsonMessage) {
		if (jsonMessage == null) {
			return null;
		}
		try {
			return fromJSON(jsonMessage);
		} catch (RuntimeException unreadable) {
			return null;
		}
	}

	/** A component back to its JSON form, so a plugin can read what it is about to send. */
	public static String asJsonString(net.minecraft.network.chat.Component component) {
		return net.minecraft.util.GsonHelper.toStableString(
				net.minecraft.network.chat.ComponentSerialization.CODEC
						.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, component)
						.getOrThrow(error -> new IllegalArgumentException(
								"[Forbric/Bukkit] a component could not be written by its own codec: " + error)));
	}
}
