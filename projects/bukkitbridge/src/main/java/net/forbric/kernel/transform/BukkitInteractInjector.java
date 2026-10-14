/*
 * NeoFobric Bukkit bridge — the interaction seam, hosted by NeoForge's class pipeline.
 *
 * CARRIED FROM the Forbric project's net.forbric.kernel.transform.BukkitInteractInjector
 * (Apache-2.0). The ASM body is adopted VERBATIM; only the host interface around it changed:
 *   Forbric: implements ClassTransformer, transform(String, byte[], TransformContext) — raw bytes in/out,
 *            with a Forbric-only AnchorSet/claims ledger for reporting.
 *   NeoForge: implements ClassProcessor, processClass(TransformationContext) — the context hands us a
 *            ClassNode already parsed, and COMPUTE_FRAMES requests the same frame recomputation the
 *            original did by hand.
 * Everything between "=== ASM CORE (carried verbatim) ===" and its end marker is the original code.
 */
package net.forbric.kernel.transform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.forbric.kernel.util.ForbricLog;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;

/**
 * Raises Bukkit's {@code PlayerInteractEvent} from the GAME's own interaction methods.
 *
 * <p>A plugin's {@code @EventHandler} only runs if something tells the bridge an interaction happened, and
 * the obvious something — subscribing to the surviving loader's event bus and relaying it — is the wrong
 * direction for this bridge: it makes every Bukkit listener depend on one ecosystem's patches surviving.
 * There is no relay here. The call sites below are vanilla game/network methods and the injected call is
 * the whole mechanism:
 *
 * <table>
 *   <caption>Injections</caption>
 *   <tr><th>Method</th><th>Raised action</th><th>A refusal means</th></tr>
 *   <tr><td>{@code ServerPlayerGameMode.useItemOn}</td><td>{@code RIGHT_CLICK_BLOCK}</td><td>return the hook's answer ({@code InteractionResult.FAIL})</td></tr>
 *   <tr><td>{@code ServerGamePacketListenerImpl.handleUseItem}</td><td>{@code RIGHT_CLICK_AIR}</td><td>return before the game-mode call</td></tr>
 *   <tr><td>{@code ServerPlayerGameMode.handleBlockBreakAction}</td><td>{@code LEFT_CLICK_BLOCK}</td><td>{@code return}, before any break starts</td></tr>
 *   <tr><td>{@code ServerGamePacketListenerImpl.handleAnimate}</td><td>{@code LEFT_CLICK_AIR}</td><td>{@code return} (the swing has nothing to suppress; the event is the point)</td></tr>
 * </table>
 *
 * <p>{@code -Dneofobric.bukkitInteract=off} leaves both classes alone.
 *
 * <p><b>NeoForge adaptation.</b> {@code handlesClass}/{@code processClass} replace the Forbric
 * {@code ClassTransformer}; the context arrives with its {@link ClassNode} already parsed and
 * {@code COMPUTE_FRAMES} asks FML's writer to recompute every stack map frame — which is what the
 * original's {@code FrameComputingWriter} existed to do. The original's {@code AnchorSet} ledger is
 * replaced by {@code context.audit(...)}, NeoForge's own channel for "this processor meant to edit
 * this class".
 */
public final class BukkitInteractInjector implements ClassProcessor {

    // ===== NeoForge host (adapted) =====

    private static final ProcessorName NAME = new ProcessorName("neofobric", "bukkit_interact");

    public static final String PROPERTY = "neofobric.bukkitInteract";

    @Override
    public ProcessorName name() {
        return NAME;
    }

    static boolean enabled() {
        return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
    }

