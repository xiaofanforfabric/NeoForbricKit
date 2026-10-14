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
     * Public because the boot-side facade ({@code BukkitBridgeHooks}) reaches this constructor by reflection,
     * and that facade exists precisely so it cannot name this class. Not API for plugins: nothing in a plugin
     * jar has business building one of these.
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
     *
     * <p><b>The first failure is caught as {@link Throwable}, not as {@code ClassNotFoundException}.</b> A
     * layer can fail to supply a class with a {@code NoClassDefFoundError} — an FML {@code ModuleClassLoader}
     * that owns a package without owning every class in it does exactly that — and a fallback that caught only
     * the checked exception would refuse to ask the other layer for a class the other layer has. When both
     * layers fail, both failures are reported: "neither layer has it" and "the first layer broke in a way that
     * hid the second layer's answer" are different diagnoses and the log must not merge them.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Throwable apiFailure = null;
        try {
            return apiLayer.loadClass(name);
        } catch (Throwable notInApi) {
            apiFailure = notInApi;
        }
        try {
            return gameLayer.loadClass(name);
        } catch (Throwable notInGame) {
            if (apiFailure != null) notInGame.addSuppressed(apiFailure);
            if (notInGame instanceof ClassNotFoundException notFound) throw notFound;
            if (notInGame instanceof RuntimeException runtime) throw runtime;
            if (notInGame instanceof Error error) throw error;
            throw new ClassNotFoundException(name, notInGame);
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

    /**
     * Names both layers, so a log line that prints a plugin's parent says which loaders are behind it instead
     * of {@code TwoLayerClassLoader@1a2b3c}. Which layers those are is the whole question when a plugin cannot
     * link a class, and the default {@code toString} answers none of it.
     */
    @Override
    public String toString() {
        return "TwoLayerClassLoader[api=" + apiLayer + ", game=" + gameLayer + "]";
    }
}
