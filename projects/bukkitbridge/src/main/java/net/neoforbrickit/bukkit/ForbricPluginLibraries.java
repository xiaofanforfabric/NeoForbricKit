/*
 * Forbric Bukkit bridge — the jars a plugin's own plugin.yml declares under `libraries:`.
 */
package net.neoforbrickit.bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.PluginDescriptionFile;

/**
 * Downloads the Maven artifacts a plugin lists under {@code libraries:} in its {@code plugin.yml}.
 *
 * <h2>Why a plugin can need this at all</h2>
 *
 * <p>Paper lets a plugin declare runtime dependencies by Maven coordinate instead of shading them into its jar.
 * The server downloads them once and puts them on the plugin's class loader. Here that list was ignored
 * entirely, so a plugin that relied on it failed at the first class it did not ship — BlueMap, whose
 * {@code plugin.yml} asks for {@code com.flowpowered:flow-math:1.0.3}, died with
 * {@code NoClassDefFoundError: com/flowpowered/math/vector/Vector3d} inside {@code onEnable}.
 *
 * <h2>Resolved against the plugin's loader</h2>
 *
 * <p>The jars are added to that one plugin's {@link PluginClassLoader}, not to the bridge's. Two plugins may
 * declare different versions of the same library, and a shared copy would hand one of them the other's
 * version — a failure that only shows up as an {@code AbstractMethodError} at some later call.
 *
 * <h2>Coordinate form</h2>
 *
 * <p>{@code group:artifact:version}, optionally with a fourth {@code classifier} field. That is what Paper
 * accepts and what its own {@code libraries:} documentation shows. A coordinate that is not of that shape is
 * refused by name rather than silently skipped: a plugin author who wrote it wrong should be told, not left
 * wondering why their dependency is missing.
 */
final class ForbricPluginLibraries {

	/** Where downloaded plugin libraries are cached, relative to the game directory. */
	private static final String CACHE_DIRECTORY = ".neofobric/bukkit-libs";

	/**
	 * The repository a coordinate is fetched from.
	 *
	 * <p>Maven Central only. Paper additionally consults a few plugin repositories, but guessing which ones a
	 * plugin meant is how a build resolves a look-alike artifact from the wrong publisher; a dependency that is
	 * not on Central belongs in the plugin's own jar.
	 */
	private static final String CENTRAL = "https://repo1.maven.org/maven2";

	private ForbricPluginLibraries() {
	}

	/**
	 * Resolves every library a plugin declared, downloading what is missing.
	 *
	 * <p>Never throws for a single bad entry: one dependency that cannot be fetched must not stop the plugin
	 * from loading, because the plugin may not need it on the path that is failing. The failure is logged with
	 * the coordinate and the plugin name, which is the information needed to fix it.
	 *
	 * @param description the plugin's own description, whose {@code libraries} list is read
	 * @param gameDir     the game directory, under which the cache lives
	 * @param pluginName  the plugin's name, for the log lines only
	 * @return the jars that resolved, in declaration order; empty when the plugin declared none
	 */
	static List<Path> resolve(PluginDescriptionFile description, Path gameDir, String pluginName) {
		List<String> declared = description.getLibraries();
		if (declared == null || declared.isEmpty()) {
			return List.of();
		}
		List<Path> jars = new ArrayList<>(declared.size());
		HttpClient client = null;
		for (String coordinate : declared) {
			try {
				Path jar = resolveOne(client, coordinate, gameDir, pluginName);
				if (jar != null) {
					jars.add(jar);
				}
			} catch (IOException failure) {
				net.neoforbrickit.bridge.util.ForbricLog.warn(
						"[Forbric/Bukkit] %s declares library %s, which could not be fetched; the plugin may fail "
								+ "to load",
						pluginName, coordinate, failure);
			}
		}
		return jars;
	}

	/** One coordinate: the cached jar, or null when it could not be resolved. */
	private static Path resolveOne(HttpClient existing, String coordinate, Path gameDir, String pluginName)
			throws IOException {
		String[] parts = coordinate.split(":");
		if (parts.length != 3 && parts.length != 4) {
			net.neoforbrickit.bridge.util.ForbricLog.warn(
					"[Forbric/Bukkit] %s declares library %s, which is not group:artifact:version[:classifier]",
					pluginName, coordinate);
			return null;
		}
		String group = parts[0].trim();
		String artifact = parts[1].trim();
		String version = parts[2].trim();
		String classifier = parts.length == 4 ? parts[3].trim() : null;

		String fileName = artifact + '-' + version + (classifier == null ? "" : '-' + classifier) + ".jar";
		String relative = group.replace('.', '/') + '/' + artifact + '/' + version + '/' + fileName;
		Path target = gameDir.resolve(CACHE_DIRECTORY).resolve(relative);
		if (Files.isRegularFile(target)) {
			return target;
		}
		HttpClient client = existing != null ? existing : HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(30))
				.build();
		download(client, CENTRAL + '/' + relative, target, pluginName);
		return target;
	}

	/**
	 * Streams one jar to a {@code .part} file, then moves it into place.
	 *
	 * <p>The rename is what keeps the cache usable: an interrupted download leaves a {@code .part}, never a
	 * truncated {@code .jar} that the next launch would take for a complete one.
	 */
	private static void download(HttpClient client, String url, Path target, String pluginName) throws IOException {
		net.neoforbrickit.bridge.util.ForbricLog.info(
				"[Forbric/Bukkit] %s needs %s; downloading it", pluginName, target.getFileName());
		Files.createDirectories(target.getParent());
		Path temporary = target.resolveSibling(target.getFileName() + ".part");

		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build();
		try {
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				throw new IOException("HTTP " + response.statusCode() + " from " + url);
			}
			try (InputStream body = response.body()) {
				Files.copy(body, temporary, StandardCopyOption.REPLACE_EXISTING);
			}
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted while downloading " + url, interrupted);
		} catch (IOException failure) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException ignored) {
				// Already gone, or cannot be removed — either way the outcome is unchanged.
			}
			throw new IOException("could not download " + url + " — check the network, or place the jar manually "
					+ "in " + target.getParent(), failure);
		}
	}
}
