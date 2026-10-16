/*
 * Forbric Bukkit bridge — the CraftBukkit-shaped entity name, in the package Simple Voice
 * Chat derives from the server's own class.
 */
package net.neoforbrickit.bukkit.entity;

import net.minecraft.world.entity.Entity;

/**
 * The CraftBukkit-shaped entity name, in the package Simple Voice Chat derives from the
 * server's own class.
 *
 * <p>SVC computes the package as {@code Bukkit.getServer().getClass().getPackage().getName()}
 * and then looks up {@code <that>.entity.CraftPlayer}. This bridge's server class lives in
 * {@code net.neoforbrickit.bukkit}, so the name it looks for is this one, while plugins that
 * hardcode the upstream name find {@link org.bukkit.craftbukkit.entity.CraftEntity}. Both
 * spellings have to resolve, so this class gives the derived spelling a home — and it descends
 * from the upstream-named base, so an object of either lineage casts to the other's.
 *
 * <p>{@link #getHandle()} is the whole point: it returns the game's own entity, not a
 * placeholder.
 */
public abstract class CraftEntity extends org.bukkit.craftbukkit.entity.CraftEntity {

	protected CraftEntity(Entity handle) {
		super(handle);
	}

	/** The game's own entity, which is what a compatibility layer operates on. */
	@Override
	public Entity getHandle() {
		return super.getHandle();
	}
}
