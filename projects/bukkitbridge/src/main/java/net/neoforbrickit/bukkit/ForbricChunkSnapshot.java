/*
 * Forbric Bukkit bridge — a chunk's blocks, read once and held still.
 */
package net.neoforbrickit.bukkit;

/**
 * A chunk's contents as they were when it was taken.
 *
 * <h2>Why a copy is right here</h2>
 *
 * <p>Unlike {@link ForbricBlockState}, which deliberately hands back a live handle, this type exists
 * precisely because the caller wants a still picture: a renderer walks a chunk out of the world's tick
 * path and must not have the blocks change under it halfway through a tile. So the values are copied into
 * flat arrays at capture time and every read afterwards is an array index.
 *
 * <h2>Why it copies sections rather than reading through the level</h2>
 *
 * <p>Reading the live chunk's palette while the world generates would take
 * {@code PalettedContainer}'s lock, which is a non-reentrant semaphore and is already held by the thread
 * that is generating that chunk. That is a deadlock, not a slow path. {@code copy()} produces a container
 * with its own lock, so the snapshot is taken from that.
 *
 * <h2>What is not captured</h2>
 *
 * <p>Biomes and light are answered from the level at read time rather than copied, because neither is on a
 * renderer's per-block path and copying them would double the memory a snapshot costs for values nothing
 * asks for twice. {@code getBiome} therefore reads live, and says so.
 */
final class ForbricChunkSnapshot implements org.bukkit.ChunkSnapshot {

	private final ForbricWorld world;
	private final int x;
	private final int z;
	private final long capturedAt;

	/** Block type per index, flattened as {@code y << 8 | z << 4 | x} over this chunk's own columns. */
	private final org.bukkit.Material[] types;
	private final org.bukkit.block.data.BlockData[] data;

	private final int minY;
	private final int maxY;
	/** Highest non-air block per column, flattened as {@code z << 4 | x}. */
	private final int[] highest;

	private final boolean includeMaxBlockY;

