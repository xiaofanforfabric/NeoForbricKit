/*
 * Forbric Bukkit bridge — the {@code ServerBuildInfo} Paper's API insists on finding.
 */
package org.cn.xiaofan.bukkit;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What this server is, in the shape Paper's API asks for it.
 *
 * <p><b>Why this class has to exist.</b> {@code Bukkit.setServer(...)} is not a plain field write: it ends by
 * calling {@code getVersionMessage()}, which calls {@code ServerBuildInfo.buildInfo()}, which is
 * {@code Services.service(ServerBuildInfo.class)} — a {@code ServiceLoader} lookup. On Paper that provider lives
 * in the server jar. A bridge that ships only the API has no provider, so the lookup returns empty, the method
 * throws {@code NoSuchElementException}, and the registration never completes: every {@code Bukkit.getXxx()} then
 * reports a null server, which reads as "the bridge never started" rather than "Paper's build-info service is
 * missing". Supplying the service here is what makes that one field writable.
 *
 * <p><b>The brand says Paper, deliberately.</b> Plugins branch on {@code brandId()} to decide which
 * implementation-specific paths they may take, and the honest answer for a plugin is "the Paper API, at the
 * version this jar was compiled against". Reporting a brand of our own would send every such plugin down its
 * "unknown server" path and cost the features it would otherwise have. The name a human sees stays Forbric, in
 * {@code Server.getName()}; this is the machine-readable brand, and it is about API compatibility.
 *
 * <p><b>The version is the API's, not the game's.</b> {@code minecraftVersionName()} answers what Paper answers:
 * the version whose API this bridge implements. The game's own version is {@code Server.getMinecraftVersion()},
 * and the two are the same number here because the bridge is built against one paper-api per game version.
 */
public final class ForbricServerBuildInfo implements io.papermc.paper.ServerBuildInfo {

    /**
     * The brand a plugin sees. The same key Paper publishes, because what a plugin is really asking is
     * "may I use the Paper API", and the answer is yes — this bridge implements it.
     */
    private static final net.kyori.adventure.key.Key BRAND =
                    net.kyori.adventure.key.Key.key("papermc", "paper");

    /**
     * The version this bridge was built against.
     *
     * <p>A constant rather than a constructor argument, and a no-argument constructor below, because
     * {@code ServiceLoader} instantiates a provider reflectively with no arguments. Passing the version in would
     * mean the provider could not be discovered at all — the lookup would come back empty and every
     * {@code Bukkit.getXxx()} would fail again, for a reason unrelated to the one this class exists to fix.
     */
    private static final String MINECRAFT_VERSION = "26.2";

    /** Required by {@link java.util.ServiceLoader}; see {@link #MINECRAFT_VERSION}. */
    public ForbricServerBuildInfo() {
        this.minecraftVersion = MINECRAFT_VERSION;
    }

    private final String minecraftVersion;

    @Override
    public net.kyori.adventure.key.Key brandId() {
        return BRAND;
    }

    /**
     * Whether a plugin built against {@code brand} may run here.
     *
     * <p>True for Paper's brand and false for everything else — Bukkit's own contract for this method, and the
     * answer that keeps a plugin from taking a Spigot-only path on an API that has Paper's classes.
     */
    @Override
    public boolean isBrandCompatible(net.kyori.adventure.key.Key brand) {
        return BRAND.equals(brand);
    }

    /** The brand's display name. */
    @Override
    public String brandName() {
        return "Paper";
    }

    /** The Minecraft version whose API this bridge implements, e.g. {@code 26.2}. */
    @Override
    public String minecraftVersionId() {
        return minecraftVersion;
    }

    /** The same version as {@link #minecraftVersionId()}; Paper separates id from name and both are the version. */
    @Override
    public String minecraftVersionName() {
        return minecraftVersion;
    }

    /**
     * Empty, and that is the truthful answer.
     *
     * <p>A build number identifies one CI run of Paper's own build pipeline. This server is not one of those runs,
     * so there is no number to give. Inventing one would make a plugin's version comparison silently wrong in the
     * direction of "newer than it is".
     */
    @Override
    public OptionalInt buildNumber() {
        return OptionalInt.empty();
    }

    /**
     * When this server started.
     *
     * <p>Not a build timestamp: there is no build to timestamp. {@code Instant.now()} at class-initialisation is
     * the closest true thing, and a plugin reading it gets a time after which this server has been running.
     */
    @Override
    public Instant buildTime() {
        return buildTime;
    }

    private static final Instant buildTime = Instant.now();

    /** Empty: this repository's branch is not the build's, and Paper's own answer is about its CI. */
    @Override
    public Optional<String> gitBranch() {
        return Optional.empty();
    }

    /** Empty, for the same reason as {@link #gitBranch()}. */
    @Override
    public Optional<String> gitCommit() {
        return Optional.empty();
    }

    /**
     * The version as one line, in the two shapes the API defines.
     *
     * <p>{@code VERSION_FULL} carries the brand and the version; {@code VERSION_SIMPLE} is the version alone.
     * {@code Bukkit.getVersionMessage()} concatenates the full form between the server's name and its Bukkit
     * version, so this decides what every plugin sees in the startup banner — and a plugin that greps that banner
     * for a version expects the number in it.
     */
    @Override
    public String asString(io.papermc.paper.ServerBuildInfo.StringRepresentation representation) {
        return switch (representation) {
            case VERSION_FULL -> brandName() + " " + minecraftVersion;
            case VERSION_SIMPLE -> minecraftVersion;
        };
    }
}
