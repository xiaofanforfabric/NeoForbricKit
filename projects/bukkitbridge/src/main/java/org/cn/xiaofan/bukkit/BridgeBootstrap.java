/*
 * Forbric Bukkit bridge — the entry point that finds and starts plugins.
 */
package org.cn.xiaofan.bukkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.plugin.InvalidDescriptionException;

/**
 * Finds the plugin jars in a directory, loads them, and enables them in order.
 *
 * <p>This is the bridge's whole job, and deliberately the smallest version of it that can be observed working:
 * discover, load, enable. There is no dependency sorting, no {@code PluginManager} registry, no reload. Each of
 * those is a real thing a server does, and each is added once something needs it rather than guessed at now.
 *
 * <h2>What "enabled" means here</h2>
 *
 * <p>For each jar: read its {@code plugin.yml}, define its main class in a {@link PluginClassLoader}, construct
 * it (which links it to its loader through {@code JavaPlugin}'s own guard), call {@code init(...)} to fill the
 * fields its accessors read, then {@code onLoad()} and {@code setEnabled(true)} — which is what runs
 * {@code onEnable()}.
 *
 * <p>A plugin that fails to load is reported and skipped; one bad jar must not stop the others, which is the
 * same rule every loader in this project follows.
 */
public final class BridgeBootstrap {

	private final Path pluginsFolder;
	private final Logger logger;
	private final ServerImpl server;
	private final ClassLoader hostLoader;
	private final List<PluginHandle> loaded = new ArrayList<>();

	private BridgeBootstrap(Path pluginsFolder, Logger logger, ServerImpl server, ClassLoader hostLoader) {
		this.pluginsFolder = pluginsFolder;
		this.logger = logger;
		this.server = server;
		this.hostLoader = hostLoader;
	}

	/**
	 * Loads every plugin in {@code pluginsFolder} and returns the bridge.
	 *
	 * <p>Called by {@link BukkitRuntime} once Forbric's server has a game directory. The return value keeps the
	 * handles alive, so a caller that drops it drops the plugins with it.
	 *
	 * @param loader the loader that defined the bridge — plugins take it as their parent, so a plugin sees the
	 *               same {@code org.bukkit} classes this bridge implements
	 */
	public static BridgeBootstrap start(Path pluginsFolder, Path dataFolder, ClassLoader loader, Object server)
			throws IOException {
		Logger logger = createLogger();
		// The running MinecraftServer, wrapped so every org.bukkit.World a plugin asks for reaches a real
		// ServerLevel. Typed Object at the seam and narrowed here, where the game types are linked anyway.
		net.minecraft.server.MinecraftServer minecraft =
				server instanceof net.minecraft.server.MinecraftServer running ? running : null;
		if (minecraft == null) {
			logger.warning("no running server was handed to the bridge; plugins that touch a world or a player "
					+ "will find none (this is a wiring fault, not a plugin fault)");
		}
		// No PluginManager yet: paper-api's SimplePluginManager is final and its constructor needs a Server, so
		// building one here would be a cycle. ServerImpl answers getPluginManager() with null, which is honest —
		// nothing has registered a listener with it. The first plugin that needs it will be the reason to build it.
		ServerImpl serverImpl = new ServerImpl(logger, null, pluginsFolder, "26.2-forbric-bridge", minecraft);
                // Register the server with the API's own static holder BEFORE any plugin loads. Every
                // `Bukkit.getXxx()` delegates to this one field, and until it is set they all throw a
                // NullPointerException naming the field rather than the missing registration — which reads as a
                // bug in the plugin. Real plugins call it in the first lines of onEnable: Simple Voice Chat
                // starts with `Bukkit.getBukkitVersion()`.
                //
                // CraftBukkit does this from its CraftServer constructor. There is no constructor of ours to
                // hang it on, because ServerImpl is a plain class rather than a subclass the API knows about,
                // so the registration is explicit here — before loadAll, and once.
                // The plugin manager next, and before the server is published: both are needed by the first line
		// of any plugin that registers a listener or disables itself, and a plugin that reaches a null
		// manager crashes while REPORTING its own failure, which hides the real reason.
		org.bukkit.Bukkit.setServer(serverImpl);
		// The game's registries, for Paper's RegistryAccess service. That is a SEPARATE
		// ServiceLoader lookup from the one above, and it has no server to read them from:
		// Paper's provider lives in the server jar and reads its own game state, while this one is
		// instantiated by the loader with no arguments. So the registries are handed over here,
		// where both the game and the API are linked.
		//
		// Without it, `Material.asItemType()` finds no ItemType, `ItemStack.of(...)` throws, and
		// every plugin that builds an item fails — at a point that looks like a broken API rather
		// than a missing service.
		if (minecraft != null) {
			ForbricRegistries.install(minecraft.registryAccess());
			logger.info("installed the game's registries for the Bukkit item registry ("
					+ minecraft.registryAccess()
							.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM).size()
					+ " items)");
		}
		// Say out loud which Bukkit was registered, and from which loader. `Bukkit` keeps the server
		// in a STATIC field, so the registration is visible only to code that resolved the SAME class
		// object. A plugin whose loader defined its own copy still sees null, and the symptom — a
		// NullPointerException naming the field — is identical to never having registered at all.
		logger.info("registered the server with org.bukkit.Bukkit; class="
				+ org.bukkit.Bukkit.class.getName()
				+ " id=" + System.identityHashCode(org.bukkit.Bukkit.class)
				+ " loader=" + org.bukkit.Bukkit.class.getClassLoader()
				+ " getServer=" + org.bukkit.Bukkit.getServer());
		BridgeBootstrap bridge = new BridgeBootstrap(pluginsFolder, logger, serverImpl, loader);
		// After the bridge exists, because the manager is handed the live list it fills while loading.
		// Before loadAll, because a plugin's first line may ask for the manager — and a plugin
		// that fails in onEnable calls back into it to disable itself.
		serverImpl.installPluginManager(bridge.loadedLive());
		// The command-argument factory next, for the same reason: a plugin asks for an argument type while
		// registering its commands, inside the COMMANDS handler, and a null factory there throws an
		// IllegalStateException from a provider that cannot be given a server any other way.
		ForbricArgumentTypes.install(serverImpl);

