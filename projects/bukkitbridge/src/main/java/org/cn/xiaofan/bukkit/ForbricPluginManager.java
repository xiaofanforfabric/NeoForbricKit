/*
 * Forbric Bukkit bridge — the plugin manager a plugin gets from {@code getServer().getPluginManager()}.
 */
package org.cn.xiaofan.bukkit;

import java.util.List;

/**
 * The plugin manager this bridge hands to plugins.
 *
 * <p><b>Almost all of it is Bukkit's own.</b> {@code SimplePluginManager} ships inside paper-api and is the
 * implementation the real server runs: event registration with priority ordering, {@code callEvent} dispatch,
 * the permission tables, and the recursive disable of a plugin's dependants. None of that is Forbric-specific and
 * none of it can be improved by rewriting — so it is delegated to, method for method.
 *
 * <p><b>Six methods are answered here instead, and the reason is the loader.</b> {@code SimplePluginManager}
 * tracks plugins in a private list that only its own {@code loadPlugin(File)} fills, and it is {@code final}, so
 * the list cannot be reached. This bridge loads plugins its own way — {@link PluginHandle} reads a jar, builds a
 * {@link PluginClassLoader}, and calls {@code onLoad}/{@code setEnabled} — which means the manager's list stays
 * empty while plugins are plainly running. Asking it {@code getPlugin("KitPlugin")} would answer null about a
 * plugin the server just started.
 *
 * <p>So the six that read or change that list are answered from the handles the bridge actually holds:
 * {@code getPlugin}, {@code getPlugins}, both {@code isPluginEnabled}, {@code enablePlugin}, and
 * {@code disablePlugin}. Everything else has no opinion about the list and is forwarded unchanged.
 *
 * <p><b>Why not register the plugins with it instead.</b> {@code loadPlugin(File)} goes through Bukkit's own
 * {@code PluginLoader} machinery, which would mean giving up {@link PluginClassLoader} — the loader that puts
 * {@code org.bukkit} and {@code net.minecraft} in a plugin's reach and that {@code JavaPlugin}'s own constructor
 * guard requires. Handing over loading to satisfy six getters would cost the thing that makes plugins work at
 * all.
 */
final class ForbricPluginManager implements org.bukkit.plugin.PluginManager {

    /** Bukkit's own manager, which everything not listed in this class's comment is delegated to. */
    private final org.bukkit.plugin.PluginManager delegate;

    /** The bridge's loaded plugins, in load order. */
    private final List<PluginHandle> handles;

    private final ServerImpl server;

    ForbricPluginManager(org.bukkit.plugin.PluginManager delegate, List<PluginHandle> handles,
                    ServerImpl server) {
        this.delegate = delegate;
        this.handles = handles;
        this.server = server;
    }

    /**
     * One plugin by the name it declares in {@code plugin.yml}, or null.
     *
     * <p>Case-insensitive, matching Bukkit: {@code getPlugin("kitplugin")} and {@code getPlugin("KitPlugin")}
     * are the same question, and a plugin loader that answered only one of them would break the other's callers.
     */
    @Override
    public org.bukkit.plugin.Plugin getPlugin(String name) {
        if (name == null) {
            return null;
        }
        for (PluginHandle handle : handles) {
            if (handle.description().getName().equalsIgnoreCase(name)) {
                return handle.plugin();
            }
        }
        return null;
    }

    /** Every loaded plugin, in load order. */
    @Override
    public org.bukkit.plugin.Plugin[] getPlugins() {
        org.bukkit.plugin.Plugin[] plugins = new org.bukkit.plugin.Plugin[handles.size()];
        for (int i = 0; i < handles.size(); i++) {
            plugins[i] = handles.get(i).plugin();
        }
        return plugins;
    }

