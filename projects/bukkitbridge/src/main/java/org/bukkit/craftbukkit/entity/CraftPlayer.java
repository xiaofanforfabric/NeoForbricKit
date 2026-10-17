/*
 * Forbric Bukkit bridge — the {@code org.bukkit.craftbukkit.entity.CraftPlayer} that plugins
 * reach for by name.
 */
package org.bukkit.craftbukkit.entity;

import net.minecraft.server.level.ServerPlayer;

/**
 * The handle-holding player base, under the upstream name.
 *
 * <p>{@link CraftEntity} explains why these upstream-named classes exist. A player is the one
 * most commonly reached for — a plugin that wants to send a packet, read the connection, or
 * touch anything the Bukkit {@code Player} interface does not expose casts to this name and
 * calls {@link #getHandle()}. So this name has to resolve, and the object has to be of this
 * lineage; the bridge's own {@code ForbricPlayer} descends from it.
 *
 * <p>Upstream this class also implements {@code org.bukkit.entity.Player}. Here it does not,
 * for the reason given in {@code CraftEntity}: the implementation that descends from it
 * already implements the interface, and repeating it would make every one of that
 * implementation's bodies abstract again.
 *
 * <p>A player is a human entity upstream, and the chain is kept because callers walk it:
 * {@code CraftHumanEntity} carries the {@code perm} field a permission plugin replaces, and a
 * lookup for it stops here rather than at {@code CraftEntity}.
 */
public abstract class CraftPlayer extends CraftHumanEntity {

	protected CraftPlayer(ServerPlayer handle) {
		super(handle);
	}

	/** The game's own player, narrowed to the type a plugin will actually use. */
	@Override
	public ServerPlayer getHandle() {
		return (ServerPlayer) super.getHandle();
	}

	/**
	 * Registers a plugin channel for this player.
	 *
	 * <p>Declared here, and abstract, because it is found by NAME on this class: a plugin looks up
	 * {@code addChannel} on the CraftPlayer class it resolved, so the method has to exist in the
	 * type hierarchy the lookup walks. The bridges's own player implements it; this class only has
	 * to promise it.
	 */
	public abstract void addChannel(String channel);

	/** The counterpart of {@link #addChannel}; found the same way, from the same lookup. */
	public abstract void removeChannel(String channel);
}
