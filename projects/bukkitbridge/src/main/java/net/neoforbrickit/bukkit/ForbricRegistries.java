/*
 * Forbric Bukkit bridge — the game's registries, in the shape Paper's API asks for them.
 */
package net.neoforbrickit.bukkit;

/**
 * The game's {@code RegistryAccess}, held for the bridge's own use.
 *
 * <p><b>Why a holder and not a field somewhere.</b> {@link ForbricRegistries}
 * (the {@code io.papermc.paper.registry.RegistryAccess} provider) is instantiated by
 * {@code ServiceLoader} at class-initialisation of Paper's own {@code RegistryAccessHolder}, which
 * happens the first time any plugin touches a registry — before the game has necessarily finished
 * booting, and with no reference to anything. The server, meanwhile, is the only thing that knows
 * the game's registries. A static field is the only channel between them: the server writes it when
 * it has the registries, and the provider reads it when a plugin asks.
 *
 * <p><b>Null means "the server has not registered yet", not "no registries".</b> Callers must treat
 * it as a not-ready signal and say so, rather than substituting an empty registry: an empty one
 * would make every item in the game resolve to nothing and the failure would surface as a missing
 * item instead of as an ordering problem.
 */
final class ForbricRegistries {

    /** Process-wide handoff shared by the game-layer and FML-Plugins copies of the bridge. */
    private static final String SHARED_ACCESS_KEY = "neofobric.bukkit.registryAccess";

    private ForbricRegistries() {
    }

    // Deliberately Object: this class is duplicated in the FML-Plugins and game layers. A field typed as
    // net.minecraft.core.RegistryAccess makes the FML-Plugins copy fail during class definition because that
    // layer cannot see Minecraft. Callers in the game layer use the generic accessor below and cast locally,
    // after the bridge has crossed this classloader boundary.
    private static volatile Object access;

    /**
     * Records the game's registries.
     *
     * <p>Called by the server once the game is far enough along to have them. Volatile because the
     * write happens on the server thread and the read happens on whatever thread a plugin first asks
     * on.
     */
    static void install(Object registries) {
        access = registries;
        // The bridge is intentionally present in two FML classloader layers so Paper's ServiceLoader can
        // resolve its providers. Static fields are therefore not shared between those copies. System
        // properties are process-wide and let the game-layer copy publish the live registry to the API-layer
        // copy without making either classloader a parent of the other.
        System.getProperties().put(SHARED_ACCESS_KEY, registries);
    }

    /** The game's registries, or null before the server has installed them. */
    @SuppressWarnings("unchecked")
    static <T> T access() {
        if (access == null) {
            Object shared = System.getProperties().get(SHARED_ACCESS_KEY);
            if (shared != null) {
                access = shared;
            }
        }
        return (T) access;
    }

    /** Whether the server has installed them yet. */
    static boolean ready() {
        return access != null;
    }
}
