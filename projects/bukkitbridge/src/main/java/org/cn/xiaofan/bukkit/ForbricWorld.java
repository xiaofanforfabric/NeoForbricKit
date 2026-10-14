/*
 * Forbric Bukkit bridge — an {@code org.bukkit.World} backed by a Minecraft {@code ServerLevel}.
 * *** GENERATED FILE — DO NOT EDIT ***
 *
 * Produced by tools/gen_stub.py from tools/stubs/ForbricWorld.table. Edits belong in the table or this template.
 */
package org.cn.xiaofan.bukkit;

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
final class ForbricWorld implements org.bukkit.World {

	private final net.minecraft.server.level.ServerLevel level;
	private final ServerImpl server;

	private ForbricWorld(net.minecraft.server.level.ServerLevel level, ServerImpl server) {
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

	private static UnsupportedOperationException notYet(String what) {
		return new UnsupportedOperationException(
				"[Forbric/Bukkit] World." + what + "() is not implemented by this bridge yet");
	}

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
		net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(from, to,
				ignorePassableBlocks ? net.minecraft.world.level.ClipContext.Block.OUTLINE
						: net.minecraft.world.level.ClipContext.Block.COLLIDER,
				clipFluid(fluidMode), net.minecraft.world.phys.shapes.CollisionContext.empty());
		net.minecraft.world.phys.BlockHitResult hit = level.clip(context);
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return null;
		}
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
		throw notYet("getChunkAt");
	}

	@Override
	public org.bukkit.Chunk getChunkAt(int arg0, int arg1, boolean arg2) {
		throw notYet("getChunkAt");
	}

	@Override
	public org.bukkit.Chunk getChunkAt(org.bukkit.block.Block arg0) {
		throw notYet("getChunkAt");
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
		throw notYet("isChunkGenerated");
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
		throw notYet("addPluginChunkTicket");
	}

	@Override
	public boolean removePluginChunkTicket(int arg0, int arg1, org.bukkit.plugin.Plugin arg2) {
		throw notYet("removePluginChunkTicket");
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
		throw notYet("getChunkAtAsync");
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

	@Override
	public void save(boolean arg0) {
		throw notYet("save");
	}

	@Override
	public java.util.List<org.bukkit.generator.BlockPopulator> getPopulators() {
		throw notYet("getPopulators");
	}

	@Override
	public <T extends org.bukkit.entity.LivingEntity> T spawn(org.bukkit.Location arg0, java.lang.Class<T> arg1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason arg2, boolean arg3, java.util.function.Consumer<? super T> arg4) throws java.lang.IllegalArgumentException {
		throw notYet("spawn");
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
		throw notYet("getLogicalHeight");
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
		throw notYet("getWorldPath");
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
		throw notYet("getWorldBorder");
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

	@Override
	public <T extends org.bukkit.entity.Entity> T spawn(org.bukkit.Location arg0, java.lang.Class<T> arg1, java.util.function.Consumer<? super T> arg2, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason arg3) throws java.lang.IllegalArgumentException {
		throw notYet("spawn");
	}

	@Override
	public <T extends org.bukkit.entity.Entity> T spawn(org.bukkit.Location arg0, java.lang.Class<T> arg1, boolean arg2, java.util.function.Consumer<? super T> arg3) throws java.lang.IllegalArgumentException {
		throw notYet("spawn");
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
	public org.bukkit.NamespacedKey getKey() {
		throw notYet("getKey");
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
		throw notYet("getEnvironment");
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
