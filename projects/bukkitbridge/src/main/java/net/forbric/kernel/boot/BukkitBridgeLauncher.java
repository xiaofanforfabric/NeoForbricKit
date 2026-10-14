/*
 * Copyright 2026 The Forbric Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.forbric.kernel.boot;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.forbric.kernel.util.ForbricLog;

/**
 * Brings up the Bukkit/Paper plugin bridge.
 *
 * <p>The bridge's own classes ({@code org.cn.xiaofan.bukkit.*}) are compiled INTO this kernel: they live in the
 * {@code runtime} source set, ride inside this subproject's jar, and are defined by the game layer like every
 * other game-side class. So there is no bridge
 * jar to locate, deploy or version-match — a kernel that boots at all has its bridge.
 *
 * <p>This class is nonetheless the BOOT side of the seam, and names no {@code org.bukkit} or {@code net.minecraft}
 * type: it reaches the bridge by string and reflection the way {@code KernelRuntimeClasses} reaches the kernel's own
 * game half. That is not ceremony — the boot jar is parent-loaded and must stay loadable with no game on the
 * classpath, and a direct reference to {@code org.bukkit.*} here would break exactly that.
 *
 * <h2>Why the API jar is downloaded rather than shipped</h2>
 *
 * <p>The bridge needs Paper's API ({@code org.bukkit.*}) and the adventure component library its signatures name.
 * Both carry no Minecraft code, so they could legally be bundled — but bundling them would add several megabytes
 * to every Forbric download, re-upload someone else's artifact, and (for some of them) change this distribution's
 * licence surface for every user who never loads a plugin. Instead they are fetched once into
 * {@code <gameDir>/.neofobric/bukkit-libs/} and reused, which is also how the installer treats the game's own
 * artifacts. This is the same rule the transfer component follows for Fabric API and Team Reborn Energy: compiled
 * against, never shipped.
 *
 * <p>The libraries are added to the loader as owned jars, which is also where the bridge already is. They are
 * game-owned (see {@code DelegationPolicy}) because Paper's API resolves its implementations through
 * {@link java.util.ServiceLoader}, which searches the interface's own class loader — and those implementations
 * call into {@code net.minecraft.*}.
 */
public final class BukkitBridgeLauncher {

/** {@code -Dneofobric.bukkitBridge=off} keeps the bridge out of a launch entirely. */
    public static final String SWITCH = "neofobric.bukkitBridge";

	/** The class {@link #start} invokes, in the bridge. Carried inside the game-side kernel jar, not a separate one. */
	private static final String ENTRY = "org.cn.xiaofan.bukkit.BukkitRuntime";

	/**
	 * The game directory, recorded at boot.
	 *
	 * <p>Set by {@link KernelBoot} from the {@code --gameDir} argument rather than read back from
	 * {@code user.dir}: a launcher may start the process anywhere, and the two differ as soon as it does.
	 */
	private static volatile Path gameDirectory;

	private BukkitBridgeLauncher() {
	}

	/** Records where the game directory is, so the later lifecycle call can find the bridge. */
	public static void setGameDirectory(Path gameDir) {
		gameDirectory = gameDir;
	}

	/** The recorded game directory, or the process working directory before boot has recorded one. */
	public static Path gameDirectory() {
		Path recorded = gameDirectory;
		return recorded != null ? recorded : Path.of(System.getProperty("user.dir", "."));
	}

	/** True unless the bridge was switched off. Present in every kernel that builds the game side. */
	public static boolean enabled() {
		return enabled(gameDirectory());
	}

	/**
	 * True unless the bridge was switched off.
	 *
	 * <p>{@code gameDir} is no longer consulted for presence: the bridge ships inside the kernel, so a boot that
	 * reached here has it. The parameter is kept because the switch and the bridge's own working folders are still
	 * per-game-directory, and because every caller already has one.
	 */
	public static boolean enabled(Path gameDir) {
		return !"off".equalsIgnoreCase(System.getProperty(SWITCH, "on"));
	}

