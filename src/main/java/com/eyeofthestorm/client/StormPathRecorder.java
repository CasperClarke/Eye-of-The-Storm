package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT)
public final class StormPathRecorder {
    private StormPathRecorder() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ClientStormState.shouldRender()) {
            StormPathHistory.record(ClientStormState.centerX, ClientStormState.centerZ);
        }
    }
}
