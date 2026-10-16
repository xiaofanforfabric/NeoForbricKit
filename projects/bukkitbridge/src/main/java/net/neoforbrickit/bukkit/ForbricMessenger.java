/*
 * Forbric Bukkit bridge — plugin messaging.
 */
package net.neoforbrickit.bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.messaging.PluginMessageListenerRegistration;
import org.bukkit.plugin.messaging.StandardMessenger;

/**
 * The {@code Messenger} a plugin receives from {@code Server.getMessenger()}.
 *
 * <p>Plugin messaging is how a server-side plugin talks to a modded client over a named channel,
 * and it is not optional furniture: a plugin registers an outgoing channel, a client joins, and
 * the plugin pushes a payload down that channel. Simple Voice Chat is built on exactly this, so
 * an unimplemented {@code getMessenger()} leaves it — and every other channel-using plugin —
 * unable to speak to the client.
 *
 * <p><b>What this class is responsible for</b> is the bookkeeping half: which plugin registered
 * which channel in which direction, which listeners are attached to an incoming channel, and the
 * dispatch of an arriving payload to those listeners. The wire half — turning a channel name and
 * a byte array into a packet — lives in {@link ForbricPlayer#sendPluginMessage}, because that is
 * the object the API hands the plugin.
 *
 * <p>Validation follows Bukkit's own rules rather than inventing any: {@link StandardMessenger}
 * is asked to check every name, so the exceptions a plugin sees for a malformed or reserved
 * channel are the ones its author already documented.
 */
final class ForbricMessenger implements Messenger {

	/** Channels each plugin may send on. Insertion-ordered so a channel list reads like a log. */
	private final Map<Plugin, Set<String>> outgoing = new LinkedHashMap<>();

	/**
	 * Listeners per incoming channel, then per plugin.
	 *
	 * <p>Keyed by channel first because dispatch — which happens once per arriving packet — asks
	 * "who wants this channel", not "what did this plugin register".
	 */
	private final Map<String, Map<Plugin, Set<PluginMessageListener>>> incoming = new LinkedHashMap<>();

	@Override
	public boolean isReservedChannel(String channel) {
		Objects.requireNonNull(channel, "channel");
		// Bukkit reserves the vanilla namespace: a plugin may not claim "minecraft:*" for itself.
		return channel.toLowerCase(java.util.Locale.ROOT).startsWith("minecraft:");
	}

