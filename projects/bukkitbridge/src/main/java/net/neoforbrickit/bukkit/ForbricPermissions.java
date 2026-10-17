/*
 * Forbric Bukkit bridge — asking an installed permission plugin about one player's node.
 */
package net.neoforbrickit.bukkit;

import java.util.UUID;

/**
 * The bridge's one question for a permission plugin: does this player hold this node?
 *
 * <h2>Why this is not just {@code player.hasPermission}</h2>
 *
 * <p>Bukkit's answer for an unregistered node is "denied": {@code PluginManager#getPermission} returns null
 * and there is no default to consult. That is correct for a server with no permission plugin — a node nobody
 * declared grants nothing — but on a server WITH one it is exactly backwards, because the whole point of
 * such a plugin is to own nodes that no {@code plugin.yml} mentions.
 *
 * <p>LuckPerms is the case that surfaced this. Every one of its own command permissions
 * ({@code luckperms.info}, {@code luckperms.user.info}, …) is declared in its code as a
 * {@code CommandPermission} constant and never registered with the {@code PluginManager}. Its command
 * completion filters on those nodes through the Bukkit {@code CommandSender}, so on this bridge the filter
 * said "denied" for every one of them and {@code /lp} completed to nothing — while the console, which is
 * allowed everything, completed the full list.
 *
 * <h2>Why reflection</h2>
 *
 * <p>The bridge must not require any permission plugin to be installed, and must not fail to load when none
 * is: a hard reference to {@code net.luckperms.api.LuckPermsProvider} would be a {@code NoClassDefFoundError}
 * on every server without LuckPerms. So the call is made against a class that is present only when the plugin
 * is, and its absence means {@link Lookup#UNKNOWN} — "no plugin answered, use the ordinary rules".
 *
 * <p>LuckPerms publishes {@code net.luckperms.api.LuckPermsProvider.get()}, which is in the plugin's OUTER
 * jar (not the nested one), so the bridge's cross-plugin group can reach it the moment the plugin is
 * enabled. {@code getUserManager().getUser(uuid)} then answers {@code getUser().getCachedData()}
 * {@code .getPermissionData().checkPermission(node)}, which returns a Tristate: {@code TRUE} / {@code FALSE}
 * for a decided node and {@code UNDEFINED} for one the plugin has nothing to say about.
 *
 * <p>{@code UNDEFINED} maps to {@link Lookup#UNKNOWN} rather than to false, so an unanswered node still falls
 * through to the declared default below. That keeps a plugin's silence from denying something a
 * {@code plugin.yml} granted.
 */
final class ForbricPermissions {

    private ForbricPermissions() {
    }

    /** What a permission plugin said about one node. */
    enum Lookup {
        /** Granted. */
        TRUE,
        /** Explicitly denied — not merely unmentioned, or this would be {@link #UNKNOWN}. */
        FALSE,
        /** No permission plugin answered, or it has no opinion; the caller applies the ordinary rules. */
        UNKNOWN
    }

    /**
     * Asks the installed permission plugin whether a player holds a node.
     *
     * <p>Never throws: a plugin that is half-initialised, a user that has not loaded, and a reflection
     * signature that moved all mean "no answer", and a permission check that threw would break every caller
     * that asks it in a loop.
     *
     * @param playerId the player's account id, which is what a permission plugin keys its users by
     * @param node     the permission node to ask about
     */
    static Lookup lookup(UUID playerId, String node) {
        if (playerId == null || node == null) {
            return Lookup.UNKNOWN;
        }
        try {
            // Loaded through a PLUGIN's class loader, not Class.forName.
            //
            // Class.forName resolves against the caller's loader, and this class is defined by the game
            // layer, which cannot see a plugin jar at all -- the attempt fails with
            // ClassNotFoundException: net.luckperms.api.LuckPermsProvider even though the plugin is enabled
            // and running. The group is the bridge's existing answer to exactly this problem.
            Class<?> provider = providerClass();
            if (provider == null) {
                return Lookup.UNKNOWN;
            }
            Object api = provider.getMethod("get").invoke(null);
            if (api == null) {
                // The plugin's provider answers null before it has registered itself, which happens during
                // its own enable. No opinion, so the caller falls through to the declared defaults.
                return Lookup.UNKNOWN;
            }
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, playerId);
            if (user == null) {
                // Not loaded yet: the plugin has no data for this player, so it has no opinion to give.
                return Lookup.UNKNOWN;
            }
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            Object result = permissionData.getClass()
                    .getMethod("checkPermission", String.class).invoke(permissionData, node);
            // Tristate is an enum: TRUE, FALSE, UNDEFINED.
            return switch (String.valueOf(result)) {
                case "TRUE" -> Lookup.TRUE;
                case "FALSE" -> Lookup.FALSE;
                default -> Lookup.UNKNOWN;
            };
        } catch (ReflectiveOperationException | RuntimeException | LinkageError noPlugin) {
            // No permission plugin, or one whose shape this bridge does not know. Either way the answer is
            // "nobody spoke", which is the behaviour every server without such a plugin already has.
            return Lookup.UNKNOWN;
        }
    }

    /**
     * The permission plugin's API class, or null when no such plugin supplies it.
     *
     * <p>Resolved through the cross-plugin group rather than {@code Class.forName}: the game layer this class
     * is defined by cannot see a plugin jar, and asking it produced
     * {@code ClassNotFoundException: net.luckperms.api.LuckPermsProvider} on a server where the plugin was
     * enabled and working.
     *
     * <p><b>Only a success is cached.</b> A miss is retried, because a permission check can run before the
     * plugin is enabled — the first player joining during startup is enough — and caching that miss would
     * disable the integration for the rest of the server's life. The search itself is a handful of already
     * loaded class-loader lookups, so retrying is cheap.
     */
    private static volatile Class<?> providerClass;

    private static Class<?> providerClass() {
        Class<?> known = providerClass;
        if (known != null) {
            return known;
        }
        ForbricClassLoaderGroup group = ForbricClassLoaderGroup.current();
        Class<?> found = group == null ? null : group.loadFromPlugins("net.luckperms.api.LuckPermsProvider");
        if (found != null) {
            providerClass = found;
        }
        return found;
    }
}
