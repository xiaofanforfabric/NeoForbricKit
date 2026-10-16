/*
 * Forbric Bukkit bridge — a Bukkit WorldBorder backed by a Minecraft WorldBorder.
 */
package net.neoforbrickit.bukkit;

/**
 * One world's border, as a plugin sees it: a handle over the level's own {@code WorldBorder}.
 *
 * <p><b>Why this is worth implementing rather than refusing.</b> A border is not an exotic corner of the API —
 * it is the boundary every world has, and the set of plugins that read it is broad: a pre-generator sizes its
 * work by it, a protection plugin refuses placements outside it, an anti-cheat watches movement across it, a
 * map mod asks the client to draw it. Chunky is only the first plugin here to reach for it, and it reaches at
 * <em>enable</em> time — {@code BukkitWorld}'s constructor calls {@code getWorldBorder} while wrapping the
 * worlds — so a refusal does not degrade the feature, it stops the plugin from loading at all.
 *
 * <p><b>A handle, not a copy.</b> The game keeps exactly one border per level, it is saved with the world, and
 * every reader of it must see the same numbers. This class therefore holds the level's own object and forwards
 * every call: a snapshot would go stale the moment another plugin moved the border, and a second border object
 * would be a second boundary that the game never consults.
 *
 * <h2>The two renames and the four defaults</h2>
 *
 * <p>Most of the two APIs line up name for name, and the exceptions are worth stating because each one is a
 * place a guess would produce plausible-looking wrong numbers:
 *
 * <ul>
 *   <li>{@code getDamageBuffer} is the game's {@code safeZone} — the distance inside the border within which
 *       no damage is taken. The game's name is about what it protects; Bukkit's is about what it buffers.</li>
 *   <li>{@code getDamageAmount} is the game's {@code damagePerBlock} — the same quantity under the two
 *       natural readings of it.</li>
 *   <li>{@code getWarningDistance} is the game's {@code warningBlocks}.</li>
 *   <li>{@code getWarningTimeTicks} is {@code warningTime}, and the game's field is already in ticks.</li>
 * </ul>
 *
 * <p>{@code getMaxSize} and {@code getMaxCenterCoordinate} are the game's two constants, answered from them
 * rather than from literals so a future change to the game's limits is not silently contradicted here.
 *
 * <h2>Validation the API asks for</h2>
 *
 * <p>Bukkit's contract says {@code setSize} throws {@code IllegalArgumentException} for a size below 1.0 or
 * above {@link #getMaxSize()}, and that {@code setCenter} does the same for a coordinate beyond
 * {@link #getMaxCenterCoordinate()}. Those checks are done here rather than left to the game, because the game
 * does not make them: it clamps. Clamping quietly turns a plugin's arithmetic error into a border that is not
 * where the plugin thinks it is, and the plugin has no way to find out — an exception at the call site names
 * the argument and the plugin's own line.
 *
 * <h2>Reset</h2>
 *
 * <p>{@code reset()} restores the values the game starts a new world with, and they are written out below
 * rather than taken from the game's {@code WorldBorder.Settings.DEFAULT} record: that record is not reachable
 * from here without reflection, and — more to the point — building a new {@code WorldBorder} from it would
 * replace the level's object, dropping the listeners the game has registered on it and leaving the level
 * pointing at the old one. The numbers themselves are the game's defaults, and the comment on each says where
 * it comes from.
 */
final class ForbricWorldBorder implements org.bukkit.WorldBorder {

	/**
	 * Bukkit's documented floor for a border size.
	 *
	 * <p>Written as a constant rather than inline because it appears in two methods and in their messages, and
	 * because the number is a fact about Bukkit's contract rather than about this bridge.
	 */
	private static final double MIN_SIZE = 1.0;

	/** The level whose border this is. The game keeps one per level and saves it with the world. */
	private final net.minecraft.server.level.ServerLevel level;

	/** The world this border belongs to, for {@link #getWorld()}. */
	private final ForbricWorld world;

	ForbricWorldBorder(net.minecraft.server.level.ServerLevel level, ForbricWorld world) {
		this.level = level;
		this.world = world;
	}

	/** The game's border, re-read on every call so a replacement by the game is followed. */
	private net.minecraft.world.level.border.WorldBorder handle() {
		return level.getWorldBorder();
	}

	@Override
	public org.bukkit.World getWorld() {
		return world;
	}

