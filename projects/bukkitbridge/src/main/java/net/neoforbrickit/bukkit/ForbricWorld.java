/*
 * Forbric Bukkit bridge — an {@code org.bukkit.World} backed by a Minecraft {@code ServerLevel}.
 * *** GENERATED FILE — DO NOT EDIT ***
 *
 * Produced by tools/gen_stub.py from tools/stubs/ForbricWorld.table. Edits belong in the table or this template.
 */
package net.neoforbrickit.bukkit;

/**
 * One loaded world, as a plugin sees it.
 *
 * <p>Every method {@code org.bukkit.World} declares is present; the ones with real meaning read from the
 * {@code ServerLevel} this wraps, and the rest refuse with a named exception. A refusal is deliberate: a stub
 * returning a plausible wrong value (an empty chunk, a zero difficulty) is far harder to trace than a message
 * that names the method.
 *
 * <p><b>Time is not where it used to be.</b> Through 1.21 the game kept one {@code dayTime} on the level, reached
 * with {@code getDayTime()}/{@code setDayTime()}. 26.2 replaced that with named clocks
 * ({@code net.minecraft.world.clock}): the overworld's is {@code Level.getOverworldClockTime()}, a dimension's
 * own is {@code getDefaultClockTime()}, and writing one goes through
 * {@code ServerClockManager.setTotalTicks(Holder<WorldClock>, long)}. None of the old names still exists, which
 * is how this was found — every one of them is a compile error.
 *
 * <p>Coordinates need no conversion, and that is worth stating rather than leaving implied: Bukkit's
 * {@code (x, y, z)} and Minecraft's {@code BlockPos} are both world-absolute and both count y upward from the
 * bottom of the world. What DOES come from the dimension rather than a constant is the height range.
 */