	@Override
	public synchronized void registerOutgoingPluginChannel(Plugin plugin, String channel) {
		Objects.requireNonNull(plugin, "plugin");
		StandardMessenger.validateChannel(channel);
		if (isReservedChannel(channel)) {
			throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName()
					+ " cannot send on reserved channel '" + channel + "'");
		}
		outgoing.computeIfAbsent(plugin, key -> new LinkedHashSet<>()).add(channel);
	}

	@Override
	public synchronized void unregisterOutgoingPluginChannel(Plugin plugin, String channel) {
		Objects.requireNonNull(plugin, "plugin");
		Set<String> channels = outgoing.get(plugin);
		if (channels != null) {
			channels.remove(channel);
			if (channels.isEmpty()) {
				outgoing.remove(plugin);
			}
		}
	}

	@Override
	public synchronized void unregisterOutgoingPluginChannel(Plugin plugin) {
		Objects.requireNonNull(plugin, "plugin");
		outgoing.remove(plugin);
	}

	@Override
	public synchronized PluginMessageListenerRegistration registerIncomingPluginChannel(
			Plugin plugin, String channel, PluginMessageListener listener) {
		Objects.requireNonNull(plugin, "plugin");
		StandardMessenger.validateChannel(channel);
		Objects.requireNonNull(listener, "listener");
		if (isReservedChannel(channel)) {
			throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName()
					+ " cannot listen on reserved channel '" + channel + "'");
		}
		incoming.computeIfAbsent(channel, key -> new LinkedHashMap<>())
				.computeIfAbsent(plugin, key -> new LinkedHashSet<>())
				.add(listener);
		return new PluginMessageListenerRegistration(this, plugin, channel, listener);
	}

	@Override
	public synchronized void unregisterIncomingPluginChannel(
			Plugin plugin, String channel, PluginMessageListener listener) {
		Objects.requireNonNull(plugin, "plugin");
		Map<Plugin, Set<PluginMessageListener>> perChannel = incoming.get(channel);
		if (perChannel == null) {
			return;
		}
		Set<PluginMessageListener> listeners = perChannel.get(plugin);
		if (listeners == null) {
			return;
		}
		listeners.remove(listener);
		if (listeners.isEmpty()) {
			perChannel.remove(plugin);
			if (perChannel.isEmpty()) {
				incoming.remove(channel);
			}
		}
	}

	@Override
	public synchronized void unregisterIncomingPluginChannel(Plugin plugin, String channel) {
		Objects.requireNonNull(plugin, "plugin");
		Map<Plugin, Set<PluginMessageListener>> perChannel = incoming.get(channel);
		if (perChannel == null) {
			return;
		}
		perChannel.remove(plugin);
		if (perChannel.isEmpty()) {
			incoming.remove(channel);
		}
	}

	@Override
	public synchronized void unregisterIncomingPluginChannel(Plugin plugin) {
		Objects.requireNonNull(plugin, "plugin");
		for (Map<Plugin, Set<PluginMessageListener>> perChannel : incoming.values()) {
			perChannel.remove(plugin);
		}
		incoming.values().removeIf(Map::isEmpty);
	}

	@Override
	public synchronized Set<String> getOutgoingChannels() {
		Set<String> all = new LinkedHashSet<>();
		for (Set<String> channels : outgoing.values()) {
			all.addAll(channels);
		}
		return Collections.unmodifiableSet(all);
	}

	@Override
	public synchronized Set<String> getOutgoingChannels(Plugin plugin) {
		Objects.requireNonNull(plugin, "plugin");
		Set<String> channels = outgoing.get(plugin);
		return channels == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(channels));
	}

	@Override
	public synchronized Set<String> getIncomingChannels() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(incoming.keySet()));
	}

	@Override
	public synchronized Set<String> getIncomingChannels(Plugin plugin) {
		Objects.requireNonNull(plugin, "plugin");
		Set<String> channels = new LinkedHashSet<>();
		for (Map.Entry<String, Map<Plugin, Set<PluginMessageListener>>> entry : incoming.entrySet()) {
			if (entry.getValue().containsKey(plugin)) {
				channels.add(entry.getKey());
			}
		}
		return Collections.unmodifiableSet(channels);
	}

	@Override
	public synchronized Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(Plugin plugin) {
		Objects.requireNonNull(plugin, "plugin");
		Set<PluginMessageListenerRegistration> registrations = new LinkedHashSet<>();
		for (Map.Entry<String, Map<Plugin, Set<PluginMessageListener>>> entry : incoming.entrySet()) {
			Set<PluginMessageListener> listeners = entry.getValue().get(plugin);
			if (listeners != null) {
				for (PluginMessageListener listener : listeners) {
					registrations.add(new PluginMessageListenerRegistration(this, plugin, entry.getKey(), listener));
				}
			}
		}
		return Collections.unmodifiableSet(registrations);
	}

	@Override
	public synchronized Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(String channel) {
		Objects.requireNonNull(channel, "channel");
		Set<PluginMessageListenerRegistration> registrations = new LinkedHashSet<>();
		Map<Plugin, Set<PluginMessageListener>> perChannel = incoming.get(channel);
		if (perChannel != null) {
			for (Map.Entry<Plugin, Set<PluginMessageListener>> entry : perChannel.entrySet()) {
				for (PluginMessageListener listener : entry.getValue()) {
					registrations.add(new PluginMessageListenerRegistration(this, entry.getKey(), channel, listener));
				}
			}
		}
		return Collections.unmodifiableSet(registrations);
	}

	@Override
	public synchronized Set<PluginMessageListenerRegistration> getIncomingChannelRegistrations(
			Plugin plugin, String channel) {
		Objects.requireNonNull(plugin, "plugin");
		Objects.requireNonNull(channel, "channel");
		Set<PluginMessageListenerRegistration> registrations = new LinkedHashSet<>();
		Map<Plugin, Set<PluginMessageListener>> perChannel = incoming.get(channel);
		if (perChannel != null) {
			Set<PluginMessageListener> listeners = perChannel.get(plugin);
			if (listeners != null) {
				for (PluginMessageListener listener : listeners) {
					registrations.add(new PluginMessageListenerRegistration(this, plugin, channel, listener));
				}
			}
		}
		return Collections.unmodifiableSet(registrations);
	}

	@Override
	public synchronized boolean isRegistrationValid(PluginMessageListenerRegistration registration) {
		Objects.requireNonNull(registration, "registration");
		Map<Plugin, Set<PluginMessageListener>> perChannel = incoming.get(registration.getChannel());
		if (perChannel == null) {
			return false;
		}
		Set<PluginMessageListener> listeners = perChannel.get(registration.getPlugin());
		return listeners != null && listeners.contains(registration.getListener());
	}

	@Override
	public synchronized boolean isIncomingChannelRegistered(Plugin plugin, String channel) {
		Objects.requireNonNull(plugin, "plugin");
		Map<Plugin, Set<PluginMessageListener>> perChannel = incoming.get(channel);
		return perChannel != null && perChannel.containsKey(plugin);
	}

	@Override
	public synchronized boolean isOutgoingChannelRegistered(Plugin plugin, String channel) {
		Objects.requireNonNull(plugin, "plugin");
		Set<String> channels = outgoing.get(plugin);
		return channels != null && channels.contains(channel);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Returns whether any listener actually received the message. The interface method above declares
	 * {@code void}, but the bridge's own seam needs the count: "no plugin registered for this
	 * channel" and "a plugin is registered and did nothing" are different failures, and a message
	 * that silently reaches nobody looks exactly like a plugin that is broken.
	 */
	public boolean deliverIncomingMessage(Player source, String channel, byte[] message) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(message, "message");

		// Snapshot under the lock, deliver outside it: a listener is plugin code, and it may
		// register or unregister channels while it runs.
		List<PluginMessageListener> listeners = new ArrayList<>();
		synchronized (this) {
			Map<Plugin, Set<PluginMessageListener>> perChannel = incoming.get(channel);
			if (perChannel == null) {
				return false;
			}
			for (Map.Entry<Plugin, Set<PluginMessageListener>> entry : perChannel.entrySet()) {
				if (entry.getKey().isEnabled()) {
					listeners.addAll(entry.getValue());
				}
			}
		}
		for (PluginMessageListener listener : listeners) {
			try {
				listener.onPluginMessageReceived(channel, source, message);
			} catch (Throwable failure) {
				// Never silent: this runs on the packet-handling thread, and a throwable escaping
				// here would be attributed to the connection rather than to the plugin that threw.
				net.neoforbrickit.bridge.util.ForbricLog.error(
						"[NeoFobric/Bukkit] plugin message listener for channel " + channel
								+ " threw; the packet is dropped but the connection is kept",
						failure);
			}
		}
		return !listeners.isEmpty();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>No registered channel means nothing to deliver, which the interface renders as a silent
	 * return — the same thing it means for a channel whose listeners all belong to disabled
	 * plugins.
	 */
	@Override
	public void dispatchIncomingMessage(Player source, String channel, byte[] message) {
		deliverIncomingMessage(source, channel, message);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Not supported, by construction rather than by omission: Paper's own packet path uses this
	 * overload, but this bridge never produces a {@code PlayerConnection} — its packets are
	 * delivered through {@link #deliverIncomingMessage(Player, String, byte[])}. The type carries
	 * no way back to the player, so answering here would mean inventing a mapping that does not
	 * exist.
	 */
	@Override
	public void dispatchIncomingMessage(io.papermc.paper.connection.PlayerConnection source,
			String channel, byte[] message) {
		throw new UnsupportedOperationException(
				"This bridge has no PlayerConnection objects; incoming plugin messages arrive as "
						+ "dispatchIncomingMessage(Player, String, byte[])");
	}
}
