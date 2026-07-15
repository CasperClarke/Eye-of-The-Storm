package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Mod-namespaced wall shader with soft alpha (no discard).
 * Lives under {@code eyeofthestorm:} so shader packs that rewrite {@code minecraft:}
 * core / render-type programs leave this path alone.
 */
@EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class StormWallShader {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(EyeOfTheStormMod.MOD_ID, "storm_wall");

    @Nullable
    private static ShaderInstance instance;

    private StormWallShader() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), ID, DefaultVertexFormat.POSITION_TEX_COLOR),
                shader -> instance = shader
        );
    }

    /** Soft-alpha POSITION_TEX_COLOR program, or null before first load. */
    @Nullable
    public static ShaderInstance get() {
        return instance;
    }
}
