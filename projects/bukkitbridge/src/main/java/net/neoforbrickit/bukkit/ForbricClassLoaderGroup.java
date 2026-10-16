/*
 * Forbric Bukkit bridge — the group that lets one plugin's classes see another's.
 */
package net.neoforbrickit.bukkit;

import java.util.function.Supplier;

import io.papermc.paper.plugin.provider.classloader.ClassLoaderAccess;
import io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader;
import io.papermc.paper.plugin.provider.classloader.PluginClassLoaderGroup;
import net.neoforbrickit.bridge.util.ForbricLog;

/**
 * The "open group" every plugin's class loader belongs to.
 *
 * <h2>Why this is needed</h2>
 *
 * <p>A plugin that integrates with another one has to load a class out of it. Simple Voice Chat is the case
 * that forced this into existence:
 *
 * <pre>{@code
 * if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
 *     new VoicechatExpansion().register();      // VoicechatExpansion extends PlaceholderExpansion
 * }
 * }</pre>
 *
 * <p>{@code VoicechatExpansion} is in Voice Chat's own jar, so Voice Chat's loader finds it. Its SUPERCLASS
 * {@code me.clip.placeholderapi.expansion.PlaceholderExpansion} is in PlaceholderAPI's jar, and resolving a
 * superclass goes through the loader that defined the subclass — which, without this group, has no way to
 * reach another plugin's jar. The result is {@code NoClassDefFoundError} inside the plugin's {@code onEnable},
 * where the plugin reports it as "failed to register" and carries on, so the only visible symptom is a feature
 * that silently does nothing.
 *
 * <p>This is stock Bukkit behaviour, not a convenience: a reference server gives each plugin its own loader
 * plus a group to look across, and {@code PluginClassLoaderGroup#getClassByName} is the hook. The previous
 * version of {@link PluginClassLoader} answered {@code null} from {@code getGroup()} and said so in its own
 * javadoc ("there is no group to look across") — correct as a description, but it left every cross-plugin
 * integration broken.
 *
 * <h2>Open, not dependency-aware</h2>
 *
 * <p>{@link #getAccess()} answers "yes" for every loader rather than consulting a dependency graph, matching
 * the {@code registerOpenGroup} shape Paper offers for loaders that do not track {@code depend} edges. The
 * alternative would need the declared dependencies of every plugin threaded through this bridge, and the
 * practical difference is small: both plugins have to be installed for the reference to resolve at all, and a
 * plugin resolving a class from one it did not declare gets the same failure either way when that plugin is
 * absent.
 *
 * <p>The plugin list is read LIVE through {@link #handles} rather than copied: loaders are created one at a
 * time as jars are examined, so a copy taken at construction would be missing every plugin loaded afterwards
 * — which, for Voice Chat being loaded last, is all of them.
 */
final class ForbricClassLoaderGroup implements PluginClassLoaderGroup {

    /**
     * The plugins loaded so far, read on every lookup.
     *
     * <p>A supplier rather than the list itself, because the bridge that owns the list is built after the
     * loaders that need it: {@link BridgeBootstrap} creates loaders while walking the plugins folder, and it
     * is the loader that must reach the bridge, not the other way round.
     */
    private final Supplier<java.util.List<PluginHandle>> handles;

    ForbricClassLoaderGroup(Supplier<java.util.List<PluginHandle>> handles) {
        this.handles = handles;
    }

    /**
     * Finds {@code name} among the other plugins' own jars.
     *
     * <p>Asks each loader for a class from ITS OWN jar ({@link PluginClassLoader#findClassHere(String)}), not
     * through {@code loadClass}: going through {@code loadClass} would re-enter this group for every plugin
     * in turn and, with two plugins that each reference the other, recurse until the stack runs out.
     *
     * <p>The requester is skipped, so a plugin that cannot find its own class gets "not found" rather than a
     * second attempt at the same jar.
     *
     * <p>Never throws: a loader that is closing, or a jar that vanished underneath it, must not turn a lookup
     * for somebody else's class into a failure for the plugin that asked.
     */
    @Override
    public Class<?> getClassByName(String name, boolean resolve, ConfiguredPluginClassLoader requester) {
        for (PluginHandle handle : handles.get()) {
            PluginClassLoader candidate = handle.classLoader();
            if (candidate == requester) {
                continue;
            }
            try {
                Class<?> found = candidate.findClassHere(name);
                if (found != null) {
                    if (resolve) {
                        // Resolved by the loader that DEFINED it, which is what the JVM would do for a class
                        // of that loader's own; resolving from here is not even possible, since resolveClass
                        // is protected on ClassLoader.
                        candidate.resolveHere(found);
                    }
                    return found;
                }
            } catch (Throwable notThisLoader) {
                // A loader that cannot supply the class, or cannot supply it right now. The next candidate may
                // still have it, so this is a "keep looking", not an error.
            }
        }
        return null;
    }

    /**
     * No-op: membership is the live plugin list, so nothing has to be tracked here.
     *
     * <p>Paper calls these to maintain a group it owns. This bridge's group wraps a list that
     * {@link BridgeBootstrap} already fills and empties, so recording the same fact twice would only create a
     * second place for it to go stale.
     */
    @Override
    public void add(ConfiguredPluginClassLoader loader) {
        // no-op — see above
    }

    /** No-op, for the reason {@link #add} gives. */
    @Override
    public void remove(ConfiguredPluginClassLoader loader) {
        // no-op — see above
    }

    /**
     * Everyone can see everyone, which is what makes this an open group.
     *
     * <p>Paper's own open group answers the same way. Returning a stricter answer would change what the
     * plugins loaded here can do compared to a reference server, and the difference would only show up as a
     * {@code NoClassDefFoundError} in some integration nobody thought to test.
     */
    @Override
    public ClassLoaderAccess getAccess() {
        return loader -> true;
    }

    /** Says so when a lookup found nothing, which is the line that explains a missing integration. */
    void reportMiss(String name, ConfiguredPluginClassLoader requester) {
        ForbricLog.debug("[Forbric/Bukkit] no other plugin supplies %s (asked by %s)", name, requester);
    }

    /**
     * The plugins a lookup would search, for diagnostics when one finds nothing.
     *
     * <p>Names the jars rather than the loaders: a loader's {@code toString} says the same thing, but the
     * question being answered is "was the plugin I expected even in this list", and a file name answers it
     * at a glance.
     */
    String describePeers() {
        java.util.List<PluginHandle> current = handles.get();
        if (current.isEmpty()) {
            return "[] (no plugins loaded yet)";
        }
        StringBuilder names = new StringBuilder("[");
        for (PluginHandle handle : current) {
            if (names.length() > 1) names.append(", ");
            names.append(handle.classLoader().jar().getFileName());
        }
        return names.append(']').toString();
    }
}
