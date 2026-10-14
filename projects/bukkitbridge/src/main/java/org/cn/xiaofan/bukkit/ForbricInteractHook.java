/*
 * Forbric Bukkit bridge — the seam the game's own interaction methods call.
 *
 * This class names NO Bukkit type. That is the point of it being separate from
 * {@link ForbricInteractDispatch}: the kernel injects a call to these methods into
 * {@code ServerPlayerGameMode.useItemOn/useItem/handleBlockBreakAction} and
 * {@code ServerGamePacketListenerImpl.handleAnimate}, and those call sites are reached on a server that
 * may have no Paper API on its classpath at all (the bridge is optional and its API jars are fetched,
 * not shipped). Linking THIS class therefore costs only game types; the class that does name
 * {@code org.bukkit.*} is instantiated when — and only when — the bridge actually starts.
 *
 * <p>A sink that is not installed answers "the game proceeds exactly as it was": every method here
 * returns null (the caller's own answer, meaning no Bukkit decision) or false.
 */
package org.cn.xiaofan.bukkit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The four game call sites that can raise a Bukkit {@code PlayerInteractEvent}, as static entry points.
 *
 * <p>They live here rather than in the kernel's transformer because the transformer only knows their
 * names as strings — the game-side half is where the types are.
 */
public final class ForbricInteractHook {

	/**
	 * What the bridge installs when it starts.
	 *
	 * <p>An interface, so the game-side call sites depend on nothing that names {@code org.bukkit}.
	 * {@code null} and {@code false} both mean "no decision was taken; run as the game would".
	 */
	public interface Sink {

		/** The answer the game must return for a block interaction, or null to keep its own. */
		InteractionResult useItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
				BlockHitResult hit);

		/** The answer the game must return for an item/air interaction, or null to keep its own. */
		InteractionResult useItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand);

		/** True when the break the packet asked for must not start. */
		boolean leftClickBlock(ServerPlayer player, BlockPos pos, Direction face);

		/** True when the swing is one the bridge refused (nothing to suppress in the game; the event is the point). */
		boolean swing(ServerPlayer player);

		/** True when the accepted use-item packet was refused by Bukkit. */
		boolean useItemPacket(ServerPlayer player, InteractionHand hand);
	}

	private static volatile Sink sink;

	private ForbricInteractHook() {
	}

	/** Installs the sink, after the bridge has loaded its plugins and the API is on the classpath. */
	public static void arm(Sink installed) {
		sink = installed;
	}

	/** Removes the sink, so a stopping server raises nothing while its plugins are gone. */
	public static void disarm() {
		sink = null;
	}

	/** {@code ServerPlayerGameMode.useItemOn} — Bukkit {@code RIGHT_CLICK_BLOCK}. */
	public static InteractionResult useItemOn(ServerPlayer player, Level level, ItemStack stack,
			InteractionHand hand, BlockHitResult hit) {
		Sink installed = sink;
		return installed == null ? null : installed.useItemOn(player, level, stack, hand, hit);
	}

	/** {@code ServerPlayerGameMode.useItem} — Bukkit {@code RIGHT_CLICK_AIR}. */
	public static InteractionResult useItem(ServerPlayer player, Level level, ItemStack stack,
			InteractionHand hand) {
		Sink installed = sink;
		return installed == null ? null : installed.useItem(player, level, stack, hand);
	}

	/** {@code ServerPlayerGameMode.handleBlockBreakAction} — Bukkit {@code LEFT_CLICK_BLOCK}. */
	public static boolean leftClickBlock(ServerPlayer player, BlockPos pos, Direction face) {
		Sink installed = sink;
		return installed != null && installed.leftClickBlock(player, pos, face);
	}

	/** {@code ServerGamePacketListenerImpl.handleAnimate} — Bukkit {@code LEFT_CLICK_AIR}. */
	public static boolean swing(ServerPlayer player) {
		Sink installed = sink;
		return installed != null && installed.swing(player);
	}

	/** {@code ServerGamePacketListenerImpl.handleUseItem} -- Bukkit {@code RIGHT_CLICK_AIR}. */
	public static boolean useItemPacket(ServerPlayer player, ServerboundUseItemPacket packet) {
		Sink installed = sink;
		return installed != null && installed.useItemPacket(player, packet.getHand());
	}
}
