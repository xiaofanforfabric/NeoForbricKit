/*
 * Forbric Bukkit bridge — the adventure boss bars one player is currently shown.
 */
package net.neoforbrickit.bukkit;

/**
 * The bridge's answer to {@code Audience.showBossBar}, for one player.
 *
 * <p><b>Why this class has to exist, and why its absence looked like nothing at all.</b> Paper's
 * {@code Player} implements adventure's {@code Audience}, and {@code Audience.showBossBar} has a
 * <em>default</em> implementation whose entire body is {@code return}. A bridge that never overrode it
 * therefore compiled, started, and ran — with every boss bar a plugin showed silently discarded. No
 * exception, no log line, and the plugin's own diagnostics report the bar as displayed, because from the
 * plugin's side the call did return normally. TAB's {@code /tab debug} said exactly that: "bossbar is
 * displayed: true" while nothing reached any client.
 *
 * <p>TAB reaches this path rather than the Bukkit one: it prefers {@code AdventureBossBar} whenever
 * {@code net.kyori.adventure.bossbar.BossBar} is on the classpath, which on Paper it always is, and only
 * falls back to {@code Bukkit.createBossBar} otherwise. Implementing the Bukkit half of the API was
 * therefore necessary but not sufficient.
 *
 * <h2>Why the bars are tracked per player</h2>
 *
 * <p>An adventure {@code BossBar} is a value a plugin holds and mutates; the game's {@code ServerBossEvent}
 * is an object that owns its own audience. The two are not the same thing, and neither can be derived from
 * the other, so this class keeps the pairing: one game bar per adventure bar per player, plus a listener
 * registered for the lifetime of that pairing so a later {@code bar.progress(0.5f)} reaches the client.
 * Without the listener a bar would be shown at the values it had when {@code showBossBar} was called and
 * then never move, which is worse than not showing it.
 *
 * <p>{@code activeBossBars()} is answered from the same map. Bukkit's contract for it is "the bars this
 * viewer is currently shown", and the map is exactly that set — a plugin that asks is entitled to see the
 * bar it just showed, and one that shows the same bar twice is entitled to see it once.
 *
 * <h2>Showing a bar twice</h2>
 *
 * <p>Adventure's own contract says showing a bar the viewer already has is a no-op, and that is what happens
 * here: the map is consulted first and an existing entry is left alone rather than re-registered. A second
 * {@code ServerBossEvent} for the same bar would give the client two bars drawn in the same place, and the
 * plugin would have no way to hide one of them — {@code hideBossBar} removes one pairing.
 *
 * <p>Everything runs on the server thread: {@code showBossBar} is called from plugin code, which is already
 * there for a Bukkit plugin, and the game sends the packet itself when {@code addPlayer} is called on a
 * visible event. There is no queue and no thread hop, which is also why the map does not need to be
 * concurrent but is made so anyway — a plugin may touch a bar from its own scheduler thread, and a
 * {@code ConcurrentHashMap} costs nothing measurable at this size.
 */
final class ForbricBossBarView {

	/** The player whose screen these bars appear on. */
	private final net.minecraft.server.level.ServerPlayer player;

	/**
	 * One entry per adventure bar this player is currently shown.
	 *
	 * <p>Keyed by the adventure bar itself, using its own identity: a plugin that shows two bars with equal
	 * contents is showing two bars, and adventure does not define value equality on them.
	 */
	private final java.util.Map<net.kyori.adventure.bossbar.BossBar, Entry> bars =
			new java.util.concurrent.ConcurrentHashMap<>();

	ForbricBossBarView(net.minecraft.server.level.ServerPlayer player) {
		this.player = player;
	}

	/**
	 * The adventure bars this player is currently shown.
	 *
	 * <p>A copy, because the caller is a plugin and iterating a live view while another thread hides a bar
	 * would throw from inside the plugin's own loop.
	 */
	java.util.Collection<net.kyori.adventure.bossbar.BossBar> active() {
		return java.util.List.copyOf(bars.keySet());
	}

