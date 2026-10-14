/*
 * Forbric Bukkit bridge — the lifecycle handler configuration a plugin receives from {@code newHandler}.
 */
package org.cn.xiaofan.bukkit;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventOwner;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.handler.configuration.MonitorLifecycleEventHandlerConfiguration;
import io.papermc.paper.plugin.lifecycle.event.handler.configuration.PrioritizedLifecycleEventHandlerConfiguration;

/**
 * What a {@code LifecycleEventType} hands back when a plugin calls {@code registerEventHandler(type, handler)}.
 *
 * <p>Paper's lifecycle types are also the builder for the handler's registration: {@code type.newHandler(handler)}
 * returns a configuration carrying both the event name and the handler, which the plugin then passes to
 * {@link ForbricLifecycleEventManager#registerEventHandler}. Real Paper's own types build their implementation of
 * this interface; this bridge's types build this one, so the manager can read the name and handler back out.
 *
 * <p>The priority and monitor flags are recorded but not yet acted on: without a real event bus there is nothing
 * to order against, and pretending to sort when the relative order cannot matter would be noise.
 *
 * <p>A plain class rather than a record on purpose. The interface's {@code monitor()} method collides with a
 * record component named {@code monitor} — javac refuses, because a record's accessor must return the component's
 * type and this one returns the configuration.
 */
public final class ForbricHandlerConfiguration
		implements PrioritizedLifecycleEventHandlerConfiguration<LifecycleEventOwner>,
				MonitorLifecycleEventHandlerConfiguration<LifecycleEventOwner> {

	private final String eventName;
	private final LifecycleEventHandler<?> handler;
	private final int priority;
	private final boolean monitor;

	public ForbricHandlerConfiguration(String eventName, LifecycleEventHandler<?> handler) {
		this(eventName, handler, 0, false);
	}

	private ForbricHandlerConfiguration(String eventName, LifecycleEventHandler<?> handler, int priority,
			boolean monitor) {
		this.eventName = eventName;
		this.handler = handler;
		this.priority = priority;
		this.monitor = monitor;
	}

	public String eventName() {
		return eventName;
	}

	public LifecycleEventHandler<?> handler() {
		return handler;
	}

	@Override
	public PrioritizedLifecycleEventHandlerConfiguration<LifecycleEventOwner> priority(int priority) {
		return new ForbricHandlerConfiguration(eventName, handler, priority, monitor);
	}

	@Override
	public ForbricHandlerConfiguration monitor() {
		return new ForbricHandlerConfiguration(eventName, handler, priority, true);
	}
}
