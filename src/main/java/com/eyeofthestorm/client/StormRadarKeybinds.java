package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT)
public final class StormRadarKeybinds {
    public static final String CATEGORY = "key.categories.eyeofthestorm";

    public static final KeyMapping TOGGLE_RADAR = new KeyMapping(
            "key.eyeofthestorm.toggle_radar",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            CATEGORY
    );

    public static final KeyMapping TOGGLE_DEBUG_WALL = new KeyMapping(
            "key.eyeofthestorm.toggle_debug_wall",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY
    );

    private StormRadarKeybinds() {}

    @EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_RADAR);
            event.register(TOGGLE_DEBUG_WALL);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        while (TOGGLE_RADAR.consumeClick()) {
            StormRadarState.toggle();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable(
                                StormRadarState.enabled
                                        ? "message.eyeofthestorm.radar_on"
                                        : "message.eyeofthestorm.radar_off"
                        ),
                        true
                );
            }
        }

        while (TOGGLE_DEBUG_WALL.consumeClick()) {
            StormDebugState.toggleWireframe();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable(
                                StormDebugState.wireframeEnabled
                                        ? "message.eyeofthestorm.debug_wall_on"
                                        : "message.eyeofthestorm.debug_wall_off"
                        ),
                        true
                );
            }
        }
    }
}