	/**
	 * Begins showing a bar to this player.
	 *
	 * <p>Takes the raw adventure bar and returns nothing, so it is callable both from {@code showBossBar} and
	 * from anywhere else that needs a bar on screen. The {@code Entry} it creates is also the listener, so the
	 * bar's later mutations arrive at the same place the initial values were written from.
	 */
	void show(net.kyori.adventure.bossbar.BossBar bar) {
		if (bar == null) {
			return;
		}
		// Shown twice is shown once -- see the class comment. computeIfAbsent would register the listener as a
		// side effect of the mapping function, which is the kind of thing that runs twice under contention.
		if (bars.containsKey(bar)) {
			return;
		}

		Entry entry = new Entry(bar);
		Entry previous = bars.putIfAbsent(bar, entry);
		if (previous != null) {
			// Another thread won the race and already has this bar on screen.
			return;
		}
		bar.addListener(entry);
		entry.apply();
	}

	/** Stops showing a bar to this player, leaving the plugin's bar itself untouched. */
	void hide(net.kyori.adventure.bossbar.BossBar bar) {
		if (bar == null) {
			return;
		}
		Entry entry = bars.remove(bar);
		if (entry != null) {
			entry.dispose();
		}
	}

	/**
	 * Stops showing every bar, for a player who is leaving.
	 *
	 * <p>Called when the player disconnects so the listener registrations do not outlive the player and keep
	 * the plugin's bars alive: an adventure bar holds a reference to every listener added to it, so a server
	 * that only dropped its own map would leak one listener per bar per player, forever.
	 */
	void clear() {
		for (Entry entry : bars.values()) {
			entry.dispose();
		}
		bars.clear();
	}

	/**
	 * One adventure bar paired with the game's bar for this player.
	 *
	 * <p>It is both the store of the pairing and the {@link net.kyori.adventure.bossbar.BossBar.Listener} for
	 * it, because the two have the same lifetime and a separate listener object would need the pairing passed
	 * to it anyway.
	 */
	private final class Entry implements net.kyori.adventure.bossbar.BossBar.Listener {

		private final net.kyori.adventure.bossbar.BossBar bar;

		/**
		 * The game's bar. Built once, in the constructor, and reused for every later change — the game
		 * identifies a bar by this object's UUID and a new one would be a second bar rather than an update.
		 */
		private final net.minecraft.server.level.ServerBossEvent handle;

		Entry(net.kyori.adventure.bossbar.BossBar bar) {
			this.bar = bar;
			this.handle = new net.minecraft.server.level.ServerBossEvent(
					java.util.UUID.randomUUID(),
					ForbricComponentBridge.toGame(bar.name()),
					gameColor(bar.color()),
					gameOverlay(bar.overlay()));
		}

		/**
		 * Writes every value the adventure bar currently holds onto the game's bar, then puts it on screen.
		 *
		 * <p>All of them, rather than only the colour and overlay the constructor took: the constructor has no
		 * room for progress or flags, and a bar whose first frame showed progress 1.0 before snapping to the
		 * plugin's 0.4 would be a visible flicker.
		 */
		void apply() {
			handle.setProgress(clamp(bar.progress()));
			handle.setDarkenScreen(bar.hasFlag(net.kyori.adventure.bossbar.BossBar.Flag.DARKEN_SCREEN));
			handle.setPlayBossMusic(bar.hasFlag(net.kyori.adventure.bossbar.BossBar.Flag.PLAY_BOSS_MUSIC));
			handle.setCreateWorldFog(bar.hasFlag(net.kyori.adventure.bossbar.BossBar.Flag.CREATE_WORLD_FOG));
			// The game ignores addPlayer on an invisible bar, so visibility is set first. The event's own
			// default is visible, which is also what adventure means by "shown".
			handle.setVisible(true);
			handle.addPlayer(player);
		}

		/** Takes the bar off this player's screen and stops listening to it. */
		void dispose() {
			bar.removeListener(this);
			handle.removePlayer(player);
		}

		@Override
		public void bossBarNameChanged(net.kyori.adventure.bossbar.BossBar bar,
						net.kyori.adventure.text.Component oldName,
						net.kyori.adventure.text.Component newName) {
			handle.setName(ForbricComponentBridge.toGame(newName));
		}

		@Override
		public void bossBarProgressChanged(net.kyori.adventure.bossbar.BossBar bar, float oldProgress,
						float newProgress) {
			handle.setProgress(clamp(newProgress));
		}

