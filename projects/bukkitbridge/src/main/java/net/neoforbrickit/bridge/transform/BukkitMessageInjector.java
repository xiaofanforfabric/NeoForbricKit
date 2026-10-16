/*
 * Forbric Bukkit bridge — the plugin-message seam, hosted by NeoForge's class pipeline.
 *
 * <p>Written in the same shape as {@link BukkitInteractInjector}, and for the same reason: a
 * plugin's {@code PluginMessageListener} only runs if something tells the bridge a payload
 * arrived, and the obvious something — subscribing to the surviving loader's event bus — would
 * make every Bukkit listener depend on another ecosystem's patches surviving. There is no relay
 * here. The call site below is a vanilla network method and the injected call is the whole
 * mechanism.
 */
package net.neoforbrickit.bridge.transform;

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforbrickit.bridge.util.ForbricLog;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Raises the bridge's plugin-message dispatch from the GAME's own payload handler.
 *
 * <p><b>Why this seam is needed at all.</b> Client-to-server plugin messages are how a modded
 * client tells a plugin what it supports. Simple Voice Chat asks the client for its channels
 * before it will send a single handshake packet, and a server that never hears that
 * registration concludes the client is incompatible — then returns silently, which looks like a
 * plugin that simply does not work. Vanilla parses the packet and finds nobody registered for
 * the channel, because nothing ever told the bridge about it.
 *
 * <table>
 *   <caption>Injections</caption>
 *   <tr><th>Method</th><th>Raised</th><th>A miss means</th></tr>
 *   <tr><td>{@code ServerGamePacketListenerImpl.handleCustomPayload}</td>
 *       <td>{@code Messenger.dispatchIncomingMessage}</td>
 *       <td>every client-to-server plugin channel is dropped, and any plugin whose handshake
 *           depends on one behaves as if the client were not there</td></tr>
 * </table>
 *
 * <p>{@code -Dneofobric.bukkitMessages=off} leaves the class alone.
 */
public final class BukkitMessageInjector implements ClassProcessor {

	/** The one class this claims. Declaring exactly it is what makes drift loud rather than silent. */
	static final String LISTENER_DOT = "net.minecraft.server.network.ServerGamePacketListenerImpl";

	/**
	 * The {@code ServerPlayer} field descriptor, as a STRING.
	 *
	 * <p>Never as a class literal: this processor runs on the "FML Plugins" layer, which has no
	 * {@code net.minecraft.*} — naming {@code ServerPlayer.class} here threw
	 * {@code NoClassDefFoundError} the moment a player joined, taking the server down. Everything
	 * the injection needs is describable in text, so nothing on this class ever loads a game type.
	 */
	static final String PLAYER_DESC = "Lnet/minecraft/server/level/ServerPlayer;";

	/** {@code handleCustomPayload(ServerboundCustomPayloadPacket)V}. */
	static final String HANDLE_PAYLOAD_DESC =
			"(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V";

	/** The static hook the injected call reaches, added to the bridge's own dispatch class. */
	static final String HOOK_OWNER = "net/neoforbrickit/bukkit/ForbricMessageHook";
	static final String HOOK_NAME = "dispatchFromGame";
	static final String HOOK_DESC =
			"(Lnet/minecraft/server/level/ServerPlayer;"
					+ "Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V";

	private static final ProcessorName NAME = new ProcessorName("neofobric", "bukkit_messages");

