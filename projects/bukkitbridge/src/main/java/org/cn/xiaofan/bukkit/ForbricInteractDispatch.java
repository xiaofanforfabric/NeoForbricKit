/*
 * Forbric Bukkit bridge — the one place a game interaction becomes a Bukkit event.
 *
 * <p>This is the {@link ForbricInteractHook.Sink} the bridge installs when it starts. The kernel's
 * transformer injects the game-side calls (see {@code net.forbric.kernel.transform.BukkitInteractInjector}),
 * so a plugin's {@code @EventHandler} hears an interaction because the GAME raised it, at the game's own
 * call site — not because something subscribed to a loader's bus and relayed it.
 *
 * <p><b>What that buys.</b> A relay through an ecosystem's event bus lives exactly as long as that
 * ecosystem's hooks survive in the merged base; the game's own methods are the one thing this project
 * cannot lose. No NeoForge type appears anywhere in this package.
 *
 * <h2>Which game method raises which Bukkit action</h2>
 *
 * <table>
 *   <caption>Action mapping</caption>
 *   <tr><th>Bukkit {@code Action}</th><th>Game call site</th></tr>
 *   <tr><td>{@code RIGHT_CLICK_BLOCK}</td><td>{@code ServerPlayerGameMode.useItemOn}</td></tr>
 *   <tr><td>{@code RIGHT_CLICK_AIR}</td><td>{@code ServerPlayerGameMode.useItem}</td></tr>
 *   <tr><td>{@code LEFT_CLICK_BLOCK}</td><td>{@code ServerPlayerGameMode.handleBlockBreakAction}, on {@code START_DESTROY_BLOCK}</td></tr>
 *   <tr><td>{@code LEFT_CLICK_AIR}</td><td>{@code ServerGamePacketListenerImpl.handleAnimate}</td></tr>
 * </table>
 *
 * <h2>Cancelling</h2>
 *
 * <p>The two right-click paths answer with {@code InteractionResult.FAIL}, which is what the game's own
 * caller reads as "this interaction did not happen" — the same answer CraftBukkit gives a cancelled
 * {@code PlayerInteractEvent}. The two left-click paths answer by returning from the game method before
 * any break starts. Bukkit's tri-state is honoured in the reading direction too:
 * {@code useInteractedBlock()}/{@code useItemInHand()} of {@code DENY} suppresses the action, so a plugin
 * that refuses one half of the interaction gets the same treatment as one that cancels the event.
 *
 * <p>Nothing here throws at a plugin: a listener that fails is reported once and the game proceeds, the
 * rule every forward in this bridge follows.
 */
package org.cn.xiaofan.bukkit;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

final class ForbricInteractDispatch implements ForbricInteractHook.Sink {

	private final ServerImpl server;
	private final ForbricPluginManager manager;
	/** One warning per launch, like every other forward: a throwing listener must not bury the log. */
	private final AtomicBoolean warned = new AtomicBoolean();

	ForbricInteractDispatch(ServerImpl server) {
		this.server = server;
		org.bukkit.plugin.PluginManager installed = server.getPluginManager();
		this.manager = installed instanceof ForbricPluginManager plugins ? plugins : null;
	}

	@Override
	public InteractionResult useItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
			BlockHitResult hit) {
		// Right-clicking a block: the block and its face are what the event carries, and the item is the
		// one the game is about to use.
		boolean refused = raise(Action.RIGHT_CLICK_BLOCK, player, level, stack, hand,
				hit == null ? null : hit.getBlockPos(), hit == null ? null : hit.getDirection());
		return refused ? InteractionResult.FAIL : null;
	}

	@Override
	public InteractionResult useItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand) {
		// Right-clicking air (or an item with no block): no block, no face.
		boolean refused = raise(Action.RIGHT_CLICK_AIR, player, level, stack, hand, null, null);
		return refused ? InteractionResult.FAIL : null;
	}

	@Override
	public boolean leftClickBlock(ServerPlayer player, BlockPos pos, Direction face) {
		// Bukkit hands the event the stack the player is holding, so the live hand is read here rather
		// than a copy the game method does not have.
		return raise(Action.LEFT_CLICK_BLOCK, player, player.level(), null, InteractionHand.MAIN_HAND, pos, face);
	}

	@Override
	public boolean swing(ServerPlayer player) {
		// LEFT_CLICK_AIR is the one action the game has no server-side event for: the swing IS the
		// interaction, so whether it is "refused" is the plugin's business, not the game's.
		return raise(Action.LEFT_CLICK_AIR, player, player.level(), null, InteractionHand.MAIN_HAND, null, null);
	}

	@Override
	public boolean useItemPacket(ServerPlayer player, InteractionHand hand) {
		return raise(Action.RIGHT_CLICK_AIR, player, player.level(), null, hand, null, null);
	}

	/**
	 * Raises one {@code PlayerInteractEvent} and reports whether the game must not perform the action.
	 *
	 * @param held  the stack the game is using, or null to read the player's own hand
	 * @param pos   the block the interaction addresses, or null for an air action
	 * @param face  the face that was clicked, or null for an air action
	 */
	private boolean raise(Action action, ServerPlayer player, Level level, ItemStack held, InteractionHand hand,
			BlockPos pos, Direction face) {
		if (manager == null) return false;
		// Diagnostic for "the event never arrives" reports, which are otherwise indistinguishable from "the
		// game never called this site". Off by default: it is one line per interaction, which is noise on a
		// busy server. -Dforbric.debug turns it on. The line says which SITE fired, so a missing RIGHT_CLICK_AIR
		// is answered by its absence here rather than guessed at from the plugin's own logging.
		if (Boolean.getBoolean("forbric.debug")) {
			server.getLogger().info("[Forbric/Bukkit] interact site raised: action=" + action
					+ " hand=" + hand + " held=" + (held == null ? "<null>" : held.getItem())
					+ " pos=" + pos + " face=" + face);
		}
		try {
			org.bukkit.entity.Player bukkitPlayer = ForbricPlayer.of(player, server);
			// The live hand stack, not a copy, so a plugin that mutates what it is handed mutates what
			// the player is holding.
			ItemStack inHand = held == null || held.isEmpty() ? player.getItemInHand(hand) : held;
			org.bukkit.inventory.ItemStack item = inHand.isEmpty() ? null : ForbricItemStack.of(inHand);
			Block block = pos == null ? null : blockAt(level, pos);
			BlockFace blockFace = face == null ? null : ForbricWorld.blockFace(face);
			EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;

			org.bukkit.event.player.PlayerInteractEvent event =
					new org.bukkit.event.player.PlayerInteractEvent(bukkitPlayer, action, item, block, blockFace, slot);
			manager.callEvent(event);

			return event.isCancelled()
					|| event.useInteractedBlock() == Event.Result.DENY
					|| event.useItemInHand() == Event.Result.DENY;
		} catch (Throwable failure) {
			// A plugin throwing inside callEvent must not take the server tick down with it, and
			// repeating the message on every right-click would bury it.
			if (warned.compareAndSet(false, true)) {
				server.getLogger().warning("[Forbric/Bukkit] PlayerInteractEvent could not be raised; plugins will "
						+ "not observe interactions - " + failure);
			}
			return false;
		}
	}

	/** A block at a game position, wrapped so it can be written back to the same world. */
	private Block blockAt(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) return null;
		return ForbricBlock.at(ForbricWorld.of(serverLevel, server), pos.getX(), pos.getY(), pos.getZ());
	}
}