		// No bossBarPercentChanged callback: adventure 5 removed it. The API still exposes percent(float) as a
		// convenience that differs from progress(float) only by a factor of 100, but it now reports the change
		// through bossBarProgressChanged like any other progress change. Writing a method for the old name would
		// compile only against adventure 4 and would silently never be called here, which is the same failure
		// this whole class exists to fix.

		@Override
		public void bossBarColorChanged(net.kyori.adventure.bossbar.BossBar bar,
						net.kyori.adventure.bossbar.BossBar.Color oldColor,
						net.kyori.adventure.bossbar.BossBar.Color newColor) {
			handle.setColor(gameColor(newColor));
		}

		@Override
		public void bossBarOverlayChanged(net.kyori.adventure.bossbar.BossBar bar,
						net.kyori.adventure.bossbar.BossBar.Overlay oldOverlay,
						net.kyori.adventure.bossbar.BossBar.Overlay newOverlay) {
			handle.setOverlay(gameOverlay(newOverlay));
		}

		@Override
		public void bossBarFlagsChanged(net.kyori.adventure.bossbar.BossBar bar,
						java.util.Set<net.kyori.adventure.bossbar.BossBar.Flag> flagsAdded,
						java.util.Set<net.kyori.adventure.bossbar.BossBar.Flag> flagsRemoved) {
			// Told what was added and what was removed rather than the resulting set, and applied as such: the
			// game has one setter per flag, so replaying the whole set would be the same work with the extra
			// step of working out which of the three changed.
			if (flagsAdded != null) {
				for (net.kyori.adventure.bossbar.BossBar.Flag flag : flagsAdded) {
					setFlag(flag, true);
				}
			}
			if (flagsRemoved != null) {
				for (net.kyori.adventure.bossbar.BossBar.Flag flag : flagsRemoved) {
					setFlag(flag, false);
				}
			}
		}

		private void setFlag(net.kyori.adventure.bossbar.BossBar.Flag flag, boolean on) {
			if (flag == null) {
				return;
			}
			switch (flag) {
				case DARKEN_SCREEN:
					handle.setDarkenScreen(on);
					break;
				case PLAY_BOSS_MUSIC:
					handle.setPlayBossMusic(on);
					break;
				case CREATE_WORLD_FOG:
					handle.setCreateWorldFog(on);
					break;
				default:
					break;
			}
		}
	}

	/**
	 * Adventure's colour, as the game's.
	 *
	 * <p>The two enums carry the same seven constants with the same names, so they are matched by name rather
	 * than by a hand-written switch: a constant added to both sides keeps working, and there is no way for the
	 * two lists to drift apart silently.
	 */
	private static net.minecraft.world.BossEvent.BossBarColor gameColor(
					net.kyori.adventure.bossbar.BossBar.Color color) {
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
	 * Adventure's overlay, as the game's.
	 *
	 * <p>Unlike the Bukkit side of the API there is nothing to translate here: adventure calls the
	 * unsegmented bar {@code PROGRESS} and the segmented ones {@code NOTCHED_6} upwards, which is exactly what
	 * the game calls them. The names are still matched rather than the ordinal taken, because an ordinal
	 * depends on declaration order and would break without a compile error if either enum were reordered.
	 */
	private static net.minecraft.world.BossEvent.BossBarOverlay gameOverlay(
					net.kyori.adventure.bossbar.BossBar.Overlay overlay) {
		if (overlay == null) {
			return net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS;
		}
		try {
			return net.minecraft.world.BossEvent.BossBarOverlay.valueOf(overlay.name());
		} catch (IllegalArgumentException e) {
			return net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS;
		}
	}

	/**
	 * Adventure's progress, as a float the game can draw.
	 *
	 * <p>Adventure documents its range as {@code [0, 1]} and rejects nothing outside it, so a value that is
	 * out of range is a plugin's arithmetic rather than a reason to fail mid-refresh. A NaN becomes 0: the
	 * game's own field is a float and a NaN would reach the packet encoder, where it is silently a bar with no
	 * fill rather than anything a stack trace could point at.
	 */
	private static float clamp(float progress) {
		if (Float.isNaN(progress)) {
			return 0.0f;
		}
		return Math.max(0.0f, Math.min(1.0f, progress));
	}
}
