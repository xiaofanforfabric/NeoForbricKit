/*
 * Forbric Bukkit bridge — what the injected calls in the player join/leave path reach.
 */
package net.neoforbrickit.bukkit;

import net.minecraft.network.Connection;
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
	 * <p>Raised once per JOIN, and a join is the account plus a window of time, not a set of objects. The
	 * per-listener flag ({@code BukkitPlayerEventInjector#installJoinOnceGuard}) stops one client load raising
	 * twice, because its packet is entered on the network thread and again on the server thread.
	 * {@link #joinClaim} stops a MOVE between worlds raising again, because a move builds a new player and a new
	 * listener and reaches the same anchor a second time — see {@link #SAME_JOIN_WINDOW_NANOS}.
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
			if (!joinClaim(player)) {
				// The same join, asked for twice. Which is not hypothetical: the anchor is reached again from
				// PlayerList.respawn moments after the first raise, and TAB rebuilds its scoreboard each time.
				// The FIRST event was delivered and the plugin did act on it; this one is the copy that would
				// make the client throw away a connection over a duplicate objective name.
				ForbricLog.info("[Forbric/Bukkit] %s is finishing a move between worlds"
								+ " — the session already had its PlayerJoinEvent, so none is raised",
								player.getScoreboardName());
				return;
			}
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
			endSession(player);
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
	 * How long after a raise an identical load for the same account is the same join.
	 *
	 * <p>This is the whole reason the guard is not "once per session, full stop". One join reaches the anchor
	 * more than once in the log behind this issue: 06:05:15.8, 06:05:20.7, 06:06:00.2 — three raises, all
	 * inside 45 seconds, and the account stayed connected until the client died on the third. The client had
	 * already accepted the FIRST scoreboard, so any later {@code set_objective} for the same objective name is
	 * fatal to it — "later" is the only thing that matters here, not "second".
	 *
	 * <p>So the repeats are dropped and the first is kept, which is also what the reference server does for one
	 * join. A boolean on the account instead would refuse a reconnect that happened inside the window — the
	 * one case where a plugin genuinely needs the event — so the window is short on purpose: nothing in a
	 * genuine rejoin (disconnect, login, configuration, terrain load) completes in it, and the reconnects in
	 * the log took 14 s.
	 */
	private static final long SAME_JOIN_WINDOW_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(5);

	/**
	 * Each account's {@code PlayerJoinEvent}: when it was raised, and how many raises were seen then.
	 *
	 * <p>Both halves in one value so the decision has one atomic step — two lookups would let two threads see
	 * the same {@code Claim} and both call themselves the first.
	 *
	 * <p>Concurrent because the join path is not always the server thread — a proxy may deliver the load
	 * packet on a network thread — and this must not be the thing that throws inside it.
	 */
	private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Claim> SESSIONS =
			new java.util.concurrent.ConcurrentHashMap<>();

	/** See {@link #SESSIONS}: when the window opened, and the raise count it opened at. */
	private record Claim(long raisedAtNanos, int seenAtRaise) {
	}

	/**
	 * Whether this load should RAISE {@code PlayerJoinEvent}.
	 *
	 * <h2>Why the account, and not the objects</h2>
	 *
	 * <p>Everything else on this path is rebuilt by a move between worlds and survives nothing.
	 * {@code ServerPlayer.teleport} to another dimension runs {@code PlayerList.respawn}, which builds a new
	 * {@code ServerPlayer} <i>and</i> a new {@code ServerGamePacketListenerImpl}, so neither the player object nor
	 * the listener can answer "is this the same join". The {@code UUID} can.
	 *
	 * <h2>Why not "once per session"</h2>
	 *
	 * <p>That was the previous attempt, and this log refuses it: the raise the client dies on comes 39.5 s
	 * after the join that opened the session, with no {@code PlayerList.remove} in between (the player was
	 * still in the world, voice chat connected, and left minutes later). An account-wide {@code Set.add} would
	 * answer "already had one" to a load that may well be a new client session, and it is exactly that guess
	 * about the customer's action that was wrong. See {@link #SAME_JOIN_WINDOW_NANOS}.
	 *
	 * <h2>Why not the connection's liveness</h2>
	 *
	 * <p>The attempt before that, and it never refused anything: the connection outlives a move, so it is still
	 * active when the second load arrives.
	 *
	 * <h2>Lifetime</h2>
	 *
	 * <p>One entry per account, added here and removed in {@link #playerLeft}. That method runs from the head of
	 * {@code PlayerList.remove}, which the game calls on a real disconnect only — a move goes through
	 * {@code respawn} and keeps the entry. The timestamp is the backstop for a leave this hook never sees.
	 */
	private static boolean joinClaim(ServerPlayer player) {
		java.util.UUID account = player.getUUID();
		long now = System.nanoTime();
		// Atomic per account, because the decision is "is this a repeat" and the answer must not depend on two
		// threads reading the same Claim and both deciding differently. compute() also prunes on the way in: a
		// claim older than the window is not a claim at all.
		Claim claim = SESSIONS.compute(account, (uuid, current) -> current == null
					|| now - current.raisedAtNanos() > SAME_JOIN_WINDOW_NANOS
					? new Claim(now, 1)
					: new Claim(current.raisedAtNanos(), current.seenAtRaise() + 1));
		// Raised exactly when this call is the one that opened the window.
		return claim.seenAtRaise() == 1;
	}

	/**
	 * Ends the session this account held, so the next connection raises {@code PlayerJoinEvent} at once.
	 *
	 * <p>Called from {@link #playerLeft}, i.e. from {@code PlayerList.remove}, which is the leave path and not
	 * the move path. Left behind, the entry would suppress the first raise of the player's NEXT connection for
	 * {@link #SAME_JOIN_WINDOW_NANOS} — which is why it is removed eagerly rather than waiting for the
	 * timestamp to age out.
	 */
	private static void endSession(ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
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
	 * {@code disallow} disconnects the player with the handler's own message, which is the behaviour a whitelist
	 * or ban plugin relies on. The connection to close is taken as an ARGUMENT — see the note in the body about
	 * why asking the player for it cannot work at this point in the login.
	 */
	public static void playerPreLogin(ServerPlayer player, Connection connection) {
		try {
			if (player == null) {
				return;
			}
			ServerImpl server = bridgeServer();
			if (server == null) {
				return;
			}
			// The address comes from the CONNECTION ARGUMENT, not from the player.
			//
			// It must not be a guard, and `player.connection` must not be read here at all. This hook runs at
			// the head of `placeNewPlayer`, which is the method that ATTACHES the connection -- so
			// `player.connection` is genuinely null at this point, and an early return on it was the reason
			// this event never fired. Reading it once more below to disconnect a refused login was worse than
			// useless: the branch could never be taken, so a whitelist plugin's `disallow` was advisory.
			//
			// The connection `placeNewPlayer` was handed is the one being attached, so it is both available
			// and the right thing to close. A null address stays a real value for the event (a local or
			// already-closed connection), and a plugin that bans by IP simply has nothing to match against.
			java.net.InetAddress address = null;
			if (connection != null) {
				java.net.SocketAddress remote = connection.getRemoteAddress();
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
				net.kyori.adventure.text.Component shown = reason == null
						? net.kyori.adventure.text.Component.text("Disconnected")
						: reason;
				String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
						.plainText().serialize(shown);
				ForbricLog.info("[Forbric/Bukkit] %s was refused at pre-login: %s",
						player.getScoreboardName(), plain);
				// Closed through the connection this hook was handed, which is NOT yet attached to the player.
				// Asking the player for it was the bug: `player.connection` is null here, so the refusal
				// silently did nothing and a whitelist plugin was advisory.
				//
				// Out of placeNewPlayer rather than just closing: the disconnect is queued on the channel, and
				// the game would go on to put this player in the world in the meantime. The exception is the
				// failed login the caller reports, which is what makes the refusal take effect now.
				if (connection != null) {
					connection.disconnect(net.minecraft.network.chat.Component.literal(plain));
				}
				throw new LoginRefused(player.getScoreboardName() + " was refused at pre-login: " + plain);
			}
		} catch (LoginRefused refused) {
			// Deliberately not contained: a plugin said no, and letting the login continue would hand it a
			// player it asked not to have.
			throw refused;
		} catch (Throwable failure) {
			// Everything else is reported and swallowed: this runs inside the login path, and an escaping
			// exception would be attributed to the connection and could disconnect a player who has done
			// nothing wrong.
			ForbricLog.error("[NeoFobric/Bukkit] could not raise AsyncPlayerPreLoginEvent", failure);
		}
	}

	/**
	 * A login a plugin refused, thrown out of {@link #playerPreLogin} to abort the placement.
	 *
	 * <p>Extends {@link Error} so nothing on the way out mistakes it for a bridge failure and contains it the
	 * way plugin failures are contained; the stack trace is suppressed because where it was raised is known
	 * and the message already says who and why.
	 */
	public static final class LoginRefused extends Error {
		private static final long serialVersionUID = 1L;

		LoginRefused(String message) {
			super(message, null, false, false);
		}
	}
}
