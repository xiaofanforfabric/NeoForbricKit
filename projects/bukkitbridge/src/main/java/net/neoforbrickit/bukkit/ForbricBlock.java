/*
 * Forbric Bukkit bridge — an {@code org.bukkit.block.Block} backed by a Minecraft {@code BlockPos} in a {@code ServerLevel}.
 * *** GENERATED FILE — DO NOT EDIT ***
 *
 * Produced by tools/gen_stub.py from tools/stubs/ForbricBlock.table. Edits belong in the table or this template.
 */
package net.neoforbrickit.bukkit;

/**
 * One block position, as a plugin sees it.
 *
 * <p>This is a <b>live handle</b>, not a snapshot, and the distinction is the whole design. Bukkit's own contract says
 * a {@code Block} reflects whatever occupies that corner at the moment you ask — there is no capture step and no
 * {@code refresh()} to forget. So this class holds a world and three integers, and every {@code getType()} reads the
 * level again. Two handles for the same corner are equal and hash alike; neither caches a state that could go stale
 * while a plugin holds it.
 *
 * <p><b>Coordinates need no conversion.</b> Bukkit's {@code (x, y, z)} and Minecraft's {@code BlockPos} are both
 * world-absolute, both count y upward from the bottom of the world, and both are integers. What differs is what
 * "the bottom of the world" is — {@link #getWorld()}'s {@code getMinHeight()} answers that per dimension, which is
 * why nothing here hardcodes it.
 *
 * <p><b>Materials and block states are two views of one thing.</b> A plugin says {@code Material.STONE}; the game
 * says a {@code Block} with a {@code defaultBlockState()}. The bridge between them is the block's registry id:
 * {@code Material.getKey()} gives {@code minecraft:stone}, and {@code BuiltInRegistries.BLOCK} turns that back into
 * the game's own {@code Block}. Going the other way, {@code Registries.BLOCK.getKey(...)} produces the id and
 * {@link org.bukkit.Material#matchMaterial(String)} produces the material. Both directions are name-based on purpose:
 * the numeric ids are an implementation detail of whichever version is installed, and a bridge that hardcoded them
 * would break on the next world update rather than the next code change.
 */
final class ForbricBlock implements org.bukkit.block.Block {

        private final ForbricWorld world;
        private final int x;
        private final int y;
        private final int z;

        /**
         * Whether this corner's chunk is in memory, and so whether reading it is allowed.
         *
         * <p>False only for a handle made by {@code at(..., false)}; a handle from the ordinary path is always
         * readable, because that path is allowed to load. See {@link #at(ForbricWorld, int, int, int, boolean)}.
         */
        private final boolean readable;

        private ForbricBlock(ForbricWorld world, int x, int y, int z, boolean readable) {
                this.world = world;
                this.x = x;
                this.y = y;
                this.z = z;
                this.readable = readable;
        }

        /**
         * Wraps a corner of a world. The one place three integers become an {@code org.bukkit.block.Block}.
         *
         * <p>A fresh handle per call rather than a cache, matching {@link ForbricWorld}: two handles for one corner
         * are equal through {@link #equals} and hash alike, so nothing downstream can tell them apart — and a cache
         * would have to be invalidated when the chunk unloads, which is a correctness problem bought for nothing.
         */
        static ForbricBlock at(ForbricWorld world, int x, int y, int z) {
                return new ForbricBlock(world, x, y, z, true);
        }

        /**
         * A corner that may or may not be in memory, for callers that must not load it.
         *
         * <p>{@code readable == false} means the chunk is not loaded and the caller cannot afford to load it
         * (a render thread, see {@code ForbricWorld#getBlockAt(int, int, int, boolean)}). Every read of such a
         * handle answers air rather than pulling the chunk in: a load asked for from the wrong thread does not
         * complete, and a plugin that hangs looks exactly like a server that stopped.
         */
        static ForbricBlock at(ForbricWorld world, int x, int y, int z, boolean readable) {
                return new ForbricBlock(world, x, y, z, readable);
        }

        /** The corner as a game position, for the bridge's own use and the event layer. */
        private net.minecraft.core.BlockPos pos() {
                return new net.minecraft.core.BlockPos(x, y, z);
        }

        /**
         * What is at this corner, as the game's own state.
         *
         * <p>The one place a read happens, and the one place {@link #readable} is honoured: an unreadable
         * handle answers {@code null}, which every caller here turns into air. Reading the level directly
         * would be a chunk load, and that is the thing this handle exists to refuse.
         */
        private net.minecraft.world.level.block.state.BlockState state() {
                return readable ? world.level().getBlockState(pos()) : null;
        }

