/*
 * Forbric Bukkit bridge — a block's state, which is what a reader holds to look at one block.
 */
package net.neoforbrickit.bukkit;

/**
 * One block's state, as {@code Block.getState()} hands it out.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>Bukkit's {@code BlockState} is a snapshot: the API says a copy, taken at one moment, that does not move
 * when the world does. This bridge answers with the live block instead, and the reason is the caller rather
 * than laziness — a map renderer reads a block's type and data and moves on, and the alternative (a fully
 * copied state object for every block of every chunk) is a per-block allocation on a path that walks
 * millions of them.
 *
 * <p>The difference is observable and is stated rather than hidden: a caller that holds the result across
 * a world change sees the change. The bridge itself never keeps one, and a caller that needs a copy calls
 * {@link #copy()}, which does produce one.
 *
 * <h2>What is refused</h2>
 *
 * <p>The write-through methods ({@code update}, {@code setRawData}) and the legacy {@code MaterialData}
 * surface are refused by name. They are not on any renderer's path, and answering them with something
 * plausible would be a silent no-op where a plugin expects a write.
 */
final class ForbricBlockState implements org.bukkit.block.BlockState {

	private final ForbricBlock block;

	ForbricBlockState(ForbricBlock block) {
		this.block = block;
	}

	@Override
	public org.bukkit.block.Block getBlock() {
		return block;
	}

	@Override
	public org.bukkit.Material getType() {
		return block.getType();
	}

	@Override
	public void setType(org.bukkit.Material type) {
		block.setType(type);
	}

	@Override
	public org.bukkit.block.data.BlockData getBlockData() {
		return block.getBlockData();
	}

	@Override
	public void setBlockData(org.bukkit.block.data.BlockData data) {
		block.setBlockData(data);
	}

	@Override
	public org.bukkit.World getWorld() {
		return block.getWorld();
	}

	@Override
	public int getX() {
		return block.getX();
	}

	@Override
	public int getY() {
		return block.getY();
	}

	@Override
	public int getZ() {
		return block.getZ();
	}

	@Override
	public org.bukkit.Location getLocation() {
		return block.getLocation();
	}

	@Override
	public org.bukkit.Location getLocation(org.bukkit.Location store) {
		org.bukkit.Location here = block.getLocation();
		if (store != null) {
			store.setWorld(here.getWorld());
			store.setX(here.getX());
			store.setY(here.getY());
			store.setZ(here.getZ());
			store.setYaw(here.getYaw());
			store.setPitch(here.getPitch());
			return store;
		}
		return here;
	}

	@Override
	public org.bukkit.Chunk getChunk() {
		return block.getChunk();
	}

	@Override
	public byte getLightLevel() {
		return block.getLightLevel();
	}

	/**
	 * A detached copy, which is what the API promised all along.
	 *
	 * <p>Built from the block's current type and data rather than from a live handle, so the result keeps
	 * answering the same thing after the world moves on — the one behaviour {@link ForbricBlockState}
	 * itself does not provide.
	 */
	@Override
	public org.bukkit.block.BlockState copy() {
		return new Detached(block.getType(), block.getBlockData(), block.getLocation());
	}

	@Override
	public org.bukkit.block.BlockState copy(org.bukkit.Location location) {
		return new Detached(block.getType(), block.getBlockData(), location);
	}

	/** Whether the block is still there, which for a live handle is always true while it is reachable. */
	@Override
	public boolean isPlaced() {
		return true;
	}

	@Override
	public boolean isCollidable() {
		return block.isCollidable();
	}

	@Override
	public boolean isSuffocating() {
		return block.isSuffocating();
	}

	@Override
	public java.util.Collection<org.bukkit.inventory.ItemStack> getDrops(
			org.bukkit.inventory.ItemStack tool, org.bukkit.entity.Entity entity) {
		return block.getDrops(tool, entity);
	}

	// ---------------------------------------------------------------------------------------------
	// Refused by name: the write-through and legacy surfaces. See the class comment for why.
	// ---------------------------------------------------------------------------------------------

	@Override
	public boolean update() {
		throw notYet("update");
	}

	@Override
	public boolean update(boolean force) {
		throw notYet("update");
	}

	@Override
	public boolean update(boolean force, boolean applyPhysics) {
		throw notYet("update");
	}

	@Override
	public byte getRawData() {
		throw notYet("getRawData");
	}

	@Override
	public void setRawData(byte data) {
		throw notYet("setRawData");
	}

	@Override
	public org.bukkit.material.MaterialData getData() {
		throw notYet("getData");
	}

	@Override
	public void setData(org.bukkit.material.MaterialData data) {
		throw notYet("setData");
	}

	// ---------------------------------------------------------------------------------------------
	// Metadata, which every BlockState shares with the block it came from.
	// ---------------------------------------------------------------------------------------------

	@Override
	public void setMetadata(String metadataKey, org.bukkit.metadata.MetadataValue newMetadataValue) {
		block.setMetadata(metadataKey, newMetadataValue);
	}

