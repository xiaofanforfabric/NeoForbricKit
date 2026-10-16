/*
 * Forbric Bukkit bridge — the {@code org.bukkit.craftbukkit.entity.CraftEntity} that plugins
 * reach for by name.
 *
 * <p><b>Why this class exists.</b> A plugin is supposed to talk only to {@code org.bukkit.*},
 * which is a set of interfaces with no bodies. The object it is handed at run time is one of
 * this bridge's {@code Forbric*} classes. That is fine until the plugin decides it needs the
 * game's own object: it then asks for CraftBukkit by name —
 *
 * <pre>
 * Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer")
 * </pre>
 *
 * <p>— and casts what it has to that type. Our implementations were named {@code Forbric*},
 * so the lookup failed and the cast threw {@code ClassCastException}. This class fixes both
 * halves: it carries the upstream name, and the real implementations descend from it, so the
 * cast succeeds.
 *
 * <p><b>It is not a stub.</b> {@link #getHandle()} returns the game's own entity, the very
 * object the plugin asked for; there is no placeholder and nothing throws from here.
 */
package org.bukkit.craftbukkit.entity;

/**
 * The handle-holding base every bridged entity descends from.
 *
 * <p>Upstream this class also implements {@code org.bukkit.entity.Entity}. Here it does not,
 * deliberately: the implementations that descend from it (see {@code ForbricEntity}) already
 * implement the interfaces, and repeating them would make every one of their bodies abstract
 * again. What a plugin needs from this name is the cast and {@link #getHandle()}, and both
 * work.
 */
public abstract class CraftEntity {

	private final net.minecraft.world.entity.Entity handle;

	protected CraftEntity(net.minecraft.world.entity.Entity handle) {
		this.handle = handle;
	}

	/**
	 * The game's own entity.
	 *
	 * <p>Returned as {@code net.minecraft.world.entity.Entity} rather than the general
	 * {@code Object} upstream uses, because a plugin that reaches for this is about to use it.
	 */
	public net.minecraft.world.entity.Entity getHandle() {
		return handle;
	}

	@Override
	public String toString() {
		return "CraftEntity[" + handle + "]";
	}
}