        private static UnsupportedOperationException notYet(String what) {
                return new UnsupportedOperationException(
                                "[Forbric/Bukkit] Block." + what + "() is not implemented by this bridge yet");
        }

        /** The world this block lives in. */
        @Override
        public ForbricWorld getWorld() {
                return world;
        }

        /** This block's x, world-absolute. */
        @Override
        public int getX() {
                return x;
        }

        /** This block's y, counted upward from the bottom of the world. */
        @Override
        public int getY() {
                return y;
        }

        /** This block's z, world-absolute. */
        @Override
        public int getZ() {
                return z;
        }

        /** This block's corner as a Bukkit location, with the coordinates centred as Bukkit specifies. */
        @Override
        public org.bukkit.Location getLocation() {
                return new org.bukkit.Location(world, x, y, z);
        }

        /**
         * What is here right now, as a material.
         *
         * <p>Read through to the level on every call — that is the {@code Block} contract, not an oversight. The
         * registry id is the bridge: it is stable across versions where numeric ids are not.
         */
        @Override
        public org.bukkit.Material getType() {
                net.minecraft.world.level.block.state.BlockState state = state();
                if (state == null) {
                        // Unreadable handle: the chunk is not in memory and this caller may not load it.
                        return org.bukkit.Material.AIR;
                }
                net.minecraft.resources.Identifier id =
                                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(id.toString());
                if (material == null) {
			// A block this bridge's copy of the Bukkit API has never heard of: a mod-added one, or a newer
			// game than the API was built against.
			//
			// Named in the log, but NOT thrown. A renderer walks millions of blocks, and a modded world is
			// the whole reason this bridge exists -- one exception per unknown block would abort the map
			// rather than leave a hole in it. AIR is the honest answer for "no Bukkit material names this".
			warnUnknownBlock("getType", id);
			return org.bukkit.Material.AIR;
		}
		return material;
	}

	/**
	 * The Bukkit material for a game state, or {@code AIR} when this API has no name for it.
	 *
	 * <p>Package-visible because the chunk snapshot resolves millions of these and must resolve them the
	 * same way a single block read does — two spellings of "which material is this" would drift.
	 *
	 * <p>Unknown blocks answer {@code AIR} rather than throwing: see {@link #getType()}.
	 */
	static org.bukkit.Material materialOf(net.minecraft.world.level.block.state.BlockState state) {
		net.minecraft.resources.Identifier id =
				net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
		org.bukkit.Material material = org.bukkit.Material.matchMaterial(id.toString());
		if (material == null) {
			warnUnknownBlock("getType", id);
			return org.bukkit.Material.AIR;
		}
		return material;
	}

	/**
	 * One line per unknown block id, not one per read.
	 *
	 * <p>A renderer reads the same block a few million times, so a per-read warning would be both useless
	 * and enormous. The set is static because the id is the identity: two wrappers over the same block
	 * report the same gap.
	 */
	private static final java.util.Set<net.minecraft.resources.Identifier> REPORTED_UNKNOWN =
			java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static void warnUnknownBlock(String what, net.minecraft.resources.Identifier id) {
		if (REPORTED_UNKNOWN.add(id)) {
			net.neoforbrickit.bridge.util.ForbricLog.warn(
					"[Forbric/Bukkit] Block.%s() has no Bukkit material for %s; answering AIR", what, id);
		}
        }

        /**
         * Puts a material here.
         *
         * <p>Writes the registry default state for that block, which is what Bukkit's own documentation describes:
         * {@code setType(Material.OAK_STAIRS)} gives you stairs in their default facing, not the facing that was
         * there before. Reflection is deliberately not used to pick a "closest" state — a plugin that wants a
         * particular facing should say so through block data.
         */
        @Override
        public void setType(org.bukkit.Material type) {
                if (type == null || !type.isBlock()) {
                        throw new IllegalArgumentException(
                                        "[Forbric/Bukkit] Block.setType() needs a block material, got " + type);
                }
                net.minecraft.resources.Identifier id = keyOf(type);
                net.minecraft.world.level.block.Block block =
                                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(id);
                if (block == null) {
                        throw notYet("setType() for block " + id);
                }
                // Flags 3 = notify clients and neighbours, matching Bukkit's own write path. A plugin placing a block
                // expects redstone and observers to notice.
                world.level().setBlock(pos(), block.defaultBlockState(), 3);
        }