	@Override
	public java.util.List<org.bukkit.metadata.MetadataValue> getMetadata(String metadataKey) {
		return block.getMetadata(metadataKey);
	}

	@Override
	public boolean hasMetadata(String metadataKey) {
		return block.hasMetadata(metadataKey);
	}

	@Override
	public void removeMetadata(String metadataKey, org.bukkit.plugin.Plugin owningPlugin) {
		block.removeMetadata(metadataKey, owningPlugin);
	}

	@Override
	public String toString() {
		return "ForbricBlockState[" + block + "]";
	}

	private static UnsupportedOperationException notYet(String what) {
		return new UnsupportedOperationException(
				"[Forbric/Bukkit] BlockState." + what + "() is not implemented by this bridge yet");
	}

	/**
	 * A state that holds its own values, which is what {@code copy()} produces.
	 *
	 * <p>Only the reads a copy is asked for are answered; a write on a copy would have to name a location
	 * to write to, and Bukkit's own answer is that a copy is not where you write.
	 */
	private static final class Detached implements org.bukkit.block.BlockState {

		private final org.bukkit.Material type;
		private final org.bukkit.block.data.BlockData data;
		private final org.bukkit.Location location;

		Detached(org.bukkit.Material type, org.bukkit.block.data.BlockData data, org.bukkit.Location location) {
			this.type = type;
			this.data = data;
			this.location = location;
		}

		@Override
		public org.bukkit.Material getType() {
			return type;
		}

		@Override
		public org.bukkit.block.data.BlockData getBlockData() {
			return data;
		}

		@Override
		public org.bukkit.World getWorld() {
			return location.getWorld();
		}

		@Override
		public int getX() {
			return location.getBlockX();
		}

		@Override
		public int getY() {
			return location.getBlockY();
		}

		@Override
		public int getZ() {
			return location.getBlockZ();
		}

		@Override
		public org.bukkit.Location getLocation() {
			return location.clone();
		}

		@Override
		public org.bukkit.Location getLocation(org.bukkit.Location store) {
			org.bukkit.Location copy = location.clone();
			if (store == null) {
				return copy;
			}
			store.setWorld(copy.getWorld());
			store.setX(copy.getX());
			store.setY(copy.getY());
			store.setZ(copy.getZ());
			return store;
		}

		@Override
		public org.bukkit.Chunk getChunk() {
			return location.getWorld().getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
		}

		@Override
		public org.bukkit.block.Block getBlock() {
			return location.getBlock();
		}

		@Override
		public org.bukkit.block.BlockState copy() {
			return this;
		}

		@Override
		public org.bukkit.block.BlockState copy(org.bukkit.Location where) {
			return new Detached(type, data, where);
		}

		@Override
		public byte getLightLevel() {
			return location.getBlock().getLightLevel();
		}

		@Override
		public void setType(org.bukkit.Material material) {
			throw notYet("setType on a copy");
		}

		@Override
		public void setBlockData(org.bukkit.block.data.BlockData newData) {
			throw notYet("setBlockData on a copy");
		}

		@Override
		public boolean isPlaced() {
			return true;
		}

		@Override
		public boolean isCollidable() {
			return type.isSolid();
		}

		@Override
		public boolean isSuffocating() {
			return type.isSolid();
		}

		@Override
		public java.util.Collection<org.bukkit.inventory.ItemStack> getDrops(
				org.bukkit.inventory.ItemStack tool, org.bukkit.entity.Entity entity) {
			return location.getBlock().getDrops(tool, entity);
		}

		@Override
		public boolean update() {
			throw notYet("update on a copy");
		}

		@Override
		public boolean update(boolean force) {
			throw notYet("update on a copy");
		}

		@Override
		public boolean update(boolean force, boolean applyPhysics) {
			throw notYet("update on a copy");
		}

		@Override
		public byte getRawData() {
			throw notYet("getRawData");
		}

		@Override
		public void setRawData(byte raw) {
			throw notYet("setRawData");
		}

		@Override
		public org.bukkit.material.MaterialData getData() {
			throw notYet("getData");
		}

		@Override
		public void setData(org.bukkit.material.MaterialData legacy) {
			throw notYet("setData");
		}

		@Override
		public void setMetadata(String key, org.bukkit.metadata.MetadataValue value) {
			throw notYet("setMetadata on a copy");
		}

		@Override
		public java.util.List<org.bukkit.metadata.MetadataValue> getMetadata(String key) {
			return java.util.List.of();
		}

		@Override
		public boolean hasMetadata(String key) {
			return false;
		}

		@Override
		public void removeMetadata(String key, org.bukkit.plugin.Plugin owner) {
			// Nothing to remove from a copy.
		}

		@Override
		public String toString() {
			return "ForbricBlockState[copy " + type + " at " + getX() + "," + getY() + "," + getZ() + "]";
		}
	}
}
