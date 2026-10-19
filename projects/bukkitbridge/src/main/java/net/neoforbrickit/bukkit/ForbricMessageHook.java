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
		// Held rather than delivered when it arrived in the middle of this player's join event. The client
		// cannot have answered something asked after that raise began, so a reply this early is to the
		// handshake that raise itself sent — and completing a plugin's handshake while the server still
		// considers the client to be loading is a state the game never produces on its own. Delivered on
		// the next queued pass instead, which is one tick away at most; nothing is dropped.
		//
		// The deep copy is not tidiness: the payload object belongs to the packet being handled, and a
		// payload whose packet has been released must not be read later.
		if (ForbricPlayerEventHook.isJoining(player)) {
			CustomPacketPayload copied = ForbricRawPayload.copyOf(packet.payload());
			if (copied != null) {
				ServerboundCustomPayloadPacket held = new ServerboundCustomPayloadPacket(copied);
				synchronized (DEFERRED) {
					DEFERRED.addLast(held);
					DEFERRED_PLAYER.addLast(player.getUUID());
				}
				net.neoforbrickit.bridge.util.ForbricLog.info(
						"[Forbric/Bukkit] client payload arrived during %s's join event, so it is delivered"
								+ " after it finishes rather than inside the join handler (thread=%s)",
						player.getScoreboardName(), Thread.currentThread().getName());
				scheduleDrain();
				return;
			}
			// No copy possible: delivering late is impossible too, and dropping a plugin message the plugin
			// is waiting for is worse than delivering it early. Falls through to the normal path.
		}
		deliver(player, packet);
	}

	/** Payloads held while a join event was running, oldest first, each with the player it came from. */
	private static final java.util.ArrayDeque<ServerboundCustomPayloadPacket> DEFERRED =
			new java.util.ArrayDeque<>();
	private static final java.util.ArrayDeque<java.util.UUID> DEFERRED_PLAYER = new java.util.ArrayDeque<>();

	/** Set while a drain is queued, so a burst of held payloads queues ONE drain rather than one each. */
	private static final java.util.concurrent.atomic.AtomicBoolean DRAIN_QUEUED =
			new java.util.concurrent.atomic.AtomicBoolean();

	/**
	 * Queues one drain on the game thread.
	 *
	 * <p>{@code MinecraftServer#execute} runs the task on the next pass of the server's own loop, which is
	 * exactly "after the handler that caused this". Queued through the bridge's server rather than a thread of
	 * our own: these are plugin messages, and a plugin listener that touches the world has to be on the game
	 * thread.
	 */
	private static void scheduleDrain() {
		if (!DRAIN_QUEUED.compareAndSet(false, true)) {
			return;
		}
		org.bukkit.Server server = org.bukkit.Bukkit.getServer();
		if (!(server instanceof ServerImpl bridgeServer) || bridgeServer.minecraft() == null) {
			// No game to queue on: deliver now rather than hold a payload nobody will come back for.
			DRAIN_QUEUED.set(false);
			drain();
			return;
		}
		try {
			bridgeServer.minecraft().execute(ForbricMessageHook::drain);
		} catch (Throwable failure) {
			DRAIN_QUEUED.set(false);
			net.neoforbrickit.bridge.util.ForbricLog.warn(
					"[NeoFobric/Bukkit] could not queue held plugin messages; they are delivered immediately");
			drain();
		}
	}

	/** Delivers everything held, oldest first, and clears the pending flag even if one of them throws. */
	private static void drain() {
		try {
			while (true) {
				ServerboundCustomPayloadPacket packet;
				java.util.UUID who;
				synchronized (DEFERRED) {
					packet = DEFERRED.pollFirst();
					who = DEFERRED_PLAYER.pollFirst();
				}
				if (packet == null) {
					return;
				}
				ServerPlayer player = playerOf(who);
				if (player == null) {
					// Left between the hold and the drain. The quit path has already told the plugin.
					net.neoforbrickit.bridge.util.ForbricLog.debug(
							"[NeoFobric/Bukkit] a held plugin message lost its player before delivery; dropped");
					continue;
				}
				deliver(player, packet);
			}
		} finally {
			DRAIN_QUEUED.set(false);
		}
	}

	/**
	 * Drops every held client message.
	 *
	 * <p>For the bridge's own shutdown: the queue is static, and the plugins that would have received what is
	 * in it have been disabled. Left behind, a restarted bridge would deliver a stranger's handshake to a
	 * freshly loaded plugin.
	 */
	public static void forgetHeld() {
		synchronized (DEFERRED) {
			DEFERRED.clear();
			DEFERRED_PLAYER.clear();
		}
	}

	/** The online player with this id, or null once they have left. */
	private static ServerPlayer playerOf(java.util.UUID id) {
		org.bukkit.Server server = org.bukkit.Bukkit.getServer();
		if (!(server instanceof ServerImpl bridgeServer) || bridgeServer.minecraft() == null) {
			return null;
		}
		for (ServerPlayer candidate : bridgeServer.minecraft().getPlayerList().getPlayers()) {
			if (candidate.getUUID().equals(id)) {
				return candidate;
			}
		}
		return null;
	}

	/** The delivery itself, unchanged apart from being callable from both the immediate and the held path. */
	private static void deliver(ServerPlayer player, ServerboundCustomPayloadPacket packet) {
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
