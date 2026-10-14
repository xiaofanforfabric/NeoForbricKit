/*
 * NeoFobric Bukkit bridge — the runtime seam NeoForge's server lifecycle calls into.
 *
 * This is the ADAPT half of the port: Forbric reached the bridge from its own kernel
 * (KernelGameServerLifecycle, on ServerStartedEvent). NeoForge has no kernel, so the same two
 * calls are exposed here as a tiny facade the server lifecycle invokes by fully-qualified name.
 * No Forbric kernel type is named: the class talks to BukkitBridgeLauncher, which is itself
 * boot-side and names no game type.
 */
package net.neoforbrickit.bridge.boot;

import java.nio.file.Path;

import net.neoforbrickit.bridge.util.ForbricLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The bridge's server-lifecycle entry points, called from NeoForge's {@code ServerLifecycleHooks}.
 *
 * <p>{@link #startBukkitBridge(Object)} is invoked once the server has started, with the running
 * {@code MinecraftServer}; {@link #stopBukkitBridge()} on shutdown. Both are by fully-qualified name
 * from NeoForge's side, so neither has to be on that project's compile classpath - only on the
 * runtime library path (the {@code libraries(...)} entry in projects/neoforge/build.gradle).
 *
 * <p><b>Where the game directory comes from.</b> {@link BukkitBridgeLauncher#setGameDirectory(Path)}
 * is called earlier, by {@code CommonModLoader} (see {@code NeoFobric: hand the Bukkit bridge the game
 * directory}) straight after FML has resolved the paths - so by the time the server starts, the launcher
 * already knows where the {@code plugins} folder and the API cache live.
 */
public final class BukkitBridgeHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger(BukkitBridgeHooks.class);

    /** The live bridge, or null. Statics because the seam is static: there is one server per process. */
    private static volatile Object handle;

    private BukkitBridgeHooks() {}

    /** {@return whether the bridge is currently running} */
    public static boolean isRunning() {
        return handle != null;
    }

    /**
     * Brings the bridge up against a running server, loading plugins from {@code <gameDir>/plugins}.
     *
     * <p>Never throws: a bridge that cannot start costs the Bukkit plugins and nothing else, which is
     * the rule the whole bridge follows.
     *
     * @param server the running {@code MinecraftServer}, typed {@code Object} so this facade is the
     *               only place the type is named (the launcher below must not).
     */
    public static void startBukkitBridge(Object server) {
        if (isRunning()) {
            return;
        }
        try {
            Path gameDir = BukkitBridgeLauncher.gameDirectory();
            // Two loaders, kept apart on purpose. The bridge's OWN classes stay on the layer that carries the
            // game: its statics are reachable from the call sites the transformer injects into the game's
            // classes, and those resolve through THAT loader — a second copy of the bridge would split them.
            // Only the PLUGINS get the layer pair: the API they were compiled against, plus the game they run
            // on, because under FML neither layer can see the other.
            ClassLoader bridgeLayer = BukkitBridgeHooks.class.getClassLoader();
            ClassLoader pluginParent = pluginParentLoader(bridgeLayer);
            handle = BukkitBridgeLauncher.start(bridgeLayer, pluginParent, gameDir, server);
            if (handle == null) {
                ForbricLog.debug("[NeoFobric/Bukkit] the bridge did not start; Bukkit plugins stay unloaded");
            }
        } catch (Throwable failure) {
            ForbricLog.error("[NeoFobric/Bukkit] starting the plugin bridge failed; Bukkit plugins stay unloaded",
                    failure);
        }
    }

    /**
     * The loader every plugin is parented to: whichever pair of layers between them carries paper-api and the
     * patched game.
     *
     * <p><b>Which layer is which is asked, not assumed.</b> The measurement is
     * {@code io.papermc.paper.ServerBuildInfo}'s own class loader, and it is NOT this class's loader: the run
     * this replaces printed {@code ServerBuildInfo defined by java.net.URLClassLoader@66eb985d} next to
     * {@code bridge defined by ...TransformingClassLoader@6ce90bc5}. Assuming the two were the same made
     * {@code apiLayer == gameLayer}, skipped the two-layer parent below, and handed the plugins the game layer
     * alone — which is exactly the loader that cannot supply
     * {@code io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType}, the NoClassDefFoundError that
     * left every plugin unloaded.
     *
     * <p><b>Resolved through FML on every call, never captured at class-init.</b> FML swaps
     * {@code FMLLoader.currentClassLoader} as it boots, and a value read once can be a loader that was correct
     * at the time and is not the game layer by the time plugins load.
     *
     * @param bridgeLayer the loader that defined this class, and the one the bridge's own classes execute from
     * @return the plugins' parent: a two-layer loader when the two halves live in different layers, the game
     *         layer alone when one layer carries both, and never a silent downgrade to one layer when the pair
     *         cannot be built
     */
    private static ClassLoader pluginParentLoader(ClassLoader bridgeLayer) {
        ClassLoader gameLayer = fmlGameLayer();
        ClassLoader apiLayer = apiLayerLoader(gameLayer != null ? gameLayer : bridgeLayer);

        // Unconditional, and the three loaders are the whole diagnosis of this seam: which layer has the API,
        // which has the game, and which defined the bridge are exactly the facts a class-loading failure is
        // read against — and none of them is visible from a stack trace.
        ForbricLog.info("[NeoFobric/Bukkit] plugin parent: bridge=%s api=%s game=%s",
                bridgeLayer, apiLayer, gameLayer);

        if (gameLayer == null) {
            // Not on FML (a unit test, or the bridge loaded standalone): there is only one layer, and it is
            // whichever one defined the API classes.
            return apiLayer != null ? apiLayer : bridgeLayer;
        }
        if (apiLayer == null || apiLayer == gameLayer) {
            ForbricLog.info("[NeoFobric/Bukkit] one layer carries both halves; plugins are parented to it alone");
            return gameLayer;
        }
        try {
            // Both layers, because neither reaches the other: the API layer has no net.minecraft.*, and the
            // game layer has no io.papermc.* — paper-api ships as a Jar-in-Jar and loads on FML's
            // "FML Plugins" layer. Built reflectively because the class lives in the bridge proper, which
            // boot-side code cannot name.
            return newTwoLayerLoader(apiLayer, gameLayer, bridgeLayer);
        } catch (Throwable failed) {
            // Never silent. Falling back to one layer reproduces the NoClassDefFoundError the pair exists to
            // prevent, and swallowing the reason here made that indistinguishable from "the pair was never
            // needed" — which is how this bug stayed hidden behind a working-looking startup log.
            ForbricLog.warn("[NeoFobric/Bukkit] could not build the two-layer plugin parent (%s); plugins get %s "
                    + "alone and any plugin naming io.papermc.* will fail", String.valueOf(failed), gameLayer);
            return gameLayer;
        }
    }

    /**
     * The loader that actually DEFINES paper-api's classes, asked of an API class rather than assumed from this
     * class's own loader — see {@link #pluginParentLoader(ClassLoader)} for why those two differ.
     *
     * <p>Several probes, because which API class is reachable first is not guaranteed; any one of them answers
     * the only question asked here, which is which loader owns the API.
     */
    private static ClassLoader apiLayerLoader(ClassLoader lookup) {
        for (String probe : new String[] {
                "io.papermc.paper.ServerBuildInfo",
                "org.bukkit.Bukkit",
                "net.kyori.adventure.text.Component" }) {
            try {
                ClassLoader owner = Class.forName(probe, false, lookup).getClassLoader();
                if (owner != null) return owner;
            } catch (Throwable notHere) {
                // Try the next probe: a bridge with none of them has no API to reach at all.
            }
        }
        return null;
    }

    /** FML's current loader — the game layer by server-start — or null when this is not running under FML. */
    private static ClassLoader fmlGameLayer() {
        try {
            var fmlloader = Class.forName("net.neoforged.fml.loading.FMLLoader");
            var current = fmlloader.getMethod("getCurrent").invoke(null);
            var loader = current.getClass().getMethod("getCurrentClassLoader").invoke(current);
            return loader instanceof ClassLoader gameLayer ? gameLayer : null;
        } catch (Throwable unreachable) {
            return null;
        }
    }

    /**
     * Builds the two-layer parent through the bridge's own class, by name.
     *
     * <p>Reflection, not ceremony: this file is boot-side and cannot name
     * {@code net.neoforbrickit.bukkit.TwoLayerClassLoader} without that class being on the boot classpath too.
     *
     * <p>The lookup is tried on more than one layer, because one package's classes are not guaranteed to
     * resolve from a single one of them: the run this replaces had {@code net.neoforbrickit.bukkit.*} answered
     * by the API layer while {@code net.neoforbrickit.bridge.boot.*} came from the game layer. Picking a layer
     * and hoping is what made the pair silently unavailable; trying each, and failing loudly when none has it,
     * cannot end that way.
     */
    private static ClassLoader newTwoLayerLoader(ClassLoader apiLayer, ClassLoader gameLayer, ClassLoader bridgeLayer)
            throws Exception {
        String name = "net.neoforbrickit.bukkit.TwoLayerClassLoader";
        Class<?> loaderClass = null;
        Throwable lastFailure = null;
        for (ClassLoader candidate : new ClassLoader[] { apiLayer, bridgeLayer, gameLayer }) {
            if (candidate == null) continue;
            try {
                loaderClass = Class.forName(name, true, candidate);
                break;
            } catch (Throwable notHere) {
                lastFailure = notHere;
            }
        }
        if (loaderClass == null) {
            throw new ClassNotFoundException(name, lastFailure);
        }
        var constructor = loaderClass.getDeclaredConstructor(ClassLoader.class, ClassLoader.class);
        try {
            constructor.setAccessible(true);
        } catch (Throwable notPermitted) {
            // A public constructor in an open package needs no flag; a layer that refuses the flag would
            // otherwise make this look like a missing class.
        }
        return (ClassLoader) constructor.newInstance(apiLayer, gameLayer);
    }

    /** Stops the bridge, running every plugin's {@code onDisable}. Safe to call when it never started. */
    public static void stopBukkitBridge() {
        Object running = handle;
        handle = null;
        if (running == null) {
            return;
        }
        BukkitBridgeLauncher.stop(running);
    }
}