	public static final String PROPERTY = "neofobric.bukkitMessages";

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
		return LISTENER_DOT.equals(selection.type().getClassName());
	}

	@Override
	public ComputeFlags processClass(TransformationContext context) {
		if (!enabled()) {
			return ComputeFlags.NO_REWRITE;
		}
		if (!LISTENER_DOT.equals(context.type().getClassName())) {
			return ComputeFlags.NO_REWRITE;
		}

		ClassNode node = context.node();
		MethodNode target = method(node, "handleCustomPayload", HANDLE_PAYLOAD_DESC);
		if (target == null) {
			// Named the class, so a miss is worth saying: every client-to-server plugin channel
			// silently stops reaching plugins.
			context.audit("neofobric:bukkit_messages found no handleCustomPayload" + HANDLE_PAYLOAD_DESC
					+ " in " + context.type().getClassName());
			return ComputeFlags.NO_REWRITE;
		}
		if (alreadyInjected(target)) {
			return ComputeFlags.NO_REWRITE;
		}
		if (headIsBranchTarget(target)) {
			// A prepended guard cannot own the entry when something jumps to instruction 0.
			context.audit("neofobric:bukkit_messages declined " + context.type().getClassName()
					+ ".handleCustomPayload: the entry is a branch target, where a prepended hook cannot be first");
			return ComputeFlags.NO_REWRITE;
		}
		if (!hasPlayerField(node)) {
			// The player is read from the class's own field rather than from an argument, so the
			// field has to be there; without it the injected call cannot name who sent the payload.
			context.audit("neofobric:bukkit_messages declined " + context.type().getClassName()
					+ ": no ServerPlayer field to read the sender from");
			return ComputeFlags.NO_REWRITE;
		}

		// The hook runs BEFORE the game's own handling, so a channel the bridge knows about is
		// dispatched without depending on what vanilla does with unknown payloads — and vanilla's
		// own path still runs afterwards, which is what keeps vanilla channels working.
		InsnList code = new InsnList();
		code.add(new VarInsnNode(Opcodes.ALOAD, 0));      // this
		code.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, playerField(node).name,
				playerField(node).desc));                 // this.player
		code.add(new VarInsnNode(Opcodes.ALOAD, 1));      // packet
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_NAME, HOOK_DESC, false));
		target.instructions.insert(code);

		ForbricLog.info("[NeoFobric/Bukkit] %s dispatches client plugin messages from handleCustomPayload"
				+ " — a plugin's PluginMessageListener hears them with no loader bus involved",
				simpleName(node.name));
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

	/** Whether the hook is already present, so a second pass does not double it. */
	private static boolean alreadyInjected(MethodNode method) {
		for (var instruction : method.instructions) {
			if (instruction instanceof MethodInsnNode call
					&& call.owner.equals(HOOK_OWNER) && call.name.equals(HOOK_NAME)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether instruction 0 is a jump target.
	 *
	 * <p>A hook prepended there would not run for a branch that lands on the entry, and the
	 * injection would be half there — which reads as "sometimes the plugin does not hear it", the
	 * worst shape of failure to debug.
	 */
	private static boolean headIsBranchTarget(MethodNode method) {
		java.util.Set<LabelNode> targets = new java.util.HashSet<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof JumpInsnNode jump) targets.add(jump.label);
			else if (insn instanceof TableSwitchInsnNode table) {
				targets.add(table.dflt);
				targets.addAll(table.labels);
			} else if (insn instanceof LookupSwitchInsnNode lookup) {
				targets.add(lookup.dflt);
				targets.addAll(lookup.labels);
			}
		}
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() >= 0) return false;
			if (insn instanceof LabelNode label && targets.contains(label)) return true;
		}
		return false;
	}

	/**
	 * The {@code ServerPlayer} field the sender is read from, or null when the class has none.
	 *
	 * <p>Matched by descriptor STRING, because the type itself cannot be loaded here — see
	 * {@link #PLAYER_DESC}.
	 */
	private static FieldNode playerField(ClassNode node) {
		for (var field : node.fields) {
			if (PLAYER_DESC.equals(field.desc)) {
				return field;
			}
		}
		return null;
	}

	/** Whether the class holds a {@code ServerPlayer} field, which is where the sender comes from. */
	private static boolean hasPlayerField(ClassNode node) {
		return playerField(node) != null;
	}

	private static String simpleName(String internal) {
		int slash = internal.lastIndexOf('/');
		return slash < 0 ? internal : internal.substring(slash + 1);
	}
}
