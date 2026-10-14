/*
 * Forbric Bukkit bridge — the game's registries, in the shape Paper's API asks for them.
 */
package org.cn.xiaofan.bukkit;

import net.minecraft.core.RegistryAccess;

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

    private ForbricRegistries() {
    }

    private static volatile RegistryAccess access;

    /**
     * Records the game's registries.
     *
     * <p>Called by the server once the game is far enough along to have them. Volatile because the
     * write happens on the server thread and the read happens on whatever thread a plugin first asks
     * on.
     */
    static void install(RegistryAccess registries) {
        access = registries;
    }

    /** The game's registries, or null before the server has installed them. */
    static RegistryAccess access() {
        return access;
    }

    /** Whether the server has installed them yet. */
    static boolean ready() {
        return access != null;
    }
}
