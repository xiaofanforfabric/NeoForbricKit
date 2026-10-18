/*
 * Forbric Bukkit bridge — a plugin-channel payload carried as raw bytes.
 */
package net.neoforbrickit.bukkit;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A custom payload that is nothing but a channel name and the bytes a plugin handed over.
 *
 * <p>Bukkit's {@code Player.sendPluginMessage} promises exactly that: the channel becomes the
 * payload type, and the plugin's byte array becomes the body, untouched. Vanilla has no such
 * payload — its {@code DiscardedPayload} keeps the <em>name</em> of an unknown channel and drops
 * the body, because it exists to answer a client's packet the server does not understand, never
 * to carry one outward.
 *
 * <p>So this class is the payload Paper ships as a patch to {@code DiscardedPayload}: the channel
 * plus the bytes. Encoding a payload type the protocol does not know is refused by vanilla's
 * clientbound codec, which is why {@link ForbricPlayer#sendPluginMessage} cannot work until the
 * codec is taught about this type — see the note on that method.
 */
public final class ForbricRawPayload implements CustomPacketPayload {

	private final Type<ForbricRawPayload> type;
	private final byte[] data;

	public ForbricRawPayload(Identifier channel, byte[] data) {
		this.type = new Type<>(channel);
		this.data = data.clone();
	}

	@Override
	public Type<ForbricRawPayload> type() {
		return type;
	}

	public byte[] data() {
		return data.clone();
	}

	/**
	 * The body of a payload that arrived from a modded client, or null when there is none to read.
	 *
	 * <p>A client sends its messages as a payload type this server has no codec for, and it arrives
	 * as {@code DiscardedPayload}. Vanilla's version of that type throws the body away, which is
	 * why the type carries a body here: the patch that added it is what lets a server-side plugin
	 * hear a modded client at all. Reading it is therefore a direct call, not a reflective probe —
	 * the field is part of the build, and a build without it would not compile rather than quietly
	 * deliver empty messages.
	 */
	public static byte[] dataOf(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
		if (payload instanceof ForbricRawPayload raw) {
			return raw.data;
		}
		if (payload instanceof net.minecraft.network.protocol.common.custom.DiscardedPayload discarded) {
			byte[] body = discarded.data();
			return body.length == 0 ? null : body;
		}
		return null;
	}

	/**
	 * A payload's body, copied out now so it can be read after the packet it came from is gone.
	 *
	 * <p>Used by the one path that holds a client message back instead of delivering it immediately (see
	 * {@code ForbricMessageHook}): the payload object belongs to the packet being handled, and reading it on a
	 * later pass would be reading something the game may already have released. The copy is of the body only —
	 * the channel is an immutable {@link Identifier}, so it can be shared.
	 *
	 * @return a payload carrying the same channel and body, or null when the body cannot be read
	 */
	public static CustomPacketPayload copyOf(CustomPacketPayload payload) {
		byte[] body = dataOf(payload);
		if (body == null) {
			return null;
		}
		return new ForbricRawPayload(payload.type().id(), body);
	}

	/**
	 * The codec for one channel's payloads.
	 *
	 * <p>One codec per channel because the channel <em>is</em> the type: the protocol writes the
	 * identifier first, then whatever the codec produces, and the plugin's bytes are the latter.
	 */
	public static StreamCodec<ByteBuf, ForbricRawPayload> codec(Identifier channel) {
		return CustomPacketPayload.codec(
				(payload, buffer) -> buffer.writeBytes(payload.data),
				buffer -> {
					byte[] body = new byte[buffer.readableBytes()];
					buffer.readBytes(body);
					return new ForbricRawPayload(channel, body);
				});
	}
}