    /** Whether a plugin by that name is loaded AND enabled. */
    @Override
    public boolean isPluginEnabled(String name) {
        org.bukkit.plugin.Plugin plugin = getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    /**
     * Whether this plugin is one of ours, and enabled.
     *
     * <p>Asked of the identity set rather than of {@code plugin.isEnabled()} alone: a plugin that failed to load
     * is not ours, and answering "enabled" for one would be worse than answering false.
     */
    @Override
    public boolean isPluginEnabled(org.bukkit.plugin.Plugin plugin) {
        if (plugin == null) {
            return false;
        }
        for (PluginHandle handle : handles) {
            if (handle.plugin() == plugin) {
                return plugin.isEnabled();
            }
        }
        return false;
    }

    /**
     * Enables one of our plugins.
     *
     * <p>Runs {@code onLoad} then {@code setEnabled(true)}, the same order the bridge's own startup uses, because
     * a re-enable has to look like a first enable to the plugin. A plugin we do not hold is refused by name
     * rather than silently ignored — the caller almost certainly has the wrong object.
     */
    @Override
    public void enablePlugin(org.bukkit.plugin.Plugin plugin) {
        for (PluginHandle handle : handles) {
            if (handle.plugin() == plugin) {
                if (!plugin.isEnabled()) {
                    // Through the server, because enabling also fires the plugin's lifecycle handlers and those
                    // need the game's command dispatcher -- which the manager has no reason to hold.
                    server.enablePlugin(handle);
                }
                return;
            }
        }
        throw new IllegalArgumentException(
                        "[Forbric/Bukkit] PluginManager.enablePlugin() does not know " + plugin
                                        + "; it was not loaded by this bridge");
    }

    /**
     * Disables one of our plugins, running {@code onDisable}.
     *
     * <p>This is the method that made the manager worth building early: it is what a plugin calls on ITSELF when
     * its {@code onEnable} fails, so a null manager turned every plugin startup failure into a
     * NullPointerException that hid the original reason.
     */
    @Override
    public void disablePlugin(org.bukkit.plugin.Plugin plugin) {
        for (PluginHandle handle : handles) {
            if (handle.plugin() == plugin) {
                handle.disable();
                return;
            }
        }
        throw new IllegalArgumentException(
                        "[Forbric/Bukkit] PluginManager.disablePlugin() does not know " + plugin
                                        + "; it was not loaded by this bridge");
    }

    // ---------------------------------------------------------------------------------------------
    // Everything below is Bukkit's own behaviour, forwarded unchanged. Listed one by one rather
    // than reached through a proxy so that a method Paper adds shows up here as a compile error
    // instead of being answered with an InvocationTargetException at runtime.
    //
    // A method is only forwarded when SimplePluginManager IMPLEMENTS it. The ones it forwards —
    // `loadPlugin`, `loadPlugins(File[])`, `disablePlugins`, `clearPlugins`, `callEvent` — are
    // answered here instead, because its forward leads back to this object and would recurse.
    // ------------------------------------------------------------------------------------------

    @Override
    public void registerInterface(Class<? extends org.bukkit.plugin.PluginLoader> loader)
                    throws IllegalArgumentException {
        // SimplePluginManager implements this one itself: it seeds its file-association table.
        delegate.registerInterface(loader);
    }

    /**
     * Loads a plugin file.
     *
     * <p>Refused. {@code SimplePluginManager} forwards this to {@code paperPluginManager} — this object — so
     * delegating would recurse, and the bridge has no loader to load a plugin with anyway: it loads plugins
     * its own way, through {@link PluginHandle}, because {@link PluginClassLoader} is what puts
     * {@code org.bukkit} and {@code net.minecraft} in a plugin's reach. A plugin dropped into
     * {@code plugins/} is picked up at startup.
     */
    @Override
    public org.bukkit.plugin.Plugin loadPlugin(java.io.File file)
                    throws org.bukkit.plugin.InvalidPluginException,
                    org.bukkit.plugin.InvalidDescriptionException,
                    org.bukkit.plugin.UnknownDependencyException {
        throw new UnsupportedOperationException(
                        "[Forbric/Bukkit] PluginManager.loadPlugin() is not supported by this bridge: plugins"
                                        + " are loaded from the plugins/ folder at startup by the bridge itself."
                                        + " Put the jar there and restart.");
    }

    /** The one-argument form is real code in {@code SimplePluginManager}; it delegates to the refused form. */
    @Override
    public org.bukkit.plugin.Plugin[] loadPlugins(java.io.File directory) {
        return delegate.loadPlugins(directory);
    }

    /** Refused for the same reason as {@link #loadPlugin}: the array form is a forward, not an implementation. */
    @Override
    public org.bukkit.plugin.Plugin[] loadPlugins(java.io.File[] files) {
        throw new UnsupportedOperationException(
                        "[Forbric/Bukkit] PluginManager.loadPlugins(File[]) is not supported by this bridge:"
                                        + " plugins are loaded from the plugins/ folder at startup by the bridge"
                                        + " itself. Put the jars there and restart.");
    }

    /**
     * Disables every plugin this bridge loaded, in reverse load order.
     *
     * <p>Answered here because {@code SimplePluginManager} forwards it. Reverse order matters: a plugin that
     * depends on another must be disabled before the one it depends on.
     */
    @Override
    public void disablePlugins() {
        for (int i = handles.size() - 1; i >= 0; i--) {
            PluginHandle handle = handles.get(i);
            if (handle.plugin() != null && handle.plugin().isEnabled()) {
                handle.disable();
            }
        }
    }

    /**
     * Clears the loaded-plugin bookkeeping.
     *
     * <p>Refused because {@code SimplePluginManager} forwards it, so delegating would recurse, and because the
     * bridge's list of loaded plugins belongs to the bridge rather than to a plugin.
     */
    @Override
    public void clearPlugins() {
        throw new UnsupportedOperationException(
                        "[Forbric/Bukkit] PluginManager.clearPlugins() is not supported by this bridge: the"
                                        + " list of loaded plugins belongs to the bridge, not to a plugin");
    }


    /**
     * Registers every {@code @EventHandler} a listener declares.
     *
     * <p>Answered here rather than forwarded, and the reason is a trap: {@code SimplePluginManager}'s own
     * {@code registerEvents} is a null forward (see the class comment), so delegating to it threw a
     * {@code NullPointerException} the moment a plugin called this in {@code onEnable} — which made the
     * plugin fail to load at all.
     *
     * <p>The scan is the documented Bukkit algorithm: walk the listener's class and its superclasses, take
     * every method carrying {@code @EventHandler}, and register it through {@code registerEvent} — which is
     * the one event method {@code SimplePluginManager} really implements, so it is safe to delegate to.
     */
    @Override
    public void registerEvents(org.bukkit.event.Listener listener, org.bukkit.plugin.Plugin plugin) {
        if (listener == null || plugin == null) {
            throw new IllegalArgumentException(
                            "[Forbric/Bukkit] registerEvents needs a listener and its plugin");
        }
        // The whole hierarchy, because a listener commonly extends a base class that holds shared handlers.
        for (Class<?> type = listener.getClass(); type != null && type != Object.class;
                        type = type.getSuperclass()) {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                org.bukkit.event.EventHandler annotation =
                                method.getAnnotation(org.bukkit.event.EventHandler.class);
                if (annotation == null) {
                    continue;
                }
                method.setAccessible(true);
                // The event class is the handler's single parameter. A method annotated but taking anything
                // else is a mistake in the plugin, and Bukkit reports it the same way we do.
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 1 || !org.bukkit.event.Event.class.isAssignableFrom(parameters[0])) {
                    throw new IllegalStateException(
                                    "[Forbric/Bukkit] " + method + " has @EventHandler but its only parameter"
                                                    + " is not an Event");
                }
                @SuppressWarnings("unchecked")
                Class<? extends org.bukkit.event.Event> event =
                                (Class<? extends org.bukkit.event.Event>) parameters[0];
                org.bukkit.plugin.EventExecutor executor = (called, eventObject) -> {
                    try {
                        method.invoke(listener, eventObject);
                    } catch (java.lang.reflect.InvocationTargetException wrapped) {
                        // The handler's own exception is what the plugin author needs to see; the
                        // reflection wrapper around it is noise.
                        Throwable cause = wrapped.getCause() == null ? wrapped : wrapped.getCause();
                        throw new java.lang.reflect.UndeclaredThrowableException(cause);
                    } catch (IllegalAccessException unreachable) {
                        throw new java.lang.IllegalStateException(
                                        "[Forbric/Bukkit] " + method + " could not be invoked", unreachable);
                    }
                };
                registerEvent(event, listener, annotation.priority(), executor, plugin,
                                annotation.ignoreCancelled());
            }
        }
    }

    /**
     * Delivers an event to every handler registered for its class.
     *
     * <p>Answered here rather than forwarded, because {@link SimplePluginManager}'s own {@code callEvent} is a
     * null forward — and even with the field set, it dispatches from ITS registration table, which is the one
     * this class cannot fill (see the class comment). The bridge's own table is
     * {@link #registrations}, so the two must be kept together: registrations are recorded here and delivered
     * here.
     *
     * <p>Ordering is Bukkit's: highest priority first, and among equal priorities the registration order.
     * A handler registered with {@code ignoreCancelled} is skipped once the event is cancelled.
     */
    @Override
    public void callEvent(org.bukkit.event.Event event) throws IllegalStateException {
        if (event == null) {
            return;
        }
        if (event.isAsynchronous() && server.isPrimaryThread()) {
            throw new IllegalStateException(
                            event.getClass().getSimpleName() + " may not be triggered from the primary thread");
        }
        java.util.List<Registration> forThisEvent = new java.util.ArrayList<>();
        synchronized (registrations) {
            for (Registration registration : registrations) {
                if (registration.event().isInstance(event)) {
                    forThisEvent.add(registration);
                }
            }
        }
        // Bukkit's ordering: HIGHEST first (it is declared first in EventPriority), then by registration order
        // for equal priorities. A stable sort keeps that second guarantee.
        forThisEvent.sort(java.util.Comparator.comparingInt(r -> r.priority().ordinal()));
        for (Registration registration : forThisEvent) {
            if (event instanceof org.bukkit.event.Cancellable cancellable
                            && cancellable.isCancelled() && !registration.ignoreCancelled()) {
                continue;
            }
            try {
                registration.executor().execute(registration.listener(), event);
            } catch (Throwable thrown) {
                // One handler failing must not stop the others -- Bukkit reports and continues.
                org.bukkit.plugin.Plugin plugin = registration.plugin();
                server.getLogger().log(java.util.logging.Level.SEVERE,
                                "[Forbric/Bukkit] " + plugin.getName() + " threw while handling "
                                                + event.getClass().getSimpleName(),
                                thrown);
            }
        }
    }

    /** The five-argument form is real code in {@code SimplePluginManager}, which then calls the six-argument one. */
    @Override
    public void registerEvent(Class<? extends org.bukkit.event.Event> event, org.bukkit.event.Listener listener,
                    org.bukkit.event.EventPriority priority, org.bukkit.plugin.EventExecutor executor,
                    org.bukkit.plugin.Plugin plugin) {
        delegate.registerEvent(event, listener, priority, executor, plugin);
    }

    /**
     * Registers one handler for one event class.
     *
     * <p>Answered here because {@code SimplePluginManager}'s own form forwards to {@code paperPluginManager}
     * — this object — so delegating would recurse. The bookkeeping is the bridge's: a list of registrations
     * per event class, consulted by {@link #callEvent} (which uses {@code SimplePluginManager}'s real
     * dispatcher, so the two must agree on what is registered).
     *
     * <p>{@code ignoreCancelled} is honoured at dispatch time, which is where Bukkit honours it too: the
     * registration records whether a cancelled event should still reach this handler.
     */
    @Override
    public void registerEvent(Class<? extends org.bukkit.event.Event> event, org.bukkit.event.Listener listener,
                    org.bukkit.event.EventPriority priority, org.bukkit.plugin.EventExecutor executor,
                    org.bukkit.plugin.Plugin plugin, boolean ignoreCancelled) {
        if (event == null || listener == null || executor == null || plugin == null) {
            throw new IllegalArgumentException(
                            "[Forbric/Bukkit] registerEvent needs an event class, a listener, an executor and its"
                                            + " plugin");
        }
        registrations.add(new Registration(event, listener, priority, executor, plugin, ignoreCancelled));
    }

    /**
     * One handler registration, as the bridge recorded it.
     *
     * <p>Kept per event class rather than pushed into {@code SimplePluginManager}'s own structures, because
     * those are reached only through the forwards that are null here.
     */
    private record Registration(Class<? extends org.bukkit.event.Event> event,
                    org.bukkit.event.Listener listener,
                    org.bukkit.event.EventPriority priority,
                    org.bukkit.plugin.EventExecutor executor,
                    org.bukkit.plugin.Plugin plugin,
                    boolean ignoreCancelled) {
    }

    /** Every handler registered through this manager, in registration order. */
    private final java.util.List<Registration> registrations =
                    java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    @Override
    public org.bukkit.permissions.Permission getPermission(String name) {
        if (name == null) {
            return null;
        }
        // SimplePluginManager's Paper extension is initialized by a real Paper
        // server, not by its public constructor. Calling delegate.getPermission
        // here therefore dereferences its null paperPluginManager field. The
        // bridge already has the authoritative plugin.yml descriptions, so use
        // those instead of reaching into Paper's private initialization path.
        for (PluginHandle handle : handles) {
            for (org.bukkit.permissions.Permission permission : handle.description().getPermissions()) {
                if (name.equalsIgnoreCase(permission.getName())) {
                    return permission;
                }
            }
        }
        return null;
    }

    /**
     * The permissions this bridge knows about, by name.
     *
     * <p>Filled from each plugin's {@code plugin.yml} as it loads, and by {@link #addPermission}. This is the
     * bridge's own table rather than {@code SimplePluginManager}'s because that one lives on the far side of
     * the {@code paperPluginManager} forward (see the class comment) and would be a null dereference.
     */
    private final java.util.Map<String, org.bukkit.permissions.Permission> permissions =
                    java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());

    /**
     * Which permissible subscribed to which permission, and to the two default sets.
     *
     * <p>Bukkit needs this for live permission recalculation: when a permission's default changes, everyone
     * subscribed to it must be re-asked. No plugin this bridge has run calls it, but the interface demands
     * an answer, and answering with an empty set is better than a null dereference.
     */
    private final java.util.Map<String, java.util.Set<org.bukkit.permissions.Permissible>> subscriptions =
                    java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());

    private final java.util.Set<org.bukkit.permissions.Permissible> defaultOpSubscriptions =
                    java.util.Collections.synchronizedSet(
                                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

    private final java.util.Set<org.bukkit.permissions.Permissible> defaultNotOpSubscriptions =
                    java.util.Collections.synchronizedSet(
                                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

    @Override
    public void addPermission(org.bukkit.permissions.Permission perm) {
        if (perm != null) {
            permissions.put(perm.getName().toLowerCase(java.util.Locale.ROOT), perm);
            recalculatePermissionDefaults(perm);
        }
    }

    @Override
    public void addPermissions(java.util.List<org.bukkit.permissions.Permission> perms) {
        if (perms != null) {
            for (org.bukkit.permissions.Permission perm : perms) {
                addPermission(perm);
            }
        }
    }

    @Override
    public void removePermission(org.bukkit.permissions.Permission perm) {
        if (perm != null) {
            permissions.remove(perm.getName().toLowerCase(java.util.Locale.ROOT));
        }
    }

    @Override
    public void removePermission(String name) {
        if (name != null) {
            permissions.remove(name.toLowerCase(java.util.Locale.ROOT));
        }
    }

    @Override
    public java.util.Set<org.bukkit.permissions.Permission> getPermissions() {
        return new java.util.LinkedHashSet<>(permissions.values());
    }

    /**
     * The permissions whose default matches {@code op}, which is what "default permissions" means.
     *
     * <p>{@code TRUE} and {@code FALSE} count for both answers, because a permission defaulted to either is
     * granted (or refused) to everyone including operators.
     */
    @Override
    public java.util.Set<org.bukkit.permissions.Permission> getDefaultPermissions(boolean op) {
        java.util.Set<org.bukkit.permissions.Permission> result = new java.util.LinkedHashSet<>();
        for (org.bukkit.permissions.Permission permission : permissions.values()) {
            org.bukkit.permissions.PermissionDefault fallback = permission.getDefault();
            boolean applies = fallback == org.bukkit.permissions.PermissionDefault.TRUE
                            || fallback == org.bukkit.permissions.PermissionDefault.FALSE
                            || (fallback == org.bukkit.permissions.PermissionDefault.OP) == op
                            || (fallback == org.bukkit.permissions.PermissionDefault.NOT_OP) != op;
            if (applies) {
                result.add(permission);
            }
        }
        return result;
    }

    /** Re-asks everyone subscribed to a permission, which is why the subscription sets exist at all. */
    @Override
    public void recalculatePermissionDefaults(org.bukkit.permissions.Permission perm) {
        if (perm == null) {
            return;
        }
        java.util.Set<org.bukkit.permissions.Permissible> subscribed =
                        getPermissionSubscriptions(perm.getName());
        for (org.bukkit.permissions.Permissible permissible : subscribed) {
            permissible.recalculatePermissions();
        }
        for (org.bukkit.permissions.Permissible permissible : getDefaultPermSubscriptions(perm.getDefault()
                        == org.bukkit.permissions.PermissionDefault.OP
                        || perm.getDefault() == org.bukkit.permissions.PermissionDefault.TRUE)) {
            permissible.recalculatePermissions();
        }
    }

    @Override
    public void subscribeToPermission(String permission, org.bukkit.permissions.Permissible permissible) {
        if (permission == null || permissible == null) {
            return;
        }
        String key = permission.toLowerCase(java.util.Locale.ROOT);
        synchronized (subscriptions) {
            subscriptions.computeIfAbsent(key, ignored -> java.util.Collections.synchronizedSet(
                            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())))
                            .add(permissible);
        }
    }

    @Override
    public void unsubscribeFromPermission(String permission, org.bukkit.permissions.Permissible permissible) {
        if (permission == null || permissible == null) {
            return;
        }
        java.util.Set<org.bukkit.permissions.Permissible> set =
                        subscriptions.get(permission.toLowerCase(java.util.Locale.ROOT));
        if (set != null) {
            set.remove(permissible);
        }
    }

    @Override
    public java.util.Set<org.bukkit.permissions.Permissible> getPermissionSubscriptions(String permission) {
        if (permission == null) {
            return java.util.Set.of();
        }
        java.util.Set<org.bukkit.permissions.Permissible> set =
                        subscriptions.get(permission.toLowerCase(java.util.Locale.ROOT));
        return set == null ? java.util.Set.of() : new java.util.LinkedHashSet<>(set);
    }

    @Override
    public void subscribeToDefaultPerms(boolean op, org.bukkit.permissions.Permissible permissible) {
        if (permissible != null) {
            (op ? defaultOpSubscriptions : defaultNotOpSubscriptions).add(permissible);
        }
    }

    @Override
    public void unsubscribeFromDefaultPerms(boolean op, org.bukkit.permissions.Permissible permissible) {
        if (permissible != null) {
            (op ? defaultOpSubscriptions : defaultNotOpSubscriptions).remove(permissible);
        }
    }

    @Override
    public java.util.Set<org.bukkit.permissions.Permissible> getDefaultPermSubscriptions(boolean op) {
        return new java.util.LinkedHashSet<>(op ? defaultOpSubscriptions : defaultNotOpSubscriptions);
    }

    @Override
    public void clearPermissions() {
        permissions.clear();
        subscriptions.clear();
        defaultOpSubscriptions.clear();
        defaultNotOpSubscriptions.clear();
    }

    /** Whether a permission name is one this bridge knows, which is what {@link #getPermission} answers. */
    @Override
    public void overridePermissionManager(org.bukkit.plugin.Plugin plugin,
                    io.papermc.paper.plugin.PermissionManager permissionManager) {
        // The bridge has one permission table for the whole server, which is what a server-side stack looks
        // like; a per-plugin override would need Paper's loader to be the one loading the plugin.
        throw new UnsupportedOperationException(
                        "[Forbric/Bukkit] PluginManager.overridePermissionManager() is not supported by this"
                                        + " bridge: it has a single permission table for the whole server");
    }

    /** Whether one plugin's metadata declares another as its transitive dependency. */
    @Override
    public boolean isTransitiveDependency(io.papermc.paper.plugin.configuration.PluginMeta dependant,
                    io.papermc.paper.plugin.configuration.PluginMeta dependee) {
        return false;
    }

    /** Whether the server is collecting timing data. This bridge has no timing recorder, so it is false. */
    @Override
    public boolean useTimings() {
        return false;
    }
}