	@Override
	public void reset() {
		net.minecraft.world.level.border.WorldBorder border = handle();
		// The game's own starting values, from WorldBorder.Settings.DEFAULT.
		border.setCenter(0.0, 0.0);
		border.setSize(net.minecraft.world.level.border.WorldBorder.MAX_SIZE);
		border.setSafeZone(5.0);
		border.setDamagePerBlock(0.2);
		border.setWarningBlocks(5);
		border.setWarningTime(300);
		// An in-flight lerp is part of the state being reset, and setting only the size would leave the border
		// still growing towards wherever it was headed. A zero-length lerp at the current size cancels it:
		// the game reads it as "start and end at this size, over no time", which is a border that has stopped.
		border.lerpSizeBetween(border.getSize(), border.getSize(), 0L, 0L);
	}

	@Override
	public double getSize() {
		return handle().getSize();
	}

	@Override
	public void setSize(double newSize) {
		checkSize(newSize);
		handle().setSize(newSize);
	}

	@Override
	public void changeSize(double newSize, long ticks) {
		checkSize(newSize);
		if (ticks < 0L) {
			throw new IllegalArgumentException(
					"[Forbric/Bukkit] a border cannot change size over a negative number of ticks, got " + ticks);
		}
		net.minecraft.world.level.border.WorldBorder border = handle();
		// The game's lerp takes an absolute start and end time in milliseconds, and Bukkit's contract is a
		// duration in ticks: the start is now, and 50ms per tick is the rate the server runs at by definition.
		long now = System.currentTimeMillis();
		border.lerpSizeBetween(border.getSize(), newSize, now, now + ticks * 50L);
	}

	@Override
	public org.bukkit.Location getCenter() {
		// The game stores the two coordinates separately and has no Location of its own, so the world is needed
		// for the y -- and the y is the world's own spawn height rather than 0, because a border's centre is a
		// point a plugin may go on to build a Location on and a y of 0 would be under the world.
		net.minecraft.world.level.border.WorldBorder border = handle();
		return new org.bukkit.Location(world, border.getCenterX(),
						world.getSpawnLocation().getY(), border.getCenterZ());
	}

	@Override
	public void setCenter(double x, double z) {
		checkCenter(x, z);
		handle().setCenter(x, z);
	}

	@Override
	public void setCenter(org.bukkit.Location location) {
		if (location == null) {
			throw new IllegalArgumentException("[Forbric/Bukkit] a border centre cannot be a null Location");
		}
		// Only x and z are read, which is what the contract says: the border is a vertical infinite prism, so a
		// y coordinate has no meaning for it.
		setCenter(location.getX(), location.getZ());
	}

	@Override
	public double getDamageBuffer() {
		return handle().getSafeZone();
	}

	@Override
	public void setDamageBuffer(double blocks) {
		handle().setSafeZone(blocks);
	}

	@Override
	public double getDamageAmount() {
		return handle().getDamagePerBlock();
	}

	@Override
	public void setDamageAmount(double damage) {
		handle().setDamagePerBlock(damage);
	}

	@Override
	public int getWarningTimeTicks() {
		return handle().getWarningTime();
	}

	@Override
	public void setWarningTimeTicks(int ticks) {
		handle().setWarningTime(ticks);
	}

	@Override
	public int getWarningDistance() {
		return handle().getWarningBlocks();
	}

	@Override
	public void setWarningDistance(int distance) {
		handle().setWarningBlocks(distance);
	}

	@Override
	public boolean isInside(org.bukkit.Location location) {
		if (location == null) {
			// Bukkit's contract does not say what a null location means, and the two readings are "false" and
			// "that is a mistake". Answered as false: a plugin asking whether a point is inside a border has
			// asked a question about a point, and no point is inside.
			return false;
		}
		return handle().isWithinBounds(location.getX(), location.getY(), location.getZ());
	}

	@Override
	public double getMaxSize() {
		return net.minecraft.world.level.border.WorldBorder.MAX_SIZE;
	}

	@Override
	public double getMaxCenterCoordinate() {
		return net.minecraft.world.level.border.WorldBorder.MAX_CENTER_COORDINATE;
	}

	/** Refuses a size outside the range Bukkit's contract documents. */
	private void checkSize(double newSize) {
		if (Double.isNaN(newSize) || newSize < MIN_SIZE || newSize > getMaxSize()) {
			throw new IllegalArgumentException(
					"[Forbric/Bukkit] a border size must be between " + MIN_SIZE + " and " + getMaxSize()
							+ ", got " + newSize);
		}
	}

	/** Refuses a centre outside the range Bukkit's contract documents. */
	private void checkCenter(double x, double z) {
		double limit = getMaxCenterCoordinate();
		if (Double.isNaN(x) || Math.abs(x) > limit) {
			throw new IllegalArgumentException(
					"[Forbric/Bukkit] a border centre x must be within +/-" + limit + ", got " + x);
		}
		if (Double.isNaN(z) || Math.abs(z) > limit) {
			throw new IllegalArgumentException(
					"[Forbric/Bukkit] a border centre z must be within +/-" + limit + ", got " + z);
		}
	}
}
