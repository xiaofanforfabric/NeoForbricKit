/*
 * Forbric Bukkit bridge — one named lifecycle event type, in its monitorable form.
 */
package io.papermc.paper.plugin.lifecycle.event.types;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventOwner;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.handler.configuration.MonitorLifecycleEventHandlerConfiguration;
import org.cn.xiaofan.bukkit.ForbricHandlerConfiguration;

/**
 * One lifecycle event type a handler may register against as a monitor.
 *
 * <p>The sibling of {@link ForbricPrioritizableEventType}, and separate for the reason described there: the two
 * interfaces cannot both be implemented, because their {@code newHandler} methods erase to the same signature.
 *
 * <p>A monitor observes and does not alter — which in Bukkit's model means its handler runs regardless of what
 * another handler does with the event. There is no ordering to observe here yet, so this differs from the
 * prioritizable form only in the configuration type it hands back.
 */
public final class ForbricMonitorableEventType<O extends LifecycleEventOwner, E extends LifecycleEvent>
                implements LifecycleEventType.Monitorable<O, E> {

    private final String name;

    ForbricMonitorableEventType(String name) {
        this.name = name;
    }

    /** The name a handler is filed under, and the name the bridge fires it by. */
    @Override
    public String name() {
        return name;
    }

    /**
     * Two unchecked steps, and each has a reason.
     *
     * <p>The cast from {@code Prioritized...<LifecycleEventOwner>} to
     * {@code MonitorLifecycleEventHandlerConfiguration<O>} is unchecked because the shared configuration is
     * built against the widest owner type and this method promises the caller's narrower one. That promise
     * holds at runtime — a type is created for one owner class and only that class's plugins can register
     * against it — but the compiler cannot see it, and erasure means the interface's own methods could not
     * have expressed it any more precisely.
     */
    @Override
    @SuppressWarnings("unchecked")
    public MonitorLifecycleEventHandlerConfiguration<O> newHandler(LifecycleEventHandler<? super E> handler) {
        return (MonitorLifecycleEventHandlerConfiguration<O>) (MonitorLifecycleEventHandlerConfiguration<?>)
                        new ForbricHandlerConfiguration(name, handler);
    }
}
