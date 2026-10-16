/*
 * Forbric Bukkit bridge — a Bukkit BossBar backed by a Minecraft ServerBossEvent.
 */
package net.neoforbrickit.bukkit;

/**
 * One boss bar, as a plugin sees it: a handle over a Minecraft {@code ServerBossEvent}.
 *
 * <p><b>Why this exists at all.</b> {@code BossBar} is not an optional extra on the API — it is how a plugin
 * shows a progress readout that is not the vanilla one, and TAB uses it as a feature in its own right (see
 * {@code bossbar} in its config). A bridge that refuses {@code createBossBar} does not merely lose the bar:
 * the feature that asked for it fails on every refresh, and the log fills with a stack trace that names the
 * plugin.
 *
 * <p><b>The Minecraft side is reached by direct reference, not reflection.</b> {@code ServerBossEvent} and
 * its {@code BossEvent} parent are public and every method used here is public too — the widening that the
 * fields would need is not required, because nothing here touches a field. That matters: reflection would
 * have to be re-resolved per call, and this class is on the path of a per-tick refresh.
 *
 * <p>One thing the game keeps private is worth knowing about: {@code ServerBossEvent} tracks which players
 * may see the bar and pushes an update packet on every change, so {@link #setProgress} on a visible bar is
 * already a network event. There is nothing here to batch, and nothing is queued.
 *
 * <h2>Progress and the visible flag</h2>
 *
 * <p>The two are independent on the Minecraft side and Bukkit's {@code show}/{@code hide} map onto
 * {@code setVisible}, so {@link #show()} is not "add a player" — it is "the bar exists for its current
 * audience". A plugin that calls {@code hide()} then {@code show()} gets the same audience back, which is
 * what {@code isVisible()} promises.
 *
 * <p>{@link #setProgress(double)} clamps to {@code [0, 1]}: the game's own field is a float that vanilla
 * only ever writes in that range, and a plugin passing 2.0 would otherwise get a bar that renders past its
 * end rather than an exception it could act on.
 *
 * <h2>Players</h2>
 *
 * <p>Bukkit hands this class {@code org.bukkit.entity.Player} and the game wants {@code ServerPlayer}, so
 * the conversion goes through {@link ForbricPlayer#of} — the bridge's one way of wrapping a game player.
 * A player that is not online is rejected by the game's own {@code addPlayer} (it checks the connection),
 * and the resulting null is not worth throwing over: a plugin adding a player who just disconnected has
 * asked a question whose honest answer is "no".
 *
 * <p><b>Not final, because {@link ForbricKeyedBossBar} is this plus a key.</b> The two differ only in whether
 * a name can find the bar again, so the keyed one inherits every answer here and adds {@code getKey}. That
 * also makes the colour and overlay mappings below reusable by the subclass, which needs them during
 * construction -- a bar built by the game's constructor must arrive with its colour already applied, since
 * there is no point in between at which the bar is not yet real.
 */
class ForbricBossBar implements org.bukkit.boss.BossBar {

	/** The game's bar. Every method below is a translation onto the game's. */
	protected final net.minecraft.server.level.ServerBossEvent handle;

	/** The bridge's server, for the {@code Player} to {@code ServerPlayer} conversion. */
	final ServerImpl server;

	/**
	 * The title, kept because the game stores a {@code Component} and Bukkit hands back a {@code String}.
	 *
	 * <p>The game's {@code getName()} would round-trip through a serialiser, and a plugin that sets a title
	 * and reads it back is entitled to the string it wrote rather than to a re-serialised equivalent of it.
	 * The first line is also what {@link #getTitle()} returns when a plugin wrote one with newlines: Minecraft
	 * renders a boss bar title on a single line, so the rest would be invisible and reporting it back would
	 * be describing something the client never showed.
	 */
	private String title;

	private org.bukkit.boss.BarColor color;
	private org.bukkit.boss.BarStyle style;

	ForbricBossBar(net.minecraft.server.level.ServerBossEvent handle, ServerImpl server, String title,
					org.bukkit.boss.BarColor color, org.bukkit.boss.BarStyle style) {
		this.handle = handle;
		this.server = server;
		this.title = title;
		this.color = color;
		this.style = style;
	}

