/*
 * NeoFobric Bukkit bridge — the runtime seam NeoForge's server lifecycle calls into.
 *
 * This is the ADAPT half of the port: Forbric reached the bridge from its own kernel
 * (KernelGameServerLifecycle, on ServerStartedEvent). NeoForge has no kernel, so the same two
 * calls are exposed here as a tiny facade the server lifecycle invokes by fully-qualified name.
 * No Forbric kernel type is named: the class talks to BukkitBridgeLauncher, which is itself
 * boot-side and names no game type.
 */
package net.forbric.kernel.boot;

import java.nio.file.Path;

import net.forbric.kernel.util.ForbricLog;
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
     * the rule the whole bridge follows. The loader handed over is this class's own loader - the game
     * content layer, so a plugin class loader parented to it sees the same {@code org.bukkit} and
     * {@code net.minecraft} classes the bridge does.
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
            // The API jars are owned jars, so they had to be on the loader before it was built - which
            // happens at boot, via ownedJars below. If the boot step was skipped (the switch was off, or
            // the download failed) there is no API on the loader and start() declines by itself.
            handle = BukkitBridgeLauncher.start(BukkitBridgeHooks.class.getClassLoader(), gameDir, server);
            if (handle == null) {
                ForbricLog.debug("[NeoFobric/Bukkit] the bridge did not start; Bukkit plugins stay unloaded");
            }
        } catch (Throwable failure) {
            ForbricLog.error("[NeoFobric/Bukkit] starting the plugin bridge failed; Bukkit plugins stay unloaded",
                    failure);
        }
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