    /**
     * Only the two classes the injections land on are claimed. Declaring exactly them is what makes a
     * drift loud: if {@code handlesClass} said yes and {@code processClass} then edited nothing, the
     * audit entry below names it rather than the miss passing silently.
     */
    @Override
    public boolean handlesClass(SelectionContext selection) {
        if (!enabled()) {
            return false;
        }
        String name = selection.type().getInternalName();
        return GAME_MODE_DOT.equals(selection.type().getClassName())
                || LISTENER_DOT.equals(selection.type().getClassName());
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        if (!enabled()) {
            return ComputeFlags.NO_REWRITE;
        }
        ClassNode node = context.node();
        boolean gameMode = GAME_MODE_DOT.equals(context.type().getClassName());
        boolean listener = LISTENER_DOT.equals(context.type().getClassName());
        if (!gameMode && !listener) {
            return ComputeFlags.NO_REWRITE;
        }

        int changed = 0;
        // The hand and the hit result travel with the event; the player comes from the method's own argument
        // on these two, and from the class's field on the two that only see a block.
        if (gameMode) {
            changed += inject(node, "useItemOn", USE_ITEM_ON_DESC, "useItemOn", USE_ITEM_ON_DESC,
                    Answer.RESULT, arguments(1, 2, 3, 4, 5), false, true);
            // NOT the head: this body serves START / ABORT / STOP and branches on the action argument, so a
            // guard at the entry would raise LEFT_CLICK_BLOCK on a release or an interruption too. Placed on
            // the START-only edge instead, which is where CraftBukkit raises it.
            changed += injectOnStartDestroy(node, "handleBlockBreakAction", BREAK_DESC);
        }
        if (listener) {
            changed += injectUseItemPacket(node);
            changed += inject(node, "handleAnimate", ANIMATE_DESC, "swing", SWING_HOOK_DESC,
                    Answer.RETURN, withPlayer(node), true, false);
        }
        if (changed == 0) {
            // handledClass claimed this class, so a miss is worth saying out loud: on GAME_MODE that means a
            // plugin's @EventHandler never hears the right-click or break interactions.
            context.audit("neofobric:bukkit_interact found no reviewed method shape in "
                    + context.type().getClassName());
            return ComputeFlags.NO_REWRITE;
        }

        ForbricLog.info("[NeoFobric/Bukkit] %s raises Bukkit's PlayerInteractEvent from %d game method(s) of its own "
                + "— no loader bus is involved", simpleName(node.name), changed);
        // COMPUTE_FRAMES is what the original's FrameComputingWriter did: let ASM derive every stack map frame
        // instead of hand-writing them, which is the class of bug it deleted.
        return ComputeFlags.COMPUTE_FRAMES;
    }

    // ===== ASM CORE (carried verbatim from BukkitInteractInjector; only ForbricLog prefixes rebranded) =====

    /**
     * The game-side seam the injected calls land on, as an INTERNAL name.
     *
     * <p>Two spellings are in play in this class and mixing them costs every injection silently: the two
     * target constants below are DOTTED, while everything written into bytecode — this class, the field
     * owners, the descriptors — is internal.
     */
    static final String HOOK = "org/cn/xiaofan/bukkit/ForbricInteractHook";

    /** Dotted, for the {@code getName()} comparisons NeoForge's context hands over. */
    static final String GAME_MODE_DOT = "net.minecraft.server.level.ServerPlayerGameMode";
    static final String LISTENER_DOT = "net.minecraft.server.network.ServerGamePacketListenerImpl";

    static final String PLAYER = "net/minecraft/server/level/ServerPlayer";
    static final String PLAYER_DESC = "L" + PLAYER + ";";
    static final String LEVEL_DESC = "Lnet/minecraft/world/level/Level;";
    static final String STACK_DESC = "Lnet/minecraft/world/item/ItemStack;";
    static final String HAND_DESC = "Lnet/minecraft/world/InteractionHand;";
    static final String USE_ITEM_PACKET_DESC = "Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;";
    static final String HIT_DESC = "Lnet/minecraft/world/phys/BlockHitResult;";
    static final String POS_DESC = "Lnet/minecraft/core/BlockPos;";
    static final String DIRECTION_DESC = "Lnet/minecraft/core/Direction;";
    static final String ACTION_DESC = "Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;";
    static final String SWING_DESC = "Lnet/minecraft/network/protocol/game/ServerboundSwingPacket;";

    /** The action enum as an INTERNAL name, for matching the {@code START_DESTROY_BLOCK} comparison in a body. */
    static final String ACTION_INTERNAL = "net/minecraft/network/protocol/game/ServerboundPlayerActionPacket";

    /** A name for the message a refusal prints; the internal name above is what bytecode matching needs. */
    static final String ACTION = "ServerboundPlayerActionPacket";

    /**
     * The local slot the action argument occupies in {@code handleBlockBreakAction}.
     *
     * <p>2, not 1: the method is an instance method, so slot 0 is {@code this}, the {@code BlockPos} argument is
     * slot 1, and the action is the SECOND argument at slot 2. Named rather than inlined because the guard
     * emitted at the head compares against it, and an off-by-one there would read the position as an enum.
     * {@code withPlayer(node, 1, 3)} loads the same method's {@code pos} and {@code face} by this numbering.
     */
    static final int ACTION_SLOT = 2;
    static final String RESULT = "net/minecraft/world/InteractionResult";
    static final String RESULT_DESC = "L" + RESULT + ";";

