package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.StormConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

/**
 * Soft full-screen storm veil when outside the wall.
 * Drawn on {@link RenderGuiEvent.Pre} so hotbar/chat render on top of it.
 */
@EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT)
public final class StormOutsideOverlay {
    private StormOutsideOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (!ClientStormState.shouldRender()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!mc.level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        // Creative included — only skip true non-participants.
        if (mc.player.getTags().contains("storm_immune") || mc.player.isSpectator()) {
            return;
        }

        Entity entity = mc.getCameraEntity();
        if (entity == null) {
            return;
        }

        // Positive inside, negative outside. Veil starts fadeBlocks inside the wall.
        float dist = StormWallRenderer.distanceToWall(entity.position());
        float fade = Math.max(0.25f, StormConfig.outsideOverlayFadeBlocks);
        if (dist >= fade || StormConfig.outsideOverlayAlpha <= 0f) {
            return;
        }

        ShaderInstance shader = StormVeilShader.get();
        if (shader == null) {
            return;
        }

        float strength = Mth.clamp(1f - dist / fade, 0f, 1f);
        strength = strength * strength * (3f - 2f * strength);
        float alpha = StormConfig.outsideOverlayAlpha * strength;
        if (alpha < 0.01f) {
            return;
        }

        if (!StormWallTextures.isRegistered()) {
            StormWallTextures.register();
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();

        float r = StormConfig.wallColorR / 255f;
        float g = StormConfig.wallColorG / 255f;
        float b = StormConfig.wallColorB / 255f;
        float tiles = Math.max(0.5f, StormConfig.outsideOverlayTiles);
        float vTiles = tiles * h / (float) Math.max(1, w);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShader(StormVeilShader::get);
        RenderSystem.setShaderTexture(0, StormWallTextures.MORPH_ATLAS);
        shader.setSampler("Sampler0", mc.getTextureManager().getTexture(StormWallTextures.MORPH_ATLAS));
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        var morphCycle = shader.getUniform("MorphCycle");
        if (morphCycle != null) {
            morphCycle.set(Math.max(0.1f, StormConfig.wallVoronoiMorphCycleSeconds));
        }
        var sliceCount = shader.getUniform("SliceCount");
        if (sliceCount != null) {
            sliceCount.set((float) Math.max(2, StormConfig.wallVoronoiMorphSliceCount));
        }
        var veilContrast = shader.getUniform("VeilContrast");
        if (veilContrast != null) {
            veilContrast.set(Mth.clamp(StormConfig.outsideOverlayContrast, 0f, 1f));
        }

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR
        );
        // One fullscreen quad; storm_veil fract()s UVs for tiling + morph blend.
        buffer.addVertex(matrix, 0, h, 0).setUv(0f, vTiles).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, w, h, 0).setUv(tiles, vTiles).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, w, 0, 0).setUv(tiles, 0f).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, 0, 0, 0).setUv(0f, 0f).setColor(r, g, b, alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
