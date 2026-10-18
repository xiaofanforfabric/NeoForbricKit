/*
 * Forbric Bukkit bridge — the player join/leave seam, hosted by NeoForge's class pipeline.
 *
 * <p>Written in the same shape as {@link BukkitMessageInjector}, and for the same reason: the
 * injected call has to land inside a vanilla method, and this source set is loaded by the "FML
 * Plugins" layer, which has no {@code net.minecraft.*} at all. Every type the injection needs is
 * therefore described as a STRING — naming {@code ServerPlayer.class} anywhere on this class
 * threw {@code NoClassDefFoundError} the moment a player joined, which is what the sibling
 * processor's own comment records. There is no relay here either: the call sites are vanilla
 * methods and the injected calls are the whole mechanism.
 */
package net.neoforbrickit.bridge.transform;

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforbrickit.bridge.util.ForbricLog;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Raises the bridge's {@code PlayerJoinEvent} and {@code PlayerQuitEvent} from the game's own
 * join and leave paths.
 *
 * <p><b>Why this seam is needed at all.</b> A Bukkit plugin's listeners only run when something
 * calls them, and the obvious something — subscribing to a surviving loader's event bus — would
 * make every Bukkit listener depend on another ecosystem's patches outliving this bridge. So the
 * calls are injected into the vanilla methods the events describe, exactly as the message and
 * interact seams do.
 *
 * <p>Without them the bridge dispatches almost nothing: a plugin can load, enable, register a
 * {@code PlayerJoinEvent} handler, and never see it fire once. That failure is silent in the
 * worst way — the plugin reports no error because there is none, and the operator concludes the
 * plugin is broken.
 *
 * <table>
 *   <caption>Injections</caption>
 *   <tr><th>Class</th><th>Method</th><th>Raised</th><th>A miss means</th></tr>
 *   <tr><td>{@code ServerGamePacketListenerImpl}</td>
 *       <td>{@code handleAcceptPlayerLoad}, before its {@code markClientLoaded()} call, behind a
 *           {@code boolean} field this processor adds to the class</td>
 *       <td>{@code PlayerJoinEvent}, once per listener</td>
 *       <td>no plugin ever hears that a player arrived</td></tr>
 *   <tr><td>{@code PlayerList}</td>
 *       <td>{@code remove}</td>
 *       <td>{@code PlayerQuitEvent}</td>
 *       <td>no plugin ever hears that a player left, so per-player state leaks</td></tr>
 *   <tr><td>{@code PlayerList}</td>
 *       <td>{@code placeNewPlayer}</td>
 *       <td>{@code AsyncPlayerPreLoginEvent}</td>
 *       <td>a permission plugin never learns the player exists, so every check answers from defaults</td></tr>
 * </table>
 *
 * <h2>Why the join hook is here and not in {@code placeNewPlayer}</h2>
 *
 * <p>{@code player} is read from the listener's own field, which is what makes
 * {@code handleAcceptPlayerLoad} the right target: the method takes only a packet, and the player
 * is already on {@code this}. That method is also the one that says the CLIENT has finished
 * loading terrain — raising the event earlier, at {@code PlayerList.placeNewPlayer}, would let a
 * plugin's chat message arrive before the client's chat window exists, which looks identical to
 * "the plugin did nothing".
 *
 * <h2>Why inside that method, rather than at its entry</h2>
 *
 * <p>{@code handleAcceptPlayerLoad} is ENTERED TWICE per join. Its first instruction is
 * {@code PacketUtils.ensureRunningOnSameThread}, which — when the caller is a Netty IO thread —
 * re-queues the packet and throws to abandon that pass; the server thread then re-enters the
 * method, and only that pass reaches {@code markClientLoaded()}. A hook at the entry therefore
 * raised {@code PlayerJoinEvent} twice, and TAB turned that into a client-side disconnect: it
 * creates a scoreboard objective named {@code TAB-PlayerList} per join, so the client received the
 * {@code set_objective} packet twice and died with
 * {@code An objective with the name 'TAB-PlayerList' already exists!}.
 *
 * <p>A missing anchor is reported rather than silently falling back to the entry.
 *
 * <h2>Why the raise is guarded, and by what</h2>
 *
 * <p>Anchoring was not the whole answer. Crossing the END portal teleports the player through
 * {@code PlayerList.respawn} → {@code placeNewPlayer}, which builds a NEW listener; the client, having
 * just loaded that new level, answers with another {@code ServerboundPlayerLoadedPacket}, so the
 * anchor is reached again and {@code PlayerJoinEvent} fires twice in one session. Same client-side
 * death, a different objective name: {@code An objective with the name 'TAB-Scoreboard' already
 * exists!}.
 *
 * <p>So the hook is preceded by a {@code boolean} field added to the listener: {@code if (this.f)
 * return;}, and the flag is set after the raise. A listener lives exactly one client load, so the
 * flag's lifetime is the lifetime of the thing being counted, and nothing has to be pruned on quit.
 *
 * <p><b>That flag alone is not enough, and the first attempt to fix this believed it was.</b>
 * {@code PlayerList.respawn} builds a new listener <i>and</i> a new {@code ServerPlayer}, so the
 * flag starts false on a listener nobody has seen before, and the anchor is reached on a session
 * that already had its join event — the client died on {@code 'TAB-Scoreboard'} again, on a server
 * whose startup log carried the line below. The flag therefore only stops one client load raising
 * twice; whether the load is a NEW session is decided in
 * {@code ForbricPlayerEventHook#beginSession}, by the account's UUID.
 *
 * <p>{@code PlayerList.remove} takes the player as its only argument, so no field is needed there.
 * It has no equivalent double-entry, because it is called by the game itself rather than from a
 * packet handler that may need re-threading.
 *
 * <p>{@code -Dneofobric.bukkitPlayerEvents=off} leaves both classes alone.
 */
public final class BukkitPlayerEventInjector implements ClassProcessor {

	/** The listener class, whose {@code player} field identifies who finished loading. */
	static final String LISTENER_DOT = "net.minecraft.server.network.ServerGamePacketListenerImpl";

	/**
	 * The {@code ServerPlayer} field descriptor, as a STRING — never a class literal.
	 *
	 * <p>See the class comment: this processor runs where {@code net.minecraft.*} cannot be loaded,
	 * and a class literal here took the server down when a player joined.
	 */
	static final String PLAYER_DESC = "Lnet/minecraft/server/level/ServerPlayer;";

	/** {@code handleAcceptPlayerLoad(ServerboundPlayerLoadedPacket)V} — the join point. */
	static final String JOIN_METHOD = "handleAcceptPlayerLoad";
	static final String JOIN_DESC =
			"(Lnet/minecraft/network/protocol/game/ServerboundPlayerLoadedPacket;)V";

	/**
	 * The call the join hook is placed BEFORE, as a name and descriptor pair.
	 *
	 * <p>Not the method entry, and that is the whole point. {@code handleAcceptPlayerLoad} runs FIRST on the
	 * Netty IO thread, where its own second instruction is
	 * {@code PacketUtils.ensureRunningOnSameThread} — which, when the calling thread is not the server's,
	 * re-queues the packet and throws {@code RunningOnDifferentThreadException} to abandon that pass. The
	 * server thread then pulls the packet out of the queue and enters the method a SECOND time, and that pass
	 * is the one that reaches {@code markClientLoaded()}.
	 *
	 * <p>Hooking the entry therefore ran the bridge twice and raised {@code PlayerJoinEvent} twice. That is not
	 * a theoretical duplicate: TAB creates a scoreboard objective named {@code TAB-PlayerList} per join, so
	 * the client received two {@code set_objective} packets, and the second killed the connection with
	 * {@code IllegalArgumentException: An objective with the name 'TAB-PlayerList' already exists!}.
	 *
	 * <p>{@code markClientLoaded()} is reached only on the pass that owns the thread — verified by
	 * disassembling the method: it is four instructions ({@code ensureRunningOnSameThread}, then
	 * {@code markClientLoaded}) — so a hook immediately before that call runs exactly once per join, with no
	 * state of this bridge's own to keep in sync.
	 */
	static final String MARK_LOADED_OWNER = "net/minecraft/server/network/ServerGamePacketListenerImpl";
	static final String MARK_LOADED_NAME = "markClientLoaded";
	static final String MARK_LOADED_DESC = "()V";

	/**
	 * The {@code boolean} this processor adds to the listener, and where its name comes from.
	 *
	 * <p>Declared by field name only: adding it is a {@link FieldNode}, and both references to it are emitted
	 * as text, so nothing here loads a {@code net.minecraft.*} type.
	 *
	 * <p>On the LISTENER, not on the bridge, and not on the player: a listener object is built per client load
	 * — {@code PlayerList.respawn} hands the new player a fresh one — while the player a plugin sees survives a
	 * dimension change. The listener's lifetime is exactly "one client load", which is what the guard counts, so
	 * the state is collected with the object and nothing has to be pruned on quit.
	 */
	static final String JOINED_FIELD = "neofobric$joinEventRaised";
	static final String FLAG_DESC = "Z";

	/** The leave point's class and method. */
	static final String PLAYER_LIST_DOT = "net.minecraft.server.players.PlayerList";
	static final String QUIT_METHOD = "remove";
	static final String QUIT_DESC = "(Lnet/minecraft/server/level/ServerPlayer;)V";

	/** The static hooks the injected calls reach. */
	static final String HOOK_OWNER = "net/neoforbrickit/bukkit/ForbricPlayerEventHook";
	static final String JOIN_HOOK = "playerJoined";
	static final String QUIT_HOOK = "playerLeft";

	/**
	 * {@code PlayerList.placeNewPlayer(Connection, ServerPlayer, CommonListenerCookie)V} — the pre-login point.
	 *
	 * <p>This is where {@code AsyncPlayerPreLoginEvent} is raised, and it has to be BEFORE the player is put in
	 * the world: a permission plugin loads its user data from that event and reads it back for every later
	 * check, so raising it after the join would leave the whole session answering from defaults.
	 *
	 * <p>The player is argument 1 (slot 2) of an instance method — {@code this} is slot 0, the connection is
	 * slot 1.
	 */
	static final String PRE_LOGIN_METHOD = "placeNewPlayer";
	static final String PRE_LOGIN_DESC = "(Lnet/minecraft/network/Connection;"
			+ "Lnet/minecraft/server/level/ServerPlayer;"
			+ "Lnet/minecraft/server/network/CommonListenerCookie;)V";
	static final String PRE_LOGIN_HOOK = "playerPreLogin";

	private static final ProcessorName NAME = new ProcessorName("neofobric", "bukkit_player_events");

	public static final String PROPERTY = "neofobric.bukkitPlayerEvents";

	@Override
	public ProcessorName name() {
		return NAME;
	}

	static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	@Override
	public boolean handlesClass(SelectionContext selection) {
		if (!enabled()) {
			return false;
		}
		String name = selection.type().getClassName();
		return LISTENER_DOT.equals(name) || PLAYER_LIST_DOT.equals(name);
	}

	@Override
	public ComputeFlags processClass(TransformationContext context) {
		if (!enabled()) {
			return ComputeFlags.NO_REWRITE;
		}
		String className = context.type().getClassName();
		if (LISTENER_DOT.equals(className)) {
			return injectJoin(context);
		}
		if (PLAYER_LIST_DOT.equals(className)) {
			// Two independent hooks in one class. Both run, and the class is rewritten if EITHER changed it --
			// returning early from the first would drop the second, which is exactly the silent half-present
			// failure this processor's own comments warn about.
			ComputeFlags quit = injectQuit(context);
			ComputeFlags preLogin = injectPreLogin(context);
			return quit == ComputeFlags.NO_REWRITE && preLogin == ComputeFlags.NO_REWRITE
					? ComputeFlags.NO_REWRITE
					: ComputeFlags.COMPUTE_FRAMES;
		}
		return ComputeFlags.NO_REWRITE;
	}

	/**
	 * {@code handleAcceptPlayerLoad}: insert a call that names the player, immediately before the method's own
	 * {@code markClientLoaded()} call.
	 *
	 * <p>Not at the entry — see {@link #MARK_LOADED_NAME} for why the entry runs twice per join. The player comes
	 * from the listener's own field rather than from an argument, so the field has to exist; it is matched by
	 * descriptor text because the type cannot be loaded on this layer.
	 */
	private ComputeFlags injectJoin(TransformationContext context) {
		ClassNode node = context.node();
		MethodNode target = method(node, JOIN_METHOD, JOIN_DESC);
		if (target == null) {
			// The class matched but the method did not: worth an audit line, because the consequence
			// is that no plugin ever hears about a joining player.
			context.audit("neofobric:bukkit_player_events found no " + JOIN_METHOD + JOIN_DESC
					+ " in " + context.type().getClassName());
			return ComputeFlags.NO_REWRITE;
		}
		FieldNode player = playerField(node);
		if (player == null) {
			context.audit("neofobric:bukkit_player_events declined " + context.type().getClassName()
					+ ": no ServerPlayer field to read the joining player from");
			return ComputeFlags.NO_REWRITE;
		}
		MethodInsnNode existingHook = hookCallIn(target);
		if (existingHook != null) {
			// Already hooked. The guard may still be missing — an older build of this bridge, or a class
			// assembled from a cached copy — and leaving it off is exactly the duplicate this class is about,
			// so it is installed either way, and the flag field is only added when it is genuinely absent.
			if (!guardPresent(target, existingHook)) {
				installJoinOnceGuard(node, target, existingHook);
			}
			return ComputeFlags.NO_REWRITE;
		}

		// The instruction the hook goes before. A miss here is fatal to the seam and must be loud: silently
		// falling back to the method entry would restore the double raise this is fixing, and nothing about
		// the resulting logs would say so.
		AbstractInsnNode anchor = callTo(target, MARK_LOADED_OWNER, MARK_LOADED_NAME, MARK_LOADED_DESC);
		if (anchor == null) {
			context.audit("neofobric:bukkit_player_events declined " + context.type().getClassName()
					+ "." + JOIN_METHOD + ": no " + MARK_LOADED_NAME + MARK_LOADED_DESC
					+ " call to anchor the join hook to, and hooking the entry instead would raise"
					+ " PlayerJoinEvent twice per join");
			return ComputeFlags.NO_REWRITE;
		}

		MethodInsnNode hookCall = new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, JOIN_HOOK, hookDesc(), false);
		InsnList code = new InsnList();
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, player.name, player.desc));
		code.add(hookCall);
		target.instructions.insertBefore(anchor, code);

		// Anchoring to markClientLoaded was right and is still not enough. Crossing the END portal runs
		// ServerPlayer.teleport -> PlayerList.respawn -> placeNewPlayer, which builds a NEW listener; the
		// client, fresh out of loading the new level, answers with another ServerboundPlayerLoadedPacket, and
		// that packet reaches the anchor again on a listener whose hook has never run.
		//
		// The per-listener flag installed below catches two raises for ONE listener (the re-threaded packet).
		// It cannot catch two listeners in one session, because a fresh listener starts with a false flag --
		// which is exactly how the first fix shipped and failed. Session identity is the hook's business: see
		// ForbricPlayerEventHook#beginSession, and keep both halves.
		installJoinOnceGuard(node, target, hookCall);
		return ComputeFlags.COMPUTE_FRAMES;
	}

	/**
	 * The {@code playerJoined} call already in this method, or null.
	 *
	 * <p>Separate from {@link #alreadyInjected} because the caller needs the INSTRUCTION, not just a yes —
	 * the guard goes in front of it. Matched by owner and name only: {@code hookDesc()} is the only signature
	 * this class ever emits for it.
	 */
	private static MethodInsnNode hookCallIn(MethodNode target) {
		for (AbstractInsnNode instruction : target.instructions) {
			if (instruction instanceof MethodInsnNode call
					&& call.owner.equals(HOOK_OWNER) && call.name.equals(JOIN_HOOK)) {
				return call;
			}
		}
		return null;
	}

	/**
	 * Whether the guard is already in front of this hook call.
	 *
	 * <p>Walked backwards over the guard's own instructions — {@code PUTFIELD, ICONST_1, ALOAD 0} precede the
	 * call, and the {@code IFEQ} before those is the test — rather than looking for an {@code IFEQ} anywhere
	 * nearby, which would find the hook inside some unrelated vanilla branch and conclude the guard was there.
	 *
	 * <p>The hook's own {@code ALOAD 0} (its player argument) sits directly behind the test, so it is skipped
	 * first: this file's earlier revision set the flag BEFORE the call, and a class carrying that shape still
	 * has to be recognised as guarded — reinstalling it would leave two sets of the same guard on the class of
	 * a running server.
	 */
	private static boolean guardPresent(MethodNode target, AbstractInsnNode hookCall) {
		AbstractInsnNode at = hookCall.getPrevious();
		// The hook sits between the test and the flag being set, so the first thing behind the test is the
		// hook's own argument load, not the guard. Exactly one instruction, and it has to be that load.
		if (at != null && at.getOpcode() == Opcodes.ALOAD) {
			at = at.getPrevious();
		}
		int putfield = 0;
		int iconst = 0;
		int aload = 0;
		while (at != null && (putfield < 1 || iconst < 1 || aload < 1)) {
			int opcode = at.getOpcode();
			if (opcode == Opcodes.PUTFIELD && at instanceof FieldInsnNode field
					&& field.name.equals(JOINED_FIELD)) {
				putfield++;
			} else if (opcode == Opcodes.ICONST_1) {
				iconst++;
			} else if (opcode == Opcodes.ALOAD && at instanceof VarInsnNode variable
					&& variable.var == 0) {
				aload++;
			} else {
				return false;
			}
			at = at.getPrevious();
		}
		while (at != null && at.getOpcode() < 0) {
			at = at.getPrevious(); // labels and line numbers
		}
		return at != null && at.getOpcode() == Opcodes.IFEQ;
	}

	/**
	 * Makes the join hook fire once per LISTENER, so one client load raises one {@code PlayerJoinEvent}.
	 *
	 * <h2>What actually went wrong on the END portal</h2>
	 *
	 * <p>Crossing to the END is {@code ServerPlayer.teleport} → {@code PlayerList.respawn} →
	 * {@code placeNewPlayer}: a new level AND a new listener, and the client — which has just loaded a new
	 * world — answers with another {@code ServerboundPlayerLoadedPacket}. That packet runs
	 * {@code handleAcceptPlayerLoad} on the fresh listener, reaching the anchor added by {@code fc3eb7697} a
	 * second time, and {@code PlayerJoinEvent} fires again for what the player did once. TAB builds a
	 * scoreboard objective per raise, so the client received {@code set_objective} twice and died with
	 * {@code An objective with the name 'TAB-Scoreboard' already exists!} — the same shape as the
	 * {@code TAB-PlayerList} duplicate that commit fixed, one layer down: that one stopped a packet being
	 * ENTERED twice, this one stops a second packet being SENT.
	 *
	 * <h2>Why the flag lives on the listener, and what it does not cover</h2>
	 *
	 * <p>Because the lifetime that matters for THE RE-THREADED PACKET is the listener's: one is built per
	 * client load, so a fresh one legitimately starts false, and the state disappears with the object. That is
	 * the duplicate this guard is for — {@code handleAcceptPlayerLoad} entered on the Netty thread and again on
	 * the server thread.
	 *
	 * <p>It is NOT the whole answer, and shipping it as though it were is why the client still died on
	 * {@code 'TAB-Scoreboard'}: {@code respawn} gives the teleported player a new listener, so this flag starts
	 * false for a session that already had its event. The session half lives in
	 * {@code ForbricPlayerEventHook#beginSession}, keyed by UUID. Two guards, two questions: "did this
	 * listener already raise?" and "did this session already have a join?".
	 *
	 * <p>The field is added by this processor, and every instruction referring to it is emitted from text
	 * (the field's own name, and {@code Z}), so this layer still names no {@code net.minecraft.*} type as a
	 * class literal. The guard sees to its own flag being on the class, so a class that already carries the
	 * hook from an earlier build gets the guard too rather than being left as it was.
	 */
	private void installJoinOnceGuard(ClassNode node, MethodNode target, AbstractInsnNode hookCall) {
		FieldNode flag = null;
		for (FieldNode candidate : node.fields) {
			if (candidate.name.equals(JOINED_FIELD)) {
				flag = candidate;
				break;
			}
		}
		if (flag == null) {
			flag = new FieldNode(Opcodes.ACC_PRIVATE, JOINED_FIELD, FLAG_DESC, null, null);
			node.fields.add(flag);
		}

		// Around the hook call: `if (this.<flag>) return;` in front of it, `this.<flag> = true;` behind it.
		//
		// The early RETURN is safe even though this sits mid-method: between here and markClientLoaded() there
		// is nothing but the hook, and markClientLoaded() is exactly what the pass that set the flag has
		// already done.
		//
		// The set comes AFTER the hook rather than before it for one reason: a hook that fails must not
		// leave the flag claiming an event nobody heard. Nothing between the two can throw.
		LabelNode raise = new LabelNode();
		InsnList guard = new InsnList();
		guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
		guard.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, flag.name, flag.desc));
		guard.add(new JumpInsnNode(Opcodes.IFEQ, raise));
		guard.add(new InsnNode(Opcodes.RETURN));
		guard.add(raise);
		target.instructions.insertBefore(hookCall, guard);
		InsnList mark = new InsnList();
		mark.add(new VarInsnNode(Opcodes.ALOAD, 0));
		mark.add(new InsnNode(Opcodes.ICONST_1));
		mark.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, flag.name, flag.desc));
		target.instructions.insert(hookCall, mark);
		ForbricLog.info("[NeoFobric/Bukkit] %s.%s raises PlayerJoinEvent at most once per client load"
						+ " — a re-threaded load packet does not raise it again",
						simpleName(node.name), JOIN_METHOD);
	}

	/**
	 * The instruction calling exactly this method, or null.
	 *
	 * <p>Matched by owner, name AND descriptor: an overload would share the name and quietly anchor the hook to
	 * the wrong call, which is the kind of mistake that produces "sometimes the event fires twice".
	 */
	private static AbstractInsnNode callTo(MethodNode method, String owner, String name, String desc) {
		for (AbstractInsnNode instruction : method.instructions) {
			if (instruction instanceof MethodInsnNode call
					&& call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) {
				return instruction;
			}
		}
		return null;
	}

	/**
	 * {@code PlayerList.remove}: prepend a call naming the player on the way out.
	 *
	 * <p>At the HEAD, before the method takes the player out of the world, because the API promises
	 * a {@code PlayerQuitEvent} listener can still read the player's location and inventory. Moving
	 * it to the tail would make those reads return a removed player.
	 */
	private ComputeFlags injectQuit(TransformationContext context) {
		ClassNode node = context.node();
		MethodNode target = method(node, QUIT_METHOD, QUIT_DESC);
		if (target == null) {
			context.audit("neofobric:bukkit_player_events found no " + QUIT_METHOD + QUIT_DESC
					+ " in " + context.type().getClassName());
			return ComputeFlags.NO_REWRITE;
		}
		if (alreadyInjected(target, QUIT_HOOK)) {
			return ComputeFlags.NO_REWRITE;
		}
		if (headIsBranchTarget(target)) {
			context.audit("neofobric:bukkit_player_events declined " + context.type().getClassName()
					+ "." + QUIT_METHOD
					+ ": the entry is a branch target, where a prepended hook cannot be first");
			return ComputeFlags.NO_REWRITE;
		}

		// The player is argument 0 of an instance method's locals, which is slot 1.
		InsnList code = new InsnList();
		code.add(new VarInsnNode(Opcodes.ALOAD, 1));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, QUIT_HOOK, hookDesc(), false));
		target.instructions.insert(code);

		ForbricLog.info("[NeoFobric/Bukkit] %s raises PlayerQuitEvent from %s"
						+ " — a plugin's listener hears that a player left, while their state is still readable",
						simpleName(node.name), QUIT_METHOD);
		return ComputeFlags.COMPUTE_FRAMES;
	}

	/** {@code (Lnet/minecraft/server/level/ServerPlayer;)V} — the join and quit hooks take one player. */
	private static String hookDesc() {
		return "(" + PLAYER_DESC + ")V";
	}

	/**
	 * {@code (Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/Connection;)V} — the pre-login
	 * hook also takes the connection being attached, which the player does not have yet.
	 *
	 * <p>Emitted as text, like {@link #PLAYER_DESC}: this layer must not name a {@code net.minecraft.*} type as
	 * a class literal.
	 */
	static final String PRE_LOGIN_HOOK_DESC = "(" + PLAYER_DESC + "Lnet/minecraft/network/Connection;)V";

	/** The pre-login hook as it was emitted before it needed the connection; matched so an old build re-emits. */
	static final String PRE_LOGIN_HOOK_DESC_V1 = "(" + PLAYER_DESC + ")V";

	/**
	 * {@code PlayerList.placeNewPlayer}: prepend a call naming the player, before it is put in the world.
	 *
	 * <p>This raises {@code AsyncPlayerPreLoginEvent}, which is where a permission plugin loads a player's
	 * data. Without it the plugin's user manager never learns the player exists — {@code /lp user <name>}
	 * answers "user not found" for somebody standing in the world, and every permission check falls back to the
	 * declared defaults, while the plugin itself reports that it enabled successfully.
	 *
	 * <p>At the HEAD rather than anchored to a later call: the point of the event is to decide whether this
	 * player may be placed at all, so it has to run before anything decides that for us.
	 */
	private ComputeFlags injectPreLogin(TransformationContext context) {
		ClassNode node = context.node();
		MethodNode target = method(node, PRE_LOGIN_METHOD, PRE_LOGIN_DESC);
		if (target == null) {
			context.audit("neofobric:bukkit_player_events found no " + PRE_LOGIN_METHOD + PRE_LOGIN_DESC
					+ " in " + context.type().getClassName());
			return ComputeFlags.NO_REWRITE;
		}
		if (alreadyInjected(target, PRE_LOGIN_HOOK)) {
			// A hook emitted by an earlier build takes only the player and leaves the connection unreachable,
			// which is how a refused login became advisory. The class is rewritten if it still carries that
			// signature; the injected call is what a cached class carries, so it is replaced by the two-argument
			// form below. Both in one pass: this runs on already-transformed classes as well.
			boolean stale = false;
			for (AbstractInsnNode instruction : target.instructions) {
				if (instruction instanceof MethodInsnNode call
						&& call.owner.equals(HOOK_OWNER) && call.name.equals(PRE_LOGIN_HOOK)
						&& call.desc.equals(PRE_LOGIN_HOOK_DESC_V1)) {
					target.instructions.remove(call);
					// The player load that fed it: `ALOAD 2` immediately before, and nothing else does.
					AbstractInsnNode previous = instruction.getPrevious();
					while (previous != null && previous.getOpcode() < 0) {
						previous = previous.getPrevious();
					}
					if (previous instanceof VarInsnNode load && load.var == 2) {
						target.instructions.remove(load);
					}
					stale = true;
				}
			}
			if (!stale) {
				return ComputeFlags.NO_REWRITE;
			}
			context.audit("neofobric:bukkit_player_events re-emitted " + PRE_LOGIN_HOOK
					+ " with the connection, so a refused login is honoured");
		} else {
			if (headIsBranchTarget(target)) {
				context.audit("neofobric:bukkit_player_events declined " + context.type().getClassName()
						+ "." + PRE_LOGIN_METHOD
						+ ": the entry is a branch target, where a prepended hook cannot be first");
				return ComputeFlags.NO_REWRITE;
			}
		}

		// Arguments 1 and 2 of an instance method: slot 0 is `this`, slot 1 the Connection, slot 2 the player.
		//
		// The Connection is passed because the hook cannot get it from the player: this method ATTACHES it,
		// so `player.connection` is still null here — and the hook's refusal path used to ask the player for
		// its connection (via a method that does not exist), which both failed to compile and, once fixed that
		// way, could never have disconnected anybody.
		InsnList code = new InsnList();
		code.add(new VarInsnNode(Opcodes.ALOAD, 2));
		code.add(new VarInsnNode(Opcodes.ALOAD, 1));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, PRE_LOGIN_HOOK, PRE_LOGIN_HOOK_DESC, false));
		target.instructions.insert(code);

		ForbricLog.info("[NeoFobric/Bukkit] %s raises AsyncPlayerPreLoginEvent from %s"
						+ " — a permission plugin learns the player exists before the first permission check",
						simpleName(node.name), PRE_LOGIN_METHOD);
		return ComputeFlags.COMPUTE_FRAMES;
	}

	/** The method with exactly this name and descriptor, or null. */
	private static MethodNode method(ClassNode node, String name, String desc) {
		for (MethodNode candidate : node.methods) {
			if (candidate.name.equals(name) && candidate.desc.equals(desc)) {
				return candidate;
			}
		}
		return null;
	}

	/** Whether this hook is already present, so a second pass does not double it. */
	private static boolean alreadyInjected(MethodNode method, String hookName) {
		for (AbstractInsnNode instruction : method.instructions) {
			if (instruction instanceof MethodInsnNode call
					&& call.owner.equals(HOOK_OWNER) && call.name.equals(hookName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether instruction 0 is a jump target.
	 *
	 * <p>A hook prepended there would not run for a branch that lands on the entry, leaving the
	 * injection half present — the worst shape of failure, because it reads as "sometimes the
	 * plugin does not hear it".
	 */
	private static boolean headIsBranchTarget(MethodNode method) {
		java.util.Set<LabelNode> targets = new java.util.HashSet<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof JumpInsnNode jump) {
				targets.add(jump.label);
			} else if (insn instanceof TableSwitchInsnNode table) {
				targets.add(table.dflt);
				targets.addAll(table.labels);
			} else if (insn instanceof LookupSwitchInsnNode lookup) {
				targets.add(lookup.dflt);
				targets.addAll(lookup.labels);
			}
		}
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() >= 0) {
				return false;
			}
			if (insn instanceof LabelNode label && targets.contains(label)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The {@code ServerPlayer} field on the listener, or null when it has none.
	 *
	 * <p>Matched by descriptor text: the type itself cannot be loaded on this layer.
	 */
	private static FieldNode playerField(ClassNode node) {
		for (FieldNode field : node.fields) {
			if (PLAYER_DESC.equals(field.desc)) {
				return field;
			}
		}
		return null;
	}

	private static String simpleName(String internal) {
		int slash = internal.lastIndexOf('/');
		return slash < 0 ? internal : internal.substring(slash + 1);
	}
}