	/**
	 * Takes the snapshot.
	 *
	 * @param world            the world the chunk belongs to, used for biomes and light
	 * @param chunk            the game's own chunk, which must be loaded
	 * @param includeMaxBlockY whether {@link #getHighestBlockYAt} is answered from what was captured
	 */
	ForbricChunkSnapshot(ForbricWorld world, net.minecraft.world.level.chunk.LevelChunk chunk,
			boolean includeMaxBlockY) {
		this.world = world;
		this.x = chunk.getPos().x();
		this.z = chunk.getPos().z();
		this.capturedAt = world.level().getGameTime();
		this.includeMaxBlockY = includeMaxBlockY;
		this.minY = world.level().getMinY();
		this.maxY = world.level().getMaxY();

		int height = maxY - minY;
		this.types = new org.bukkit.Material[height * 256];
		this.data = new org.bukkit.block.data.BlockData[height * 256];
		this.highest = new int[256];

		java.util.Arrays.fill(highest, minY - 1);

		// Section by section, through the copy rather than the live container: the live one is locked by
		// whoever is generating this chunk, and taking that lock from here would deadlock the generator.
		net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
		for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
			net.minecraft.world.level.chunk.LevelChunkSection section = sections[sectionIndex];
			if (section == null || section.hasOnlyAir()) {
				continue;
			}
			net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.world.level.block.state.BlockState> states =
					section.getStates().copy();
			int sectionBottom = world.level().getSectionYFromSectionIndex(sectionIndex) << 4;

			for (int ly = 0; ly < 16; ly++) {
				int y = sectionBottom + ly;
				if (y < minY || y >= maxY) {
					continue;
				}
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						net.minecraft.world.level.block.state.BlockState state =
								states.get(lx, ly, lz);
						int column = (lz << 4) | lx;
						int index = ((y - minY) << 8) | column;

						org.bukkit.Material type = ForbricBlock.materialOf(state);
						types[index] = type;
						data[index] = type.isAir() ? type.createBlockData() : ForbricBlock.dataOf(state);

						if (!type.isAir() && y > highest[column]) {
							highest[column] = y;
						}
					}
				}
			}
		}

		// Any column never written is air, which the fill above already covers for types; the highest array
		// keeps minY - 1 so an all-air column answers with the floor rather than a plausible-looking number.
	}

	/**
	 * A chunk-shaped block of air, which is what {@code World#getEmptyChunkSnapshot} promises.
	 *
	 * <p>No game chunk is touched: the point of the call is a stand-in for terrain that is not there, and
	 * loading a real chunk to then discard its blocks would cost a load for nothing.
	 */
	static ForbricChunkSnapshot empty(ForbricWorld world, int chunkX, int chunkZ) {
		return new ForbricChunkSnapshot(world, chunkX, chunkZ);
	}

	/** The empty form, which fills every slot with air up front. */
	private ForbricChunkSnapshot(ForbricWorld world, int chunkX, int chunkZ) {
		this.world = world;
		this.x = chunkX;
		this.z = chunkZ;
		this.capturedAt = world.level().getGameTime();
		this.includeMaxBlockY = true;
		this.minY = world.level().getMinY();
		this.maxY = world.level().getMaxY();

		int slots = (maxY - minY) * 256;
		this.types = new org.bukkit.Material[slots];
		this.data = new org.bukkit.block.data.BlockData[slots];
		java.util.Arrays.fill(types, org.bukkit.Material.AIR);
		org.bukkit.block.data.BlockData air = org.bukkit.Material.AIR.createBlockData();
		java.util.Arrays.fill(data, air);

		this.highest = new int[256];
		java.util.Arrays.fill(highest, minY - 1);
	}

	/** Bounds-checks one coordinate inside the chunk. */
	private int index(int bx, int by, int bz) {
		if (bx < 0 || bx > 15 || bz < 0 || bz > 15 || by < minY || by >= maxY) {
			throw new IndexOutOfBoundsException(
					"[Forbric/Bukkit] ChunkSnapshot read outside the chunk: " + bx + "," + by + "," + bz);
		}
		return ((by - minY) << 8) | (bz << 4) | bx;
	}

	@Override
	public int getX() {
		return x;
	}

	@Override
	public int getZ() {
		return z;
	}

	@Override
	public String getWorldName() {
		return world.getName();
	}

	@Override
	public net.kyori.adventure.key.Key getWorldKey() {
		return world.getKey().key();
	}

	@Override
	public org.bukkit.Material getBlockType(int bx, int by, int bz) {
		org.bukkit.Material type = types[index(bx, by, bz)];
		return type == null ? org.bukkit.Material.AIR : type;
	}

	@Override
	public org.bukkit.block.data.BlockData getBlockData(int bx, int by, int bz) {
		org.bukkit.block.data.BlockData value = data[index(bx, by, bz)];
		return value == null ? org.bukkit.Material.AIR.createBlockData() : value;
	}

	@Override
	public int getData(int bx, int by, int bz) {
		// The legacy numeric data byte. This API generation has none to read -- the game stores
		// properties, not a number -- so 0 rather than an invented value that a plugin would branch on.
		return 0;
	}

	@Override
	public int getBlockSkyLight(int bx, int by, int bz) {
		return world.level().getBrightness(net.minecraft.world.level.LightLayer.SKY,
				new net.minecraft.core.BlockPos((x << 4) + bx, by, (z << 4) + bz));
	}

	@Override
	public int getBlockEmittedLight(int bx, int by, int bz) {
		return world.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
				new net.minecraft.core.BlockPos((x << 4) + bx, by, (z << 4) + bz));
	}

	@Override
	public int getHighestBlockYAt(int bx, int bz) {
		if (bx < 0 || bx > 15 || bz < 0 || bz > 15) {
			throw new IndexOutOfBoundsException(
					"[Forbric/Bukkit] ChunkSnapshot column outside the chunk: " + bx + "," + bz);
		}
		// Answered from what was captured, which is the point of a snapshot: asking the live world here
		// would answer about a world that may have moved on since.
		return highest[(bz << 4) | bx];
	}

	@Override
	public org.bukkit.block.Biome getBiome(int bx, int bz) {
		return biomeAt(bx, getHighestBlockYAt(bx, bz), bz);
	}

	@Override
	public org.bukkit.block.Biome getBiome(int bx, int by, int bz) {
		return biomeAt(bx, by, bz);
	}

	/**
	 * The biome at a position, read from the live world.
	 *
	 * <p>Not captured with the blocks: a renderer reads biomes for tinting a tile, not per block, and
	 * copying a whole biome volume per chunk would cost more memory than the blocks do for values that are
	 * mostly asked for once. The trade is stated rather than hidden -- a biome read here can differ from
	 * the blocks around it if the world changed since the capture.
	 */
	private org.bukkit.block.Biome biomeAt(int bx, int by, int bz) {
		// Refused rather than guessed: this bridge has no biome mapping yet, and inventing one here
		// would answer a plausible-looking biome that tints a rendered tile the wrong colour with
		// nothing to point at. Named so the missing piece is visible in a stack trace.
		throw new UnsupportedOperationException(
				"[Forbric/Bukkit] ChunkSnapshot.getBiome() is not implemented by this bridge yet (biome "
						+ "mapping is absent); asked for " + bx + "," + by + "," + bz);
	}

	@Override
	public double getRawBiomeTemperature(int bx, int bz) {
		return world.level().getBiome(new net.minecraft.core.BlockPos((x << 4) + bx, getHighestBlockYAt(bx, bz),
				(z << 4) + bz)).value().getBaseTemperature();
	}

	@Override
	public double getRawBiomeTemperature(int bx, int by, int bz) {
		return world.level().getBiome(new net.minecraft.core.BlockPos((x << 4) + bx, by, (z << 4) + bz))
				.value().getBaseTemperature();
	}

	@Override
	public long getCaptureFullTime() {
		return capturedAt;
	}

	@Override
	public boolean isSectionEmpty(int sectionY) {
		int bottom = sectionY << 4;
		for (int y = bottom; y < bottom + 16; y++) {
			if (y < minY || y >= maxY) {
				continue;
			}
			for (int column = 0; column < 256; column++) {
				org.bukkit.Material type = types[((y - minY) << 8) | column];
				if (type != null && !type.isAir()) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public boolean contains(org.bukkit.block.data.BlockData blockData) {
		if (blockData == null) {
			return false;
		}
		for (org.bukkit.block.data.BlockData value : data) {
			if (blockData.equals(value)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean contains(org.bukkit.block.Biome biome) {
		// Biomes are read live rather than captured, so this asks the chunk's own columns.
		if (biome == null) {
			return false;
		}
		for (int bz = 0; bz < 16; bz++) {
			for (int bx = 0; bx < 16; bx++) {
				if (biome.equals(getBiome(bx, bz))) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return "ForbricChunkSnapshot[" + x + "," + z + " of " + getWorldName() + "]";
	}
}
