/*
 * Forbric Bukkit bridge — the keyed boss bars a plugin registers on the server.
 */
package net.neoforbrickit.bukkit;

/**
 * The keyed boss bars this server knows about, and the one place they are created and removed.
 *
 * <p><b>What the key is for.</b> Bukkit offers two kinds of bar. The plain one is a handle a plugin keeps and
 * forgets about, and is exactly what {@link ForbricBossBar} already is. The keyed one is registered on the
 * server under a {@link org.bukkit.NamespacedKey}, so that another plugin — or the same one after a reload —
 * can find the bar again by name, and so that {@code /bossbar} and the server's own listings have something
 * to show. That second kind is what this class holds, and TAB's feature is written against it.
 *
 * <h2>Why the game's own registry is not the store</h2>
 *
 * <p>Minecraft keeps its bars per player connection, not per server: a {@code ServerBossEvent} is a
 * broadcast object whose identity is only meaningful to whoever holds it, and the game never enumerates the
 * bars that exist. There is therefore nothing to ask for "all registered bars", and the answer has to be
 * kept on this side. A {@link java.util.concurrent.ConcurrentHashMap} keyed by the {@code NamespacedKey} is
 * what {@code getBossBar} and {@code removeBossBar} need, and it is concurrent because a plugin may register
 * a bar from a worker thread while the server is iterating the set on the tick thread.
 *
 * <h2>Removal has to reach the game</h2>
 *
 * <p>Dropping a bar from the map is not enough. A {@code ServerBossEvent} holds the set of players who can
 * see it, so a bar that is removed from this map but left visible keeps showing on every client that had it
 * — a leak a plugin cannot undo afterwards, because it has just been told the bar is gone. {@link #remove}
 * therefore calls {@link org.bukkit.boss.BossBar#removeAll()} before dropping the entry, which is the game's
 * own way of telling those clients to stop drawing it.
 *
 * <p>The map itself is deliberately not cleared on shutdown: the server object goes away with the process,
 * and a bridge that emptied the map during shutdown would be doing bookkeeping nobody can observe.
 */
final class ForbricBossBarRegistry {

	/** Bars registered under a key, in the order the game does not preserve. */
	private final java.util.Map<org.bukkit.NamespacedKey, ForbricKeyedBossBar> keyed =
			new java.util.concurrent.ConcurrentHashMap<>();

	private final ServerImpl server;

	ForbricBossBarRegistry(ServerImpl server) {
		this.server = server;
	}

	/**
	 * Registers a new bar, or replaces the one already under that key.
	 *
	 * <p>Replacing rather than refusing is what Bukkit's contract asks for — the returned bar is the one a
	 * caller should use from then on — and it is also what a reload does: the plugin asks for the same key it
	 * asked for last time. The bar that was there is removed from the clients first, so the players do not end
	 * up with two bars stacked at the same position.
	 */
	ForbricKeyedBossBar create(org.bukkit.NamespacedKey key, String title, org.bukkit.boss.BarColor color,
					org.bukkit.boss.BarStyle style, org.bukkit.boss.BarFlag... flags) {
		ForbricKeyedBossBar existing = keyed.remove(key);
		if (existing != null) {
			existing.removeAll();
		}

		// The game identifies a bar by UUID, and it is never sent to a client -- it exists so that a server
		// and a client can agree on which bar an update refers to. A random one is what vanilla does and is
		// the only requirement the game places on it.
		ForbricKeyedBossBar bar = ForbricKeyedBossBar.create(key, server, java.util.UUID.randomUUID(),
						title, color, style);
		if (flags != null) {
			for (org.bukkit.boss.BarFlag flag : flags) {
				bar.addFlag(flag);
			}
		}
		keyed.put(key, bar);
		return bar;
	}

	/** The bar under that key, or null when nothing is registered there. */
	ForbricKeyedBossBar get(org.bukkit.NamespacedKey key) {
		return keyed.get(key);
	}

	/** Every registered bar, for the iterator Bukkit hands out. */
	java.util.Collection<ForbricKeyedBossBar> all() {
		return java.util.List.copyOf(keyed.values());
	}

	/**
	 * Removes the bar under that key, telling its audience first.
	 *
	 * <p>Returns false when nothing was registered there, which is the answer Bukkit documents for a key that
	 * is not in use — and, importantly, it is not treated as an error: a plugin tidying up after a reload
	 * calls this for bars that may never have existed.
	 */
	boolean remove(org.bukkit.NamespacedKey key) {
		ForbricKeyedBossBar bar = keyed.remove(key);
		if (bar == null) {
			return false;
		}
		// Before the entry goes, not after: with the entry gone there is no other way to reach the bar, and
		// the clients would keep a bar this server can no longer update or remove.
		bar.removeAll();
		return true;
	}
}
