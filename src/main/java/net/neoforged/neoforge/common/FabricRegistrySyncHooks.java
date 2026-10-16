/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

/**
 * Keeps a client that Fabric API's registry sync cannot talk to on the server, instead of letting
 * {@code RegistrySyncManager.configureClient} disconnect it.
 *
 * <p>Fabric API's {@code fabric-registry-sync-v0} screens the client during configuration: it asks
 * {@code ServerConfigurationNetworking.canSend(listener, RegistrySyncPayload.ID)}, and when the answer is
 * false it disconnects with "This server requires Fabric API installed on your client!". That test is the
 * right one on a Fabric server, where a peer without the payload type genuinely cannot decode the registry
 * dump - but it is the wrong one here. The client of this server is a <em>NeoForge</em> client: NeoForge
 * syncs the registries its mods add through its own channel, the Fabric payload type is only registered
 * when the peer is a Fabric client, and a server whose Fabric mods add no registry entries has nothing for
 * the Fabric path to carry. Disconnecting on that answer turns "the Fabric route is not needed" into "the
 * client is not allowed in", which is what this hook prevents.
 *
 * <p>The hook replaces the {@code disconnect} call only. The method still returns right after it, so no
 * {@code SyncConfigurationTask} is queued: the connection continues through NeoForge's own configuration
 * handling, and the registry entries the platform and its mods define travel the NeoForge route as they
 * always did.
 *
 * <p>Because configuration happens before any player exists, the warning cannot be sent there - a play
 * phase packet is not accepted yet. The message is held per connection and delivered from the bridge's
 * player-join hook instead, see {@link #takePendingWarning(UUID)}.
 */
@ApiStatus.Internal
public final class FabricRegistrySyncHooks {
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Player name -> the warning Fabric API wanted to disconnect with, kept until the player joins. */
    private static final Map<UUID, Component> PENDING_WARNINGS = new ConcurrentHashMap<>();

    private FabricRegistrySyncHooks() {}

    /**
     * Stands in for {@code ServerConfigurationPacketListenerImpl#disconnect} in
     * {@code RegistrySyncManager.configureClient}: the connection is kept, the reason is logged and held
     * for the player-join hook.
     *
     * @param listener the configuration listener whose disconnect call was replaced
     * @param reason   the component Fabric API built for the disconnect
     */
    public static void keepInsteadOfDisconnecting(ServerConfigurationPacketListenerImpl listener,
            Component reason) {
        // GameProfile is a record in this authlib (9.0.75): the accessors are name()/id(), not the old
        // getName()/getId() beans. getOwner() itself is public on ServerCommonPacketListenerImpl.
        var profile = listener.getOwner();
        var name = profile == null ? null : profile.name();
        var uuid = profile == null ? null : profile.id();
        LOGGER.warn("[NeoFobric] Fabric API's registry sync cannot reach {} - keeping the connection anyway."
                        + " The client is expected to be a NeoForge one, where NeoForge syncs the registries"
                        + " itself. Fabric API's reason was: {}", name == null ? "the joining client" : name,
                reason == null ? "(none)" : reason.getString().replace('\n', ' '));
        if (uuid != null && reason != null) {
            PENDING_WARNINGS.put(uuid, reason);
        }
    }

    /**
     * {@return the warning held for that player, removing it, or null when there is none}
     *
     * <p>Called by the bridge's player-join hook. The component Fabric API built carries the registry entry
     * namespaces it could not match, which is exactly what a player needs to compare client and server mods.
     */
    public static Component takePendingWarning(UUID playerId) {
        return playerId == null ? null : PENDING_WARNINGS.remove(playerId);
    }

    /** Drops the held warning of a player who never made it to the play phase. */
    public static void forget(UUID playerId) {
        if (playerId != null) {
            PENDING_WARNINGS.remove(playerId);
        }
    }
}
