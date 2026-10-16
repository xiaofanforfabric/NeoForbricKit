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
 *       <td>{@code handleAcceptPlayerLoad}, before its {@code markClientLoaded()} call</td>
 *       <td>{@code PlayerJoinEvent}</td>
 *       <td>no plugin ever hears that a player arrived</td></tr>
 *   <tr><td>{@code PlayerList}</td>
 *       <td>{@code remove}</td>
 *       <td>{@code PlayerQuitEvent}</td>
 *       <td>no plugin ever hears that a player left, so per-player state leaks</td></tr>
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
 * <p>Anchoring to {@code markClientLoaded} makes the raise once-per-join without this bridge
 * keeping any state, and a missing anchor is reported rather than silently falling back.
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

	/** The leave point's class and method. */
	static final String PLAYER_LIST_DOT = "net.minecraft.server.players.PlayerList";
	static final String QUIT_METHOD = "remove";
	static final String QUIT_DESC = "(Lnet/minecraft/server/level/ServerPlayer;)V";

	/** The static hooks the injected calls reach. */
	static final String HOOK_OWNER = "net/neoforbrickit/bukkit/ForbricPlayerEventHook";
	static final String JOIN_HOOK = "playerJoined";
	static final String QUIT_HOOK = "playerLeft";

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
			return injectQuit(context);
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
		if (alreadyInjected(target, JOIN_HOOK)) {
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

		InsnList code = new InsnList();
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));
		code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, player.name, player.desc));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, JOIN_HOOK, hookDesc(), false));
		target.instructions.insertBefore(anchor, code);

		ForbricLog.info("[NeoFobric/Bukkit] %s raises PlayerJoinEvent from %s"
						+ " — a plugin's listener hears that a player arrived, once per join",
						simpleName(node.name), JOIN_METHOD);
		return ComputeFlags.COMPUTE_FRAMES;
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

	/** {@code (Lnet/minecraft/server/level/ServerPlayer;)V} — both hooks take one player. */
	private static String hookDesc() {
		return "(" + PLAYER_DESC + ")V";
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
