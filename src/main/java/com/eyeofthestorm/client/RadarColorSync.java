package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.network.SetRadarColorPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends the local player's radar RGB to the server on join and config change. */
public final class RadarColorSync {
    private RadarColorSync() {}

    public static void sendConfiguredColor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }
        PacketDistributor.sendToServer(new SetRadarColorPayload(
                StormRadarConfig.playerColorR(),
                StormRadarConfig.playerColorG(),
                StormRadarConfig.playerColorB()
        ));
    }

    @EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        @SubscribeEvent
        public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
            sendConfiguredColor();
        }
    }

    @EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onConfigReload(ModConfigEvent.Reloading event) {
            if (event.getConfig().getSpec() == StormRadarConfig.SPEC) {
                sendConfiguredColor();
            }
        }
    }
}
