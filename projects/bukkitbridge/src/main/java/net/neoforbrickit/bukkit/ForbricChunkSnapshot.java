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
 * <h2>Two ways to fill one</h2>
 *
 * <p>From a chunk in memory (the constructor), or from the chunk's region file
 * ({@link #read(ForbricWorld, int, int)}). The second is the one a renderer takes: it never loads the chunk,
 * so it cannot hang the thread that is asking. What a file does not carry -- light, packed there in a layout
 * this bridge does not decode -- stays at zero and is documented rather than approximated.
 *
 * <h2>What is not captured</h2>
 *
 * <p>Biomes. They are not on a renderer's per-block path, and copying a whole biome volume per chunk would
 * cost more than the blocks do for values that are mostly read once. {@code getBiome} therefore refuses, and
 * says so by name.
 */
final class ForbricChunkSnapshot implements org.bukkit.ChunkSnapshot {

	private final ForbricWorld world;
	private final int x;
	private final int z;
	private final long capturedAt;

	/** Block type per index, flattened as {@code y << 8 | z << 4 | x} over this chunk's own columns. */
	private final org.bukkit.Material[] types;
	private final org.bukkit.block.data.BlockData[] data;

	/** Sky light per index, same flattening as {@link #types}. */
	private final byte[] skyLight;
	/** Block light per index, same flattening as {@link #types}. */
	private final byte[] emittedLight;

	private final int minY;
	private final int maxY;
	/** Highest non-air block per column, flattened as {@code z << 4 | x}. */
	private final int[] highest;

	private final boolean includeMaxBlockY;

	/**
	 * Takes the snapshot.
	 *
	 * @param world            the world the chunk belongs to, used for biomes
	 * @param chunk            the game's own chunk, which must be in memory
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
		this.skyLight = new byte[height * 256];
		this.emittedLight = new byte[height * 256];
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
			// Light is copied with the blocks, from the chunk's own light engine: it is part of the same
			// moment, and it is what a renderer shades with. Asking the level for brightness instead would
			// read the live chunk that this snapshot exists to stop reading from.
			net.minecraft.world.level.chunk.DataLayer sky = lightLayer(chunk, sectionIndex,
					net.minecraft.world.level.LightLayer.SKY);
			net.minecraft.world.level.chunk.DataLayer block = lightLayer(chunk, sectionIndex,
					net.minecraft.world.level.LightLayer.BLOCK);
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

						if (sky != null) {
							skyLight[index] = (byte) sky.get(lx, ly, lz);
						}
						if (block != null) {
							emittedLight[index] = (byte) block.get(lx, ly, lz);
						}

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

	/**
	 * Reads a chunk straight out of its region file, without loading it into the world.
	 *
	 * <h2>Why not load it</h2>
	 *
	 * <p>A renderer asks for the chunks of a saved world while the server is running, from its own thread.
	 * Loading a chunk means asking the main thread to produce it, and a snapshot taken from the render thread
	 * can only wait for that -- wait, on the thread that is the reason the render thread exists. BlueMap's
	 * render thread did exactly this and stopped producing tiles instead of producing them.
	 *
	 * <p>It does not need to load it, either: the blocks are already on disk. Reading them is the work the
	 * game's own load does, minus everything that makes a chunk live -- heightmaps, entities, ticking,
	 * tickets. For a picture that is all that is wanted.
	 *
	 * <h2>What a file-only snapshot does not have</h2>
	 *
	 * <p>Light. A save keeps it packed per section in a layout of its own, and a decode invented here would be
	 * a guess that shows up as a wrong shade on every tile. A snapshot read this way reports light 0, the same
	 * as a world whose light data is gone, and every caller that cares already has to handle that.
	 *
	 * @return the snapshot, or {@code null} when the save holds no such chunk or it cannot be decoded
	 */
	static ForbricChunkSnapshot read(ForbricWorld world, int chunkX, int chunkZ) {
		int regionX = chunkX >> 5;
		int regionZ = chunkZ >> 5;
		java.io.File file = new java.io.File(new java.io.File(world.getWorldFolder(), "region"),
				"r." + regionX + "." + regionZ + ".mca");
		if (!file.isFile()) {
			return null;
		}
		try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
			long entry = 4L * ((chunkX & 31) + (chunkZ & 31) * 32);
			if (raf.length() < entry + 4) {
				return null;
			}
			raf.seek(entry);
			int header = raf.readInt();
			if (header == 0) {
				// Zero says the chunk was never written; the rest of those four bytes means nothing.
				return null;
			}
			long start = (header >>> 8) * 4096L;
			raf.seek(start);
			int length = raf.readInt();
			if (length <= 1 || start + length + 4L > raf.length()) {
				return null;
			}
			byte[] payload = new byte[length];
			raf.readFully(payload);
			// The first byte says how the rest is compressed: 1 gzip, 2 zlib, 3 raw, 4 LZ4 (an external mod).
			// The first three are decoded here; anything else is reported rather than guessed at.
			int compression = payload[0] & 0xFF;
			java.io.ByteArrayInputStream bytes = new java.io.ByteArrayInputStream(payload, 1, length - 1);
			java.io.DataInput data;
			if (compression == 1) {
				data = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(bytes));
			} else if (compression == 2) {
				data = new java.io.DataInputStream(new java.util.zip.InflaterInputStream(bytes));
			} else if (compression == 3) {
				data = new java.io.DataInputStream(bytes);
			} else {
				net.neoforbrickit.bridge.util.ForbricLog.warn(
						"[Forbric/Bukkit] chunk %s,%s in %s is compressed with %s, which this bridge cannot "
								+ "read; leaving it out of the map", chunkX, chunkZ, file.getPath(),
						String.valueOf(compression));
				return null;
			}
			// The game's own reader, so the format is decoded once and by the side that owns it.
			net.minecraft.nbt.CompoundTag root =
					net.minecraft.nbt.NbtIo.read(data, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			return root == null ? null : fromTag(world, chunkX, chunkZ, root);
		} catch (java.io.IOException | RuntimeException unreadable) {
			// A corrupt or half-written region is a hole in the map, not a reason to stop rendering: the next
			// save rewrites the chunk and the next render picks it up.
			net.neoforbrickit.bridge.util.ForbricLog.warn(
					"[Forbric/Bukkit] could not read chunk %s,%s out of %s: %s",
					chunkX, chunkZ, file.getPath(), unreadable);
			return null;
		}
	}

	/**
	 * Turns a chunk's saved NBT into a snapshot.
	 *
	 * <p>Each section's {@code block_states} is a palette plus (for more than one palette entry) a bit-packed
	 * index array. Both halves are decoded through the game's own code rather than re-implemented here: the
	 * packing rules changed in 1.16 and will change again, and a bridge with its own copy would render a world
	 * that looks almost right.
	 */
	private static ForbricChunkSnapshot fromTag(ForbricWorld world, int chunkX, int chunkZ,
			net.minecraft.nbt.CompoundTag root) {
		ForbricChunkSnapshot snapshot = new ForbricChunkSnapshot(world, chunkX, chunkZ);
		net.minecraft.nbt.ListTag sections = root.getListOrEmpty("sections");
		for (int i = 0; i < sections.size(); i++) {
			net.minecraft.nbt.CompoundTag section = sections.getCompoundOrEmpty(i);
			// `Y` is the section's own index in the world, so the offset from the bottom of the world is
			// already a section index -- no shifting of the world's minimum Y here.
			int sectionY = section.getInt("Y").orElse(0);
			int sectionIndex = sectionY - (snapshot.minY >> 4);
			if (sectionIndex < 0 || (sectionIndex + 1) << 4 > snapshot.maxY - snapshot.minY) {
				continue;
			}
			net.minecraft.nbt.CompoundTag blockStates = section.getCompoundOrEmpty("block_states");
			net.minecraft.nbt.ListTag palette = blockStates.getListOrEmpty("palette");
			if (palette.isEmpty()) {
				continue;
			}
			snapshot.fillSection(sectionIndex, palette, blockStates.getLongArray("data").orElse(null));
		}
		return snapshot;
	}

	/**
	 * Fills one 16³ section from its saved palette and storage.
	 *
	 * @param sectionIndex the section's position counted from the bottom of the world
	 * @param palette      the section's palette entries, each a {@code {Name, Properties}} compound
	 * @param storage      the packed indices, or null when the palette has a single entry
	 */
	private void fillSection(int sectionIndex, net.minecraft.nbt.ListTag palette, long[] storage) {
		org.bukkit.Material[] entries = new org.bukkit.Material[palette.size()];
		for (int i = 0; i < palette.size(); i++) {
			// An entry that cannot be resolved still keeps its slot: the storage's indices are positions in
			// this list, so dropping one would shift every block after it.
			net.minecraft.world.level.block.state.BlockState state =
					stateOf(palette.getCompoundOrEmpty(i));
			entries[i] = state == null ? org.bukkit.Material.AIR : ForbricBlock.materialOf(state);
		}
		int sectionBottom = sectionIndex << 4;
		if (storage == null) {
			// A single-entry palette is every block in the section, and the save omits the storage entirely.
			if (entries.length == 0 || entries[0].isAir()) {
				return;
			}
			for (int ly = 0; ly < 16; ly++) {
				int y = sectionBottom + ly;
				if (y < minY || y >= maxY) {
					continue;
				}
				for (int column = 0; column < 256; column++) {
					write(y, column, entries[0]);
				}
			}
			return;
		}
		int bits = Math.max(4, net.minecraft.util.Mth.ceillog2(entries.length));
		long mask = (1L << bits) - 1L;
		int valuesPerLong = 64 / bits;
		for (int ly = 0; ly < 16; ly++) {
			int y = sectionBottom + ly;
			if (y < minY || y >= maxY) {
				continue;
			}
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					// The save's order is y, then z, then x -- the same order a section's own index uses.
					int valueIndex = (ly << 8) | (lz << 4) | lx;
					int longIndex = valueIndex / valuesPerLong;
					if (longIndex >= storage.length) {
						continue;
					}
					int paletteIndex = (int) ((storage[longIndex] >>> ((valueIndex % valuesPerLong) * bits)) & mask);
					org.bukkit.Material type = paletteIndex < entries.length
							? entries[paletteIndex] : org.bukkit.Material.AIR;
					write(y, (lz << 4) | lx, type == null ? org.bukkit.Material.AIR : type);
				}
			}
		}
	}

	/** Writes one captured block, and keeps the column's highest non-air block up to date. */
	private void write(int y, int column, org.bukkit.Material type) {
		int index = ((y - minY) << 8) | column;
		types[index] = type;
		data[index] = type.createBlockData();
		if (!type.isAir() && y > highest[column]) {
			highest[column] = y;
		}
	}

	/**
	 * One palette entry as a game state.
	 *
	 * <p>A palette entry is {@code {Name, Properties}}, which is exactly what the game's own block codec
	 * parses, so the decode is the game's rather than a second reading of the same format. A failure is
	 * reported once per entry and answers null, which the caller turns into air.
	 */
	private static net.minecraft.world.level.block.state.BlockState stateOf(
			net.minecraft.nbt.CompoundTag entry) {
		com.mojang.serialization.DataResult<net.minecraft.world.level.block.state.BlockState> parsed =
				// BlockState.CODEC, not Block.CODEC: a palette entry carries Name AND Properties, and
				// Block.CODEC is a MapCodec over the block alone -- it has no parse() at all.
				net.minecraft.world.level.block.state.BlockState.CODEC
						.parse(net.minecraft.nbt.NbtOps.INSTANCE, entry);
		if (parsed.isError()) {
			net.neoforbrickit.bridge.util.ForbricLog.warn(
					"[Forbric/Bukkit] unreadable palette entry %s: %s", entry.toString(),
					parsed.error().map(Object::toString).orElse("no detail"));
		}
		return parsed.result().orElse(null);
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
		this.skyLight = new byte[slots];
		this.emittedLight = new byte[slots];
		java.util.Arrays.fill(types, org.bukkit.Material.AIR);
		org.bukkit.block.data.BlockData air = org.bukkit.Material.AIR.createBlockData();
		java.util.Arrays.fill(data, air);

		this.highest = new int[256];
		java.util.Arrays.fill(highest, minY - 1);
	}

	/**
	 * One section's light, or null when the chunk's light engine has nothing for it.
	 *
	 * <p>Null is a real state: a chunk that has been read but not lit has no layers at all, and a section
	 * that was just generated may have them missing. Both are answered as zero light rather than as an
	 * exception -- a dark tile is a tile, a thrown exception is no map.
	 */
	private static net.minecraft.world.level.chunk.DataLayer lightLayer(
			net.minecraft.world.level.chunk.LevelChunk chunk, int sectionIndex,
			net.minecraft.world.level.LightLayer layer) {
		// The light engine belongs to the level's chunk source, not to the chunk:
		// LevelChunk has no getLightEngine() of its own.
		return chunk.getLevel().getChunkSource().getLightEngine().getLayerListener(layer)
				.getDataLayerData(net.minecraft.core.SectionPos.of(chunk.getPos(),
						chunk.getSectionYFromSectionIndex(sectionIndex)));
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
		// From the snapshot, not from the level. Reading the live world here is a chunk load when the chunk
		// is not in memory -- and a renderer takes snapshots for chunks it never loaded, off the tick path,
		// where a load cannot finish. Light is captured with the blocks for that reason; a chunk read out
		// of its region file carries none, and a renderer that gets 0 here lights its own tile.
		return skyLight[index(bx, by, bz)];
	}

	@Override
	public int getBlockEmittedLight(int bx, int by, int bz) {
		return emittedLight[index(bx, by, bz)];
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