	/** The game's bar, for the bridge's own registry to key on. */
	net.minecraft.server.level.ServerBossEvent handle() {
		return handle;
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public void setTitle(String title) {
		this.title = title;
		// A null title is what the game calls an empty bar title; Bukkit's own contract says the argument is
		// not nullable, and a plugin passing null is better served by an empty component than by a crash from
		// inside a packet encoder.
		handle.setName(ForbricComponentBridge.toGame(
						net.kyori.adventure.text.Component.text(title == null ? "" : title)));
	}

	@Override
	public org.bukkit.boss.BarColor getColor() {
		return color;
	}

	@Override
	public void setColor(org.bukkit.boss.BarColor color) {
		if (color == null) {
			return;
		}
		this.color = color;
		handle.setColor(gameColor(color));
	}

	@Override
	public org.bukkit.boss.BarStyle getStyle() {
		return style;
	}

	@Override
	public void setStyle(org.bukkit.boss.BarStyle style) {
		if (style == null) {
			return;
		}
		this.style = style;
		handle.setOverlay(gameOverlay(style));
	}

	@Override
	public void removeFlag(org.bukkit.boss.BarFlag flag) {
		setFlag(flag, false);
	}

	@Override
	public void addFlag(org.bukkit.boss.BarFlag flag) {
		setFlag(flag, true);
	}

	@Override
	public boolean hasFlag(org.bukkit.boss.BarFlag flag) {
		if (flag == null) {
			return false;
		}
		switch (flag) {
			case DARKEN_SKY:
				return handle.shouldDarkenScreen();
			case PLAY_BOSS_MUSIC:
				return handle.shouldPlayBossMusic();
			case CREATE_FOG:
				return handle.shouldCreateWorldFog();
			default:
				return false;
		}
	}

	/**
	 * Sets or clears one of the three flags.
	 *
	 * <p>Both directions go through here because the game has one setter per flag rather than a set of them,
	 * so the switch below has to exist twice otherwise. The methods return the event for chaining and the
	 * return value is discarded.
	 */
	private void setFlag(org.bukkit.boss.BarFlag flag, boolean on) {
		if (flag == null) {
			return;
		}
		switch (flag) {
			case DARKEN_SKY:
				handle.setDarkenScreen(on);
				break;
			case PLAY_BOSS_MUSIC:
				handle.setPlayBossMusic(on);
				break;
			case CREATE_FOG:
				handle.setCreateWorldFog(on);
				break;
			default:
				break;
		}
	}

	@Override
	public void setProgress(double progress) {
		// Clamped rather than rejected: the game's field is a float vanilla only writes in [0, 1], and a
		// plugin that passes 1.5 has made an arithmetic slip, not a request to throw mid-refresh.
		double clamped = Double.isNaN(progress) ? 0.0 : Math.max(0.0, Math.min(1.0, progress));
		handle.setProgress((float) clamped);
	}

	@Override
	public double getProgress() {
		return handle.getProgress();
	}

	@Override
	public void addPlayer(org.bukkit.entity.Player player) {
		net.minecraft.server.level.ServerPlayer gamePlayer = gamePlayerOf(player);
		if (gamePlayer != null) {
			handle.addPlayer(gamePlayer);
		}
	}

	@Override
	public void removePlayer(org.bukkit.entity.Player player) {
		net.minecraft.server.level.ServerPlayer gamePlayer = gamePlayerOf(player);
		if (gamePlayer != null) {
			handle.removePlayer(gamePlayer);
		}
	}

	@Override
	public void removeAll() {
		handle.removeAllPlayers();
	}

	@Override
	public java.util.List<org.bukkit.entity.Player> getPlayers() {
		java.util.List<org.bukkit.entity.Player> players = new java.util.ArrayList<>();
		for (net.minecraft.server.level.ServerPlayer gamePlayer : handle.getPlayers()) {
			players.add(ForbricPlayer.of(gamePlayer, server));
		}
		return players;
	}

	@Override
	public void setVisible(boolean visible) {
		handle.setVisible(visible);
	}

	@Override
	public boolean isVisible() {
		return handle.isVisible();
	}

	@Override
	public void show() {
		setVisible(true);
	}

	@Override
	public void hide() {
		setVisible(false);
	}

	/**
	 * The game's player behind an {@code org.bukkit.entity.Player}, or null when there is none to reach.
	 *
	 * <p>Only a {@link ForbricPlayer} can answer this, and that is not a limitation worth working around: the
	 * bridge builds every {@code Player} it hands out, so a plugin's player is one of ours, and a wrapper from
	 * somewhere else has no game player to find. Returning null rather than throwing keeps the "add a player
	 * who left" case quiet.
	 */
	private net.minecraft.server.level.ServerPlayer gamePlayerOf(org.bukkit.entity.Player player) {
		if (player instanceof ForbricPlayer wrapper) {
			return wrapper.player();
		}
		return null;
	}

	/**
	 * Bukkit's colour, as the game's.
	 *
	 * <p>The two enums carry the same seven constants with the same names, and they are asked for by name
	 * rather than by a hand-written switch so a constant added to both sides keeps working. Should the lookup
	 * ever fail the bar keeps its current colour and answers BLUE, which is the value the game's own
	 * constructor picks when a caller passes null.
	 */
	static net.minecraft.world.BossEvent.BossBarColor gameColor(org.bukkit.boss.BarColor color) {
		if (color == null) {
			return net.minecraft.world.BossEvent.BossBarColor.BLUE;
		}
		try {
			return net.minecraft.world.BossEvent.BossBarColor.valueOf(color.name());
		} catch (IllegalArgumentException e) {
			return net.minecraft.world.BossEvent.BossBarColor.BLUE;
		}
	}

	/**
	 * Bukkit's bar style, as the game's overlay.
	 *
	 * <p>The names differ by exactly one constant: Bukkit calls the unsegmented bar {@code SOLID} and the game
	 * calls it {@code PROGRESS}. The segmented ones match by name after the prefix, so the mapping is written
	 * out rather than derived — deriving it would mean string surgery on a name, which is how a rename turns
	 * into a silent wrong style.
	 */
	static net.minecraft.world.BossEvent.BossBarOverlay gameOverlay(org.bukkit.boss.BarStyle style) {
		if (style == null) {
			return net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS;
		}
		switch (style) {
			case SOLID:
				return net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS;
			case SEGMENTED_6:
				return net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_6;
			case SEGMENTED_10:
				return net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10;
			case SEGMENTED_12:
				return net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_12;
			case SEGMENTED_20:
				return net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_20;
			default:
				return net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS;
		}
	}
}
