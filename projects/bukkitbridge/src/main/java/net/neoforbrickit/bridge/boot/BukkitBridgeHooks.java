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
            handle = BukkitBridgeLauncher.start(pluginParentLoader(), gameDir, server);
            if (handle == null) {
                ForbricLog.debug("[NeoFobric/Bukkit] the bridge did not start; Bukkit plugins stay unloaded");
            }
        } catch (Throwable failure) {
            ForbricLog.error("[NeoFobric/Bukkit] starting the plugin bridge failed; Bukkit plugins stay unloaded",
                    failure);
        }
    }

    /**
     * The loader every plugin is parented to: the one that carries the patched game classes AND the
     * Bukkit API, so a plugin sees the same {@code net.minecraft.*} and {@code org.bukkit.*} the bridge
     * does (and, because it is the game layer, shares the registries, the server and every static the
     * game keeps there).
     *
     * <p><b>Resolved through FML on every call, never captured at class-init.</b> This is the lesson the
     * Fabric bridge learned the hard way (see {@code BridgedLauncher#targetClassLoader}): FML swaps
     * {@code FMLLoader.currentClassLoader} as it boots, and a value read once - even as a static
     * initialiser - can be a loader that was correct at the time and is not the game layer by the time
     * plugins load. Querying FML gives whatever is current, which by server-start is the game layer.
     *
     * <p>Falls back to this class's own loader when FML cannot be reached (a unit test, or the bridge
     * loaded standalone): that loader is where this class came from, so it is at worst as narrow as the
     * original choice and still sees {@code org.bukkit}.
     */
    private static ClassLoader pluginParentLoader() {
        ClassLoader apiLayer = BukkitBridgeHooks.class.getClassLoader();
        try {
            var fmlloader = Class.forName("net.neoforged.fml.loading.FMLLoader");
            var current = fmlloader.getMethod("getCurrent").invoke(null);
            var loader = current.getClass().getMethod("getCurrentClassLoader").invoke(current);
            if (loader instanceof ClassLoader gameLayer && gameLayer != apiLayer) {
                // Both layers, because a plugin needs classes from both and neither can see the other.
                // The API layer alone has no net.minecraft.*; the game layer alone has no io.papermc.*,
                // because this bridge now ships as a Jar-in-Jar and therefore loads on the "FML Plugins"
                // layer rather than the game layer. See TwoLayerClassLoader.
                //
                // Built reflectively because the class lives in the bridge proper, which is on apiLayer
                // and cannot be named from boot-side code.
                return newTwoLayerLoader(apiLayer, gameLayer);
            }
        } catch (Throwable unreachable) {
            // Not on FML (tests / standalone): fall through to the API layer.
        }
        return apiLayer;
    }

    /**
     * Builds the two-layer parent through the bridge's own class, by name.
     *
     * <p>Reflection, not ceremony: this file is boot-side and is loaded by the same layer as the API, so
     * it cannot name {@code net.neoforbrickit.bukkit.TwoLayerClassLoader} without that class being on the
     * boot classpath too. Its constructor is package-private for that reason — this call is the only
     * entrance, and its shape is pinned by the two-argument signature below.
     */
    private static ClassLoader newTwoLayerLoader(ClassLoader apiLayer, ClassLoader gameLayer) throws Exception {
        Class<?> loaderClass = Class.forName("net.neoforbrickit.bukkit.TwoLayerClassLoader", true, apiLayer);
        var constructor = loaderClass.getDeclaredConstructor(ClassLoader.class, ClassLoader.class);
        constructor.setAccessible(true);
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
