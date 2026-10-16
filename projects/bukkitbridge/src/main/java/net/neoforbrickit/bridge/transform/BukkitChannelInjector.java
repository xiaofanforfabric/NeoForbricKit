/*
 * Forbric Bukkit bridge — the channel-announcement seam, hosted by NeoForge's
 * class pipeline.
 *
 * <p>Written in the same shape as {@link BukkitMessageInjector}, and for the same
 * reason: the injected call has to land inside a NeoForge method that this
 * source set cannot name at compile time — {@code NetworkRegistry} lives in the
 * {@code neoforge} module, and a compile-time reference from the bridge would tie
 * the bridge to a class the game layer has not loaded yet. There is no relay
 * here either. The call site is NeoForge's own configuration-phase method and
 * the injected call is the whole mechanism.
 */
package net.neoforbrickit.bridge.transform;

import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforbrickit.bridge.util.ForbricLog;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Announces the bridge's plugin channels to a joining client.
 *
 * <p><b>Why this seam is needed at all.</b> A NeoForge client will not send a
 * modded payload it has not been told the server accepts.
 * {@code ClientPacketDistributor.sendToServer} asks
 * {@code NetworkRegistry.hasChannel} first and throws
 * {@code UnsupportedOperationException} when the answer is no, which the sending
 * mod can only surface as "the server does not accept these packets". The ids
 * the client consults come from the {@code minecraft:register} packet the server
 * sends as the configuration phase ends, and NeoForge fills that packet in
 * {@code NetworkRegistry.onConfigurationFinished}.
 *
 * <p>For a NeoForge connection that method adds only NeoForge's own handshake
 * ids — the channels a {@code Bukkit} plugin registered through
 * {@code Messenger} are not among them, because nothing in NeoForge knows they
 * exist. The result is a client mod and a Bukkit plugin that address each other
 * over the same channel and never exchange a byte: the plugin's registration is
 * recorded on this side, and the client is never told.
 *
 * <table>
 *   <caption>Injections</caption>
 *   <tr><th>Method</th><th>Raised</th><th>A miss means</th></tr>
 *   <tr><td>{@code NetworkRegistry.onConfigurationFinished}</td>
 *       <td>{@code ForbricChannelHook.addBridgeChannels}</td>
 *       <td>the announcement carries only NeoForge's own ids, and every client
 *           mod speaking to a Bukkit plugin over a plugin channel is told the
 *           server does not accept it</td></tr>
 * </table>
 *
 * <p>{@code -Dneofobric.bukkitChannels=off} leaves the class alone.
 */
public final class BukkitChannelInjector implements ClassProcessor {

	/** The class the injection lands in. */
	static final String REGISTRY_DOT = "net.neoforged.neoforge.network.registration.NetworkRegistry";

	/**
	 * {@code onConfigurationFinished(ICommonPacketListener)V}.
	 *
	 * <p>The descriptor is the JVM form — slashes, not the dots {@code javap} prints
	 * — because {@link MethodNode#desc} holds the former. Getting this wrong is
	 * silent: a descriptor that names no method simply never matches, and the
	 * injection looks like "the seam was never offered the class".
	 */
	static final String METHOD_NAME = "onConfigurationFinished";
	static final String METHOD_DESC =
			"(Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V";

	/** The payload NeoForge is filling when the announcement is about to be sent. */
	static final String REGISTER_PAYLOAD = "net/neoforged/neoforge/network/payload/MinecraftRegisterPayload";

	static final String HOOK_OWNER = "net/neoforbrickit/bukkit/ForbricChannelHook";
	static final String HOOK_NAME = "addBridgeChannels";
	static final String HOOK_DESC = "(Ljava/lang/Object;)V";

	private static final ProcessorName NAME = new ProcessorName("neofobric", "bukkit_channels");

	public static final String PROPERTY = "neofobric.bukkitChannels";

	static {
		// Proof of life. Without it, "no announcement log" reads the same whether this
		// class was never loaded, was loaded but never offered the class, or was offered
		// it and declined — three different fixes.
		ForbricLog.info("[NeoFobric/Bukkit] bukkit_channels processor loaded"
				+ " (target: %s.%s; -D%s=off disables it)", REGISTRY_DOT, METHOD_NAME, PROPERTY);
	}

	@Override
	public ProcessorName name() {
		return NAME;
	}

	static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	@Override
	public boolean handlesClass(ClassProcessor.SelectionContext selection) {
		if (!enabled()) {
			return false;
		}
		boolean hit = REGISTRY_DOT.equals(selection.type().getClassName());
		if (hit) {
			// Said out loud because the only other evidence that this processor ran at all is
			// the announcement in processClass, and "no log" cannot distinguish "never asked"
			// from "asked and declined".
			ForbricLog.info("[NeoFobric/Bukkit] bukkit_channels was offered %s — claiming it",
					selection.type().getClassName());
		}
		return hit;
	}

