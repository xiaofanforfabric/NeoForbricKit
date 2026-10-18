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
	 * <p>Raised once per SESSION, decided by {@link #joinClaim} on the account's UUID: the load packet is
	 * entered twice on two threads, {@code PlayerList.respawn} rebuilds the player and the listener during a
	 * move between worlds, and this issue's logs show a further raise arriving seconds later on a session
	 * that never left — all three are the same join, and only the UUID survives all three.
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
				// The same session, asked for another join. Not hypothetical: the anchor is reached again from
				// PlayerList.respawn on a move between worlds, and TAB rebuilds its scoreboard each time. The
				// FIRST event was delivered and the plugin acted on it; a second one makes the client throw the
				// connection away over a duplicate objective name. The thread is logged because the raise is not
				// always on the server thread, and which one it was is the first question a repeat asks.
				traceRepeatRaise(player);
				ForbricLog.info("[Forbric/Bukkit] %s already has a PlayerJoinEvent this session — no second one is"
								+ " raised (thread=%s)", player.getScoreboardName(),
								Thread.currentThread().getName());
				return;
			}
			// Traced on the raise that actually goes OUT, the first of its session included. The report behind
			// this issue carried the switch on for two runs and produced no trace at all, because the one
			// branch that could reach the trace was the branch the guard had already closed: the second raise
			// was suppressed here, so nothing was ever printed, and the log read as "the probe is broken".
			// Every raise that reaches a client is a candidate for the one that kills it, so the trace is
			// taken here — before the call, from the thread that really is raising — and capped per account
			// rather than per session.
			traceRaise(player);
			// Unconditional otherwise, while the seam is being proven: the only other evidence an event fired is
			// the plugin's own reaction, and for a plugin whose listener does nothing visible that is
			// not evidence at all. One line per join is nothing next to what the game already logs.
			ForbricLog.info("[Forbric/Bukkit] raising PlayerJoinEvent for %s",
							player.getScoreboardName());
			// The default message, which a plugin may replace: the player's own display name, which is
			// what the reference server shows and what a plugin reading joinMessage() expects to see.
			// Passed as a Component rather than a legacy string because the legacy constructor formats
			// through ChatColor, and this bridge hands plugins Adventure text everywhere else.
			//
			// The window published around the call is what lets a reply the client sends DURING this handler
			// wait for it to finish, instead of completing the plugin's handshake half way through a join
			// the game has not acknowledged yet. Removed in a finally: a listener that throws must not leave
			// the account looking permanently half-joined.
			JOIN_IN_FLIGHT.add(player.getUUID());
			try {
				server.getPluginManager().callEvent(
								new org.bukkit.event.player.PlayerJoinEvent(bukkitPlayer,
										net.kyori.adventure.text.Component.text(player.getScoreboardName())));
			} finally {
				JOIN_IN_FLIGHT.remove(player.getUUID());
			}
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
	 * The players whose join event is being delivered right now, so the packet in flight is not counted.
	 *
	 * <p>See {@link #isJoining} and the channel seam (`ForbricMessageHook`): the client cannot have
	 * answered a question that was asked after this raise began, so a reply that arrives while the raise is
	 * still running is not a reply to it.
	 */
	private static final java.util.Set<java.util.UUID> JOIN_IN_FLIGHT =
			java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * Whether {@code player}'s {@code PlayerJoinEvent} is on the stack of some thread at this moment.
	 *
	 * <h2>Why the bridge has to know this at all</h2>
	 *
	 * <p>A plugin that talks to its own client mod asks a question on join and waits for the answer: Simple
	 * Voice Chat creates a verification code in its join handler, tells the client to prove itself, and only
	 * moves the player out of {@code disconnected} when the reply arrives. The reply is a plugin message.
	 *
	 * <p>So "the client answered" and "the join handler has finished" are two different moments, and the
	 * answer can be in the game's hands <i>while the handler is still running</i> — ProtocolLib's reply to
	 * the very handshake packet is the case in this issue's log: it is a {@code PLAY} packet, the loader's
	 * join event fires before the client's screen is ready, and the mod answers immediately. Delivering that
	 * answer inside the raise lets the plugin complete its handshake <i>while the server still thinks the
	 * client is loading</i>, which is a state the game never produces by itself.
	 *
	 * <p>The channel seam asks this question, and holds the packet until it can answer no. Nothing else
	 * changes: the packet is delivered, only not in the middle of the event that caused it.
	 */
	public static boolean isJoining(ServerPlayer player) {
		return player != null && JOIN_IN_FLIGHT.contains(player.getUUID());
	}

	/**
	 * The accounts that have an open session, i.e. have had a {@code PlayerJoinEvent} since their last leave.
	 *
	 * <p>Concurrent because the raise does not always happen on the server thread — a proxy or a re-threaded
	 * packet may deliver it from another one — and this must not be the thing that throws inside the join path.
	 */
	private static final java.util.Set<java.util.UUID> SESSIONS =
			java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * Whether this load should RAISE {@code PlayerJoinEvent}.
	 *
	 * <h2>What a move between worlds does to this path</h2>
	 *
	 * <p>{@code ServerPlayer.teleport} to another dimension runs {@code PlayerList.respawn}, which builds a new
	 * {@code ServerPlayer} <i>and</i> a new {@code ServerGamePacketListenerImpl}, so neither the player object nor
	 * the listener can answer "is this the same join". The {@code UUID} can.
	 *
	 * <h2>Why a session, and not a window of time</h2>
	 *
	 * <p>A window was the previous attempt and it was wrong in both directions. In the log behind this issue the
	 * raises for one session came 4.9 s and 39.5 s apart, so the window had to be narrower than 4.9 s to drop the
	 * first repeat, and a genuine reconnect at 14 s had to survive. Between those two numbers there is nothing
	 * but a guess about how fast a client can rejoin: a player kicked by an anticheat and back in four seconds
	 * gets no {@code PlayerJoinEvent} at all, and the duplicate the client dies on is only caught while it
	 * happens to land inside the window.
	 *
	 * <p>What the log actually shows is that nothing ends the session early: the same account receives a second
	 * {@code PlayerJoinEvent} 24.4 s after the first while its connection, its voice-chat state and its entity
	 * are all still alive, and {@code PlayerList.remove} never runs in between. So the question is not "how long
	 * ago" but "has this account been left since", and that is answered by {@link #playerLeft}.
	 *
	 * <h2>Lifetime</h2>
	 *
	 * <p>One entry per account, added here and removed in {@link #playerLeft} — the head of
	 * {@code PlayerList.remove}, which the game calls on a real disconnect only (a move goes through
	 * {@code respawn} and keeps the entry). A leave this hook never sees would keep the entry for the life of
	 * the server and silence that account's next join, so {@link #playerLeft} also runs when the hook cannot
	 * raise the quit event, and {@link #forgetAccounts} is called from the bridge's own shutdown.
	 *
	 * <h2>Why not "once per session" was refused before</h2>
	 *
	 * <p>An earlier revision used the same {@code Set.add} and was said to be a no-op because the log showed two
	 * raises with no {@code PlayerList.remove} in between — which is exactly what this guard is for. A set that
	 * is only cleared by the game's own leave path answers "already had one" to every raise of one session, and
	 * admits the next connection as soon as the game says the player left. Read that way the log does not refuse
	 * it; it describes the case it fixes.
	 */
	private static boolean joinClaim(ServerPlayer player) {
		// `add` is the whole decision, done in one atomic step: true means this account was not in a session,
		// so this is the raise. False means its session is still open — a move, or a re-dispatch.
		return SESSIONS.add(player.getUUID());
	}

	/**
	 * Whether the join path prints WHO raised it, which is the only evidence that tells the candidates apart.
	 *
	 * <h2>Why this exists at all</h2>
	 *
	 * <p>Every guard written for this issue so far answered a question about the raise and got it wrong,
	 * because the trigger was never identified — only its frequency measured. Which code path reaches the
	 * anchor a second time is what a stack trace settles in one run: the load packet again, {@code respawn}
	 * during a move between worlds, or a re-dispatch from somewhere that is not the join path at all.
	 *
	 * <h2>Why it is ON unless switched off</h2>
	 *
	 * <p>It shipped default-off, and a report came back saying the switch had been on for two runs with no
	 * trace in the log. Both statements were true: the switch was on, and no trace was possible, because the
	 * only branch that could reach the printer belonged to the raise the guard had already suppressed. A
	 * probe that is silent when the bug is present is worse than no probe — it reads as proof of absence.
	 *
	 * <p>So it prints on every raise that is actually delivered, and it is on by default: the cost is one
	 * trace per join, and the alternative is another round of guessing.
	 * {@code -Dneofobric.joinTrace=off} turns it off.
	 *
	 * <h2>Why a cap per account</h2>
	 *
	 * <p>The stack is the same on every join, so a few per account are enough to catch a pattern, and an
	 * uncapped trace on a server that keeps being tested buries everything else. Counted per account and
	 * kept after the player leaves, because a reconnect is the case worth seeing twice.
	 */
	private static final boolean JOIN_TRACE = traceEnabled();

	private static boolean traceEnabled() {
		String value = System.getProperty("neofobric.joinTrace", "on");
		return !("off".equalsIgnoreCase(value) || "0".equals(value) || "false".equalsIgnoreCase(value));
	}

	/** Traces printed per account before it goes quiet; enough to see a repeat, not enough to bury a log. */
	private static final int TRACE_LIMIT = 6;

	/** How many traces each account has had, capped by {@link #TRACE_LIMIT}. */
	private static final java.util.Map<java.util.UUID, Integer> TRACES =
			new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Prints the stack of a raise that is about to reach the plugins.
	 *
	 * <p>A few frames up are the ones that matter: this method, {@link #playerJoined}, the injected call site
	 * and then whichever of the game's own paths reached it. Nothing here interprets the trace — the point is
	 * that the reading is done against a real one instead of a hypothesis.
	 *
	 * <p>Never throws: it runs inside the client's load packet, where anything escaping would be attributed
	 * to the connection. A failure to print is reported and the raise continues.
	 */
	private static void traceRaise(ServerPlayer player) {
		if (!JOIN_TRACE) {
			return;
		}
		try {
			int printed = TRACES.merge(player.getUUID(), 1, Integer::sum);
			if (printed > TRACE_LIMIT) {
				return;
			}
			ForbricLog.info("[Forbric/Bukkit] PlayerJoinEvent #%d for %s is being raised; the stack below says"
							+ " from where (thread=%s)", printed, player.getScoreboardName(),
							Thread.currentThread().getName());
			new Throwable("PlayerJoinEvent #" + printed + " for " + player.getScoreboardName())
					.printStackTrace();
		} catch (Throwable failure) {
			ForbricLog.warn("[Forbric/Bukkit] could not trace the PlayerJoinEvent of %s (%s)",
							player.getScoreboardName(), failure);
		}
	}

	/**
	 * Prints the stack of a raise the guard has just suppressed.
	 *
	 * <p>A different question from {@link #traceRaise}: this one never reaches a client, so it cannot be the
	 * packet that kills one — but it is the evidence that the anchor is being reached more than once, and
	 * which path reached it. Shared cap, so the two together stay readable.
	 */
	private static void traceRepeatRaise(ServerPlayer player) {
		if (!JOIN_TRACE) {
			return;
		}
		try {
			int printed = TRACES.merge(player.getUUID(), 1, Integer::sum);
			if (printed > TRACE_LIMIT) {
				return;
			}
			ForbricLog.info("[Forbric/Bukkit] a second PlayerJoinEvent was reached for %s and suppressed;"
							+ " the stack below says from where (thread=%s)",
							player.getScoreboardName(), Thread.currentThread().getName());
			new Throwable("suppressed PlayerJoinEvent for " + player.getScoreboardName()).printStackTrace();
		} catch (Throwable failure) {
			ForbricLog.warn("[Forbric/Bukkit] could not trace the suppressed PlayerJoinEvent of %s (%s)",
							player.getScoreboardName(), failure);
		}
	}

	/**
	 * Ends the session this account held, so the next connection raises {@code PlayerJoinEvent} at once.
	 *
	 * <p>Called from {@link #playerLeft}, i.e. from {@code PlayerList.remove}, which is the leave path and not
	 * the move path. Left behind, the entry would silence the first raise of the player's NEXT connection.
	 */
	private static void endSession(ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
		// TRACES is deliberately NOT cleared here: a reconnect is the case the probe exists to compare.
		// A quit arriving while the join event is still on the stack (a listener that disconnects the player)
		// would otherwise leave the in-flight mark behind, and every later payload from that account would be
		// held for a join that has already ended.
		JOIN_IN_FLIGHT.remove(player.getUUID());
	}

	/**
	 * Ends every session this hook is holding.
	 *
	 * <p>For the bridge's own shutdown: the entries are static, so a bridge that is torn down and started again
	 * in the same JVM would otherwise believe every account is still in a session and raise no join event for
	 * anybody until they disconnect.
	 */
	public static void forgetAccounts() {
		SESSIONS.clear();
		JOIN_IN_FLIGHT.clear();
		TRACES.clear();
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
