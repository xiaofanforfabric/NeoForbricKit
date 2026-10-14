/*
 * Forbric Bukkit bridge — Paper's plugin lifecycle event manager, reimplemented.
 */
package org.cn.xiaofan.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventOwner;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.handler.configuration.LifecycleEventHandlerConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * The {@link LifecycleEventManager} a plugin receives from {@code JavaPlugin.getLifecycleManager()}.
 *
 * <p>Paper's lifecycle lets a plugin hand the server code at named points rather than calling the server
 * directly — a {@code LifecycleEvents.COMMANDS} handler, for example, is where a modern Paper plugin registers
 * its Brigadier commands. {@code JavaPlugin}'s constructor asks {@code InternalAPIBridge} for one of these on
 * every instantiation, so a plugin cannot even be constructed without it.
 *
 * <p><b>What this does, and what it does not.</b> Registrations are recorded, in order, per event name, and can
 * be fired by the bridge when the corresponding point is reached. The lifecycle this bridge actually drives is
 * small — a plugin's {@code onLoad}/{@code onEnable} — so most registered events have no firing point yet and
 * simply sit in the list. That is deliberate: a plugin registering a handler must not crash, and the handler
 * running at the wrong time would be worse than it not running at all.
 *
 * <p>The "already fired" guard matters more than it looks. Paper's real manager throws if a plugin registers
 * after the owning plugin has finished enabling, because the handler would never run. Being silent there would
 * make a plugin look like it registered commands that never appear.
 */
final class ForbricLifecycleEventManager implements LifecycleEventManager<Plugin> {

	private final Plugin owner;
	private final BooleanSupplier allowsRegistration;

	/** One recorded registration: the event it belongs to and the handler to run. */
	private record Registration(String eventName, LifecycleEventHandler<?> handler) {
	}

	private final List<Registration> registrations = new ArrayList<>();

	/** Set once the owning plugin's enable window has closed, matching Paper's late-registration refusal. */
	private boolean closed;

	ForbricLifecycleEventManager(Plugin owner, BooleanSupplier allowsRegistration) {
		this.owner = owner;
		this.allowsRegistration = allowsRegistration;
	}

	@Override
	public void registerEventHandler(LifecycleEventHandlerConfiguration<? super Plugin> configuration) {
		if (!allowsRegistration.getAsBoolean()) {
			throw new IllegalStateException("[Forbric/Bukkit] " + owner.getName()
					+ " tried to register a lifecycle event handler outside its enable time");
		}
		if (!(configuration instanceof ForbricHandlerConfiguration forging)) {
			throw new IllegalStateException("[Forbric/Bukkit] unknown lifecycle handler configuration "
					+ configuration.getClass().getName() + " — this bridge only produces its own");
		}
		registrations.add(new Registration(forging.eventName(), forging.handler()));
	}

	/**
	 * Fires every handler registered for {@code eventName}, in registration order, and drops them.
	 *
	 * <p>Called by the bridge, not by a plugin. A handler that throws propagates: Paper's own manager does not
	 * swallow plugin code, and a lifecycle handler is plugin code.
	 */
	void fire(String eventName, LifecycleEvent event) {
		List<Registration> matching = new ArrayList<>();
		for (Registration registration : registrations) {
			if (registration.eventName().equals(eventName)) matching.add(registration);
		}
		registrations.removeAll(matching);

		for (Registration registration : matching) {
			fireOne(registration.handler(), event);
		}
	}

	@SuppressWarnings("unchecked")
	private static <E extends LifecycleEvent> void fireOne(LifecycleEventHandler<?> handler, LifecycleEvent event) {
		((LifecycleEventHandler<E>) handler).run((E) event);
	}

	/** True when nothing has been registered for any event — the common case, and cheap to ask. */
	boolean isEmpty() {
		return registrations.isEmpty();
	}

	/** Closes the registration window, as Paper does once the plugin has enabled. */
	void close() {
		this.closed = true;
	}

	boolean isClosed() {
		return closed;
	}

	/** Every event name this manager has a handler for, for diagnostics. */
	List<String> registeredEventNames() {
		List<String> names = new ArrayList<>();
		for (Registration registration : registrations) {
			if (!names.contains(registration.eventName())) names.add(registration.eventName());
		}
		return names;
	}
}
