package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT)
public final class StormClientLifecycle {
    private StormClientLifecycle() {}

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientStormState.reset();
            ClientRadarPlayers.reset();
            StormDebugState.reset();
            StormPathHistory.reset();
        }
    }
}
