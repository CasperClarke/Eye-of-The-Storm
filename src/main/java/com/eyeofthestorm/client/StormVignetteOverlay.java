package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.storm.StormBoundaryMath;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * World-border-style vignette when near or outside the storm wall.
 */
@EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT)
public final class StormVignetteOverlay {
    private static final ResourceLocation VIGNETTE =
            ResourceLocation.withDefaultNamespace("textures/misc/vignette.png");

    private StormVignetteOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientStormState.shouldRender()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return;
        }
        if (mc.player.getTags().contains("storm_immune") || mc.player.isSpectator() || mc.player.isCreative()) {
            return;
        }

        Entity entity = mc.getCameraEntity();
        if (entity == null) {
            return;
        }

        float strength = StormWallRenderer.vignetteStrength(entity.position());
        if (strength <= 0f) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ZERO,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        strength = Mth.clamp(strength, 0f, 1f);
        graphics.setColor(0f, strength, strength, 1f);
        graphics.blit(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);

        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
