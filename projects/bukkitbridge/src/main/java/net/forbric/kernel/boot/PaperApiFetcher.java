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

import net.forbric.kernel.util.ForbricLog;

/**
 * Fetches the Paper API and the adventure libraries its signatures name, once, into a cache under the game
 * directory.
 *
 * <p>Boot-side and dependency-free ({@code java.net.http} only). It runs before the class loader exists, because
 * the jars it returns are owned jars.
 *
 * <h2>Only what the game does not already have</h2>
 *
 * <p>The API also needs gson, guava, joml, snakeyaml, log4j, slf4j, fastutil and brigadier — all of which
 * Minecraft 26.2 ships, and which the kernel already puts on the owned classpath ahead of any guest jar. Fetching
 * a second copy would put two versions of one library on a single loader, where resolution goes by whichever URL
 * comes first; that is the same failure the kernel's library ordering exists to prevent.
 *
 * <p>The version is pinned rather than resolved. {@code 26.2.build.+} would work and would also silently move the
 * API out from under the generated {@code Server} stub, which is written against one specific version.
 */
final class PaperApiFetcher {

	private static final String PAPER_MAVEN = "https://repo.papermc.io/repository/maven-public";
	private static final String CENTRAL = "https://repo1.maven.org/maven2";

/** Where the cache lives, relative to the game directory. NeoFobric's own working folder. */
        private static final String CACHE_DIRECTORY = ".neofobric/bukkit-libs";

	/** The version the bridge is built against. Bumping it means re-running the bridge's stub generator. */
	private static final String PAPER_API_VERSION = "26.2.build.133-stable";

	private record Artifact(String group, String artifact, String version, String repository) {

		String path() {
			return group.replace('.', '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version + ".jar";
		}
	}

	private static final List<Artifact> ARTIFACTS = List.of(
			artifact("io.papermc.paper", "paper-api", PAPER_API_VERSION, PAPER_MAVEN),
			// snakeyaml: PluginDescriptionFile's static initialiser builds a Loader through it, so reading any
			// plugin.yml fails without it. Unlike gson and guava, Minecraft does NOT ship it — measured against
			// 26.2's own library list — which is why this one is fetched rather than assumed present.
			artifact("org.yaml", "snakeyaml", "2.2", CENTRAL),
			artifact("net.kyori", "adventure-api", "5.2.0", CENTRAL),
			artifact("net.kyori", "adventure-key", "5.2.0", CENTRAL),
			artifact("net.kyori", "adventure-text-minimessage", "5.2.0", CENTRAL),
			artifact("net.kyori", "adventure-text-serializer-gson", "5.2.0", CENTRAL),
			artifact("net.kyori", "adventure-text-serializer-json", "5.2.0", CENTRAL),
			artifact("net.kyori", "adventure-text-serializer-legacy", "5.2.0", CENTRAL),
			artifact("net.kyori", "adventure-text-serializer-plain", "5.2.0", CENTRAL),
			artifact("net.kyori", "adventure-text-logger-slf4j", "5.2.0", CENTRAL),
			artifact("net.kyori", "option", "1.1.0", CENTRAL),
			// `+build.N` qualifiers are served by Paper's Maven only; Central answers 404.
			artifact("net.md-5", "bungeecord-chat", "1.21-R0.2-deprecated+build.21", PAPER_MAVEN));

	private PaperApiFetcher() {
	}

	private static Artifact artifact(String group, String artifact, String version, String repository) {
		return new Artifact(group, artifact, version, repository);
	}

	/** Downloads what is missing and returns every jar's path, in load order. */
	static List<Path> fetch(Path gameDir) throws IOException {
		Path cache = gameDir.resolve(CACHE_DIRECTORY);
		Files.createDirectories(cache);

		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(30))
				.build();

		List<Path> jars = new ArrayList<>(ARTIFACTS.size());
		for (Artifact artifact : ARTIFACTS) {
			Path target = cache.resolve(artifact.path());
			if (!Files.isRegularFile(target)) {
				download(client, artifact, target);
			}
			jars.add(target);
		}
		return jars;
	}

	/**
	 * Streams one artifact to a {@code .part} file, then moves it into place.
	 *
	 * <p>The rename is what makes the cache safe to reuse: an interrupted download leaves a {@code .part}, never a
	 * truncated {@code .jar} that the next launch would mistake for a complete one.
	 */
	private static void download(HttpClient client, Artifact artifact, Path target) throws IOException {
		String url = artifact.repository() + '/' + artifact.path();
ForbricLog.info("[NeoFobric/Bukkit] downloading %s-%s", artifact.artifact(), artifact.version());

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
			throw new IOException("could not download " + url + " — check the network, or place the jar "
					+ "manually in " + target.getParent(), failure);
		}
	}
}
