/*
 * Forbric Bukkit bridge — the PluginLoader paper-api looks up by name.
 */
package org.cn.xiaofan.bukkit;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.bukkit.event.Event;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.event.Listener;

/**
 * The {@link PluginLoader} {@code JavaPlugin} looks up when it is constructed.
 *
 * <p>{@code JavaPlugin.init(...)} starts with {@code this.loader = DummyPluginLoaderImplHolder.INSTANCE}, and that
 * holder resolves a {@code PluginLoader} through {@code Services.service(...).orElseThrow()}. Without one
 * registered, every {@code JavaPlugin} construction dies in a static initialiser — before the plugin's own code
 * runs, and with a stack that names {@code JavaPlugin} rather than the missing service. This class is what the
 * lookup finds.
 *
 * <p>Paper's own implementation is a bridge to its plugin provider system, which does the real loading. Here the
 * loading is {@link PluginHandle}'s job and this class is only the identity {@code JavaPlugin} needs, so the
 * methods that would do work answer honestly instead of pretending: {@link #loadPlugin} and
 * {@link #getPluginDescription} are refused by name, because the bridge loads through {@link PluginHandle} and a
 * caller reaching them is reaching a path this bridge does not run.
 */
public final class ForbricPluginLoader implements PluginLoader {

	@Override
	public Plugin loadPlugin(File file) throws InvalidPluginException, org.bukkit.plugin.UnknownDependencyException {
		throw new InvalidPluginException(
				"[Forbric/Bukkit] PluginLoader.loadPlugin is not how this bridge loads plugins; "
						+ "see BridgeBootstrap");
	}

	@Override
	public PluginDescriptionFile getPluginDescription(File file) throws InvalidDescriptionException {
		throw new InvalidDescriptionException(
				"[Forbric/Bukkit] PluginLoader.getPluginDescription is not how this bridge reads descriptions; "
						+ "see PluginHandle");
	}

	/** A plugin jar, which is the only thing this loader recognises. */
	@Override
	public Pattern[] getPluginFileFilters() {
		return new Pattern[] {Pattern.compile("\\.jar$")};
	}

	/**
	 * No listeners are registered through this loader.
	 *
	 * <p>An empty map rather than a refusal: this method's contract is "the listeners this listener would
	 * produce", and a loader that owns no event system produces none. Returning empty is the true answer, not a
	 * placeholder.
	 */
	@Override
	public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(Listener listener,
			Plugin plugin) {
		return Map.of();
	}

	@Override
	public void enablePlugin(Plugin plugin) {
		throw new UnsupportedOperationException(
				"[Forbric/Bukkit] PluginLoader.enablePlugin is not how this bridge enables plugins; "
						+ "see PluginHandle.enable");
	}

	@Override
	public void disablePlugin(Plugin plugin) {
		throw new UnsupportedOperationException(
				"[Forbric/Bukkit] PluginLoader.disablePlugin is not how this bridge disables plugins; "
						+ "see PluginHandle.disable");
	}
}