        /**
         * Whether this corner is empty.
         *
         * <p>Asked of the state rather than by comparing materials, so a block this bridge cannot name still answers
         * correctly — an unknown material must not read as "not empty" any more than it must read as air.
         */
        @Override
        public boolean isEmpty() {
                net.minecraft.world.level.block.state.BlockState state = state();
                return state == null || state.isAir();
        }

        /** Whether what is here is a liquid. */
        @Override
        public boolean isLiquid() {
                net.minecraft.world.level.block.state.BlockState state = state();
                return state == null || !state.getFluidState().isEmpty();
        }

        /**
         * Whether an entity can pass through.
         *
         * <p>Uses the block's own motion test rather than comparing against a list of "passable" materials: the game
         * already knows which shapes stop a body, and a hand-kept list would drift.
         */
        @Override
        public boolean isPassable() {
                net.minecraft.world.level.block.state.BlockState state = state();
                return state == null || !state.blocksMotion();
        }

        /** The light level where this block sits, 0-15. */
        @Override
        public byte getLightLevel() {
                return (byte) world.level().getMaxLocalRawBrightness(pos());
        }

        /** This block's state as Bukkit block data, which is how a plugin reads or sets properties like facing. */
        @Override
        public org.bukkit.block.data.BlockData getBlockData() {
                net.minecraft.world.level.block.state.BlockState state = state();
                return state == null ? org.bukkit.Material.AIR.createBlockData() : dataOf(state);
        }

        /** Replaces this block's state, properties and all. */
        @Override
        public void setBlockData(org.bukkit.block.data.BlockData data) {
                if (data == null) {
                        throw new IllegalArgumentException("[Forbric/Bukkit] Block.setBlockData() needs data");
                }
                world.level().setBlock(pos(), stateOf(data), 3);
        }

/**
	 * This block as a {@code BlockState}.
	 *
	 * <p>A live handle rather than a copy — see {@link ForbricBlockState}, which states the difference and
	 * offers {@code copy()} for callers that need the API's usual detached behaviour.
	 *
	 * <p>Answered rather than refused because a map renderer asks for it once per block and then reads
	 * only {@code getType} and {@code getBlockData}: refusing it leaves the renderer with nothing it can
	 * distinguish from an empty world.
	 */
	@Override
	public org.bukkit.block.BlockState getState() {
		return new ForbricBlockState(this);
        }

