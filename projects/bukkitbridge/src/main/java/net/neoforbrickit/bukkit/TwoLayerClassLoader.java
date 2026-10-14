/*
 * Forbric Bukkit bridge — the parent loader plugins are given.
 */
package net.neoforbrickit.bukkit;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * A parent loader for plugins that searches TWO layers, because a plugin needs classes from both.
 *
 * <h2>Why one loader is not enough</h2>
 *
 * <p>Under FML the bridge's own classes and the Paper API live in different places than the game does:
 *
 * <ul>
 *   <li>{@code org.bukkit.*} and {@code io.papermc.paper.*} come from paper-api, which ships as a
 *       Jar-in-Jar and is therefore loaded by FML's "FML Plugins" layer — a plain {@code URLClassLoader}
 *       that cannot see {@code net.minecraft.*};</li>
 *   <li>{@code net.minecraft.*} comes from the game layer — a {@code TransformingClassLoader} that cannot
 *       see the Jar-in-Jar jars.</li>
 * </ul>
 *
 * <p>The two are siblings, not parent and child, so neither reaches the other. A plugin handed either one
 * alone dies at its first reference to the other half: parented to the game layer, a plugin that touches
 * {@code LifecycleEventType} throws ClassNotFoundException out of {@code URLClassLoader.findClass}; parented
 * to the API layer, it cannot see {@code ServerLevel} or anything else it was written against.
 *
 * <p>This loader is that missing link. It is the parent the plugin's own {@code PluginClassLoader} is built
 * with, so every delegation the plugin makes lands here first.
 *
 * <h2>Search order</h2>
 *
 * <p>API first, game second, and the order matters: both layers can define a class named
 * {@code org.bukkit...} in principle, and the API layer's copy is the one the bridge itself links against —
 * taking the game layer's would give the plugin a different {@code JavaPlugin} than its loader expects and
 * its own constructor guard would reject it.
 */
public final class TwoLayerClassLoader extends ClassLoader {

    private final ClassLoader apiLayer;
    private final ClassLoader gameLayer;

    /**
     * Public because the boot-side facade ({@code BukkitBridgeHooks}, which shares the API layer with this
     * class but is compiled before it exists on any classpath) reaches this constructor by reflection.
     * Not API for plugins: nothing in a plugin jar has business building one of these.
     */
    public TwoLayerClassLoader(ClassLoader apiLayer, ClassLoader gameLayer) {
        // Parented to the API layer so that JDK classes and anything the API layer delegates upward are
        // still found the ordinary way; this class only has to add what the parent chain cannot reach.
        super(apiLayer);
        this.apiLayer = apiLayer;
        this.gameLayer = gameLayer;
    }

    /**
     * {@inheritDoc}
     *
     * <p>API layer first, game layer second. Neither is asked with {@code resolve} — the caller decides
     * when to link, and passing it through to a {@code ModuleClassLoader} that ignores it would hide a
     * failure to link rather than surface one.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return apiLayer.loadClass(name);
        } catch (ClassNotFoundException notInApi) {
            // Well-known: io.papermc / org.bukkit / kyori are API-only, net.minecraft / com.mojang are
            // game-only, so this branch is taken exactly once per name and only for the game half.
            return gameLayer.loadClass(name);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resources are merged rather than short-circuited — the API layer first, then the game layer — so
     * that a plugin calling {@code getResources("META-INF/services/...")} sees both. Returning only the
     * first layer's answer is what makes a service lookup silently miss half its providers.
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        var fromApi = apiLayer.getResources(name);
        var fromGame = gameLayer.getResources(name);
        return new Enumeration<>() {
            @Override
            public boolean hasMoreElements() {
                return fromApi.hasMoreElements() || fromGame.hasMoreElements();
            }

            @Override
            public URL nextElement() {
                return fromApi.hasMoreElements() ? fromApi.nextElement() : fromGame.nextElement();
            }
        };
    }

    @Override
    public URL getResource(String name) {
        URL fromApi = apiLayer.getResource(name);
        return fromApi != null ? fromApi : gameLayer.getResource(name);
    }
}