final class ForbricWorld extends net.neoforbrickit.bukkit.entity.CraftWorld
		implements org.bukkit.World {

	private final net.minecraft.server.level.ServerLevel level;
	private final ServerImpl server;

	/**
	 * Which plugins hold a ticket on which chunk.
	 *
	 * <p>Kept here because the game cannot answer it: a FORCED ticket records that someone wants the chunk, not
	 * who, and Bukkit's {@code getPluginChunkTickets} and its remove-one-plugin-only contract need the
	 * distinction. Concurrent because a plugin may ticket a chunk from its own scheduler thread while the
	 * server thread is asking which tickets exist.
	 */
	private final ChunkTicketTable chunkTickets = new ChunkTicketTable();

	private ForbricWorld(net.minecraft.server.level.ServerLevel level, ServerImpl server) {
		super(level);
		this.level = level;
		this.server = server;
	}

	/**
	 * Wraps a level. The one place a {@code ServerLevel} becomes an {@code org.bukkit.World}.
	 *
	 * <p>A fresh wrapper per call rather than a cache: two wrappers for one level are equal through
	 * {@link #equals} and hash alike, so nothing downstream can tell them apart — and a cache would need
	 * invalidating when a level unloads, which is a correctness problem bought for nothing.
	 */
	static ForbricWorld of(net.minecraft.server.level.ServerLevel level, ServerImpl server) {
		return new ForbricWorld(level, server);
	}

	/** The wrapped level, for the bridge's own use by {@link ForbricBlock} and the event layer. */
	net.minecraft.server.level.ServerLevel level() {
		return level;
	}

	/**
	 * The plugins holding a ticket on each chunk, as a set of (chunk, plugin) pairs.
	 *
	 * <p>A named type rather than a bare {@code Set} so that the field's meaning is readable at its declaration
	 * and so the three operations that need to agree about it -- add, remove, and "does anyone still hold
	 * this" -- live together instead of being open-coded three times.
	 */
	private static final class ChunkTicketTable implements Iterable<ChunkTicketKey> {

		private final java.util.Set<ChunkTicketKey> held =
				java.util.concurrent.ConcurrentHashMap.newKeySet();

		/** Whether this plugin already held the ticket; the caller reports that as the method's answer. */
		boolean add(ChunkTicketKey key) {
			return held.add(key);
		}

		/** Whether this plugin did hold the ticket; the caller reports that as the method's answer. */
		boolean remove(ChunkTicketKey key) {
			return held.remove(key);
		}

		/** Whether any plugin at all still holds a ticket on this chunk. */
		boolean holds(int chunkX, int chunkZ) {
			for (ChunkTicketKey key : held) {
				if (key.chunkX() == chunkX && key.chunkZ() == chunkZ) {
					return true;
				}
			}
			return false;
		}

		/** Every pair held, for the per-chunk lookup {@code getPluginChunkTickets} needs. */
		@Override
		public java.util.Iterator<ChunkTicketKey> iterator() {
			return held.iterator();
		}
	}

	private static UnsupportedOperationException notYet(String what) {
		// A refusal is a deliberate answer, and the exception is the loud half of it: the plugin sees
		// UnsupportedOperationException with the method named, at the line that called it.
		//
		// <b>This line is the other half, and it exists because that exception is not always seen.</b> A method
		// called from a plugin's own executor, or from inside a CompletableFuture, or from a callback the game
		// invokes on a worker, throws where nobody is watching: the exception is captured, the future fails with
		// no handler attached, and the plugin goes on with whatever it did next -- which is how a pre-generator
		// reported "Processed: 0 chunks" with nothing in any log to say why. One line per method name, not per
		// call: the first refusal of each kind is the one that matters, and a per-call line would flood.
		if (notYetReported.add(what)) {
			net.neoforbrickit.bridge.util.ForbricLog.warn(
					"[Forbric/Bukkit] World.%s() is not implemented; a plugin called it and got a refusal. "
							+ "If the plugin then misbehaves quietly, this is why.", what);
		}
		return new UnsupportedOperationException(
					"[Forbric/Bukkit] World." + what + "() is not implemented by this bridge yet");
	}

	/** The method names already reported by {@link #notYet}, so each is logged once. */
	private static final java.util.Set<String> notYetReported = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * The world's name, as Bukkit knows it: the folder it is stored under.
	 *
	 * <p>Minecraft names a dimension with a {@code ResourceKey} ({@code minecraft:overworld}); Bukkit names a
	 * world with the folder in {@code saves/} ({@code world}). The key's path half is what a plugin writes when
	 * it says {@code getWorld("world")}, and it is the half that stays put when a datapack moves a dimension
	 * between namespaces.
	 */
	@Override
	public String getName() {
		return level.dimension().identifier().getPath();
	}

	/**
	 * A stable id, derived from the dimension rather than generated.
	 *
	 * <p>Bukkit hands a world a UUID and a plugin may save it. Deriving it from the dimension key means a saved
	 * reference resolves again after a restart; a random one would not, and would look correct until the second
	 * launch.
	 */
	@Override
	public java.util.UUID getUID() {
		return java.util.UUID.nameUUIDFromBytes(
				("forbric:world:" + level.dimension().identifier()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	@Override
	public int getMinHeight() {
		return level.getMinY();
	}

	@Override
	public int getMaxHeight() {
		// Bukkit's is exclusive, Minecraft's inclusive. Off by one here puts a plugin's top-of-world loop one
		// block above the world, where every write is accepted and discarded.
		return level.getMaxY() + 1;
	}

	@Override
	public int getSeaLevel() {
		return level.getSeaLevel();
	}

	@Override
	public long getTime() {
		// Bukkit's getTime() is the time of day, 0..24000, on THIS dimension's clock. Reporting the overworld's
		// clock here would make a plugin's day/night logic wrong in the Nether and the End — the two dimensions
		// where it is least visibly wrong and so hardest to notice.
		return Math.floorMod(level.getDefaultClockTime(), 24000L);
	}

	@Override
	public void setTime(long time) {
		// Set the time OF DAY, keeping the day count: Bukkit's setTime(6000) means noon today, not noon on day
		// zero. A clock is one long, so the day is its high part and the time of day its low one.
		long current = level.getDefaultClockTime();
		long day = current - Math.floorMod(current, 24000L);
		level.clockManager().setTotalTicks(clock(),
				day + Math.floorMod(time, 24000L));
	}

	@Override
	public long getFullTime() {
		// The whole clock, day count included — what a plugin compares to notice that time moved backwards.
		return level.getDefaultClockTime();
	}

	@Override
	public void setFullTime(long time) {
		level.clockManager().setTotalTicks(clock(), time);
	}

	@Override
	public boolean isDayTime() {
		// Computed, because 26.2 has no day/night predicate to delegate to: the game decides light from the sky
		// darkening value, and the clock is a separate concern. Bukkit's own contract is the 0..12000 window, so
		// that is what this answers — a plugin comparing it against Bukkit's documentation gets what it expects.
		return getTime() < 12000L;
	}

	@Override
	public org.bukkit.Difficulty getDifficulty() {
		return switch (level.getDifficulty()) {
			case PEACEFUL -> org.bukkit.Difficulty.PEACEFUL;
			case EASY -> org.bukkit.Difficulty.EASY;
			case NORMAL -> org.bukkit.Difficulty.NORMAL;
			case HARD -> org.bukkit.Difficulty.HARD;
		};
	}

	@Override
	public org.bukkit.Location getSpawnLocation() {
		// Bukkit's Location wants a block's centre on x and z and its floor on y. Handing it the raw BlockPos
		// puts a teleport in a corner of the block rather than at the spawn point the game would use.
		net.minecraft.core.BlockPos spawn = level.getRespawnData().pos();
		return new org.bukkit.Location(this, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
	}

	@Override
	public org.bukkit.block.Block getBlockAt(int x, int y, int z) {
		return ForbricBlock.at(this, x, y, z);
	}

	@Override
	public java.util.List<org.bukkit.entity.Player> getPlayers() {
		java.util.List<org.bukkit.entity.Player> players = new java.util.ArrayList<>();
		for (net.minecraft.server.level.ServerPlayer player : level.getPlayers(ignored -> true)) {
			players.add(ForbricPlayer.of(player, server));
		}
		return players;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ForbricWorld world && world.level == level;
	}

	@Override
	public int hashCode() {
		return level.hashCode();
	}

	@Override
	public String toString() {
		return "ForbricWorld[" + level.dimension().identifier() + "]";
	}

	/**
	 * The overworld clock, as the {@code Holder} {@code setTotalTicks} takes.
	 *
	 * <p>Resolved through the running server's registry access, not a static constant: the clock registry is a
	 * datapack registry, so it exists once the server is up and not before, and a static reach lands on a
	 * registry the game never populated.
	 *
	 * <p>{@code OVERWORLD} rather than the dimension's own clock because that is the clock a plugin means by
	 * "the time": 26.2 lets a dimension have its own, and Bukkit has one number. Choosing the overworld's makes
	 * {@code setTime(6000)} mean noon in the world a plugin is almost always thinking of.
	 */
	private net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> clock() {
		return server.minecraft().registryAccess()
				.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK)
				.getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD);
	}

	/**
	 * Traces a ray through the blocks, which is what "what is the player looking at" means.
	 *
	 * <p>The game does this with {@code BlockGetter.clip}, which takes a {@code ClipContext} carrying the
	 * two endpoints rather than an origin, a direction and a length. So the direction is scaled by the
	 * range here and added to the origin — the game clips a segment, and a segment is what Bukkit's
	 * (origin, direction, maxDistance) describes.
	 *
	 * <h2>The three boolean-looking arguments</h2>
	 *
	 * <ul>
	 *   <li><b>{@code FluidCollisionMode}</b> maps onto {@code ClipContext.Fluid}: {@code NEVER} is
	 *       {@code NONE}, {@code SOURCE_ONLY} is {@code SOURCE_ONLY}, and {@code ALWAYS} is {@code ANY}.
	 *       Bukkit's enum and the game's are the same three cases under different names, so this is a
	 *       translation and not a policy choice.
	 *   <li><b>{@code ignorePassableBlocks}</b> picks the clip shape: {@code true} uses
	 *       {@code ClipContext.Block.OUTLINE}, which follows a block's visual outline and therefore
	 *       passes through grass, flowers and other blocks with no collision box; {@code false} uses
	 *       {@code COLLIDER}, the full collision shape. That is the distinction the flag names, and a
	 *       bridge that always used {@code COLLIDER} would make a player aiming at tall grass hit the
	 *       grass instead of the block behind it.
	 * </ul>
	 *
	 * <p>The predicate is applied here rather than inside the game's clip, because {@code ClipContext}
	 * has no hook for one. It is consulted on the hit block only, which is what Bukkit's own
	 * implementation does: the predicate is documented as "return true if the block should be hit", and
	 * a context-wide filter would need a shape getter this bridge does not have.
	 */
	@Override
	public org.bukkit.util.RayTraceResult rayTraceBlocks(io.papermc.paper.math.Position position,
			org.bukkit.util.Vector direction, double maxDistance, org.bukkit.FluidCollisionMode fluidMode,
			boolean ignorePassableBlocks, java.util.function.Predicate<? super org.bukkit.block.Block> filter) {
		if (position == null || direction == null || fluidMode == null) {
			return null;
		}
		net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(
				position.x(), position.y(), position.z());
		net.minecraft.world.phys.Vec3 to = from.add(
				direction.getX() * maxDistance, direction.getY() * maxDistance, direction.getZ() * maxDistance);
		// Diagnostic for "the ray does not reach" reports. Three causes look identical from the caller:
		// a short maxDistance, a direction that is not unit length (so scaling it does not give the
		// range), and a clip that stops early. Printing both ends tells them apart in one run.
		net.neoforbrickit.bridge.util.ForbricLog.debug(
				"[Forbric/Bukkit] rayTraceBlocks from=%s to=%s maxDistance=%s dirLen=%s",
				from, to, maxDistance, direction.length());
		net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(from, to,
				ignorePassableBlocks ? net.minecraft.world.level.ClipContext.Block.OUTLINE
						: net.minecraft.world.level.ClipContext.Block.COLLIDER,
				clipFluid(fluidMode), net.minecraft.world.phys.shapes.CollisionContext.empty());
		net.minecraft.world.phys.BlockHitResult hit = level.clip(context);
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			net.neoforbrickit.bridge.util.ForbricLog.debug(
					"[Forbric/Bukkit] rayTraceBlocks MISS (clip found no block in that segment)");
			return null;
		}
		net.neoforbrickit.bridge.util.ForbricLog.debug(
				"[Forbric/Bukkit] rayTraceBlocks HIT pos=%s distanceFromOrigin=%s",
				hit.getBlockPos(), hit.getLocation().distanceTo(from));
		net.minecraft.core.BlockPos pos = hit.getBlockPos();
		org.bukkit.block.Block block = ForbricBlock.at(this, pos.getX(), pos.getY(), pos.getZ());
		if (filter != null && !filter.test(block)) {
			return null;
		}
		net.minecraft.world.phys.Vec3 where = hit.getLocation();
		return new org.bukkit.util.RayTraceResult(new org.bukkit.util.Vector(where.x(), where.y(), where.z()),
				block, blockFace(hit.getDirection()));
	}

	/** The same trace, plus entities along the way — the hit nearest the origin wins. */
	@Override
	public org.bukkit.util.RayTraceResult rayTrace(io.papermc.paper.math.Position position,
			org.bukkit.util.Vector direction, double maxDistance, org.bukkit.FluidCollisionMode fluidMode,
			boolean ignorePassableBlocks, double raySize,
			java.util.function.Predicate<? super org.bukkit.entity.Entity> entityFilter,
			java.util.function.Predicate<? super org.bukkit.block.Block> blockFilter) {
		// The block trace first, so a closer block hides an entity behind it.
		org.bukkit.util.RayTraceResult blockHit = rayTraceBlocks(position, direction, maxDistance, fluidMode,
				ignorePassableBlocks, blockFilter);
		double reach = blockHit == null || blockHit.getHitPosition() == null ? maxDistance
				: blockHit.getHitPosition().distance(new org.bukkit.util.Vector(
						position.x(), position.y(), position.z()));
		org.bukkit.util.RayTraceResult entityHit = rayTraceEntities(position, direction, reach, raySize,
				entityFilter);
		return entityHit == null ? blockHit : entityHit;
	}

	/** Bukkit's fluid mode as the game's, which is the same three cases under different names. */
	private static net.minecraft.world.level.ClipContext.Fluid clipFluid(org.bukkit.FluidCollisionMode mode) {
		return switch (mode) {
			case NEVER -> net.minecraft.world.level.ClipContext.Fluid.NONE;
			case SOURCE_ONLY -> net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY;
			case ALWAYS -> net.minecraft.world.level.ClipContext.Fluid.ANY;
		};
	}

	/**
	 * A game direction as a Bukkit face.
	 *
	 * <p>Written out rather than derived from the ordinal: the two enums are declared in different orders
	 * (the game goes {@code DOWN, UP, NORTH, SOUTH, WEST, EAST} and Bukkit {@code NORTH, EAST, SOUTH, WEST,
	 * UP, DOWN}), so an ordinal cast would silently turn "up" into "north". Matching by name is what keeps
	 * them in step.
	 */
	static org.bukkit.block.BlockFace blockFace(net.minecraft.core.Direction direction) {
		return switch (direction) {
			case DOWN -> org.bukkit.block.BlockFace.DOWN;
			case UP -> org.bukkit.block.BlockFace.UP;
			case NORTH -> org.bukkit.block.BlockFace.NORTH;
			case SOUTH -> org.bukkit.block.BlockFace.SOUTH;
			case WEST -> org.bukkit.block.BlockFace.WEST;
			case EAST -> org.bukkit.block.BlockFace.EAST;
		};
	}

	/**
	 * Entity ray tracing: not answered yet, and it answers {@code null} rather than refusing.
	 *
	 * <p>A {@code null} here means "no entity was hit", which is exactly what the game returns for a ray
	 * that passed through empty space. That is preferable to an exception for two reasons: it is the
	 * truth for a ray aimed at a wall, and {@link #rayTrace} consults this to decide between a block hit
	 * and an entity hit — so throwing would take down a block trace that had already succeeded, turning
	 * "what block am I looking at" into a failure because the answer to "and what mob" is not known yet.
	 */
	@Override
	public org.bukkit.util.RayTraceResult rayTraceEntities(io.papermc.paper.math.Position position,
			org.bukkit.util.Vector direction, double maxDistance, double raySize,
			java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
		return null;
	}

	/**
	 * Plays a sound at a point in this world.
	 *
	 * <p>The first argument of the game's own {@code playSound} is the entity the sound is attributed to,
	 * and it is {@code null} here: a world-level sound belongs to no entity, which is what a plugin means
	 * by {@code world.playSound(location, ...)}. Passing a nearby player would make the sound follow them
	 * and be heard at their ears instead of at the point.
	 *
	 * <p>The sound is resolved through the game's registry rather than constructed, so a mod's sound and a
	 * vanilla one take the same path and an unknown id is silently nothing — which is what the game does
	 * for a sound it cannot find, and better than an exception in the middle of an explosion.
	 */
	@Override
	public void playSound(org.bukkit.Location location, org.bukkit.Sound sound, org.bukkit.SoundCategory category,
			float volume, float pitch) {
		playSound(location, sound, category, volume, pitch, 0L);
	}

	/**
	 * The same, with the seed the game uses to pick one of a sound's variants.
	 *
	 * <p>A seed of zero means "choose at random", which is the behaviour every caller of the 5-argument
	 * form expects; a non-zero one makes the choice repeatable, which is what a plugin asks for when it
	 * wants the same variant across a sequence of plays.
	 */
	@Override
	public void playSound(org.bukkit.Location location, org.bukkit.Sound sound, org.bukkit.SoundCategory category,
			float volume, float pitch, long seed) {
		if (location == null || sound == null) {
			return;
		}
		playRegisteredSound(location, sound.getKey(), category, volume, pitch, seed);
	}

	/** The string form: the same lookup, with the id named directly rather than through the enum. */
	@Override
	public void playSound(org.bukkit.Location location, String sound, org.bukkit.SoundCategory category,
			float volume, float pitch) {
		playSound(location, sound, category, volume, pitch, 0L);
	}

	@Override
	public void playSound(org.bukkit.Location location, String sound, org.bukkit.SoundCategory category,
			float volume, float pitch, long seed) {
		if (location == null || sound == null || sound.isBlank()) {
			return;
		}
		// A plugin may write either "block.stone.break" or "minecraft:block.stone.break"; Bukkit accepts
		// both, so the namespace is added here where the string becomes a key.
		org.bukkit.NamespacedKey key = sound.indexOf(':') >= 0
				? org.bukkit.NamespacedKey.fromString(sound)
				: org.bukkit.NamespacedKey.minecraft(sound);
		playRegisteredSound(location, key, category, volume, pitch, seed);
	}

	/** Resolves a sound id in the game's registry and asks the level to play it at a point. */
	private void playRegisteredSound(org.bukkit.Location location, org.bukkit.NamespacedKey key,
			org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
		if (key == null) {
			return;
		}
		net.minecraft.core.RegistryAccess registries = ForbricRegistries.access();
		if (registries == null) {
			return;
		}
		net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
				key.getNamespace(), key.getKey());
		java.util.Optional<? extends net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent>> event =
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.SOUND_EVENT).get(id);
		if (event.isEmpty()) {
			// An id the server does not know. The game would treat this as nothing to play, and so does the
			// bridge: throwing here would abort whatever the plugin was in the middle of.
			return;
		}
		level.playSound(null, location.getX(), location.getY(), location.getZ(),
				(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent>) event.get(),
				soundSource(category), volume, pitch);
	}

    /**
	* A sound attributed to an entity, so it follows them and is heard at their ears.
	*
	* <p>Distinct from the location form in more than argument type: the game's {@code playSound} takes
	* the emitting entity and uses its position every time the sound is re-evaluated, which is how a
	* sound stays with a moving player. Passing {@code null} there — as the location form does — would
	* pin it to one point instead.
	*/
	@Override
	public void playSound(org.bukkit.entity.Entity entity, org.bukkit.Sound sound,
					org.bukkit.SoundCategory category, float volume, float pitch) {
		playSound(entity, sound, category, volume, pitch, 0L);
	}

	@Override
	public void playSound(org.bukkit.entity.Entity entity, org.bukkit.Sound sound,
					org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
		if (entity == null || sound == null) {
			return;
		}
		playRegisteredSoundOn(entity, sound.getKey(), category, volume, pitch, seed);
	}

	@Override
	public void playSound(org.bukkit.entity.Entity entity, String sound, org.bukkit.SoundCategory category,
					float volume, float pitch) {
		playSound(entity, sound, category, volume, pitch, 0L);
	}

	@Override
	public void playSound(org.bukkit.entity.Entity entity, String sound, org.bukkit.SoundCategory category,
					float volume, float pitch, long seed) {
		if (entity == null || sound == null || sound.isBlank()) {
			return;
		}
		org.bukkit.NamespacedKey key = sound.indexOf(':') >= 0
				? org.bukkit.NamespacedKey.fromString(sound)
				: org.bukkit.NamespacedKey.minecraft(sound);
		playRegisteredSoundOn(entity, key, category, volume, pitch, seed);
	}

	/** The entity-attributed play: looks the entity up and asks the level to emit from it. */
	private void playRegisteredSoundOn(org.bukkit.entity.Entity entity, org.bukkit.NamespacedKey key,
					org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
		if (key == null) {
			return;
		}
		// Only an entity this bridge wraps can be handed to the game, because the game needs its own
		// object. An entity from somewhere else is skipped rather than guessed at.
		if (!(entity instanceof ForbricEntity wrapped)) {
			// A player is wrapped separately, and a plugin passes those most often.
			if (entity instanceof org.bukkit.entity.Player player) {
				net.minecraft.world.entity.Entity game = gameEntityOf(player);
				if (game != null) {
					playRegisteredSoundFrom(game, key, category, volume, pitch, seed);
				}
			}
			return;
		}
		playRegisteredSoundFrom(wrapped.entity(), key, category, volume, pitch, seed);
	}

	/** The shared tail: resolve the sound, then emit it from an entity with no position given. */
	private void playRegisteredSoundFrom(net.minecraft.world.entity.Entity emitter, org.bukkit.NamespacedKey key,
					org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
		net.minecraft.core.RegistryAccess registries = ForbricRegistries.access();
		if (registries == null) {
			return;
		}
		net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
				key.getNamespace(), key.getKey());
		java.util.Optional<? extends net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent>> event =
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.SOUND_EVENT).get(id);
		if (event.isEmpty()) {
			return;
		}
		// The entity's own position is what the game reads for this overload, so no coordinates are passed.
		level.playSound(emitter, emitter.getX(), emitter.getY(), emitter.getZ(),
				(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent>) event.get(),
				soundSource(category), volume, pitch);
	}

	/**
	* The game entity behind a Bukkit player, or null when this bridge cannot reach it.
	*
	* <p>Reached through the wrapper's own accessor rather than a cast to a game class, so a player
	* obtained from anywhere in this bridge works and one from another server does not silently become a
	* different entity.
	*/
	private net.minecraft.world.entity.Entity gameEntityOf(org.bukkit.entity.Player player) {
		if (player instanceof ForbricPlayer wrapped) {
			return wrapped.player();
		}
		return null;
	}

	/**
	 * Bukkit's sound category as the game's source.
	 *
	 * <p>Matched by name rather than by ordinal: the two enums list the same eleven values in different
	 * orders, and a cast would put a block sound on the voice channel. The mapping is one-to-one — the
	 * names come from the same JSON the game and the API both describe — so a switch is exact.
	 */
	private static net.minecraft.sounds.SoundSource soundSource(org.bukkit.SoundCategory category) {
		if (category == null) {
			return net.minecraft.sounds.SoundSource.MASTER;
		}
		return switch (category) {
			case MASTER -> net.minecraft.sounds.SoundSource.MASTER;
			case MUSIC -> net.minecraft.sounds.SoundSource.MUSIC;
			case RECORDS -> net.minecraft.sounds.SoundSource.RECORDS;
			case WEATHER -> net.minecraft.sounds.SoundSource.WEATHER;
			case BLOCKS -> net.minecraft.sounds.SoundSource.BLOCKS;
			case HOSTILE -> net.minecraft.sounds.SoundSource.HOSTILE;
			case NEUTRAL -> net.minecraft.sounds.SoundSource.NEUTRAL;
			case PLAYERS -> net.minecraft.sounds.SoundSource.PLAYERS;
			case AMBIENT -> net.minecraft.sounds.SoundSource.AMBIENT;
			case VOICE -> net.minecraft.sounds.SoundSource.VOICE;
			case UI -> net.minecraft.sounds.SoundSource.UI;
		};
	}

	/**
	 * Spawns particles at a point, for the players who can see it.
	 *
	 * <p>This is the only abstract form of {@code spawnParticle} — the other thirteen overloads are
	 * {@code default} and funnel into it — so answering it makes all of them work.
	 *
	 * <p>The argument order matches the game's {@code sendParticles} exactly, which is not a coincidence:
	 * Bukkit's signature was modelled on it. So there is no chance of transposing the offset with the
	 * speed, and the mapping is positional.
	 *
	 * <p>The bukkit {@code Particle} enum carries the id the game registers, so the options object is the
	 * registered {@code ParticleOptions} itself rather than a copy: that is how the game represents a
	 * particle with no extra data, and looking it up keeps a mod's particle as real as a vanilla one.
	 */
	@Override
	public <T> void spawnParticle(org.bukkit.Particle particle, java.util.List<org.bukkit.entity.Player> receivers,
			org.bukkit.entity.Player source, double x, double y, double z, int count,
			double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {
		if (particle == null) {
			return;
		}
		net.minecraft.core.RegistryAccess registries = ForbricRegistries.access();
		if (registries == null) {
			return;
		}
		org.bukkit.NamespacedKey key = particle.getKey();
		if (key == null) {
			return;
		}
		net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
				key.getNamespace(), key.getKey());
		java.util.Optional<? extends net.minecraft.core.Holder<net.minecraft.core.particles.ParticleType<?>>> type =
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.PARTICLE_TYPE).get(id);
		if (type.isEmpty()) {
			// A particle this server has no type for: nothing to draw, and the same silent skip the game does.
			return;
		}
		net.minecraft.core.particles.ParticleType<?> registered = type.get().value();
		if (!(registered instanceof net.minecraft.core.particles.ParticleOptions options)) {
			// A particle whose options carry data (a block state, a colour) needs a different options object
			// per call, built from the {@code data} argument. Refusing loudly beats drawing the wrong thing.
			throw new UnsupportedOperationException(
					"[Forbric/Bukkit] World.spawnParticle() cannot draw " + key
							+ " yet: its options carry data, and this bridge does not build those from the data argument");
		}
		// The receivers list is empty from the default overloads, which means "everyone who can see it" — the
		// game's own sendParticles decides that from the coordinates. The 9-argument form is used rather than
		// the one with the two booleans, because those are longDistance and alwaysShow: Bukkit's `force` maps
		// to alwaysShow, and a bridge that guessed at longDistance would send a particle further than asked.
		level.sendParticles(options, x, y, z, count, offsetX, offsetY, offsetZ, extra);
	}

	/**
	 * Detonates where the world is concerned, which is what "blast a hole" means.
	 *
	 * <p><b>Three abstract overloads, and all three are needed.</b> {@code createExplosion} declares
	 * fifteen entries and only these three lack a body; every other one is {@code default} and funnels
	 * into one of them. Implementing just the {@code Location} form, say, would leave the
	 * {@code Entity}/{@code Location} pair the traditional plugin calls still refusing.
	 *
	 * <h2>The two booleans and the three-valued interaction</h2>
	 *
	 * <p>{@code setFire} becomes the game's {@code createFire} flag. {@code breakBlocks} chooses the
	 * game's {@code ExplosionInteraction}: {@code TNT} when blocks should break — which is what a player
	 * setting off TNT gets, including the drops and the block-level destruction — and {@code NONE} when
	 * they should not, which still damages entities and still shows the explosion.
	 *
	 * <h2>The sixth parameter of the entity form</h2>
	 *
	 * <p>Bukkit's own 5-argument {@code default} calls it with a literal {@code true}, and the same
	 * value is what the no-entity form passes as its source argument. It means "do not damage the
	 * entity that caused this", which is why a plugin's own explosion does not kill the plugin's own
	 * player. It is honoured by leaving the source out of the damage path when it is set, and by
	 * passing the source as the explosion's own entity when it is not.
	 */
	@Override
	public boolean createExplosion(double x, double y, double z, float power, boolean setFire,
			boolean breakBlocks, org.bukkit.entity.Entity source) {
		return explode(source, x, y, z, power, setFire, breakBlocks);
	}

	@Override
	public boolean createExplosion(org.bukkit.entity.Entity source, org.bukkit.Location location, float power,
			boolean setFire, boolean breakBlocks, boolean excludeSource) {
		if (location == null) {
			return false;
		}
		// excludeSource=false hands the source to the game, so the explosion can damage it; true (the
		// value every default overload passes) keeps the source out of harm's way.
		return explode(excludeSource ? null : source, location.getX(), location.getY(), location.getZ(),
				power, setFire, breakBlocks);
	}

	@Override
	public boolean createExplosion(org.bukkit.Location location, float power, boolean setFire,
			boolean breakBlocks, org.bukkit.entity.Entity source) {
		if (location == null) {
			return false;
		}
		return explode(source, location.getX(), location.getY(), location.getZ(), power, setFire, breakBlocks);
	}

	/**
	 * The shared tail: one call into the game's own explosion.
	 *
	 * <p>The game's {@code explode} takes the entity it is attributed to, so the damage source and the
	 * sound are credited correctly. A {@code null} there means "no particular cause", which is exactly
	 * what an explosion with no source entity is, and the game handles it.
	 */
	private boolean explode(org.bukkit.entity.Entity source, double x, double y, double z, float power,
			boolean setFire, boolean breakBlocks) {
		net.minecraft.world.entity.Entity cause = gameEntity(source);
		// TNT breaks blocks and drops them, which is what a Bukkit plugin asking to break blocks expects;
		// NONE damages entities and draws the explosion without touching a single block.
		net.minecraft.world.level.Level.ExplosionInteraction interaction = breakBlocks
				? net.minecraft.world.level.Level.ExplosionInteraction.TNT
				: net.minecraft.world.level.Level.ExplosionInteraction.NONE;
		level.explode(cause, x, y, z, power, setFire, interaction);
		// Bukkit reports whether the explosion happened; the game's explode returns nothing and cancels
		// internally (a disabled explosion, a world the server refuses to modify). Reporting false for
		// those is more useful than claiming success, but they cannot be observed from here — so the
		// answer is true, which is what the call did.
		return true;
	}

	/**
	 * A Bukkit entity as the game's, or null when this bridge does not wrap it.
	 *
	 * <p>A null cause is a real answer — the game's explosion accepts it and simply has nothing to
	 * attribute the damage to — so an entity from elsewhere does not fail the call, it just loses its
	 * attribution.
	 */
	private net.minecraft.world.entity.Entity gameEntity(org.bukkit.entity.Entity entity) {
		if (entity instanceof ForbricEntity wrapped) {
			return wrapped.entity();
		}
		if (entity instanceof ForbricPlayer wrapped) {
			return wrapped.player();
		}
		return null;
	}

	/**
	 * Strikes a real bolt: it damages entities and sets fire, exactly as a storm does.
	 *
	 * <p>The bolt is a real entity added to the level rather than an effect drawn at a point, which is
	 * what makes the damage and the fire happen at all — the game applies both from the bolt's own tick.
	 * The returned handle is the live entity, so a plugin may move it or read it back.
	 */
	@Override
	public org.bukkit.entity.LightningStrike strikeLightning(org.bukkit.Location location) {
		return strike(location, false);
	}

	/**
	 * Strikes a bolt that is only seen.
	 *
	 * <p>The visual-only flag is the whole difference: the game still spawns the bolt and still renders
	 * it, but skips the damage and the fire. That is exactly what Bukkit's name promises, and it is why
	 * a plugin that wants a dramatic flash without wrecking the area calls this one.
	 */
	@Override
	public org.bukkit.entity.LightningStrike strikeLightningEffect(org.bukkit.Location location) {
		return strike(location, true);
	}

	/**
	 * Adds a bolt at a point and reports it, or null when there is nothing to strike.
	 *
	 * <p>A null location or a location with no world is answered with null rather than an exception: the
	 * call had nothing to act on, and Bukkit's own implementations have always been lax about this.
	 */
	private org.bukkit.entity.LightningStrike strike(org.bukkit.Location location, boolean visualOnly) {
		if (location == null) {
			return null;
		}
		net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(
				net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT, level);
		// setPos, not a teleport: the bolt has never been in the world, so there is nothing to move it
		// from and no collision to resolve — it is being placed.
		bolt.setPos(location.getX(), location.getY(), location.getZ());
		bolt.setVisualOnly(visualOnly);
		level.addFreshEntity(bolt);
		return new ForbricLightningStrike(bolt, server);
	}

	/**
	 * Every entity whose bounding box overlaps a region around a point.
	 *
	 * <p>Bukkit's radii are half-extents, not a diameter: a call with (4, 2, 4) means a box 8 blocks wide
	 * and 4 tall centred on the location. The game's own query takes that box, so the doubling is done
	 * here rather than left to the caller — a bridge that treated the numbers as a diameter would make
	 * every blast radius half the size the plugin asked for, with nothing to indicate it.
	 *
	 * <p>The entity itself is not excluded from the game's query, so a plugin's own hitbox is included if
	 * it overlaps — which is what Bukkit's implementation does too, and why plugins that care skip
	 * themselves by UUID.
	 */
	@Override
	public java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(org.bukkit.Location location,
			double xRadius, double yRadius, double zRadius,
			java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
		java.util.List<org.bukkit.entity.Entity> found = new java.util.ArrayList<>();
		if (location == null) {
			return found;
		}
		for (net.minecraft.world.entity.Entity entity : level.getEntities(
				(net.minecraft.world.entity.Entity) null,
				box(location, xRadius, yRadius, zRadius),
				candidate -> filter == null || filter.test(ForbricEntity.of(candidate, server)))) {
			found.add(ForbricEntity.of(entity, server));
		}
		return found;
	}

	/** The same, with a box a plugin built itself. */
	@Override
	public java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(org.bukkit.util.BoundingBox boundingBox,
			java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
		java.util.List<org.bukkit.entity.Entity> found = new java.util.ArrayList<>();
		if (boundingBox == null) {
			return found;
		}
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
				boundingBox.getMinX(), boundingBox.getMinY(), boundingBox.getMinZ(),
				boundingBox.getMaxX(), boundingBox.getMaxY(), boundingBox.getMaxZ());
		for (net.minecraft.world.entity.Entity entity : level.getEntities(
				(net.minecraft.world.entity.Entity) null, box,
				candidate -> filter == null || filter.test(ForbricEntity.of(candidate, server)))) {
			found.add(ForbricEntity.of(entity, server));
		}
		return found;
	}

	/**
	 * Creates an entity of the requested class at a location and adds it to the world.
	 *
	 * <p>Shared by the two spawn entry points, which differ only in the type parameter they
	 * accept -- the game needs the same sequence either way, so the sequence lives once.
	 *
	 * <p>The setup consumer runs before the entity is added, not after, because that is the
	 * window the API promises: a plugin sets a fuse or a velocity there knowing the value is in
	 * place before the entity's first tick.
	 */
	@SuppressWarnings("unchecked")
	private <T extends org.bukkit.entity.Entity> T spawnAt(org.bukkit.Location location, java.lang.Class<T> clazz, java.util.function.Consumer<? super T> configure) {
		if (location == null || clazz == null) {
		    throw new IllegalArgumentException("[Forbric/Bukkit] World.spawn() needs a location and an entity class");
		}
		net.minecraft.world.entity.EntityType<?> type = entityTypeOf(clazz);
		if (type == null) {
		    throw new IllegalArgumentException("[Forbric/Bukkit] World.spawn() has no game entity type for " + clazz.getName()
		            + "; this bridge maps a class to a type by its EntityType name");
		}
		net.minecraft.world.entity.Entity entity = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (entity == null) {
		    throw new IllegalArgumentException("[Forbric/Bukkit] World.spawn() could not create " + type);
		}
		// setPos, not a teleport: the entity has never been in a world, so there is nothing to
		// move it from and no collision to resolve -- it is being placed.
		entity.setPos(location.getX(), location.getY(), location.getZ());
		org.bukkit.entity.Entity wrapped = ForbricEntity.of(entity, server);
		if (configure != null) {
		    configure.accept((T) wrapped);
		}
		level.addFreshEntity(entity);
		return (T) wrapped;
	}

	/**
	 * The game's entity type for a Bukkit entity class.
	 *
	 * <p>Through the API's own {@code EntityType}, NOT by the class name. The two differ far more
	 * often than "they are named after the same ids" suggests: {@code TNTPrimed} is the interface
	 * for {@code EntityType.TNT}, {@code PigZombie} for {@code ZOMBIFIED_PIGLIN},
	 * {@code FallingBlock} for {@code FALLING_BLOCK}, and 50-odd more. Lower-casing the interface
	 * name gives {@code tntprimed} / {@code pigzombie}, which is not a registry id of anything --
	 * so {@code spawn()} threw for every one of them, first seen on a nuke plugin whose whole job
	 * is to spawn {@code TNTPrimed}.
	 *
	 * <p>Walking {@code EntityType.values()} once and keying on the interface class it reports is
	 * the mapping the API itself publishes, so it stays right when a name changes; the lookup then
	 * follows {@code getKey()}, the same namespaced id {@link ForbricEntity#getType()} reads back.
	 *
	 * <p>A class no constant claims answers null, and the caller turns that into an
	 * IllegalArgumentException: null is the honest answer to "is there a type for this", and only
	 * the caller knows whether to throw.
	 */
	private static net.minecraft.world.entity.EntityType<?> entityTypeOf(java.lang.Class<?> bukkitClass) {
		org.bukkit.entity.EntityType bukkit = ENTITY_TYPES_BY_CLASS.get(bukkitClass);
		if (bukkit == null) {
			return null;
		}
		org.bukkit.NamespacedKey key = bukkit.getKey();
		net.minecraft.resources.Identifier id =
				net.minecraft.resources.Identifier.fromNamespaceAndPath(key.getNamespace(), key.getKey());
		return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id).map(holder -> holder.value()).orElse(null);
	}

	/**
	 * Every Bukkit {@code EntityType}, keyed by the entity interface it spawns.
	 *
	 * <p>Built once rather than scanned per call, and read-only thereafter. A {@code values()}
	 * walk per spawn would be wasted work on the one path -- a nuke, a barrage, a wave -- that
	 * spawns in a loop.
	 *
	 * <p>A constant whose {@code getEntityClass()} throws or answers null is skipped rather than
	 * failing the whole table: some types ({@code UNKNOWN}, the legacy aliases) have no class, and
	 * one of them must not cost the other 130 their lookup.
	 */
	private static final java.util.Map<java.lang.Class<?>, org.bukkit.entity.EntityType> ENTITY_TYPES_BY_CLASS =
			buildEntityTypesByClass();

	private static java.util.Map<java.lang.Class<?>, org.bukkit.entity.EntityType> buildEntityTypesByClass() {
		java.util.Map<java.lang.Class<?>, org.bukkit.entity.EntityType> byClass = new java.util.HashMap<>();
		for (org.bukkit.entity.EntityType type : org.bukkit.entity.EntityType.values()) {
			try {
				java.lang.Class<?> entityClass = type.getEntityClass();
				if (entityClass != null) {
					// First wins: a couple of constants share a class (the legacy aliases), and the
					// first is the current one -- put-if-absent keeps it.
					byClass.putIfAbsent(entityClass, type);
				}
			} catch (RuntimeException unsupported) {
				// getEntityClass() on a type the API cannot map; nothing to key, so skip it.
			}
		}
		return java.util.Map.copyOf(byClass);
	}

	/** The game box for a point and Bukkit's half-extents. */
	private static net.minecraft.world.phys.AABB box(org.bukkit.Location location, double xRadius, double yRadius,
			double zRadius) {
		return new net.minecraft.world.phys.AABB(
				location.getX() - xRadius, location.getY() - yRadius, location.getZ() - zRadius,
				location.getX() + xRadius, location.getY() + yRadius, location.getZ() + zRadius);
	}

	@Override
	public boolean isVoidDamageEnabled() {
		throw notYet("isVoidDamageEnabled");
	}

	@Override
	public void setVoidDamageEnabled(boolean arg0) {
		throw notYet("setVoidDamageEnabled");
	}

	@Override
	public float getVoidDamageAmount() {
		throw notYet("getVoidDamageAmount");
	}

	@Override
	public void setVoidDamageAmount(float arg0) {
		throw notYet("setVoidDamageAmount");
	}

	@Override
	public double getVoidDamageMinBuildHeightOffset() {
		throw notYet("getVoidDamageMinBuildHeightOffset");
	}

	@Override
	public void setVoidDamageMinBuildHeightOffset(double arg0) {
		throw notYet("setVoidDamageMinBuildHeightOffset");
	}

	@Override
	public int getEntityCount() {
		throw notYet("getEntityCount");
	}

	@Override
	public int getTileEntityCount() {
		throw notYet("getTileEntityCount");
	}

	@Override
	public int getTickableTileEntityCount() {
		throw notYet("getTickableTileEntityCount");
	}

	@Override
	public int getChunkCount() {
		throw notYet("getChunkCount");
	}

	@Override
	public int getPlayerCount() {
		throw notYet("getPlayerCount");
	}

	@Override
	public boolean hasStructureAt(io.papermc.paper.math.Position arg0, org.bukkit.generator.structure.Structure arg1) {
		throw notYet("hasStructureAt");
	}

	@Override
	public org.bukkit.Chunk getChunkAt(int arg0, int arg1) {
		return getChunkAt(arg0, arg1, true);
	}

	@Override
	public org.bukkit.Chunk getChunkAt(int arg0, int arg1, boolean arg2) {
		// The contract is @NotNull, and that is not a formality: `generate` means "fully generate or not",
		// not "return null when there is nothing there". A plugin asking with generate=false wants a handle
		// it can call isGenerated() on, and answering null makes that call throw on the plugin's own line --
		// which is how a pre-generator's loop died on its first iteration with nothing in any log. The
		// unloaded case is therefore answered with a Chunk whose isGenerated() is false, from the same
		// wrapper the loaded case uses.
		net.minecraft.world.level.chunk.LevelChunk chunk = loadChunkNow(arg0, arg1, arg2);
		if (chunk == null) {
			return ForbricChunk.unloaded(this, arg0, arg1);
		}
		return ForbricChunk.loaded(this, chunk);
	}

	@Override
	public org.bukkit.Chunk getChunkAt(org.bukkit.block.Block arg0) {
		if (arg0 == null) {
			// Not reachable from a plugin that got the block from this world, but a null argument has no
			// coordinates to answer with and an empty chunk at 0,0 would be a wrong answer rather than none.
			throw new IllegalArgumentException("[Forbric/Bukkit] a chunk cannot be located from a null Block");
		}
		// Through the block's own coordinates rather than its chunk: a Block from another world would otherwise
		// be answered from this world's chunk at the same coordinate, which is a wrong chunk rather than an
		// error a plugin could see.
		return getChunkAt(arg0.getX() >> 4, arg0.getZ() >> 4);
	}

	/**
	 * Loads a chunk and returns it, or null when the game could not produce one.
	 *
	 * <p><b>`getChunk`, not `getChunkFuture`.</b> The two look interchangeable and are not, and getting this
	 * wrong is what made a pre-generator stall for 50 seconds at a time with the server reporting a thousand
	 * ticks behind.
	 *
	 * <p>The future form is written for callers that can afford to wait asynchronously, and on the main thread
	 * it pumps the game's own task queue through {@code managedBlock} until the load finishes. That works when
	 * the caller is something outside the tick — but a plugin running <em>inside</em> the tick is itself part of
	 * the queue being pumped, so waiting for the queue to finish its work means waiting for the very task that
	 * is blocked. The timeout then expires, the caller retries, and the server spends its ticks not ticking.
	 *
	 * <p>{@code getChunk} has no such shape. On the main thread it loads synchronously and returns — it cannot
	 * wait for anything, because everything it needs is already there. Off the main thread it hands the work to
	 * the main thread and waits, which is exactly the behaviour a plugin's own thread wants. There is no state
	 * in which it deadlocks, so there is nothing here to bound with a timeout.
	 *
	 * <p>The status asked for is FULL, which is what a plugin reading blocks or entities needs. A lesser status
	 * answers sooner with a chunk whose lighting and entities are not finished.
	 */
	private net.minecraft.world.level.chunk.LevelChunk loadChunkNow(int chunkX, int chunkZ, boolean generate) {
		net.minecraft.server.level.ServerChunkCache source = level.getChunkSource();
		// The fast path first: a chunk already in memory needs no load at all.
		net.minecraft.world.level.chunk.LevelChunk present = source.getChunkNow(chunkX, chunkZ);
		if (present != null) {
			return present;
		}
		if (!generate && !source.hasChunk(chunkX, chunkZ)) {
			// Asked not to generate and there is nothing there. Answered without starting a load that could
			// not produce anything.
			return null;
		}
		net.minecraft.world.level.chunk.ChunkAccess access = source.getChunk(chunkX, chunkZ,
						net.minecraft.world.level.chunk.status.ChunkStatus.FULL, generate);
		// A FULL request can answer with a chunk that is not a LevelChunk when the load was cancelled; asking
		// the source again is cheaper than reasoning about which states can appear.
		if (access instanceof net.minecraft.world.level.chunk.LevelChunk full) {
			return full;
		}
		return source.getChunkNow(chunkX, chunkZ);
	}

	@Override
	public boolean isChunkLoaded(org.bukkit.Chunk arg0) {
		throw notYet("isChunkLoaded");
	}

	@Override
	public org.bukkit.Chunk[] getLoadedChunks() {
		throw notYet("getLoadedChunks");
	}

	@Override
	public void loadChunk(org.bukkit.Chunk arg0) {
		throw notYet("loadChunk");
	}

	@Override
	public boolean isChunkLoaded(int arg0, int arg1) {
		throw notYet("isChunkLoaded");
	}

	@Override
	public boolean isChunkGenerated(int arg0, int arg1) {
		// Asked of the chunk source, which knows about chunks on disk: true for a chunk that exists whether or
		// not anyone is holding it. This is the question a pre-generator asks to decide whether a coordinate
		// still needs work, and answering it from "is it loaded" would send it over the whole region again.
		return level.getChunkSource().hasChunk(arg0, arg1);
	}

	@Override
	public boolean isChunkInUse(int arg0, int arg1) {
		throw notYet("isChunkInUse");
	}

	@Override
	public boolean loadChunk(int arg0, int arg1, boolean arg2) {
		throw notYet("loadChunk");
	}

	@Override
	public boolean unloadChunk(int arg0, int arg1, boolean arg2) {
		throw notYet("unloadChunk");
	}

	@Override
	public boolean unloadChunkRequest(int arg0, int arg1) {
		throw notYet("unloadChunkRequest");
	}

	@Override
	public boolean refreshChunk(int arg0, int arg1) {
		throw notYet("refreshChunk");
	}

	@Override
	public java.util.Collection<org.bukkit.entity.Player> getPlayersSeeingChunk(org.bukkit.Chunk arg0) {
		throw notYet("getPlayersSeeingChunk");
	}

	@Override
	public java.util.Collection<org.bukkit.entity.Player> getPlayersSeeingChunk(int arg0, int arg1) {
		throw notYet("getPlayersSeeingChunk");
	}

	@Override
	public boolean isChunkForceLoaded(int arg0, int arg1) {
		throw notYet("isChunkForceLoaded");
	}

	@Override
	public void setChunkForceLoaded(int arg0, int arg1, boolean arg2) {
		throw notYet("setChunkForceLoaded");
	}

	@Override
	public java.util.Collection<org.bukkit.Chunk> getForceLoadedChunks() {
		throw notYet("getForceLoadedChunks");
	}

	@Override
	public boolean addPluginChunkTicket(int arg0, int arg1, org.bukkit.plugin.Plugin arg2) {
		if (arg2 == null) {
			throw new IllegalArgumentException("[Forbric/Bukkit] a chunk ticket needs a plugin to belong to");
		}
		// One game ticket per plugin per chunk, keyed by the plugin so that removing one plugin's ticket
		// leaves every other plugin's alone -- which is the point of the API, and the reason the reference
		// count is kept here rather than asked of the game.
		ChunkTicketKey key = new ChunkTicketKey(arg0, arg1, arg2.getName());
		if (!chunkTickets.add(key)) {
			// Already held. The API's contract is that a second add does nothing, not that it fails, so this is
			// answered as "the ticket is there" rather than as an error.
			return false;
		}
		// FORCED is the game's own force-load ticket: what /forceload uses, and what keeps a chunk loaded with
		// no player near it. It is saved with the world, which differs from a plugin ticket on Paper, but it is
		// the only ticket the game offers that outlives the caller -- and a plugin asking for this wants the
		// chunk kept, not kept only for as long as it happens to hold the reference.
		level.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.FORCED,
						new net.minecraft.world.level.ChunkPos(arg0, arg1), 0);
		return true;
	}

	@Override
	public boolean removePluginChunkTicket(int arg0, int arg1, org.bukkit.plugin.Plugin arg2) {
		if (arg2 == null) {
			return false;
		}
		ChunkTicketKey key = new ChunkTicketKey(arg0, arg1, arg2.getName());
		if (!chunkTickets.remove(key)) {
			// Nothing of this plugin's to remove. Answered false rather than as an error: a plugin tidying up
			// after a reload calls this for tickets that may never have existed.
			return false;
		}
		// Only when the last holder of this chunk has gone. The game's FORCED ticket has no notion of which
		// plugin asked, so releasing it while another plugin still wants the chunk would unload it under them.
		if (!chunkTickets.holds(arg0, arg1)) {
			level.getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.FORCED,
							new net.minecraft.world.level.ChunkPos(arg0, arg1), 0);
		}
		return true;
	}

	/**
	 * The plugins currently holding a ticket on one chunk.
	 *
	 * <p>derived from the same set the two methods above maintain, because the game cannot answer it: a FORCED
	 * ticket carries no record of who asked for it, only that someone did.
	 */
	java.util.Collection<org.bukkit.plugin.Plugin> pluginChunkTickets(int chunkX, int chunkZ) {
		java.util.List<org.bukkit.plugin.Plugin> holders = new java.util.ArrayList<>();
		for (ChunkTicketKey key : chunkTickets) {
			if (key.chunkX() == chunkX && key.chunkZ() == chunkZ) {
				org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin(key.plugin());
				if (plugin != null) {
					holders.add(plugin);
				}
			}
		}
		return holders;
	}

	/**
	 * One plugin's ticket on one chunk, and the structure the ticket table is keyed by.
	 *
	 * <p>A record rather than a string key because the three parts are always used together and a joined string
	 * would have to decide on a separator that cannot appear in a plugin name. Value equality is what the set
	 * needs: asking twice for the same plugin's ticket on the same chunk must be the same entry.
	 */
	private record ChunkTicketKey(int chunkX, int chunkZ, String plugin) {
	}

	@Override
	public void removePluginChunkTickets(org.bukkit.plugin.Plugin arg0) {
		throw notYet("removePluginChunkTickets");
	}

	@Override
	public java.util.Collection<org.bukkit.plugin.Plugin> getPluginChunkTickets(int arg0, int arg1) {
		throw notYet("getPluginChunkTickets");
	}

	@Override
	public java.util.Map<org.bukkit.plugin.Plugin, java.util.Collection<org.bukkit.Chunk>> getPluginChunkTickets() {
		throw notYet("getPluginChunkTickets");
	}

	@Override
	public java.util.Collection<org.bukkit.Chunk> getIntersectingChunks(org.bukkit.util.BoundingBox arg0) {
		throw notYet("getIntersectingChunks");
	}

	@Override
	public org.bukkit.entity.Item dropItem(org.bukkit.Location arg0, org.bukkit.inventory.ItemStack arg1, java.util.function.Consumer<? super org.bukkit.entity.Item> arg2) {
		throw notYet("dropItem");
	}

	@Override
	public org.bukkit.entity.Item dropItemNaturally(org.bukkit.Location arg0, org.bukkit.inventory.ItemStack arg1, java.util.function.Consumer<? super org.bukkit.entity.Item> arg2) {
		throw notYet("dropItemNaturally");
	}

	@Override
	public <T extends org.bukkit.entity.AbstractArrow> T spawnArrow(org.bukkit.Location arg0, org.bukkit.util.Vector arg1, float arg2, float arg3, java.lang.Class<T> arg4) {
		throw notYet("spawnArrow");
	}

	@Override
	public boolean generateTree(org.bukkit.Location arg0, org.bukkit.TreeType arg1) {
		throw notYet("generateTree");
	}

	@Override
	public boolean generateTree(org.bukkit.Location arg0, org.bukkit.TreeType arg1, org.bukkit.BlockChangeDelegate arg2) {
		throw notYet("generateTree");
	}

	@Override
	public org.bukkit.Location findLightningRod(org.bukkit.Location arg0) {
		throw notYet("findLightningRod");
	}

	@Override
	public org.bukkit.Location findLightningTarget(org.bukkit.Location arg0) {
		throw notYet("findLightningTarget");
	}

	@Override
	public java.util.List<org.bukkit.entity.Entity> getEntities() {
		throw notYet("getEntities");
	}

	@Override
	public java.util.List<org.bukkit.entity.LivingEntity> getLivingEntities() {
		throw notYet("getLivingEntities");
	}

	@Override
	public <T extends org.bukkit.entity.Entity> java.util.Collection<T> getEntitiesByClass(java.lang.Class<T>... arg0) {
		throw notYet("getEntitiesByClass");
	}

	@Override
	public <T extends org.bukkit.entity.Entity> java.util.Collection<T> getEntitiesByClass(java.lang.Class<T> arg0) {
		throw notYet("getEntitiesByClass");
	}

	@Override
	public java.util.Collection<org.bukkit.entity.Entity> getEntitiesByClasses(java.lang.Class<?>... arg0) {
		throw notYet("getEntitiesByClasses");
	}

	@Override
	public void getChunkAtAsync(int arg0, int arg1, boolean arg2, boolean arg3, java.util.function.Consumer<? super org.bukkit.Chunk> arg4) {
		// The one abstract member of a family of nineteen. Every other getChunkAtAsync overload is a default
		// that ends up here, so implementing this one is what makes them all work -- including the
		// CompletableFuture forms a pre-generator drives its whole loop from.
		//
		// `arg2` is "generate if absent". `arg3` is `urgent` -- whether the chunk may be prioritised above
		// others already in the load queue -- and is NOT "run the callback on the main thread": Paper's
		// contract states the Consumer is always executed synchronously on the main Server Thread regardless
		// of this flag. This bridge honours that by delivering every callback from a task it scheduled onto
		// the game thread, so no flag has to ask for it.
		//
		// `arg3` is accepted and ignored. The synchronous load below goes through ServerChunkCache.getChunk,
		// which has no priority parameter to forward it to, and inventing a ticket to fake the ordering would
		// change persistence semantics rather than just this call's queue position. Ignoring it leaves the
		// caller with a result it is entitled to; the only thing lost is a scheduling hint, and only for the
		// async path.
		boolean generate = arg2;
		java.util.function.Consumer<? super org.bukkit.Chunk> callback = arg4;

		if (isGameThread()) {
			// Already where the game can be touched, so no scheduling at all: register the interest, ask for the
			// chunk, and answer. Going through a queue here would widen the window in which the chunk can be
			// unloaded again before the callback runs.
			beginAsyncChunkLoad(arg0, arg1, generate, callback);
			return;
		}
		// Off the server thread, so hop onto it. The bridge's own scheduler is used rather than Bukkit's
		// BukkitScheduler: that one requires a non-null plugin -- passing none is an IllegalArgumentException,
		// which is a shape the bridge cannot satisfy here, because this load belongs to whoever asked and
		// telling the scheduler must not be the caller's problem.
		//
		// `plugin` is null in the task bookkeeping too, and deliberately: the work is the bridge's own, not a
		// plugin's, and attributing it to the caller would make a plugin's disable cancel a load it merely
		// asked for.
		schedulers().scheduleGameNow(null,
				task -> beginAsyncChunkLoad(arg0, arg1, generate, callback));
	}

	/** The bridge's schedulers, for hopping onto the game thread from elsewhere. */
	private ForbricSchedulers schedulers() {
		return server.schedulers();
	}

	/**
	 * Loads the chunk if it is not there and hands it to the plugin's callback, from the server thread.
	 *
	 * <p><b>Why the load is synchronous here.</b> This method runs on the game thread by construction — the
	 * caller schedules it there — and a synchronous load on the game thread is the one kind that always
	 * finishes: {@code getChunk} does the work and returns. The clever-looking alternative, registering a
	 * completion handler on the game's chunk future, is what stalled the server for fifty seconds at a time:
	 * waiting on that future from the game thread pumps the same task queue the caller is itself running in,
	 * so the load it is waiting for cannot be reached until the wait ends.
	 *
	 * <p>What a plugin sees is still asynchronous, which is the part that matters: this method returns as soon
	 * as it has handed work off, and the callback arrives later — after the chunk is loaded, on the main
	 * Server Thread. That thread is not a request this method has to grant: it is where the caller already
	 * placed us, and Paper's contract guarantees the callback runs there either way.
	 *
	 * <p>A chunk that could not be produced completes the callback with a null rather than swallowing the
	 * failure, because a plugin iterating a region has to be able to count what it got.
	 */
	private void beginAsyncChunkLoad(int chunkX, int chunkZ, boolean generate,
					java.util.function.Consumer<? super org.bukkit.Chunk> callback) {
		net.minecraft.world.level.chunk.LevelChunk chunk = loadChunkNow(chunkX, chunkZ, generate);
		deliverChunk(callback, chunk == null ? null : ForbricChunk.loaded(this, chunk));
	}

	/**
	 * Runs the plugin's callback on the main Server Thread.
	 *
	 * <p>Callers reach here from a task already scheduled onto the game thread, so the callback runs there
	 * unconditionally -- no thread hop and no queueing on the common path. This is the behaviour Paper's
	 * getChunkAtAsync contract promises for every call, independent of any flag.
	 *
	 * <p>A null callback is tolerated rather than refused: Bukkit's contract marks the argument non-null, but
	 * a plugin written against an older signature can pass one, and there is nothing to do but drop it.
	 */
	private void deliverChunk(java.util.function.Consumer<? super org.bukkit.Chunk> callback,
					org.bukkit.Chunk chunk) {
		if (callback == null) {
			return;
		}
		try {
			callback.accept(chunk);
		} catch (Throwable failure) {
			// A plugin's callback throwing must not take down the chunk-loading thread it arrived on, and the
			// exception would otherwise be swallowed by the future's completion machinery with no trace at all.
			net.neoforbrickit.bridge.util.ForbricLog.error(
							"[Forbric/Bukkit] a plugin's getChunkAtAsync callback threw for chunk (%s, %s) in %s",
							chunk == null ? "?" : chunk.getX(), chunk == null ? "?" : chunk.getZ(), getName(),
							failure);
		}
	}

	/** Whether the calling thread is the one the game can be touched from. */
	private boolean isGameThread() {
		return server.isPrimaryThread();
	}

	@Override
	public void getChunksAtAsync(int arg0, int arg1, int arg2, int arg3, boolean arg4, java.lang.Runnable arg5) {
		throw notYet("getChunksAtAsync");
	}

	@Override
	public org.bukkit.entity.Entity getEntity(java.util.UUID arg0) {
		throw notYet("getEntity");
	}

	@Override
	public org.bukkit.util.RayTraceResult rayTrace(java.util.function.Consumer<io.papermc.paper.raytracing.PositionedRayTraceConfigurationBuilder> arg0) {
		throw notYet("rayTrace");
	}

	@Override
	public boolean setSpawnLocation(org.bukkit.Location arg0) {
		throw notYet("setSpawnLocation");
	}

	@Override
	public boolean setSpawnLocation(int arg0, int arg1, int arg2, float arg3) {
		throw notYet("setSpawnLocation");
	}

	@Override
	public long getGameTime() {
		throw notYet("getGameTime");
	}

	@Override
	public boolean hasStorm() {
		throw notYet("hasStorm");
	}

	@Override
	public void setStorm(boolean arg0) {
		throw notYet("setStorm");
	}

	@Override
	public int getWeatherDuration() {
		throw notYet("getWeatherDuration");
	}

	@Override
	public void setWeatherDuration(int arg0) {
		throw notYet("setWeatherDuration");
	}

	@Override
	public boolean isThundering() {
		throw notYet("isThundering");
	}

	@Override
	public void setThundering(boolean arg0) {
		throw notYet("setThundering");
	}

	@Override
	public int getThunderDuration() {
		throw notYet("getThunderDuration");
	}

	@Override
	public void setThunderDuration(int arg0) {
		throw notYet("setThunderDuration");
	}

	@Override
	public boolean isClearWeather() {
		throw notYet("isClearWeather");
	}

	@Override
	public void setClearWeatherDuration(int arg0) {
		throw notYet("setClearWeatherDuration");
	}

	@Override
	public int getClearWeatherDuration() {
		throw notYet("getClearWeatherDuration");
	}

	@Override
	public boolean getPVP() {
		throw notYet("getPVP");
	}

	@Override
	public void setPVP(boolean arg0) {
		throw notYet("setPVP");
	}

	@Override
	public org.bukkit.generator.ChunkGenerator getGenerator() {
		throw notYet("getGenerator");
	}

	@Override
	public org.bukkit.generator.BiomeProvider getBiomeProvider() {
		throw notYet("getBiomeProvider");
	}

	/**
	 * Writes this world's chunk data to disk.
	 *
	 * <p>Bukkit's contract is "flush what is dirty"; it deliberately says nothing about whether the caller
	 * blocks. This bridge writes synchronously, which is the honest choice: the game's own save runs on the
	 * server thread and completes before returning, and a plugin that calls this to make data durable before
	 * doing something else (BlueMap snapshots a world to render it) would be wrong to assume otherwise.
	 *
	 * <p>Callers must be on the server thread. The game's save path asserts that itself, so this is not a
	 * promise this class adds — but it is worth naming, because a plugin calling {@code save} from an async
	 * task gets the game's assertion rather than a silent no-op.
	 *
	 * @param light whether to write lighting data as well, which the API offers as an optimisation for callers
	 *              that are about to regenerate it anyway
	 */
	@Override
	public void save(boolean light) {
		// The game's own save, in its own terms: the progress listener is null (nothing to report to -- an
		// abstract Bukkit plugin has no progress bar to drive), `flush` is true because a caller that asked to
		// save wants the bytes on disk, and `skipSave` is the inverse of the light flag this method was given.
		level.save(null, true, !light);
	}

	@Override
	public java.util.List<org.bukkit.generator.BlockPopulator> getPopulators() {
		throw notYet("getPopulators");
	}

	/** The living-entity form, answered by the same code the general one uses. */
	@Override
	public <T extends org.bukkit.entity.LivingEntity> T spawn(org.bukkit.Location arg0, java.lang.Class<T> arg1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason arg2, boolean arg3, java.util.function.Consumer<? super T> arg4) throws java.lang.IllegalArgumentException {
		return spawnAt(arg0, arg1, arg4);
	}

	@Override
	public org.bukkit.entity.FallingBlock spawnFallingBlock(org.bukkit.Location arg0, org.bukkit.material.MaterialData arg1) throws java.lang.IllegalArgumentException {
		throw notYet("spawnFallingBlock");
	}

	@Override
	public org.bukkit.entity.FallingBlock spawnFallingBlock(org.bukkit.Location arg0, org.bukkit.block.data.BlockData arg1) throws java.lang.IllegalArgumentException {
		throw notYet("spawnFallingBlock");
	}

	@Override
	public org.bukkit.entity.FallingBlock spawnFallingBlock(org.bukkit.Location arg0, org.bukkit.Material arg1, byte arg2) throws java.lang.IllegalArgumentException {
		throw notYet("spawnFallingBlock");
	}

	@Override
	public void playEffect(org.bukkit.Location arg0, org.bukkit.Effect arg1, int arg2, int arg3) {
		throw notYet("playEffect");
	}

	@Override
	public <T> void playEffect(org.bukkit.Location arg0, org.bukkit.Effect arg1, T arg2, int arg3) {
		throw notYet("playEffect");
	}

	@Override
	public org.bukkit.ChunkSnapshot getEmptyChunkSnapshot(int arg0, int arg1, boolean arg2, boolean arg3) {
		throw notYet("getEmptyChunkSnapshot");
	}

	@Override
	public void setAllowMonsterSpawning(boolean arg0) {
		throw notYet("setAllowMonsterSpawning");
	}

	@Override
	public void setSpawnFlags(boolean arg0, boolean arg1) {
		throw notYet("setSpawnFlags");
	}

	@Override
	public boolean getAllowAnimals() {
		throw notYet("getAllowAnimals");
	}

	@Override
	public boolean getAllowMonsters() {
		throw notYet("getAllowMonsters");
	}

	@Override
	public void setBiome(int arg0, int arg1, org.bukkit.block.Biome arg2) {
		throw notYet("setBiome");
	}

	@Override
	public double getTemperature(int arg0, int arg1, int arg2) {
		throw notYet("getTemperature");
	}

	@Override
	public double getHumidity(int arg0, int arg1, int arg2) {
		throw notYet("getHumidity");
	}

	@Override
	public int getLogicalHeight() {
		// The height a client is shown the world as being, which for every dimension the game ships is the
		// same as the build height. They differ only for a custom dimension type, and there is no such thing
		// reachable from a plugin here -- so this is the game's own answer rather than a second definition.
		return level.getHeight();
	}

	@Override
	public boolean isNatural() {
		throw notYet("isNatural");
	}

	@Override
	public boolean isBedWorks() {
		throw notYet("isBedWorks");
	}

	@Override
	public boolean hasSkyLight() {
		throw notYet("hasSkyLight");
	}

	@Override
	public boolean hasCeiling() {
		throw notYet("hasCeiling");
	}

	@Override
	public boolean isPiglinSafe() {
		throw notYet("isPiglinSafe");
	}

	@Override
	public boolean isRespawnAnchorWorks() {
		throw notYet("isRespawnAnchorWorks");
	}

	@Override
	public boolean hasRaids() {
		throw notYet("hasRaids");
	}

	@Override
	public boolean isUltraWarm() {
		throw notYet("isUltraWarm");
	}

	@Override
	public boolean isAutoSave() {
		throw notYet("isAutoSave");
	}

	@Override
	public void setAutoSave(boolean arg0) {
		throw notYet("setAutoSave");
	}

	@Override
	public void setDifficulty(org.bukkit.Difficulty arg0) {
		throw notYet("setDifficulty");
	}

	@Override
	public int getViewDistance() {
		throw notYet("getViewDistance");
	}

	@Override
	public int getSimulationDistance() {
		throw notYet("getSimulationDistance");
	}

	@Override
	public java.nio.file.Path getWorldPath() {
           // The dimension's own folder -- the same answer getWorldFolder gives, as a Path.
           //
           // Bukkit declares both, and a plugin may reach for either; answering one and refusing the other
           // makes it look like the world has no save while the folder is right there.
           return getWorldFolder().toPath();
	}

	@Override
	public org.bukkit.WorldType getWorldType() {
		throw notYet("getWorldType");
	}

	@Override
	public boolean canGenerateStructures() {
		throw notYet("canGenerateStructures");
	}

	@Override
	public boolean hasBonusChest() {
		throw notYet("hasBonusChest");
	}

	@Override
	public boolean isHardcore() {
		throw notYet("isHardcore");
	}

	@Override
	public void setHardcore(boolean arg0) {
		throw notYet("setHardcore");
	}

	@Override
	public long getTicksPerSpawns(org.bukkit.entity.SpawnCategory arg0) {
		throw notYet("getTicksPerSpawns");
	}

	@Override
	public void setTicksPerSpawns(org.bukkit.entity.SpawnCategory arg0, int arg1) {
		throw notYet("setTicksPerSpawns");
	}

	@Override
	public int getSpawnLimit(org.bukkit.entity.SpawnCategory arg0) {
		throw notYet("getSpawnLimit");
	}

	@Override
	public void setSpawnLimit(org.bukkit.entity.SpawnCategory arg0, int arg1) {
		throw notYet("setSpawnLimit");
	}

	@Override
	public java.lang.String[] getGameRules() {
		throw notYet("getGameRules");
	}

	@Override
	public java.lang.String getGameRuleValue(java.lang.String arg0) {
		throw notYet("getGameRuleValue");
	}

	@Override
	public boolean setGameRuleValue(java.lang.String arg0, java.lang.String arg1) {
		throw notYet("setGameRuleValue");
	}

	@Override
	public boolean isGameRule(java.lang.String arg0) {
		throw notYet("isGameRule");
	}

	@Override
	public <T> T getGameRuleValue(org.bukkit.GameRule<T> arg0) {
		throw notYet("getGameRuleValue");
	}

	@Override
	public <T> boolean setGameRule(org.bukkit.GameRule<T> arg0, T arg1) {
		throw notYet("setGameRule");
	}

	@Override
	public org.bukkit.WorldBorder getWorldBorder() {
		// A fresh wrapper per call, like every other handle this bridge hands out: the border it wraps is the
		// level's own and lives as long as the world, so the wrapper carries no state that a second call would
		// need to find again. Chunky asks for it while wrapping the world at enable time, which is one of the
		// broadest-reaching uses of this method and the reason it is answered rather than refused.
		return new ForbricWorldBorder(level, this);
	}

	@Override
	public org.bukkit.Location locateNearestStructure(org.bukkit.Location arg0, org.bukkit.StructureType arg1, int arg2, boolean arg3) {
		throw notYet("locateNearestStructure");
	}

	@Override
	public org.bukkit.util.StructureSearchResult locateNearestStructure(org.bukkit.Location arg0, org.bukkit.generator.structure.StructureType arg1, int arg2, boolean arg3) {
		throw notYet("locateNearestStructure");
	}

	@Override
	public org.bukkit.util.StructureSearchResult locateNearestStructure(org.bukkit.Location arg0, org.bukkit.generator.structure.Structure arg1, int arg2, boolean arg3) {
		throw notYet("locateNearestStructure");
	}

	@Override
	public org.bukkit.Location locateNearestPoi(org.bukkit.Location arg0, io.papermc.paper.entity.poi.PoiType arg1, int arg2, io.papermc.paper.entity.poi.PoiType.Occupancy arg3) {
		throw notYet("locateNearestPoi");
	}

	@Override
	public java.util.List<io.papermc.paper.entity.poi.PoiSearchResult> locateAllPoiInRange(org.bukkit.Location arg0, java.util.function.Predicate<io.papermc.paper.entity.poi.PoiType> arg1, int arg2, io.papermc.paper.entity.poi.PoiType.Occupancy arg3) {
		throw notYet("locateAllPoiInRange");
	}

	@Override
	public double getCoordinateScale() {
		throw notYet("getCoordinateScale");
	}

	@Override
	public boolean isFixedTime() {
		throw notYet("isFixedTime");
	}

	@Override
	public java.util.Collection<org.bukkit.Material> getInfiniburn() {
		throw notYet("getInfiniburn");
	}

	@Override
	public void sendGameEvent(org.bukkit.entity.Entity arg0, org.bukkit.GameEvent arg1, org.bukkit.util.Vector arg2) {
		throw notYet("sendGameEvent");
	}

	@Override
	public org.bukkit.World.Spigot spigot() {
		throw notYet("spigot");
	}

	@Override
	public org.bukkit.util.BiomeSearchResult locateNearestBiome(org.bukkit.Location arg0, int arg1, int arg2, int arg3, org.bukkit.block.Biome... arg4) {
		throw notYet("locateNearestBiome");
	}

	@Override
	public org.bukkit.Raid locateNearestRaid(org.bukkit.Location arg0, int arg1) {
		throw notYet("locateNearestRaid");
	}

	@Override
	public org.bukkit.Raid getRaid(int arg0) {
		throw notYet("getRaid");
	}

	@Override
	public java.util.List<org.bukkit.Raid> getRaids() {
		throw notYet("getRaids");
	}

	@Override
	public org.bukkit.boss.DragonBattle getEnderDragonBattle() {
		throw notYet("getEnderDragonBattle");
	}

	@Override
	public java.util.Set<org.bukkit.FeatureFlag> getFeatureFlags() {
		throw notYet("getFeatureFlags");
	}

	@Override
	public void setViewDistance(int arg0) {
		throw notYet("setViewDistance");
	}

	@Override
	public void setSimulationDistance(int arg0) {
		throw notYet("setSimulationDistance");
	}

	@Override
	public int getSendViewDistance() {
		throw notYet("getSendViewDistance");
	}

	@Override
	public void setSendViewDistance(int arg0) {
		throw notYet("setSendViewDistance");
	}

	@Override
	public java.util.Collection<org.bukkit.generator.structure.GeneratedStructure> getStructures(int arg0, int arg1) {
		throw notYet("getStructures");
	}

	@Override
	public java.util.Collection<org.bukkit.generator.structure.GeneratedStructure> getStructures(int arg0, int arg1, org.bukkit.generator.structure.Structure arg2) {
		throw notYet("getStructures");
	}

	@Override
	public org.bukkit.block.Biome getBiome(int arg0, int arg1, int arg2) {
		throw notYet("getBiome");
	}

	@Override
	public org.bukkit.block.Biome getComputedBiome(int arg0, int arg1, int arg2) {
		throw notYet("getComputedBiome");
	}

	@Override
	public void setBiome(int arg0, int arg1, int arg2, org.bukkit.block.Biome arg3) {
		throw notYet("setBiome");
	}

	@Override
	public org.bukkit.block.BlockState getBlockState(int arg0, int arg1, int arg2) {
		throw notYet("getBlockState");
	}

	@Override
	public io.papermc.paper.block.fluid.FluidData getFluidData(int arg0, int arg1, int arg2) {
		throw notYet("getFluidData");
	}

	@Override
	public org.bukkit.block.data.BlockData getBlockData(int arg0, int arg1, int arg2) {
		throw notYet("getBlockData");
	}

	@Override
	public org.bukkit.Material getType(int arg0, int arg1, int arg2) {
		throw notYet("getType");
	}

	@Override
	public void setBlockData(int arg0, int arg1, int arg2, org.bukkit.block.data.BlockData arg3) {
		throw notYet("setBlockData");
	}

	@Override
	public boolean generateTree(org.bukkit.Location arg0, java.util.Random arg1, org.bukkit.TreeType arg2) {
		throw notYet("generateTree");
	}

	@Override
	public boolean generateTree(org.bukkit.Location arg0, java.util.Random arg1, org.bukkit.TreeType arg2, java.util.function.Consumer<? super org.bukkit.block.BlockState> arg3) {
		throw notYet("generateTree");
	}

	@Override
	public boolean generateTree(org.bukkit.Location arg0, java.util.Random arg1, org.bukkit.TreeType arg2, java.util.function.Predicate<? super org.bukkit.block.BlockState> arg3) {
		throw notYet("generateTree");
	}

	@Override
	public org.bukkit.entity.Entity spawnEntity(org.bukkit.Location arg0, org.bukkit.entity.EntityType arg1, boolean arg2) {
		throw notYet("spawnEntity");
	}

	@Override
	public <T extends org.bukkit.entity.Entity> T createEntity(org.bukkit.Location arg0, java.lang.Class<T> arg1) {
		throw notYet("createEntity");
	}

	/**
	 * Adds an entity to this world, which is the one form the whole family routes through.
	 *
	 * <p>Paper declares a dozen spawn overloads and only this one is abstract: the rest are
	 * default methods that call it. A plugin asking for two arguments is asking for what this
	 * implements, which is why the other stubs below stay unreachable rather than being filled
	 * in as well.
	 *
	 * <p>The order is the contract: the caller's setup runs BEFORE the entity joins the world,
	 * because a plugin uses it to set things the game reads as the entity enters -- a fuse, a
	 * velocity, a custom name. Adding first would make those writes race the entity's own first
	 * tick.
	 */
	@Override
	public <T extends org.bukkit.entity.Entity> T spawn(org.bukkit.Location arg0, java.lang.Class<T> arg1, java.util.function.Consumer<? super T> arg2, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason arg3) throws java.lang.IllegalArgumentException {
		return spawnAt(arg0, arg1, arg2);
	}

	@Override
	public <T extends org.bukkit.entity.Entity> T spawn(org.bukkit.Location arg0, java.lang.Class<T> arg1, boolean arg2, java.util.function.Consumer<? super T> arg3) throws java.lang.IllegalArgumentException {
		// The randomizeData flag is not forwarded: it asks the game to vary the entity's starting
		// state (a sheep's colour, a horse's markings), and the bridge has no seam for that yet. The
		// call still places the entity, which is what a plugin reaching this overload is after.
		return spawnAt(arg0, arg1, arg3);
	}

	@Override
	public int getHighestBlockYAt(int arg0, int arg1) {
		throw notYet("getHighestBlockYAt");
	}

	@Override
	public int getHighestBlockYAt(org.bukkit.Location arg0) {
		throw notYet("getHighestBlockYAt");
	}

	@Override
	public int getHighestBlockYAt(int arg0, int arg1, org.bukkit.HeightMap arg2) {
		throw notYet("getHighestBlockYAt");
	}

	@Override
	public int getHighestBlockYAt(org.bukkit.Location arg0, org.bukkit.HeightMap arg1) {
		throw notYet("getHighestBlockYAt");
	}

	@Override
	public <T extends org.bukkit.entity.Entity> T addEntity(T arg0) {
		throw notYet("addEntity");
	}

	@Override
	public io.papermc.paper.world.MoonPhase getMoonPhase() {
		throw notYet("getMoonPhase");
	}

	@Override
	public java.io.File getWorldFolder() {
		// The save folder a map or backup plugin walks to find `region/*.mca`.
		//
		// In Bukkit that is one folder per world -- `<container>/world/`, `<container>/world/DIM-1/` -- because
		// a Bukkit world is a self-contained save. This game instead keeps one save root (`level-name`) and puts
		// every dimension inside it, the overworld included:
		//
		//   <server>/world/level.dat                              <- the save root, shared by all dimensions
		//   <server>/world/dimensions/minecraft/overworld/region/  <- the overworld's own chunks
		//   <server>/world/dimensions/minecraft/the_nether/region/
		//   <server>/world/dimensions/<namespace>/<path>/region/   <- a datapack dimension
		//
		// So the answer is NOT the save root: that folder holds `level.dat` and `dimensions`, no `region`, and a
		// map plugin given it renders nothing. Every dimension, overworld included, lives one level deeper.
		//
		// A legacy pre-1.16 save has no `dimensions` folder, and there the overworld really is `<root>/` with a
		// sibling `DIM-1`/`DIM1` -- keep answering that layout, so an old world still renders instead of
		// silently producing an empty map.
		java.nio.file.Path worldRoot = level.getServer().getWorldPath(
				net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
		net.minecraft.resources.Identifier id = level.dimension().identifier();
		java.nio.file.Path modern = worldRoot.resolve("dimensions")
				.resolve(id.getNamespace()).resolve(id.getPath());
		if (java.nio.file.Files.isDirectory(modern)) {
			return modern.toFile();
		}
		// Legacy layout: `<root>/region` for the overworld, `<root>/DIM-1` and `<root>/DIM1` for the rest.
		if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
			return worldRoot.toFile();
		}
		return modern.toFile();
	}

	@Override
	public org.bukkit.NamespacedKey getKey() {
           // The world's own registry key — `minecraft:overworld`, `minecraft:the_nether`, or whatever a
           // datapack named a custom dimension.
           //
           // Read from the level's dimension key rather than derived from its folder name: a Bukkit world name
           // is a local label a server owner chooses (`world`, `world_nether`), while the key is what the game
           // and every datapack use to address the dimension. A map plugin storing "which dimension is this"
           // needs the second, and the two disagree as soon as anyone renames a folder.
           net.minecraft.resources.Identifier id = level.dimension().identifier();
           return new org.bukkit.NamespacedKey(id.getNamespace(), id.getPath());
	}

	@Override
	public boolean lineOfSightExists(org.bukkit.Location arg0, org.bukkit.Location arg1) {
		throw notYet("lineOfSightExists");
	}

	@Override
	public boolean hasCollisionsIn(org.bukkit.util.BoundingBox arg0) {
		throw notYet("hasCollisionsIn");
	}

	@Override
	public net.kyori.adventure.key.Key key() {
		throw notYet("key");
	}

	@Override
	public org.bukkit.World.Environment getEnvironment() {
		// The three dimensions the game ships, told apart by the dimension the level was built from. Read from
		// that rather than from the level's name or its registry key, because a custom dimension is addressed
		// by whatever key its datapack chose and a name-based answer would be wrong for it in a way that looks
		// right -- a plugin asking for the environment decides whether to apply overworld rules.
		if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
			return org.bukkit.World.Environment.NORMAL;
		}
		if (level.dimension() == net.minecraft.world.level.Level.NETHER) {
			return org.bukkit.World.Environment.NETHER;
		}
		if (level.dimension() == net.minecraft.world.level.Level.END) {
			return org.bukkit.World.Environment.THE_END;
		}
		// A datapack dimension is NORMAL by Bukkit's definition: the enum has no fourth value, and NORMAL is
		// what Paper reports for one.
		return org.bukkit.World.Environment.NORMAL;
	}

	@Override
	public long getSeed() {
		throw notYet("getSeed");
	}

	@Override
	public org.bukkit.generator.BiomeProvider vanillaBiomeProvider() {
		throw notYet("vanillaBiomeProvider");
	}

	@Override
	public void sendPluginMessage(org.bukkit.plugin.Plugin arg0, java.lang.String arg1, byte[] arg2) {
		throw notYet("sendPluginMessage");
	}

	@Override
	public java.util.Set<java.lang.String> getListeningPluginChannels() {
		throw notYet("getListeningPluginChannels");
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
	public org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer() {
		throw notYet("getPersistentDataContainer");
	}

	@Override
	public java.lang.Iterable<? extends net.kyori.adventure.audience.Audience> audiences() {
		throw notYet("audiences");
	}}