    static final String USE_ITEM_ON_DESC = "(" + PLAYER_DESC + LEVEL_DESC + STACK_DESC + HAND_DESC + HIT_DESC + ")" + RESULT_DESC;
    static final String BREAK_DESC = "(" + POS_DESC + ACTION_DESC + DIRECTION_DESC + "II)V";
    static final String LEFT_CLICK_DESC = "(" + PLAYER_DESC + POS_DESC + DIRECTION_DESC + ")Z";
    static final String USE_ITEM_PACKET_HOOK_DESC = "(" + PLAYER_DESC + USE_ITEM_PACKET_DESC + ")Z";
    static final String SWING_HOOK_DESC = "(" + PLAYER_DESC + ")Z";
    static final String ANIMATE_DESC = "(" + SWING_DESC + ")V";
    static final String HANDLE_USE_ITEM_DESC = "(" + USE_ITEM_PACKET_DESC + ")V";

    /** The field both target classes hold the interacting player in, read instead of re-deriving it from arguments. */
    static final String PLAYER_FIELD = "player";

    /** What an injected call decides: the value the game method returns, or that it returns at all. */
    private enum Answer { RESULT, RETURN }

    /**
     * Prepends one guard to {@code methodName}: call the seam, and if it refused, leave the game method.
     *
     * @param playerField whether the argument loads read {@code this.player}; declined when the field is not there
     * @param required    whether a miss is loud (the interaction's only source) or a word (a known gap)
     * @return 1 when the method was edited, 0 when it was left alone (and why, in one line)
     */
    private int inject(ClassNode node, String methodName, String methodDesc, String hookName, String hookDesc,
            Answer answer, InsnList loads, boolean playerField, boolean required) {
        MethodNode method = method(node, methodName, methodDesc);
        String where = simpleName(node.name) + "." + methodName;
        if (method == null) {
            return declined(where + " is not " + methodDesc + " on this base", required);
        }
        if ((method.access & Opcodes.ACC_STATIC) != 0) {
            return declined(where + " is static, so it has no player to raise an event for", required);
        }
        if (playerField && !hasPlayerField(node)) {
            return declined(where + " has no " + PLAYER + " " + PLAYER_FIELD + " field to read the player from",
                    required);
        }
        if (alreadyInjected(method, hookName)) return 0;
        if (headIsBranchTarget(method)) {
            return declined(where + " starts on a branch target, where a prepended guard cannot own the entry",
                    required);
        }

        LabelNode proceed = new LabelNode();
        InsnList injected = new InsnList();
        injected.add(loads);
        injected.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, hookName, hookDesc, false));
        if (answer == Answer.RESULT) {
            // The seam answers with the result the game must return, or null for "mine stands": the null is
            // carried to the fall-through and dropped there, so the original body runs untouched.
            //
            // The frame at the target is a FULL frame whose stack is the RESULT type. This is the FALL-THROUGH
            // edge of `IFNULL`, where the operand did NOT compare null, so the value is still
            // InteractionResult — DUP left two on the stack and ARETURN consumed one. Writing NULL here is a
            // VerifyError, and the verifier says so: "Type 'InteractionResult' (current frame, stack[0]) is
            // not assignable to null (stack map, stack[0])", raised on the first class load.
            injected.add(new InsnNode(Opcodes.DUP));
            injected.add(new JumpInsnNode(Opcodes.IFNULL, proceed));
            injected.add(new InsnNode(Opcodes.ARETURN));
            injected.add(proceed);
            injected.add(new InsnNode(Opcodes.POP));
        } else {
            injected.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
            injected.add(new InsnNode(Opcodes.RETURN));
            injected.add(proceed);
        }
        method.instructions.insert(injected);
        return 1;
    }

    /**
     * Raises {@code LEFT_CLICK_BLOCK} only for {@code START_DESTROY_BLOCK}, from the method's HEAD.
     *
     * <p>{@code handleBlockBreakAction} is the one site where a plain entry guard would be WRONG: the same body
     * serves {@code START_DESTROY_BLOCK}, {@code ABORT_DESTROY_BLOCK} and {@code STOP_DESTROY_BLOCK}, so an
     * unguarded entry call raises {@code LEFT_CLICK_BLOCK} on a release or an interruption too — a plugin counting
     * breaks sees the count double. CraftBukkit raises it inside the START case only.
     *
     * <p>So the guard is still installed at the head, but it TESTS THE ACTION ITSELF first and only calls the seam
     * on the START edge. That is deliberate: an {@code F_FULL} frame written at a mid-body offset would carry the
     * entry locals, and the merged body has already stored a {@code float} and a {@code BlockState} into slots 6
     * and 7 by the time the START branch is reached. At the head there is nothing to overwrite.
     *
     * <pre>
     *   aload_1; getstatic START_DESTROY_BLOCK; if_acmpne proceed
     *   this.player, pos, face; invokestatic leftClickBlock; ifne return
     * proceed: &lt;the original body&gt;
     * </pre>
     */
    private int injectOnStartDestroy(ClassNode node, String methodName, String methodDesc) {
        String where = simpleName(node.name) + "." + methodName;
        MethodNode method = method(node, methodName, methodDesc);
        if (method == null) {
            return declined(where + " is not " + methodDesc + " on this base", true);
        }
        if ((method.access & Opcodes.ACC_STATIC) != 0) {
            return declined(where + " is static, so it has no player to raise an event for", true);
        }
        if (!hasPlayerField(node)) {
            return declined(where + " has no " + PLAYER + " " + PLAYER_FIELD + " field to read the player from",
                    true);
        }
        if (alreadyInjected(method, "leftClickBlock")) return 0;
        if (headIsBranchTarget(method)) {
            return declined(where + " starts on a branch target, where a prepended guard cannot own the entry",
                    true);
        }

        LabelNode proceed = new LabelNode();
        LabelNode callHook = new LabelNode();
        InsnList injected = new InsnList();
        // if (action == START_DESTROY_BLOCK) take the hook; otherwise fall straight through to the body, which
        // is the abort/stop path and must not raise LEFT_CLICK_BLOCK.
        injected.add(new VarInsnNode(Opcodes.ALOAD, ACTION_SLOT));
        injected.add(new FieldInsnNode(Opcodes.GETSTATIC, ACTION_INTERNAL + "$Action", "START_DESTROY_BLOCK",
                ACTION_DESC));
        injected.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, callHook));
        injected.add(new JumpInsnNode(Opcodes.GOTO, proceed));
        injected.add(callHook);
        injected.add(withPlayer(node, 1, 3));
        injected.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "leftClickBlock", LEFT_CLICK_DESC, false));
        injected.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
        injected.add(new InsnNode(Opcodes.RETURN));
        injected.add(proceed);

        method.instructions.insert(injected);
        return 1;
    }

    /**
     * Raises {@code RIGHT_CLICK_AIR} in the packet handler, after vanilla has moved the packet onto the server
     * thread and established that this connection is in the play state, but before the handler calls
     * {@code ServerPlayerGameMode.useItem}.
     *
     * <p>Do not inject into {@code useItem}: that game-mode method is a later implementation detail. The packet
     * handler is the source-of-truth boundary: every air/item right click that the server accepts enters through
     * {@code handleUseItem(ServerboundUseItemPacket)}. Injecting immediately after {@code hasClientLoaded()}'s
     * false-return branch also matters: the method starts on a Netty thread and its first operation hands
     * execution to the server thread. A head hook would run Bukkit listeners on Netty.
     *
     * <p>The hook returns true only when Bukkit cancelled or denied the interaction; in that case return from this
     * void packet handler before vanilla calls {@code useItem}. Otherwise fall through and let the game handle it.
     */
    private int injectUseItemPacket(ClassNode node) {
        String methodName = "handleUseItem";
        String where = simpleName(node.name) + "." + methodName;
        MethodNode method = method(node, methodName, HANDLE_USE_ITEM_DESC);
        if (method == null) {
            return declined(where + " is not " + HANDLE_USE_ITEM_DESC + " on this base", true);
        }
        if ((method.access & Opcodes.ACC_STATIC) != 0) {
            return declined(where + " is static, so it has no player to raise an event for", true);
        }
        if (!hasPlayerField(node)) {
            return declined(where + " has no " + PLAYER + " " + PLAYER_FIELD + " field to read the player from", true);
        }
        if (alreadyInjected(method, "useItemPacket")) return 0;

        JumpInsnNode loadedBranch = null;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode call) || !call.name.equals("hasClientLoaded")
                    || !call.desc.equals("()Z")) continue;
            AbstractInsnNode next = call.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (next instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ) {
                loadedBranch = jump;
                break;
            }
            return declined(where + " hasClientLoaded() but not the reviewed false-return branch", true);
        }
        if (loadedBranch == null) {
            return declined(where + " has no hasClientLoaded() false-return branch to anchor the main-thread hook", true);
        }

        LabelNode proceed = new LabelNode();
        InsnList injected = new InsnList();
        injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
        injected.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, PLAYER_FIELD, PLAYER_DESC));
        injected.add(new VarInsnNode(Opcodes.ALOAD, 1));
        injected.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "useItemPacket", USE_ITEM_PACKET_HOOK_DESC, false));
        injected.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
        injected.add(new InsnNode(Opcodes.RETURN));
        injected.add(proceed);
        method.instructions.insert(loadedBranch, injected);
        return 1;
    }

    /** The reviewed method, or null: name and descriptor both, so a widened overload is not silently taken. */
    private static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) return method;
        }
        return null;
    }

    private static boolean hasPlayerField(ClassNode node) {
        for (FieldNode field : node.fields) {
            if (field.name.equals(PLAYER_FIELD) && field.desc.equals(PLAYER_DESC)) return true;
        }
        return false;
    }

    /** True when the seam is already called in this body, so a second pass over the bytes changes nothing. */
    private static boolean alreadyInjected(MethodNode method, String hookName) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(HOOK) && call.name.equals(hookName)) return true;
        }
        return false;
    }

    /**
     * True when the method's body begins on a label a branch jumps to.
     *
     * <p>Inserting a guard before such a label would put the guard inside a loop head rather than at the entry,
     * and the frame the guard's own branch needs would collide with the one already there. Vanilla's four methods
     * do not start that way; a base that does is left alone instead of being rewritten blind.
     */
    private static boolean headIsBranchTarget(MethodNode method) {
        Set<LabelNode> targets = new HashSet<>();
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
     * The locals at the method's entry, in the form a stack map frame wants: the receiver then the arguments,
     * with every primitive narrower than an int written as {@code INTEGER}. Kept from the original even though
     * {@code COMPUTE_FRAMES} no longer needs hand-written frames: {@code frameLocals} is still the descriptor-
     * derived source of truth for the receiver type, and dropping it would remove the one place the original
     * reasoned about the merged body's locals.
     */
    @SuppressWarnings("unused")
    private static Object[] frameLocals(ClassNode node, MethodNode method) {
        List<Object> locals = new ArrayList<>();
        if ((method.access & Opcodes.ACC_STATIC) == 0) locals.add(node.name);
        for (Type argument : Type.getArgumentTypes(method.desc)) locals.add(frameType(argument));
        return locals.toArray();
    }

    private static Object frameType(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Opcodes.INTEGER;
            case Type.FLOAT -> Opcodes.FLOAT;
            case Type.LONG -> Opcodes.LONG;
            case Type.DOUBLE -> Opcodes.DOUBLE;
            default -> type.getInternalName();
        };
    }

    /** {@code this.player} followed by the named argument slots, in that order. */
    private static InsnList withPlayer(ClassNode node, int... slots) {
        InsnList loads = new InsnList();
        loads.add(new VarInsnNode(Opcodes.ALOAD, 0));
        loads.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, PLAYER_FIELD, PLAYER_DESC));
        for (int slot : slots) loads.add(new VarInsnNode(Opcodes.ALOAD, slot));
        return loads;
    }

    /** The named argument slots, in that order. */
    private static InsnList arguments(int... slots) {
        InsnList loads = new InsnList();
        for (int slot : slots) loads.add(new VarInsnNode(Opcodes.ALOAD, slot));
        return loads;
    }

    /** Left alone, and why. Rung loudly on the class this transformer cannot afford to miss, quietly elsewhere. */
    private static int declined(String reason, boolean required) {
        String cost = " — left as it is, so a plugin's @EventHandler will not hear this interaction";
        if (required) {
            ForbricLog.warn("[NeoFobric/Bukkit] %s%s", reason, cost);
        } else {
            ForbricLog.info("[NeoFobric/Bukkit] %s%s", reason, cost);
        }
        return 0;
    }

    private static String simpleName(String internal) {
        return internal.substring(internal.lastIndexOf('/') + 1);
    }

    // ===== end ASM CORE =====

    /** Kept only so the carried {@code FrameComputingWriter} rationale in the original is not lost; NeoForge's
     * {@code ComputeFlags.COMPUTE_FRAMES} replaces it, and FML's writer answers {@code getCommonSuperClass} the
     * same way (it never loads game classes to merge them). */
    @SuppressWarnings("unused")
    private static final class FrameComputingWriter extends ClassWriter {
        FrameComputingWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return "java/lang/Object";
        }
    }
}
