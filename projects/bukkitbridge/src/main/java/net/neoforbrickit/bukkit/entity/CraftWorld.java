/*
 * Forbric Bukkit bridge — the CraftBukkit-shaped world name, in the package Simple Voice
 * Chat derives from the server's own class.
 */
package net.neoforbrickit.bukkit.entity;

import net.minecraft.server.level.ServerLevel;

/**
 * The CraftBukkit-shaped world name, in the package a plugin derives from the server's own
 * class.
 *
 * <p>See {@link CraftEntity} for the mechanism. A world is reached for by resource-pack and
 * map plugins; this spelling is the one a derived package lookup finds, while plugins that
 * hardcode {@code org.bukkit.craftbukkit.CraftWorld} find the upstream-named base this
 * descends from.
 */
public abstract class CraftWorld extends org.bukkit.craftbukkit.CraftWorld {

	protected CraftWorld(ServerLevel handle) {
		super(handle);
	}

	/** The game's own level. */
	@Override
	public ServerLevel getHandle() {
		return (ServerLevel) super.getHandle();
	}
}
