/*
 * Forbric Bukkit bridge — an {@code org.bukkit.block.Block} backed by a Minecraft {@code BlockPos} in a {@code ServerLevel}.
 * *** GENERATED FILE — DO NOT EDIT ***
 *
 * Produced by tools/gen_stub.py from tools/stubs/ForbricBlock.table. Edits belong in the table or this template.
 */
package org.cn.xiaofan.bukkit;

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

        private ForbricBlock(ForbricWorld world, int x, int y, int z) {
                this.world = world;
                this.x = x;
                this.y = y;
                this.z = z;
        }

        /**
         * Wraps a corner of a world. The one place three integers become an {@code org.bukkit.block.Block}.
         *
         * <p>A fresh handle per call rather than a cache, matching {@link ForbricWorld}: two handles for one corner
         * are equal through {@link #equals} and hash alike, so nothing downstream can tell them apart — and a cache
         * would have to be invalidated when the chunk unloads, which is a correctness problem bought for nothing.
         */
        static ForbricBlock at(ForbricWorld world, int x, int y, int z) {
                return new ForbricBlock(world, x, y, z);
        }

        /** The corner as a game position, for the bridge's own use and the event layer. */
        private net.minecraft.core.BlockPos pos() {
                return new net.minecraft.core.BlockPos(x, y, z);
        }

        private net.minecraft.world.level.block.state.BlockState state() {
                return world.level().getBlockState(pos());
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
                net.minecraft.resources.Identifier id =
                                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state().getBlock());
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(id.toString());
                if (material == null) {
                        // A block this bridge's copy of the Bukkit API has never heard of: a mod-added one, or a
                        // newer game than the API was built against. Naming it beats returning air, which would
                        // look like an empty corner and send a plugin down the wrong path.
                        throw notYet("getType() for block " + id);
                }
                return material;
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
                return state().isAir();
        }

        /** Whether what is here is a liquid. */
        @Override
        public boolean isLiquid() {
                return !state().getFluidState().isEmpty();
        }

        /**
         * Whether an entity can pass through.
         *
         * <p>Uses the block's own motion test rather than comparing against a list of "passable" materials: the game
         * already knows which shapes stop a body, and a hand-kept list would drift.
         */
        @Override
        public boolean isPassable() {
                return !state().blocksMotion();
        }

        /** The light level where this block sits, 0-15. */
        @Override
        public byte getLightLevel() {
                return (byte) world.level().getMaxLocalRawBrightness(pos());
        }

        /** This block's state as Bukkit block data, which is how a plugin reads or sets properties like facing. */
        @Override
        public org.bukkit.block.data.BlockData getBlockData() {
                return dataOf(state());
        }

        /** Replaces this block's state, properties and all. */
        @Override
        public void setBlockData(org.bukkit.block.data.BlockData data) {
                if (data == null) {
                        throw new IllegalArgumentException("[Forbric/Bukkit] Block.setBlockData() needs data");
                }
                world.level().setBlock(pos(), stateOf(data), 3);
        }

        /** The block's full state as a Bukkit snapshot. Refuses until the snapshot type is bridged. */
        @Override
        public org.bukkit.block.BlockState getState() {
                throw notYet("getState()");
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
        private static org.bukkit.block.data.BlockData dataOf(
                        net.minecraft.world.level.block.state.BlockState state) {
                net.minecraft.resources.Identifier id =
                                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(id.toString());
                if (material == null) {
                        throw notYet("block data for " + id);
                }
                return material.createBlockData();
        }

        /**
         * Bukkit block data as a game state.
         *
         * <p>The round trip is name-based on both ends: the data's material names the block, and the game's default
         * state for it is what gets written. Properties the API models but the game does not (or spells differently)
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
		throw notYet("getLightFromSky");
	}

	@Override
	public byte getLightFromBlocks() {
		throw notYet("getLightFromBlocks");
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
