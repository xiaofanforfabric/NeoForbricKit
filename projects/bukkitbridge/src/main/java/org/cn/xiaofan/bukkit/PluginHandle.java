/*
 * Forbric Bukkit bridge — reading a plugin.yml and bringing one plugin up.
 */
package org.cn.xiaofan.bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Everything needed to bring one plugin up: read its {@code plugin.yml}, define it in its own loader, construct
 * it, link it to the server, and run {@code onLoad} then {@code onEnable}.
 *
 * <p>The order is not arbitrary. {@code JavaPlugin}'s constructor refuses to finish unless its class loader is a
 * {@link PluginClassLoader} — so the loader must exist, and be the one defining the class, before the plugin is
 * constructed. And {@code init(...)} must run before {@code onLoad}/{@code onEnable}, because every accessor a
 * plugin calls ({@code getDataFolder}, {@code getLogger}, {@code getName}) reads a field it fills.
 *
 * <p>Mirrors the sequence Paper's own provider runs, without the parts this bridge does not have (a provider
 * registry, a module layer, a plugin meta file format of its own).
 */
final class PluginHandle {

	/** The entry name Bukkit reads a plugin's description from. */
	private static final String DESCRIPTOR = "plugin.yml";

	private final Path jarPath;
	private final PluginDescriptionFile description;
	private final PluginClassLoader classLoader;
	private final JavaPlugin plugin;
	private final String source;

	private PluginHandle(Path jarPath, PluginDescriptionFile description, PluginClassLoader classLoader,
			JavaPlugin plugin, String source) {
		this.jarPath = jarPath;
		this.description = description;
		this.classLoader = classLoader;
		this.plugin = plugin;
		this.source = source;
	}

	/**
	 * Loads one plugin jar, up to but not including {@code onEnable}. The caller decides when to enable, because
	 * the order across plugins is the caller's to choose.
	 *
	 * @param jarPath   the plugin jar
	 * @param parent    the loader carrying the Bukkit API and the game (shared by every plugin)
	 * @param dataRoot  where per-plugin data folders live; one directory per plugin is created beneath it
	 * @param logger    the logger the plugin receives from {@code getLogger()}
	 * @param server    the {@code Server} the plugin receives from {@code getServer()}
	 */
	static PluginHandle load(Path jarPath, ClassLoader parent, Path dataRoot, Logger logger, Object server)
			throws IOException, InvalidDescriptionException, ReflectiveOperationException {
		PluginDescriptionFile description = readDescription(jarPath);

		PluginClassLoader loader = new PluginClassLoader(jarPath, parent, description);
		Class<?> mainClass = Class.forName(description.getMainClass(), true, loader);
		if (!JavaPlugin.class.isAssignableFrom(mainClass)) {
			throw new IllegalStateException(description.getMainClass() + " does not extend JavaPlugin");
		}

		// Constructing is where JavaPlugin's own guard runs: its constructor calls back into `loader.init(this)`,
		// so the instance and the loader are linked before a single plugin field is assigned.
		JavaPlugin plugin = (JavaPlugin) mainClass.getDeclaredConstructor().newInstance();

		Path dataFolder = dataRoot.resolve(description.getName());
		// The seven-argument init is the one that fills every accessor a plugin has; the older six-argument one
		// derives the same fields but takes a PluginLoader this bridge has no equivalent of.
		plugin.init((org.bukkit.Server) server, description, dataFolder.toFile(), jarPath.toFile(), loader,
				description, logger);

		return new PluginHandle(jarPath, description, loader, plugin, jarPath.getFileName().toString());
	}

	/**
	 * Runs {@code onLoad}, enables the plugin (which runs {@code onEnable}), then fires its lifecycle
	 * handlers.
	 *
	 * <p>The order is the whole point. A modern Paper plugin registers its commands from a
	 * {@code LifecycleEvents.COMMANDS} handler DURING {@code onEnable} -- registration outside that window
	 * is refused by the manager, as on a real server. So the handlers can only be fired after
	 * {@code setEnabled(true)} returns: a bridge that fired them first would find nothing registered, and
	 * one that never fired them would leave the plugin's commands silently absent.
	 *
	 * <p>{@code commands} is the only event fired, because it is the only one with a point in this
	 * bridge's lifecycle -- the server is already running when plugins load, so the startup events have
	 * passed. The rest stay registered and unrun, which {@link ForbricLifecycleEventManager} says out loud.
	 */
	void enable(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
			ServerImpl server) {
		plugin.onLoad();

		// plugin.yml commands must exist before onEnable: JavaPlugin.getCommand() is deliberately called
		// from onEnable by traditional Bukkit plugins, where the executor and tab completer are attached.
		// Installing only after onEnable creates a perfectly visible Brigadier node whose PluginCommand has
		// no executor, which looks like a command that accepts input and then does nothing.
		ForbricDeclaredCommands.installCommandMap(description, plugin, server);

		plugin.setEnabled(true);

		ForbricLifecycleEventManager manager = ForbricApiBridge.lifecycleManagerOf(plugin);
		if (manager != null) {
			manager.fire("commands", new io.papermc.paper.plugin.lifecycle.event.registrar
					.ReloadableRegistrarEvent<io.papermc.paper.command.brigadier.Commands>() {
				@Override
				public io.papermc.paper.command.brigadier.Commands registrar() {
					return new ForbricCommands(dispatcher, plugin.getPluginMeta(), server);
				}

				/**
				 * INITIAL, not RELOAD: these handlers run while the plugin is first enabling, which is what the
				 * enum distinguishes. A plugin that branches on it -- some register more commands on a reload --
				 * must see the same value it would on a real server startup.
				 */
				@Override
				public io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause cause() {
					return io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.INITIAL;
				}
			});
		}

		// After the lifecycle handlers, not before: a plugin that declares a command in BOTH places registers
		// its Brigadier tree above, and this must not shadow it. On a real server the plugin.yml declarations go
		// into the command map first and a Brigadier registration merges into the same label, so a tree wins for
		// the names it defines; adding the declaration only where nothing claimed the label reproduces that.
		//
		// This is also what makes getCommand(name) answer, which is the call a traditional plugin makes in its
		// own onEnable -- before this, it answered null and the plugin threw NPE naming itself.
		ForbricDeclaredCommands.install(dispatcher, description, plugin, server);
	}

	/** Disables the plugin, running {@code onDisable} when it was enabled. */
	void disable() {
		if (plugin.isEnabled()) plugin.setEnabled(false);
	}

	/** Reads {@code plugin.yml} through Bukkit's own parser, so the accepted syntax is Bukkit's, not ours. */
	private static PluginDescriptionFile readDescription(Path jarPath) throws IOException, InvalidDescriptionException {
		try (JarFile jar = new JarFile(jarPath.toFile())) {
			JarEntry entry = jar.getJarEntry(DESCRIPTOR);
			if (entry == null) {
				throw new InvalidDescriptionException(
						"[Forbric/Bukkit] " + jarPath.getFileName() + " has no " + DESCRIPTOR);
			}
			try (InputStream in = jar.getInputStream(entry)) {
				return new PluginDescriptionFile(in);
			}
		}
	}

	PluginDescriptionFile description() {
		return description;
	}

	JavaPlugin plugin() {
		return plugin;
	}

	PluginClassLoader classLoader() {
		return classLoader;
	}

	Path jarPath() {
		return jarPath;
	}

	String source() {
		return source;
	}
}
