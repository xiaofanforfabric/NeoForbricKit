/*
 * Forbric Bukkit bridge — what the injected calls in the player join/leave path reach.
 */
package net.neoforbrickit.bukkit;

import net.minecraft.server.level.ServerPlayer;
import net.neoforbrickit.bridge.util.ForbricLog;

/**
 * Raises {@code PlayerJoinEvent} and {@code PlayerQuitEvent} from the game's own join/leave path.
 *
 * <p><b>Why this exists.</b> A Bukkit plugin's most basic hook is "a player arrived" — welcome
 * messages, permissions, per-player state, and in this repository's own test case, a verification
 * code sent to chat. None of it runs unless something turns the game's join into an event, and
 * nothing in NeoForge knows that an {@code org.bukkit.event.player.PlayerJoinEvent} is supposed to
 * exist. Before this class the bridge dispatched exactly three event types (two interact paths and
 * the channel registration), so a plugin could be loaded, enabled, and have every listener it
 * registered never fire once.
 *
 * <p><b>Called from the game, not from the bridge.</b> The call sites are injected into
 * {@code ServerGamePacketListenerImpl.handleAcceptPlayerLoad} and {@code PlayerList.remove} by
 * {@code net.neoforbrickit.bridge.transform.BukkitPlayerEventInjector}, which is why these are
 * static entry points taking the player rather than methods on a bridge object: the injected
 * bytecode has to reach them with nothing but what the target method already has.
 *
 * <p><b>Nothing here may throw.</b> These run inside the game's own join and leave handling. An
 * exception on the way in would be attributed to the connection and could disconnect the player
 * before they ever spawn; an exception on the way out would leave the player list half-updated.
 * A plugin that throws in its listener is reported and skipped — see
 * {@code ForbricPluginManager#callEvent}, which already contains plugin failures.
 *
 * <h2>Why the join hook is {@code handleAcceptPlayerLoad} and not {@code placeNewPlayer}</h2>
 *
 * <p>{@code PlayerList.placeNewPlayer} runs while the player is still moving through the
 * configuration-to-play handshake: the client has not yet reported that it finished loading the
 * terrain. A message sent at that point can arrive before the client's chat window exists, which
 * is indistinguishable from "the plugin did nothing". {@code handleAcceptPlayerLoad} is the packet
 * that says the client is ready, and it is where the reference server raises the same event.
 */
public final class ForbricPlayerEventHook {

	private ForbricPlayerEventHook() {
	}

	/**
	 * A player finished joining: raise {@code PlayerJoinEvent}.
	 *
	 * <p>Reads the bridge's server from {@code Bukkit.getServer()} rather than being handed one,
	 * because the injected call has no way to name the bridge — the hook's caller is the game's
	 * network thread, which holds a {@code ServerPlayer} and nothing else about this bridge.
	 *
	 * <p>Idempotence is not enforced here: the injected call sits at the head of a method the client
	 * sends once. A repeated join packet would raise a second event, which is what a reference
	 * server does too.
	 */
	public static void playerJoined(ServerPlayer player) {
		try {
			if (player == null) {
				return;
			}
			ServerImpl server = bridgeServer();
			if (server == null) {
				return;
			}
			// The player is registered with the game by this point, so the wrapper is live: it reads
			// through to the game object for every accessor, and a plugin that stores it keeps working.
			org.bukkit.entity.Player bukkitPlayer = ForbricPlayer.of(player, server);
			// Unconditional while the seam is being proven: the only other evidence an event fired is
			// the plugin's own reaction, and for a plugin whose listener does nothing visible that is
			// not evidence at all. One line per join is nothing next to what the game already logs.
			ForbricLog.info("[Forbric/Bukkit] raising PlayerJoinEvent for %s",
							player.getScoreboardName());
			// The default message, which a plugin may replace: the player's own display name, which is
			// what the reference server shows and what a plugin reading joinMessage() expects to see.
			// Passed as a Component rather than a legacy string because the legacy constructor formats
			// through ChatColor, and this bridge hands plugins Adventure text everywhere else.
			server.getPluginManager().callEvent(
							new org.bukkit.event.player.PlayerJoinEvent(bukkitPlayer,
									net.kyori.adventure.text.Component.text(player.getScoreboardName())));
		} catch (Throwable failure) {
			// Reported and swallowed, never rethrown: this runs inside the client's own load packet,
			// where an escaping exception would be attributed to the connection and could disconnect
			// a player who has done nothing wrong. A plugin's own failure is contained inside
			// callEvent, so anything that reaches here is this side's fault and must not be fatal.
			ForbricLog.error("[NeoFobric/Bukkit] could not raise PlayerJoinEvent; the player stays connected",
							failure);
		}
	}

	/**
	 * A player left: raise {@code PlayerQuitEvent}.
	 *
	 * <p>Raised BEFORE {@code PlayerList.remove} finishes taking the player out of the world, which
	 * is what the API promises: a listener can still read the player's location, world and inventory
	 * while handling the event. The injection runs at the head of the method for that reason.
	 */
	public static void playerLeft(ServerPlayer player) {
		try {
			if (player == null) {
				return;
			}
			ServerImpl server = bridgeServer();
			if (server == null) {
				return;
			}
			org.bukkit.entity.Player bukkitPlayer = ForbricPlayer.of(player, server);
			ForbricLog.info("[Forbric/Bukkit] raising PlayerQuitEvent for %s",
							player.getScoreboardName());
			// The default quit message, same reasoning as the join side.
			server.getPluginManager().callEvent(
							new org.bukkit.event.player.PlayerQuitEvent(bukkitPlayer,
									net.kyori.adventure.text.Component.text(player.getScoreboardName())));
		} catch (Throwable failure) {
			// Same reasoning as the join hook: the player is already leaving, and an exception here
			// would turn a normal disconnect into a failed one.
			ForbricLog.error("[NeoFobric/Bukkit] could not raise PlayerQuitEvent; the player still disconnects",
							failure);
		}
	}

	/**
	 * The bridge's server, or null when the bridge has not registered itself.
	 *
	 * <p>Asked of {@code Bukkit.getServer()} and cast to {@link ServerImpl} rather than reached
	 * through a static field of our own: the singleton is the one object both this class and a
	 * plugin agree on, and a null answer means the bridge is not running — in which case there is
	 * no plugin to tell and returning is correct, not an error.
	 */
	private static ServerImpl bridgeServer() {
		org.bukkit.Server server = org.bukkit.Bukkit.getServer();
		return server instanceof ServerImpl bridge ? bridge : null;
	}
}
