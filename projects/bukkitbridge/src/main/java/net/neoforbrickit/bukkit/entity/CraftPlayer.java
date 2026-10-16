/*
 * Forbric Bukkit bridge — the CraftBukkit-shaped player name, in the package Simple Voice
 * Chat derives from the server's own class.
 */
package net.neoforbrickit.bukkit.entity;

import net.minecraft.server.level.ServerPlayer;

/**
 * The CraftBukkit-shaped player name, in the package Simple Voice Chat derives from the
 * server's own class.
 *
 * <p>See {@link CraftEntity} for why this class is here. A player is what a voice
 * compatibility layer ends up holding — it sends packets to one — so {@link #getHandle()} is
 * the method it reaches for, and it returns the game's own {@code ServerPlayer}.
 */
public abstract class CraftPlayer extends org.bukkit.craftbukkit.entity.CraftPlayer {

	protected CraftPlayer(ServerPlayer handle) {
		super(handle);
	}

	/** The game's own player, which is what a compatibility layer sends through. */
	@Override
	public ServerPlayer getHandle() {
		return super.getHandle();
	}

	@Override
	public String toString() {
		return "CraftPlayer[" + getHandle().getName().getString() + "]";
	}
}
