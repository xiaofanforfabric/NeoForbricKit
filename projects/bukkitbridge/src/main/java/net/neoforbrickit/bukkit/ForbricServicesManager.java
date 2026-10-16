/*
 * Forbric Bukkit bridge — the plugin service registry (e.g. for Simple Voice Chat).
 */
package net.neoforbrickit.bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The service registry a plugin uses to share code with others.
 *
 * <p>Simple Voice Chat registers itself as a service so other plugins can discover and call it. This
 * implementation stores registrations by service class, sorted by priority (highest first), and answers
 * queries by class or by plugin.
 */
final class ForbricServicesManager implements ServicesManager {
	/** One registration: service class, provider, plugin, priority. */
	private static final class Reg {
		final Class<?> service;
		final Object provider;
		final Plugin plugin;
		final ServicePriority priority;

		Reg(Class<?> service, Object provider, Plugin plugin, ServicePriority priority) {
			this.service = service;
			this.provider = provider;
			this.plugin = plugin;
			this.priority = priority;
		}
	}

	/** All registrations, in order by service class and priority. */
	private final List<Reg> all = new ArrayList<>();

	@Override
	public <T> void register(@NotNull Class<T> service, @NotNull T provider, @NotNull Plugin plugin,
			@NotNull ServicePriority priority) {
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(plugin, "plugin");
		Objects.requireNonNull(priority, "priority");

		all.add(new Reg(service, provider, plugin, priority));
		// Re-sort by service + priority descending (highest first).
		all.sort((a, b) -> {
			int svc = a.service.getName().compareTo(b.service.getName());
			if (svc != 0) return svc;
			return Integer.compare(b.priority.ordinal(), a.priority.ordinal());
		});
	}

	@Override
	public void unregisterAll(@NotNull Plugin plugin) {
		Objects.requireNonNull(plugin, "plugin");
		all.removeIf(r -> r.plugin.equals(plugin));
	}

	@Override
	public void unregister(@NotNull Class<?> service, @NotNull Object provider) {
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(provider, "provider");
		all.removeIf(r -> r.service.equals(service) && r.provider.equals(provider));
	}

	@Override
	public void unregister(@NotNull Object provider) {
		Objects.requireNonNull(provider, "provider");
		all.removeIf(r -> r.provider.equals(provider));
	}

	@Override
	@Nullable
	public <T> T load(@NotNull Class<T> service) {
		Objects.requireNonNull(service, "service");
		RegisteredServiceProvider<T> reg = getRegistration(service);
		return reg == null ? null : reg.getProvider();
	}

	@Override
	@Nullable
	public <T> RegisteredServiceProvider<T> getRegistration(@NotNull Class<T> service) {
		Objects.requireNonNull(service, "service");
		for (Reg r : all) {
			if (r.service.equals(service)) {
				return new SimpleRegistration<>(service, r.provider, r.plugin, r.priority);
			}
		}
		return null;
	}

	@Override
	@NotNull
	public List<RegisteredServiceProvider<?>> getRegistrations(@NotNull Plugin plugin) {
		Objects.requireNonNull(plugin, "plugin");
		List<RegisteredServiceProvider<?>> result = new ArrayList<>();
		for (Reg r : all) {
			if (r.plugin.equals(plugin)) {
				result.add(new SimpleRegistration<>(r.service, r.provider, r.plugin, r.priority));
			}
		}
		return result;
	}

	@Override
	@NotNull
	public <T> Collection<RegisteredServiceProvider<T>> getRegistrations(@NotNull Class<T> service) {
		Objects.requireNonNull(service, "service");
		List<RegisteredServiceProvider<T>> result = new ArrayList<>();
		for (Reg r : all) {
			if (r.service.equals(service)) {
				result.add(new SimpleRegistration<>(service, r.provider, r.plugin, r.priority));
			}
		}
		return result;
	}

	@Override
	@NotNull
	public Collection<Class<?>> getKnownServices() {
		java.util.Set<Class<?>> result = new java.util.HashSet<>();
		for (Reg r : all) {
			result.add(r.service);
		}
		return result;
	}

	@Override
	public <T> boolean isProvidedFor(@NotNull Class<T> service) {
		Objects.requireNonNull(service, "service");
		return all.stream().anyMatch(r -> r.service.equals(service));
	}

	/** A minimal RegisteredServiceProvider implementation. */
	private static final class SimpleRegistration<T> implements RegisteredServiceProvider<T> {
		final Class<T> service;
		final Object provider;
		final Plugin plugin;
		final ServicePriority priority;

		SimpleRegistration(Class<?> service, Object provider, Plugin plugin, ServicePriority priority) {
			@SuppressWarnings("unchecked")
			Class<T> s = (Class<T>) service;
			this.service = s;
			this.provider = provider;
			this.plugin = plugin;
			this.priority = priority;
		}

		@Override
		public Class<T> getService() {
			return service;
		}

		@Override
		@SuppressWarnings("unchecked")
		public T getProvider() {
			return (T) provider;
		}

		@Override
		public Plugin getPlugin() {
			return plugin;
		}

		@Override
		public ServicePriority getPriority() {
			return priority;
		}
	}
}
