/*
 * Forbric Bukkit bridge — what the injected call in
 * NetworkRegistry.onConfigurationFinished reaches.
 */
package net.neoforbrickit.bukkit;

/**
 * Declares the bridge's plugin channels to a joining client.
 *
 * <p><b>Why this exists.</b> A NeoForge client refuses to send a modded payload
 * whose id it has not been told the server accepts:
 * {@code ClientPacketDistributor.sendToServer} consults
 * {@code NetworkRegistry.hasChannel} first and throws otherwise, which the
 * sending mod can only report as "the server does not accept this". The list the
 * client checks is filled by the {@code minecraft:register} packet the server
 * sends when the configuration phase ends. NeoForge builds that list in
 * {@code NetworkRegistry.onConfigurationFinished}, and for a NeoForge connection
 * it puts only its own handshake ids in it — the channels a {@code Bukkit}
 * plugin registered through {@link org.bukkit.plugin.messaging.Messenger} are
 * not among them.
 *
 * <p>So a client mod and a Bukkit plugin that speak over the same channel never
 * meet: the plugin's {@code registerIncomingPluginChannel} is recorded on this
 * side, and the client is never told. The injected call below appends the
 * bridge's incoming channels to that same list, which is the whole fix. Nothing
 * here relays a packet — it makes the client willing to send the first one.
 *
 * <p>Called from injected bytecode, so it is a static entry point taking the
 * game's own builder rather than a method on a bridge object.
 */
public final class ForbricChannelHook {

	private ForbricChannelHook() {
	}

	/**
	 * Adds every channel a plugin has registered for incoming messages to the set
	 * the server is about to announce.
	 *
	 * <p>The builder is the one NeoForge is already holding, so the ids land in the
	 * same {@code minecraft:register} payload. Channels are validated on the way in
	 * by {@link ForbricMessenger}, so what arrives here is already
	 * {@code namespace:path}.
	 *
	 * <p>Nothing here may throw: it runs on the network thread while the
	 * configuration phase is being finished, where an exception would be
	 * attributed to the connection. A channel that cannot be turned into an
	 * identifier is skipped rather than allowed to abort the join.
	 */
	@SuppressWarnings("unchecked")
	public static void addBridgeChannels(Object builder) {
		try {
			if (!(builder instanceof com.google.common.collect.ImmutableSet.Builder<?> raw)) {
				return;
			}
			com.google.common.collect.ImmutableSet.Builder<net.minecraft.resources.Identifier> target = (com.google.common.collect.ImmutableSet.Builder<net.minecraft.resources.Identifier>) raw;

			java.util.Set<String> channels = bridgeChannels();
			for (String channel : channels) {
				net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(channel);
				if (id != null) {
					target.add(id);
				}
			}
			if (!channels.isEmpty()) {
				net.neoforbrickit.bridge.util.ForbricLog.info(
						"[Forbric/Bukkit] announced %s plugin channel(s) to the joining client: %s",
						channels.size(), channels);
			}
		} catch (Throwable failure) {
			// Never silent: a failure here is the difference between "the client
			// mod does not work" and "the client was never told it could send
			// anything", and those two look identical from the client's log.
			net.neoforbrickit.bridge.util.ForbricLog.error(
					"[NeoFobric/Bukkit] plugin channels could not be announced to a joining client;"
							+ " a client mod speaking over one of them will refuse to send",
					failure);
		}
	}

	/**
	 * The channels plugins have registered for incoming messages, or an empty set
	 * while no server exists.
	 *
	 * <p>The plugin manager is global rather than per-player — a plugin registers
	 * its channels in {@code onEnable} — so this is answerable before the player
	 * has joined, which is when the announcement is sent.
	 */
	private static java.util.Set<String> bridgeChannels() {
		org.bukkit.Server server = org.bukkit.Bukkit.getServer();
		if (!(server instanceof ServerImpl bridgeServer)) {
			return java.util.Set.of();
		}
		return bridgeServer.messenger().getIncomingChannels();
	}
}
