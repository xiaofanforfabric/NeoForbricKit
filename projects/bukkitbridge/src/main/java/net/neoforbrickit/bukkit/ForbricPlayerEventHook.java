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
	 * <p>Raised once per client load, which is what makes it once per session: the injected call site is
	 * guarded by a flag the processor puts on the listener itself, so the extra
	 * {@code ServerboundPlayerLoadedPacket} a dimension change produces — a fresh listener, and a client that
	 * legitimately finished loading terrain again — does not raise a second {@code PlayerJoinEvent}. See
	 * {@code BukkitPlayerEventInjector#installJoinOnceGuard} for why the flag belongs to the listener.
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
			// Boss bars after the event, not before: a plugin handling the quit may still hide one, and that
			// call would find nothing to hide if the view had already been emptied. This runs afterwards so
			// that whatever the plugin left behind is what gets cleaned up.
			//
			// The reason it has to happen at all is that an adventure bar keeps a reference to every listener
			// registered on it. Dropping only the bridge's map would leave one dangling listener per bar per
			// departed player, held by the plugin's own bar object, for the life of the server.
			ForbricPlayer.forgetBossBarView(player);
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

	/**
	 * The pool the pre-login event is raised on.
	 *
	 * <p>Daemon threads, because a server shutting down must not wait for a login that will never finish.
	 * Bounded to one thread per core and named, so a thread dump during a slow login says who is stuck rather
	 * than showing an anonymous pool.
	 */
	private static final java.util.concurrent.ExecutorService PRE_LOGIN_POOL =
			java.util.concurrent.Executors.newCachedThreadPool(runnable -> {
				Thread thread = new Thread(runnable, "forbric-pre-login");
				thread.setDaemon(true);
				return thread;
			});

	/**
	 * Raises an asynchronous event from wherever the caller happens to be, and waits for it.
	 *
	 * <h2>Why not just {@code callEvent}</h2>
	 *
	 * <p>{@code AsyncPlayerPreLoginEvent} is declared asynchronous, and both Paper and this bridge refuse to
	 * deliver an async event from the primary thread — {@code IllegalStateException: ... may not be triggered
	 * from the primary thread}. That check is right: its whole purpose is that listeners may block on a
	 * database read. The place the event has to be raised from, {@code PlayerList.placeNewPlayer}, IS the
	 * primary thread, so the event has to move rather than the check being relaxed.
	 *
	 * <p>Waiting is deliberate. A permission plugin loads the player's data in that handler and every later
	 * check reads what it stored; letting the login continue before the handler finished would leave the
	 * session answering from defaults — the exact failure this event exists to prevent.
	 *
	 * <p>A handler that throws is reported and treated as "allowed": a broken listener must not become a login
	 * that hangs forever or a player who cannot connect.
	 */
	private static void raiseOffThread(org.bukkit.event.Event event, String who) {
		ServerImpl server = bridgeServer();
		if (server == null) {
			return;
		}
		if (!server.isPrimaryThread()) {
			// Already off the game thread (a plugin calling this itself); the check above would pass anyway.
			server.getPluginManager().callEvent(event);
			return;
		}
		try {
			PRE_LOGIN_POOL.submit(() -> server.getPluginManager().callEvent(event)).get();
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			ForbricLog.warn("[Forbric/Bukkit] interrupted while waiting for the pre-login handlers of %s", who);
		} catch (java.util.concurrent.ExecutionException failed) {
			// The plugin's own listener threw. Reported, not rethrown: this runs inside the login path.
			ForbricLog.error("[Forbric/Bukkit] a pre-login handler for " + who + " threw", failed.getCause());
		}
	}

	/**
	 * A player is about to be placed into the world: raise {@code AsyncPlayerPreLoginEvent}.
	 *
	 * <h2>Why this event, and why it is not optional</h2>
	 *
	 * <p>It is where a permission plugin loads a player's data. LuckPerms registers
	 * {@code onPlayerPreLogin} for it and does nothing at join time but read what that handler stored — so
	 * without this event its user manager never learns the player exists, {@code /lp user <name>} answers
	 * "user not found" for somebody standing in the world, and every permission check falls back to the
	 * declared defaults. That is exactly the state this bridge was in: the plugin enabled, the commands ran,
	 * and no player was ever known to it.
	 *
	 * <p>Raised here rather than at the network handshake because this is the earliest point the bridge has
	 * a {@code ServerPlayer} to describe: an {@code AsyncPlayerPreLoginEvent} needs a name, an address and a
	 * UUID, and all three are on the player the game has already authenticated. The event is documented as
	 * asynchronous, and it is — a handler may block on a database read — but raising it from the game thread
	 * is what a single-threaded bridge can honestly do; a handler that needs to block still can, and the cost
	 * is one login's worth of latency rather than a wrong answer for the whole session.
	 *
	 * <p>Because it is the event plugins DISALLOW logins with, the result is honoured: a handler that calls
	 * {@code disallow} disconnects the player, which is the behaviour a whitelist or ban plugin relies on.
	 */
	public static void playerPreLogin(ServerPlayer player) {
		try {
			if (player == null) {
				return;
			}
			ServerImpl server = bridgeServer();
			if (server == null) {
				return;
			}
			// The address comes from the player's own connection WHEN THERE IS ONE, and is null otherwise.
			//
			// It must not be a guard. This hook runs at the head of `placeNewPlayer`, which is the method that
			// ATTACHES the connection -- so `player.connection` is genuinely null here, and an early return on
			// that was the reason this event never fired. A null address is a real value for the event (a
			// local or already-closed connection), and a plugin that bans by IP simply has nothing to match
			// against.
			java.net.InetAddress address = null;
			if (player.connection != null) {
				java.net.SocketAddress remote = player.connection.getRemoteAddress();
				address = remote instanceof java.net.InetSocketAddress inet ? inet.getAddress() : null;
			}
			org.bukkit.event.player.AsyncPlayerPreLoginEvent event =
					new org.bukkit.event.player.AsyncPlayerPreLoginEvent(
							player.getScoreboardName(), address, player.getUUID());
			raiseOffThread(event, player.getScoreboardName());
			if (event.getLoginResult() != org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED) {
				// The plugin refused this login. Disconnect with ITS message, which is what a reference server
				// shows -- a generic "disconnected" would leave the player with no idea why.
				net.kyori.adventure.text.Component reason = event.kickMessage();
				ForbricLog.info("[Forbric/Bukkit] %s was refused at pre-login: %s",
						player.getScoreboardName(), reason);
				net.kyori.adventure.text.Component shown = reason == null
						? net.kyori.adventure.text.Component.text("Disconnected")
						: reason;
				// The connection is not attached yet, so there is nothing to disconnect through. Refusing at
				// this point still has to take effect, or a whitelist plugin would be advisory: the player is
				// marked by throwing out of placeNewPlayer, which the game reports as a failed login.
				if (player.connection != null) {
					player.connection.getConnection().disconnect(
							net.minecraft.network.chat.Component.literal(
									net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
											.plainText().serialize(shown)));
				}
			}
		} catch (Throwable failure) {
			// Never rethrown: this runs inside the login path, and an escaping exception would be attributed to
			// the connection and could disconnect a player who has done nothing wrong.
			ForbricLog.error("[NeoFobric/Bukkit] could not raise AsyncPlayerPreLoginEvent", failure);
		}
	}
}
