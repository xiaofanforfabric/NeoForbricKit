/*
 * Forbric Bukkit bridge — a Bukkit KeyedBossBar backed by a Minecraft ServerBossEvent.
 */
package net.neoforbrickit.bukkit;

/**
 * A {@link ForbricBossBar} that also carries the key it is registered under.
 *
 * <p><b>Why this is a separate class rather than a field on the plain bar.</b> Bukkit's API separates them:
 * {@code createBossBar(String, ...)} hands back a {@code BossBar} that nothing else can ever find, and
 * {@code createBossBar(NamespacedKey, ...)} hands back a {@code KeyedBossBar} that a name can locate again.
 * A plugin may rely on that distinction — for instance by holding a plain bar and not expecting it to show up
 * in the server's listings — so collapsing the two would make a plain bar discoverable when the API says it
 * is not.
 *
 * <p>Everything else is inherited: the title, colour, style, flags, progress, visibility and audience all
 * live in {@link ForbricBossBar} and are answered from the same {@code ServerBossEvent} the game pushes to
 * clients, so a change made through this class is on the wire immediately.
 *
 * <p>The {@link #getKey()} answer is the key the registry filed this bar under, held here because the game
 * has no notion of it: Minecraft identifies a bar by UUID and never by a name, and the key therefore exists
 * only on this side of the bridge.
 */
final class ForbricKeyedBossBar extends ForbricBossBar implements org.bukkit.boss.KeyedBossBar {

	/** The name this bar is registered under; the game stores no equivalent. */
	private final org.bukkit.NamespacedKey key;

	private ForbricKeyedBossBar(org.bukkit.NamespacedKey key, net.minecraft.server.level.ServerBossEvent handle,
					ServerImpl server, String title, org.bukkit.boss.BarColor color,
					org.bukkit.boss.BarStyle style) {
		super(handle, server, title, color, style);
		this.key = key;
	}

	/**
	 * Builds the game's bar and wraps it.
	 *
	 * <p>Colour and style go through the game's constructor rather than a setter afterwards, because the
	 * constructor takes both and a bar that briefly existed with the wrong ones would have been sent to
	 * nobody. The mappings are the ones {@link ForbricBossBar} already holds, so the two sides of the bridge
	 * cannot disagree about what {@code RED} or {@code SEGMENTED_10} means.
	 *
	 * <p>The id is a random UUID, which is what vanilla does: the game identifies a bar by it and it is never
	 * sent to a client — it exists so a server and a client can agree on which bar an update refers to, and
	 * the only requirement the game places on it is that it differ from every other live bar's.
	 */
	static ForbricKeyedBossBar create(org.bukkit.NamespacedKey key, ServerImpl server, java.util.UUID id,
					String title, org.bukkit.boss.BarColor color, org.bukkit.boss.BarStyle style) {
		net.minecraft.server.level.ServerBossEvent handle = new net.minecraft.server.level.ServerBossEvent(
						id,
						ForbricComponentBridge.toGame(net.kyori.adventure.text.Component.text(
										title == null ? "" : title)),
						ForbricBossBar.gameColor(color),
						ForbricBossBar.gameOverlay(style));
		return new ForbricKeyedBossBar(key, handle, server, title, color, style);
	}

	@Override
	public org.bukkit.NamespacedKey getKey() {
		return key;
	}
}