		logger.info("Forbrickit Bukkit bridge starting; plugins folder: " + pluginsFolder);
		bridge.loadAll(dataFolder);
		return bridge;
	}

	/** A logger that writes to the console at INFO, so a plugin's own logging is visible while this is built. */
	private static Logger createLogger() {
		Logger logger = Logger.getLogger("Forbric.Bukkit");
		logger.setUseParentHandlers(false);
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(Level.ALL);
		logger.addHandler(handler);
		logger.setLevel(Level.ALL);
		return logger;
	}

	private void loadAll(Path dataFolder) throws IOException {
		// Create it when absent, rather than warn and return. A server that says nothing and loads nothing is
		// indistinguishable from a broken one: the user drops a jar next to the server, sees no new folder, sees
		// no plugin load, and concludes the loader lied. Making the folder answers the question before it is
		// asked — and the first launch of a fresh server is exactly when it gets asked.
		if (!Files.isDirectory(pluginsFolder)) {
			Files.createDirectories(pluginsFolder);
			logger.info("created the plugins folder: " + pluginsFolder);
		}

		List<Path> jars;
		try (Stream<Path> entries = Files.list(pluginsFolder)) {
			jars = entries
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".jar"))
					.sorted()
					.toList();
		}

		if (jars.isEmpty()) {
			// Said plainly and with the path, because this is the message a user reads when their plugin is in
			// the wrong place — the MODS folder, a subfolder, a .zip, or a sibling directory named "plugin".
			long others;
			try (Stream<Path> entries = Files.list(pluginsFolder)) {
				others = entries.filter(path -> !path.getFileName().toString().endsWith(".jar")).count();
			}
			logger.info("no plugin jars found. Put Bukkit/Paper plugin .jar files directly in: " + pluginsFolder
					+ (others > 0 ? " (" + others + " other entr" + (others == 1 ? "y is" : "ies are")
							+ " there and were not .jar files)" : ""));
			return;
		}

		for (Path jar : jars) {
			try {
				PluginHandle handle = PluginHandle.load(jar, hostLoader, dataFolder, logger, server);
				loaded.add(handle);
				server.enablePlugin(handle);
				logger.info("enabled " + handle.description().getFullName() + " from " + jar.getFileName());
			} catch (IOException | InvalidDescriptionException | ReflectiveOperationException
					| RuntimeException failure) {
				// One jar's problem stays that jar's. The next one may be the one the user cares about.
				logger.log(Level.SEVERE, "could not load " + jar.getFileName() + ": " + failure, failure);
			}		}

		logger.info("bridge ready: " + loaded.size() + " plugin(s) enabled");
	}

	/** Disables every plugin, in reverse enable order. */
	public void stop() {
		for (int i = loaded.size() - 1; i >= 0; i--) {
			PluginHandle handle = loaded.get(i);
			try {
				handle.disable();
			} catch (RuntimeException failure) {
				logger.log(Level.WARNING, "could not disable " + handle.description().getName(), failure);
			}
		}
		loaded.clear();
                // Unregister last, and only after every plugin has had its onDisable: a plugin's shutdown code
                // still calls Bukkit.getXxx(), and clearing the field first would turn a clean disable into the
                // same NullPointerException this bridge exists to avoid. Never reset to null, either — a stale
                // bridge is friendlier to a plugin that runs late than an absent one.
                org.bukkit.Bukkit.setServer(server);
	}

	/** The plugins this bridge enabled, in order, as a snapshot. */
	public List<PluginHandle> loaded() {
		return List.copyOf(loaded);
	}

	/**
	 * The LIVE list of loaded plugins.
	 *
	 * <p>Handed to the plugin manager rather than a copy, and that is the point: the manager is
	 * built before any plugin loads (so a plugin's first line can ask for it) but has to answer
	 * {@code getPlugins()} afterwards. A copy would freeze the list at empty and every lookup would
	 * come back missing a plugin the server is plainly running.
	 */
	List<PluginHandle> loadedLive() {
		return loaded;
	}

	/** The bridge's own logger, for a caller that wants to report through the same channel. */
	public Logger logger() {
		return logger;
	}

	/**
	 * The server the bridge installed, so a caller that starts the game-event forwards can reach
	 * the plugin manager and wrap players against the same server the plugins already see.
	 *
	 * <p>Package-visible rather than public: the only caller is {@link BukkitRuntime}, which is in
	 * this package, and a public accessor would invite a plugin to reach behind Bukkit to its
	 * implementation.
	 */
	ServerImpl server() {
		return server;
	}
}
