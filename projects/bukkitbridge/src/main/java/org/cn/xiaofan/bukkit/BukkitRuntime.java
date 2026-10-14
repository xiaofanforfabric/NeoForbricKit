/*
 * Forbric Bukkit bridge — the entry point the kernel calls into.
 *
 * Compiled into the kernel's game-side jar (forbric-kernel-runtime.jar), which ForbricClassLoader defines from.
 * Paper's API is the half that is NOT shipped: PaperApiFetcher downloads it into the game directory and
 * BukkitBridgeLauncher adds it to the same loader.
 */
package org.cn.xiaofan.bukkit;

import java.nio.file.Path;

/**
 * The bridge as the kernel sees it: one class, one static method to start, one instance method to stop.
 *
 * <p>The kernel reaches this by string and reflection ({@code BukkitBridgeLauncher}), because the kernel's boot
 * half names no game or Bukkit type. That is the same seam the kernel uses for its own game side.
 *
 * <p>The signatures here are a contract: {@code start(Path, Path, ClassLoader, Object)} and {@code stop()}.
 * Changing them means changing {@code BukkitBridgeLauncher} in the same breath.
 */
public final class BukkitRuntime {

	private final BridgeBootstrap bridge;

	private BukkitRuntime(BridgeBootstrap bridge) {
		this.bridge = bridge;
	}

	/**
	 * Loads and enables every plugin in {@code pluginsFolder}.
	 *
	 * @param pluginsFolder where the plugin jars are
	 * @param dataFolder    the directory per-plugin data folders are created under
	 * @param loader        the loader that defined this class; passed through so plugin class loaders have the
	 *                      game's classes as their parent rather than whatever defined the bridge
	 * @param server        the running {@code net.minecraft.server.MinecraftServer}, the thing every {@code World}
	 *                      and {@code Player} the plugins see is ultimately reached through. Typed as
	 *                      {@code Object} on purpose: the caller is the boot-side {@code BukkitBridgeLauncher},
	 *                      which links no game type, and a parameter typed {@code MinecraftServer} would drag the
	 *                      whole game onto its signature.
	 * @return the running bridge, whose {@link #stop()} the kernel calls on shutdown
	 */
	public static BukkitRuntime start(Path pluginsFolder, Path dataFolder, ClassLoader loader, Object server)
			throws Exception {
		BridgeBootstrap bridge = BridgeBootstrap.start(pluginsFolder, dataFolder, loader, server);
		// Armed LAST, once the plugins are loaded and the server the events are raised against exists.
		//
		// The seam itself needed no call here: the kernel's transformer (BukkitInteractInjector) put the call
		// into ServerPlayerGameMode's and ServerGamePacketListenerImpl's own methods, so what is left for this
		// side is the half that names org.bukkit. Nothing subscribes to a loader's event bus, which is the
		// point: a plugin's @EventHandler now depends on net.minecraft.* only.
		ForbricInteractHook.arm(new ForbricInteractDispatch(bridge.server()));
		return new BukkitRuntime(bridge);
	}

	/** Disables every plugin, in reverse enable order, and stops raising events while it does. */
	public void stop() {
		// Disarmed first: a plugin's onDisable runs inside bridge.stop(), and an interaction raised after the
		// manager has begun tearing down would report a failure that is this side's, not the plugin's.
		ForbricInteractHook.disarm();
		bridge.stop();
	}
}