	/**
	 * The API jars the bridge needs, fetching them into {@code gameDir} on first use.
	 *
	 * <p>Called before the class loader is built, because these are owned jars. On a machine with no network and
	 * no cache this throws; the caller decides whether that is fatal (it is not — the bridge is optional, and a
	 * game without it is the normal game).
	 */
	public static List<Path> apiJars(Path gameDir) throws IOException {
		return PaperApiFetcher.fetch(gameDir);
	}

	/**
	 * Starts the bridge against {@code gameDir}, loading plugins from the {@code plugins} folder beside it.
	 *
	 * @param loader   the game-side loader, which must already own the API jars and the bridge jar
	 * @param gameDir  the game directory; plugins live in {@code <gameDir>/plugins} and the API cache in
	 *                 {@code <gameDir>/.neofobric/}
	 * @return an opaque handle to keep alive for the life of the server, or {@code null} if the bridge did not start
	 */
	public static Object start(ClassLoader loader, Path gameDir, Object server) {
		try {
			Class<?> runtime = Class.forName(ENTRY, true, loader);
			// Four arguments now: the bridge wraps the running server, and a bridge that cannot see one
			// can answer no world and no player. Passed as Object because this is boot-side code and must
			// not name a game type; the bridge narrows it and refuses by name if handed something else.
			Method start = runtime.getMethod("start", Path.class, Path.class, ClassLoader.class, Object.class);
			Object handle = start.invoke(null, gameDir.resolve("plugins"), gameDir, loader, server);
			ForbricLog.info("[NeoFobric/Bukkit] plugin bridge started (plugins: %s)", gameDir.resolve("plugins"));
			return handle;
		} catch (ClassNotFoundException absent) {
			ForbricLog.debug("[NeoFobric/Bukkit] bridge entry %s not on the loader; no Bukkit plugins will load",
					ENTRY);
			return null;
		} catch (Throwable failure) {
			// Never fatal: a bridge that cannot start costs the Bukkit plugins and nothing else. The throwable is
			// unpacked so a plugin's own NoClassDefFoundError is not hidden behind an InvocationTargetException.
			Throwable cause = failure instanceof java.lang.reflect.InvocationTargetException wrapped
					? wrapped.getCause() : failure;
			ForbricLog.error("[NeoFobric/Bukkit] the plugin bridge could not start; Bukkit plugins stay unloaded", cause);
			return null;
		}
	}

	/** Stops the bridge, running every plugin's {@code onDisable}. */
	public static void stop(Object handle) {
		if (handle == null) return;
		try {
			Method stop = handle.getClass().getMethod("stop");
			stop.invoke(handle);
		} catch (Throwable failure) {
			ForbricLog.warn("[NeoFobric/Bukkit] the plugin bridge did not stop cleanly: %s", String.valueOf(failure));
		}
	}

	/**
	 * The API jars the bridge needs, in load order. The bridge itself is not here — it is already in the game-side
	 * kernel jar, which is the loader this list is added to.
	 *
	 * <p>Called before the class loader is built, because these are owned jars. On a machine with no network and
	 * no cache this throws; the caller decides whether that is fatal. It is not fatal for the SERVER — a game with
	 * no Bukkit plugins is the normal game — but it does cost the bridge, because the API is what its
	 * {@code ServiceLoader} descriptors resolve through.
	 */
	public static List<URL> ownedJars(Path gameDir) {
		List<URL> urls = new ArrayList<>();
		if (!enabled(gameDir)) return urls;
		try {
			for (Path api : apiJars(gameDir)) urls.add(api.toUri().toURL());
		} catch (IOException missing) {
			ForbricLog.warn("[NeoFobric/Bukkit] could not prepare the bridge's API jars — no Bukkit plugins will "
					+ "load this launch: %s", String.valueOf(missing));
			urls.clear();
		}
		return urls;
	}
}
