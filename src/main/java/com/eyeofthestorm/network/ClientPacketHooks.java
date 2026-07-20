package com.eyeofthestorm.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

/**
 * Common-side hooks for play-to-client packet handling.
 * Client code installs real handlers during client setup; dedicated server keeps no-ops.
 */
public final class ClientPacketHooks {
    private ClientPacketHooks() {}

    public static BiConsumer<StormSyncPayload, IPayloadContext> stormSync = (payload, context) -> {};
    public static BiConsumer<RadarPlayersPayload, IPayloadContext> radarPlayers = (payload, context) -> {};
    public static BiConsumer<PushRadarColorPayload, IPayloadContext> pushRadarColor = (payload, context) -> {};
    public static BiConsumer<OpenStormPathPreviewPayload, IPayloadContext> openStormPathPreview =
            (payload, context) -> {};
}
