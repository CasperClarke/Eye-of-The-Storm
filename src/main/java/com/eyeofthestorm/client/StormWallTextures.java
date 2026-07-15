package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.StormConfig;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * Storm wall morph atlas loaded from a pre-baked asset (not generated at runtime).
 * Regenerate with {@code gradlew previewStormWall} (writes the PNG under assets/).
 */
public final class StormWallTextures {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Vertical morph atlas: {@code sliceCount} tiles stacked in V, white RGB + density alpha. */
    public static final ResourceLocation MORPH_ATLAS = ResourceLocation.fromNamespaceAndPath(
            EyeOfTheStormMod.MOD_ID,
            "textures/storm/wall_morph_atlas.png"
    );

    private static boolean registered;

    private StormWallTextures() {}

    public static boolean isRegistered() {
        return registered;
    }

    public static void register() {
        if (registered) {
            return;
        }

        var textureManager = Minecraft.getInstance().getTextureManager();
        textureManager.register(MORPH_ATLAS, new MorphAtlasTexture(MORPH_ATLAS));
        registered = true;
        LOGGER.debug(
                "Registered storm wall morph atlas {} ({} slices)",
                MORPH_ATLAS,
                StormConfig.wallVoronoiMorphSliceCount
        );
    }

    /** Binds morph atlas and sets shader uniforms. */
    public static void applyMorphToShader(
            ShaderInstance shader,
            float stormRelX,
            float stormRelZ
    ) {
        if (shader == null) {
            return;
        }

        if (!registered) {
            register();
        }

        var textureManager = Minecraft.getInstance().getTextureManager();
        AbstractTexture atlas = textureManager.getTexture(MORPH_ATLAS);

        RenderSystem.setShaderTexture(0, MORPH_ATLAS);
        shader.setSampler("Sampler0", atlas);
        var morphCycle = shader.getUniform("MorphCycle");
        if (morphCycle != null) {
            // Seconds; storm_wall.fsh converts via GameTime * (1200 / MorphCycle).
            morphCycle.set(Math.max(0.1f, StormConfig.wallVoronoiMorphCycleSeconds));
        }
        var sliceCount = shader.getUniform("SliceCount");
        if (sliceCount != null) {
            sliceCount.set((float) Math.max(2, StormConfig.wallVoronoiMorphSliceCount));
        }
        var stormCenter = shader.getUniform("StormCenterRel");
        if (stormCenter != null) {
            stormCenter.set(stormRelX, stormRelZ);
        }
    }

    /** Asset-backed atlas with GL_REPEAT so wall UVs tile in U. */
    private static final class MorphAtlasTexture extends SimpleTexture {
        MorphAtlasTexture(ResourceLocation location) {
            super(location);
        }

        @Override
        public void load(ResourceManager resourceManager) throws IOException {
            super.load(resourceManager);
            bind();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GlConst.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
    }
}
