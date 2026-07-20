package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.network.ClientItemHooks;
import com.eyeofthestorm.network.ClientPacketHooks;
import com.eyeofthestorm.network.OpenStormPathPreviewPayload;
import com.eyeofthestorm.network.PushRadarColorPayload;
import com.eyeofthestorm.network.RadarPlayersPayload;
import com.eyeofthestorm.network.StormSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-only handlers for play-to-client payloads and client item interactions. */
@EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientNetwork {
    static {
        // Installed as soon as this client-only class loads (never on dedicated server).
        ClientPacketHooks.stormSync = ClientNetwork::handleStormSync;
        ClientPacketHooks.radarPlayers = ClientNetwork::handleRadarPlayers;
        ClientPacketHooks.pushRadarColor = ClientNetwork::handlePushRadarColor;
        ClientPacketHooks.openStormPathPreview = ClientNetwork::handleOpenStormPathPreview;
        ClientItemHooks.toggleStormRadar = ClientNetwork::toggleStormRadar;
    }

    private ClientNetwork() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(EyeOfTheStormMod.MOD_ID).ifPresent(ClientConfigScreenRegistration::register);
    }

    public static void handleStormSync(StormSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientStormState.update(payload));
    }

    public static void handleRadarPlayers(RadarPlayersPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRadarPlayers.update(payload.players()));
    }

    public static void handlePushRadarColor(PushRadarColorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            StormRadarConfig.setPlayerColor(payload.colorR(), payload.colorG(), payload.colorB());
            StormRadarConfig.save();
        });
    }

    public static void handleOpenStormPathPreview(OpenStormPathPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new StormPathPreviewScreen(
                payload.seed(),
                payload.circleCount(),
                payload.pathRadiusScale(),
                payload.peakSpeed(),
                payload.speedPhaseRatePerTick(),
                payload.pathParam(),
                payload.speedPhase(),
                payload.pathOriginX(),
                payload.pathOriginZ(),
                payload.eyeRadius(),
                payload.strongholdX(),
                payload.strongholdZ()
        )));
    }

    private static void toggleStormRadar(Player player) {
        StormRadarState.toggle();
        player.displayClientMessage(
                Component.translatable(
                        StormRadarState.enabled()
                                ? "message.eyeofthestorm.radar_on"
                                : "message.eyeofthestorm.radar_off"
                ),
                true
        );
    }
}
