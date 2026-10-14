/*
 * Forbric Bukkit bridge — the provider Paper's API looks up through ServiceLoader.
 */
package io.papermc.paper.plugin.lifecycle.event.types;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventOwner;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType.Monitorable;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType.Prioritizable;

/**
 * Supplies the lifecycle event types {@code LifecycleEvents} is built from.
 *
 * <p><b>Why this class is in Paper's package.</b> {@code LifecycleEventTypeProvider} is not public, and
 * {@code ServiceLoader} resolves a provider through the interface's own class loader, so an implementation has
 * to be in {@code io.papermc.paper.plugin.lifecycle.event.types} to be seen at all. Paper's own server jar puts
 * its provider here for the same reason; this bridge is standing in for it.
 *
 * <p><b>What was failing without it.</b> {@code LifecycleEvents} is a class whose static fields are built by
 * calling {@code LifecycleEventTypeProvider.provider()}. With no provider the lookup returns empty, the
 * {@code orElseThrow} inside {@code provider()} throws {@code NoSuchElementException}, and the class fails to
 * initialise — so the FIRST plugin to mention {@code LifecycleEvents.COMMANDS} dies in an
 * {@code ExceptionInInitializerError} that names neither the missing service nor this bridge.
 *
 * <p><b>What the types it returns can and cannot do.</b> Each carries a name and builds a handler
 * configuration. The monitorable and prioritizable forms are separate classes because their
 * {@code newHandler} methods erase to one signature and cannot both be implemented; see
 * {@link ForbricPrioritizableEventType}. Firing is what they do not do:
 * that belongs to {@code ForbricLifecycleEventManager}, which the bridge drives. A type that claimed to fire
 * on its own would be lying about where the server's lifecycle points are, and a plugin cannot tell the
 * difference until its handler silently never runs.
 */
public final class ForbricLifecycleEventTypeProvider implements LifecycleEventTypeProvider {

    /**
     * Required by {@code ServiceLoader}, which instantiates a provider reflectively with no arguments.
     *
     * <p>Trivial because the provider is stateless: it is a factory, and every type it hands out is a fresh
     * object carrying its own name. Sharing one instance across the three fields {@code LifecycleEvents}
     * initialises would be possible and would gain nothing.
     */
    public ForbricLifecycleEventTypeProvider() {
    }

    @Override
    public <O extends LifecycleEventOwner, E extends io.papermc.paper.plugin.lifecycle.event.LifecycleEvent>
                    Monitorable<O, E> monitor(String name, Class<? extends O> ownerType) {
        return new ForbricMonitorableEventType<>(name);
    }

    @Override
    public <O extends LifecycleEventOwner, E extends io.papermc.paper.plugin.lifecycle.event.LifecycleEvent>
                    Prioritizable<O, E> prioritized(String name, Class<? extends O> ownerType) {
        return new ForbricPrioritizableEventType<>(name);
    }

    @Override
    public TagEventTypeProvider tagProvider() {
        return new ForbricTagEventTypeProvider();
    }
}
