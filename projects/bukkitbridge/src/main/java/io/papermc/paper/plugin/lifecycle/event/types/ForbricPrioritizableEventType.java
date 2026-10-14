/*
 * Forbric Bukkit bridge — one named lifecycle event type, in its prioritizable form.
 */
package io.papermc.paper.plugin.lifecycle.event.types;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventOwner;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.handler.configuration.PrioritizedLifecycleEventHandlerConfiguration;
import org.cn.xiaofan.bukkit.ForbricHandlerConfiguration;

/**
 * One lifecycle event type that handlers may register against with a priority.
 *
 * <p><b>Why this is two classes and not one.</b> {@code Monitorable} and {@code Prioritizable} both declare
 * {@code newHandler(LifecycleEventHandler)} and differ only in the configuration type they return. That is a
 * legal pair of interfaces in the API, but not an implementable one: erasure makes both methods
 * {@code newHandler(LifecycleEventHandler)}, same parameters, different return types, and javac refuses —
 * "cannot implement ... return type is not compatible". So the bridge has one class per interface, and the
 * provider picks the one the caller asked for.
 *
 * <p><b>What {@code newHandler} does.</b> Packages the name and handler into a
 * {@link ForbricHandlerConfiguration}, which the plugin hands back to
 * {@code LifecycleEventManager.registerEventHandler}; the type itself keeps nothing, so one type can produce
 * any number of configurations. Paper's own types behave the same way.
 */
public final class ForbricPrioritizableEventType<O extends LifecycleEventOwner, E extends LifecycleEvent>
                implements LifecycleEventType.Prioritizable<O, E> {

    private final String name;

    ForbricPrioritizableEventType(String name) {
        this.name = name;
    }

    /** The name a handler is filed under, and the name the bridge fires it by. */
    @Override
    public String name() {
        return name;
    }

    @Override
    public PrioritizedLifecycleEventHandlerConfiguration<O> newHandler(LifecycleEventHandler<? super E> handler) {
        return (PrioritizedLifecycleEventHandlerConfiguration<O>) new ForbricHandlerConfiguration(name, handler);
    }
}