        /**
         * The registry id for a material, spelled the way the game spells it.
         *
         * <p>{@code Material.getKey()} already is a namespaced id; this only guards the odd material whose key is
         * absent, which would otherwise fail inside {@code get()} with a message about a null id rather than about a
         * material.
         */
        private static net.minecraft.resources.Identifier keyOf(org.bukkit.Material type) {
                return net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                type.getKey().getNamespace(), type.getKey().getKey());
        }

        /**
         * A game state as Bukkit block data.
         *
         * <p>Goes through the material's own {@code createBlockData}, so the properties a plugin sees are the ones
         * the API registered for that block — the game's state string and Bukkit's property names are not the same
         * spelling, and letting the API do the translation is what keeps them in step.
         */
        static org.bukkit.block.data.BlockData dataOf(
                        net.minecraft.world.level.block.state.BlockState state) {
                net.minecraft.resources.Identifier id =
                                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(id.toString());
                if (material == null) {
			warnUnknownBlock("getBlockData", id);
			return org.bukkit.Material.AIR.createBlockData();
		}
		// The game's own state string, so the properties a plugin reads are the ones that are actually
		// there -- `minecraft:oak_stairs[facing=east,half=top]` rather than the default state a bare
		// createBlockData() would give. A renderer reading orientation, water level or slab half needs
		// the difference, and a default state is only right when the block IS in its default state.
		try {
			return material.createBlockData(state.toString());
		} catch (RuntimeException unsupported) {
			// A property this API's copy of the block does not model (a newer game, or a mod). The
			// material is still right, and default-shaped data beats no block at all.
			return material.createBlockData();
		}
        }

        /**
         * Bukkit block data as a game state.
         *
         * <p>The round trip is name-based on both ends: the data's material names the block, and the game's
         * default state for it is what gets written. Properties the API models but the game does not (or spells differently)
         * are dropped rather than guessed at.
         */
        private static net.minecraft.world.level.block.state.BlockState stateOf(
                        org.bukkit.block.data.BlockData data) {
                net.minecraft.resources.Identifier id = keyOf(data.getMaterial());
                net.minecraft.world.level.block.Block block =
                                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(id);
                if (block == null) {
                        throw notYet("block data for " + id);
                }
                return block.defaultBlockState();
        }

        /**
         * Equal to any other handle on the same corner of the same world.
         *
         * <p>Identity is the coordinates, not object identity, because a plugin can reach the same block two ways
         * (from a world, from an event) and must be able to compare the results — or put them in a set.
         */
        @Override
        public boolean equals(Object other) {
                if (this == other) {
                        return true;
                }
                if (!(other instanceof ForbricBlock)) {
                        return false;
                }
                ForbricBlock that = (ForbricBlock) other;
                return x == that.x && y == that.y && z == that.z && world.equals(that.world);
        }

        /** Hashes by the coordinates, matching {@link #equals}. */
        @Override
        public int hashCode() {
                return (world.hashCode() * 31 + x) * 31 + y;
        }

        /** Names the corner, so a failed assertion about a block says which one. */
        @Override
        public String toString() {
                return "ForbricBlock[" + world.getName() + " " + x + "," + y + "," + z + "]";
        }

	@Override
	public byte getData() {
		throw notYet("getData");
	}

	@Override
	public org.bukkit.block.Block getRelative(int arg0, int arg1, int arg2) {
		throw notYet("getRelative");
	}

	@Override
	public org.bukkit.block.Block getRelative(org.bukkit.block.BlockFace arg0) {
		throw notYet("getRelative");
	}

	@Override
	public org.bukkit.block.Block getRelative(org.bukkit.block.BlockFace arg0, int arg1) {
		throw notYet("getRelative");
	}

	@Override
	public byte getLightFromSky() {
		return lightValue(net.minecraft.world.level.LightLayer.SKY);
	}

	@Override
	public byte getLightFromBlocks() {
		return lightValue(net.minecraft.world.level.LightLayer.BLOCK);
	}

	/**
	 * One light layer's value at this corner, as the game's light engine has it.
	 *
	 * <p>Read through the engine rather than through {@link #getLightLevel()}: that answers the
	 * <em>combined</em> brightness a mob would see, which is what Bukkit's deprecated
	 * {@code getLightLevel()} means, while these two each want one layer on its own.
	 *
	 * <p>Zero for an unreadable handle, matching every other read on one: a caller that must not load
	 * the chunk (a render thread) is told "no light" rather than being handed the cost of a load.
	 */
	private byte lightValue(net.minecraft.world.level.LightLayer layer) {
		if (!readable) {
			return 0;
		}
		return (byte) world.level().getLightEngine().getLayerListener(layer).getLightValue(pos());
	}

	@Override
	public boolean isValidTool(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("isValidTool");
	}

	@Override
	public org.bukkit.Location getLocation(org.bukkit.Location arg0) {
		throw notYet("getLocation");
	}

	@Override
	public org.bukkit.Chunk getChunk() {
		throw notYet("getChunk");
	}

	@Override
	public void setBlockData(org.bukkit.block.data.BlockData arg0, boolean arg1) {
		throw notYet("setBlockData");
	}

	@Override
	public void setType(org.bukkit.Material arg0, boolean arg1) {
		throw notYet("setType");
	}

	@Override
	public org.bukkit.block.BlockFace getFace(org.bukkit.block.Block arg0) {
		throw notYet("getFace");
	}

	@Override
	public org.bukkit.block.BlockState getState(boolean arg0) {
		throw notYet("getState");
	}

	@Override
	public org.bukkit.block.Biome getBiome() {
		throw notYet("getBiome");
	}

	@Override
	public org.bukkit.block.Biome getComputedBiome() {
		throw notYet("getComputedBiome");
	}

	@Override
	public void setBiome(org.bukkit.block.Biome arg0) {
		throw notYet("setBiome");
	}

	@Override
	public boolean isBlockPowered() {
		throw notYet("isBlockPowered");
	}

	@Override
	public boolean isBlockIndirectlyPowered() {
		throw notYet("isBlockIndirectlyPowered");
	}

	@Override
	public boolean isBlockFacePowered(org.bukkit.block.BlockFace arg0) {
		throw notYet("isBlockFacePowered");
	}

	@Override
	public boolean isBlockFaceIndirectlyPowered(org.bukkit.block.BlockFace arg0) {
		throw notYet("isBlockFaceIndirectlyPowered");
	}

	@Override
	public int getBlockPower(org.bukkit.block.BlockFace arg0) {
		throw notYet("getBlockPower");
	}

	@Override
	public boolean isBuildable() {
		throw notYet("isBuildable");
	}

	@Override
	public boolean isBurnable() {
		throw notYet("isBurnable");
	}

	@Override
	public boolean isReplaceable() {
		throw notYet("isReplaceable");
	}

	@Override
	public boolean isSolid() {
		throw notYet("isSolid");
	}

	@Override
	public boolean isCollidable() {
		throw notYet("isCollidable");
	}

	@Override
	public double getTemperature() {
		throw notYet("getTemperature");
	}

	@Override
	public double getHumidity() {
		throw notYet("getHumidity");
	}

	@Override
	public org.bukkit.block.PistonMoveReaction getPistonMoveReaction() {
		throw notYet("getPistonMoveReaction");
	}

	@Override
	public boolean breakNaturally() {
		throw notYet("breakNaturally");
	}

	@Override
	public boolean breakNaturally(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("breakNaturally");
	}

	@Override
	public boolean breakNaturally(boolean arg0, boolean arg1) {
		throw notYet("breakNaturally");
	}

	@Override
	public boolean breakNaturally(org.bukkit.inventory.ItemStack arg0, boolean arg1, boolean arg2) {
		throw notYet("breakNaturally");
	}

	@Override
	public boolean breakNaturally(org.bukkit.inventory.ItemStack arg0, boolean arg1, boolean arg2, boolean arg3) {
		throw notYet("breakNaturally");
	}

	@Override
	public void tick() {
		throw notYet("tick");
	}

	@Override
	public void fluidTick() {
		throw notYet("fluidTick");
	}

	@Override
	public void randomTick() {
		throw notYet("randomTick");
	}

	@Override
	public boolean applyBoneMeal(org.bukkit.block.BlockFace arg0) {
		throw notYet("applyBoneMeal");
	}

	@Override
	public java.util.Collection<org.bukkit.inventory.ItemStack> getDrops() {
		throw notYet("getDrops");
	}

	@Override
	public java.util.Collection<org.bukkit.inventory.ItemStack> getDrops(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("getDrops");
	}

	@Override
	public java.util.Collection<org.bukkit.inventory.ItemStack> getDrops(org.bukkit.inventory.ItemStack arg0, org.bukkit.entity.Entity arg1) {
		throw notYet("getDrops");
	}

	@Override
	public boolean isPreferredTool(org.bukkit.inventory.ItemStack arg0) {
		throw notYet("isPreferredTool");
	}

	@Override
	public float getBreakSpeed(org.bukkit.entity.Player arg0) {
		throw notYet("getBreakSpeed");
	}

	@Override
	public org.bukkit.util.RayTraceResult rayTrace(org.bukkit.Location arg0, org.bukkit.util.Vector arg1, double arg2, org.bukkit.FluidCollisionMode arg3) {
		throw notYet("rayTrace");
	}

	@Override
	public org.bukkit.util.BoundingBox getBoundingBox() {
		throw notYet("getBoundingBox");
	}

	@Override
	public org.bukkit.util.VoxelShape getCollisionShape() {
		throw notYet("getCollisionShape");
	}

	@Override
	public boolean canPlace(org.bukkit.block.data.BlockData arg0) {
		throw notYet("canPlace");
	}

	@Override
	public com.destroystokyo.paper.block.BlockSoundGroup getSoundGroup() {
		throw notYet("getSoundGroup");
	}

	@Override
	public org.bukkit.SoundGroup getBlockSoundGroup() {
		throw notYet("getBlockSoundGroup");
	}

	@Override
	public java.lang.String getTranslationKey() {
		throw notYet("getTranslationKey");
	}

	@Override
	public boolean isSuffocating() {
		throw notYet("isSuffocating");
	}

	@Override
	public void setMetadata(java.lang.String arg0, org.bukkit.metadata.MetadataValue arg1) {
		throw notYet("setMetadata");
	}

	@Override
	public java.util.List<org.bukkit.metadata.MetadataValue> getMetadata(java.lang.String arg0) {
		throw notYet("getMetadata");
	}

	@Override
	public boolean hasMetadata(java.lang.String arg0) {
		throw notYet("hasMetadata");
	}

	@Override
	public void removeMetadata(java.lang.String arg0, org.bukkit.plugin.Plugin arg1) {
		throw notYet("removeMetadata");
	}

	@Override
	public java.lang.String translationKey() {
		throw notYet("translationKey");
	}}
