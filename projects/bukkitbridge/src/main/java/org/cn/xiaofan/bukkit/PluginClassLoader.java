/*
 * Forbric Bukkit bridge — the class loader a plugin is defined by.
 */
package org.cn.xiaofan.bukkit;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader;
import io.papermc.paper.plugin.provider.classloader.PluginClassLoaderGroup;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The class loader one plugin's classes come from.
 *
 * <p>Paper requires this shape for a structural reason rather than a stylistic one. {@code JavaPlugin}'s
 * constructor runs before any subclass field is set, and it refuses to finish unless its own class loader is a
 * {@link ConfiguredPluginClassLoader}:
 *
 * <pre>{@code
 * if (this.getClass().getClassLoader() instanceof ConfiguredPluginClassLoader loader) {
 *     loader.init(this);
 * } else {
 *     throw new IllegalStateException("JavaPlugin requires to be created by a valid classloader.");
 * }
 * }</pre>
 *
 * <p>So the loader is what links a freshly constructed plugin to the server: {@link #init} is called from inside
 * the constructor and stores the instance, which {@link #getPlugin()} then answers with.
 *
 * <h2>What this loader does not do</h2>
 *
 * <p>Paper gives each plugin its own loader and a group to look across. This bridge runs every plugin in the
 * child-first loader that also carries the API, so {@link #loadClass(String, boolean, boolean, boolean)} delegates
 * to the ordinary class-loading path and {@link #getGroup()} answers {@code null} — there is no group to look
 * across, and a plugin reaching {@code Class.forName} gets the same answer it would from any class it can see.
 */
final class PluginClassLoader extends URLClassLoader implements ConfiguredPluginClassLoader {

	private final PluginMeta meta;
	private final Path jar;

	/** Set by {@link #init} while the plugin's constructor is still running. */
	private JavaPlugin plugin;

	/**
	 * @param jar    the plugin's own jar, used as this loader's code source
	 * @param parent the loader that carries the Bukkit API and the game — every plugin shares it, which is what
	 *               lets one plugin's types and another's be the same types
	 * @param meta   the plugin's description, answered from {@link #getConfiguration()}
	 */
	PluginClassLoader(Path jar, ClassLoader parent, PluginMeta meta) throws IOException {
		super(new URL[] {jar.toUri().toURL()}, parent);
		this.jar = jar;
		this.meta = meta;
	}

	@Override
	public PluginMeta getConfiguration() {
		return meta;
	}

	/**
	 * Loads a class for this plugin.
	 *
	 * <p>The four booleans Paper passes describe how wide the search should be — across transitive dependencies,
	 * into libraries. This bridge has one flat loader and no cross-plugin visibility graph, so all four collapse
	 * into the ordinary lookup; {@code resolve} is the only one with a meaning here, and it is honoured.
	 */
	@Override
	public Class<?> loadClass(String name, boolean resolve, boolean checkGlobal, boolean checkLibraries)
			throws ClassNotFoundException {
		Class<?> found = loadClass(name);
		if (resolve) resolveClass(found);
		return found;
	}

	/**
	 * Links a plugin instance to this loader.
	 *
	 * <p>Called by {@code JavaPlugin}'s constructor, so it runs while the plugin is only partly constructed —
	 * nothing here may touch the instance beyond keeping the reference.
	 */
	@Override
	public void init(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public JavaPlugin getPlugin() {
		return plugin;
	}

	/** Null: this bridge has no plugin class-loader group, and inventing an empty one would hide that. */
	@Override
	public PluginClassLoaderGroup getGroup() {
		return null;
	}

	@Override
	public void close() throws IOException {
		super.close();
	}

	Path jar() {
		return jar;
	}

	@Override
	public String toString() {
		return "PluginClassLoader[" + jar.getFileName() + "]";
	}
}