	@Override
	public ClassProcessor.ComputeFlags processClass(ClassProcessor.TransformationContext context) {
		if (!enabled()) {
			return ClassProcessor.ComputeFlags.NO_REWRITE;
		}
		if (!REGISTRY_DOT.equals(context.type().getClassName())) {
			return ClassProcessor.ComputeFlags.NO_REWRITE;
		}

		ClassNode node = context.node();
		MethodNode target = method(node, METHOD_NAME, METHOD_DESC);
		if (target == null) {
			// Named the class, so a miss is worth saying: it is the only place the
			// server tells a client which plugin channels it will accept.
			ForbricLog.warn("[NeoFobric/Bukkit] bukkit_channels declined %s: no %s%s",
					REGISTRY_DOT, METHOD_NAME, METHOD_DESC);
			context.audit("neofobric:bukkit_channels found no " + METHOD_NAME + METHOD_DESC + " in "
					+ context.type().getClassName());
			return ClassProcessor.ComputeFlags.NO_REWRITE;
		}
		if (alreadyInjected(target)) {
			ForbricLog.info("[NeoFobric/Bukkit] bukkit_channels already present in %s — nothing to do",
					REGISTRY_DOT);
			return ClassProcessor.ComputeFlags.NO_REWRITE;
		}

		// The announcement is the `send(new MinecraftRegisterPayload(<builder>.build()))`
		// call at the end of the method, and the local the injected code needs is the
		// ImmutableSet.Builder that call builds from.
		//
		// Finding it means finding the two instructions that name it, in the order the
		// JVM runs them:
		//
		//   aload_0                                // listener - NOT the builder
		//   new  MinecraftRegisterPayload
		//   dup
		//   aload_3                                // <- the builder
		//   invokevirtual ImmutableSet$Builder.build()
		//   invokespecial MinecraftRegisterPayload.<init>(Ljava/util/Set;)V
		//
		// So the slot is read from the ALOAD that precedes `build()`, while the code is
		// INSERTED before `new` - the only point in that stretch where the operand stack
		// is empty and a call can safely be prepended. Reading the slot from the ALOAD
		// before `new` instead would hand the hook the listener, and the channel set
		// would be appended to an unrelated collection with no error at all.
		AbstractInsnNode anchor = null;
		AbstractInsnNode builderLoad = null;
		for (AbstractInsnNode insn = target.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof TypeInsnNode allocation)
					|| allocation.getOpcode() != Opcodes.NEW
					|| !REGISTER_PAYLOAD.equals(allocation.desc)) {
				continue;
			}
			builderLoad = loadBeforeBuildAfter(allocation);
			if (builderLoad != null) {
				anchor = allocation;
				break;
			}
		}
		if (anchor == null) {
			ForbricLog.warn("[NeoFobric/Bukkit] bukkit_channels declined %s: no MinecraftRegisterPayload built"
					+ " from a local builder — NeoForge's onConfigurationFinished no longer looks the way"
					+ " this seam expects, so plugin channels stay unannounced",
					REGISTRY_DOT);
			context.audit("neofobric:bukkit_channels declined " + REGISTRY_DOT + "." + METHOD_NAME
					+ ": no MinecraftRegisterPayload allocation built from a local builder");
			return ClassProcessor.ComputeFlags.NO_REWRITE;
		}

		// The builder is still on the stack when the injected call runs — the payload
		// constructor consumes it later — so the injected code loads it again rather
		// than consuming it.
		InsnList code = new InsnList();
		code.add(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) builderLoad).var));
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_NAME, HOOK_DESC, false));
		target.instructions.insertBefore(anchor, code);

		ForbricLog.info("[NeoFobric/Bukkit] %s announces plugin channels (builder slot %s) alongside"
				+ " NeoForge's own — a client mod now hears this server accepts them,"
				+ " with no loader bus involved",
				simpleName(REGISTRY_DOT), ((VarInsnNode) builderLoad).var);
		return ClassProcessor.ComputeFlags.COMPUTE_FRAMES;
	}

	/**
	 * The local load that feeds {@code ImmutableSet$Builder.build()} inside the
	 * {@code MinecraftRegisterPayload} construction starting at {@code allocation}, or
	 * null when that construction does not look the way this seam expects.
	 *
	 * <p>Pseudo-instructions ({@code LabelNode}, {@code LineNumberNode},
	 * {@code FrameNode}) are walked over: they carry no stack effect but do sit
	 * between real instructions, and treating them as the predecessor is how a hook
	 * ends up reading the wrong local.
	 */
	private static AbstractInsnNode loadBeforeBuildAfter(AbstractInsnNode allocation) {
		for (AbstractInsnNode insn = allocation.getNext(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode call
					&& "build".equals(call.name)
					&& call.owner.equals("com/google/common/collect/ImmutableSet$Builder")) {
				for (AbstractInsnNode back = previousReal(insn); back != null; back = previousReal(back)) {
					if (back instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD) {
						return load;
					}
					return null;
				}
				return null;
			}
			// The construction ends at the constructor call; looking past it would find
			// some later build() and silently attach the channels to a different set.
			if (insn instanceof MethodInsnNode end
					&& end.getOpcode() == Opcodes.INVOKESPECIAL
					&& REGISTER_PAYLOAD.equals(end.owner)) {
				return null;
			}
		}
		return null;
	}

	/** The previous instruction that is not a label, line number or frame. */
	private static AbstractInsnNode previousReal(AbstractInsnNode from) {
		for (AbstractInsnNode insn = from.getPrevious(); insn != null; insn = insn.getPrevious()) {
			if (insn.getOpcode() >= 0) {
				return insn;
			}
		}
		return null;
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

	private static String simpleName(String internal) {
		int slash = internal.lastIndexOf('/');
		return slash < 0 ? internal : internal.substring(slash + 1);
	}
}
