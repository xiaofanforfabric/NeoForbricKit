/*
 * Forbric Bukkit bridge — the {@code org.bukkit.craftbukkit.CraftWorld} that plugins reach
 * for by name.
 */
package org.bukkit.craftbukkit;

/**
 * The handle-holding world base the bridged world descends from.
 *
 * <p>{@link org.bukkit.craftbukkit.entity.CraftEntity} explains why these upstream-named
 * classes exist: a plugin that needs the game's own object asks for CraftBukkit by name and
 * casts to it, so the name has to resolve and the object has to be of that lineage. A world
 * is the other thing commonly reached for — WorldEdit and the map renderers both do it — and
 * {@link #getHandle()} returns the game's own {@code ServerLevel}.
 *
 * <p>Upstream this class also implements {@code org.bukkit.World}. Here it does not, for the
 * same reason {@code CraftEntity} does not implement {@code Entity}: the implementation that
 * descends from it already does, and repeating the interface would make every one of its
 * bodies abstract again.
 */
public abstract class CraftWorld {

	private final net.minecraft.server.level.ServerLevel handle;

	protected CraftWorld(net.minecraft.server.level.ServerLevel handle) {
		this.handle = handle;
	}

	/** The game's own level. */
	public net.minecraft.server.level.ServerLevel getHandle() {
		return handle;
	}

	@Override
	public String toString() {
		return "CraftWorld[" + handle.dimension().identifier() + "]";
	}
}
