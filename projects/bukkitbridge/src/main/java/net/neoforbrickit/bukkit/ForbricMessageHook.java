/*
 * Forbric Bukkit bridge — what the injected call in handleCustomPayload reaches.
 */
package net.neoforbrickit.bukkit;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Turns an arriving client payload into a Bukkit plugin message.
 *
 * <p><b>Called from the game, not from the bridge.</b> The call site is injected into
 * {@code ServerGamePacketListenerImpl.handleCustomPayload} by
 * {@code net.neoforbrickit.bridge.transform.BukkitMessageInjector}, which is why this is a
 * static entry point taking the sender and the packet rather than a method on a bridge object:
 * the injected bytecode has to be able to reach it with nothing but the listener's own field.
 *
 * <p>Nothing here may throw. It runs inside the game's packet handling, where an exception would
 * be attributed to the connection — and the honest answer to a malformed plugin message is to
 * drop it, not to disconnect the player.
 */
public final class ForbricMessageHook {

	private ForbricMessageHook() {
	}

	/**
	 * Delivers one arriving payload to the plugins listening on its channel.
	 *
	 * <p>The listener's player field is what identifies the sender, and it is null before the
	 * player has joined — a payload cannot legitimately arrive then, so the packet is dropped
	 * rather than delivered to nobody.
	 */
	public static void dispatchFromGame(ServerPlayer player, ServerboundCustomPayloadPacket packet) {
		try {
			if (player == null || packet == null) {
				return;
			}
			CustomPacketPayload payload = packet.payload();
			String channel = payload.type().id().toString();
			byte[] data = ForbricRawPayload.dataOf(payload);
			// Unconditional while the seam is being proven: the only other evidence a plugin
			// message arrived is the plugin's own reaction, and "the plugin did nothing" is not
			// evidence about which side of the seam broke.
			net.neoforbrickit.bridge.util.ForbricLog.info(
								"[Forbric/Bukkit] client payload channel=%s type=%s body=%s bytes",
					channel, payload.getClass().getSimpleName(), data == null ? "none" : data.length);
			if (data == null) {
				// A payload whose body vanilla discarded: it named a channel this server does not
				// know, which is exactly what happens for a mod channel before anything registers
				// it. There is no message to deliver, so say so rather than inventing an empty one.
				net.neoforbrickit.bridge.util.ForbricLog.info(
										"[Forbric/Bukkit] client payload on %s carried no body, so nothing is dispatched", channel);
				return;
			}

			org.bukkit.Server server = org.bukkit.Bukkit.getServer();
			if (!(server instanceof ServerImpl bridgeServer)) {
				return;
			}
			org.bukkit.entity.Player bukkitPlayer = ForbricPlayer.of(player, bridgeServer);
			boolean delivered = bridgeServer.messenger().deliverIncomingMessage(bukkitPlayer, channel, data);
			net.neoforbrickit.bridge.util.ForbricLog.info(
								"[Forbric/Bukkit] channel %s had %s registered listener(s)", channel, delivered ? "at least one" : "no");
		} catch (Throwable failure) {
			// Never silent: this is the only place a client-to-server plugin message is turned
			// into a plugin callback, so a failure here is the difference between "the plugin does
			// not work" and "the plugin was never told", and the operator has to be able to tell.
			net.neoforbrickit.bridge.util.ForbricLog.error(
					"[NeoFobric/Bukkit] a client plugin message could not be dispatched; it is dropped", failure);
		}
	}
}
