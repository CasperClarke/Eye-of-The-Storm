package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.StormConfig;
import com.eyeofthestorm.storm.StormBoundaryMath;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Storm wall as stacked translucent cylinder shells — same pipeline as
 * vanilla {@code LevelRenderer.renderWorldBorder}, with standard alpha blend
 * in the fade band so sky pixels are not blown out.
 */
@EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT)
public final class StormWallRenderer {
    private static final ResourceLocation FORCEFIELD =
            ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");

    private static final int FADE_SHELLS = 28;
    private static final int MIN_CYLINDER_SEGMENTS = 128;

    private StormWallRenderer() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        if (!ClientStormState.shouldRender()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        double cx = ClientStormState.centerX;
        double cz = ClientStormState.centerZ;
        double radius = ClientStormState.radius;

        double d0 = mc.options.getEffectiveRenderDistance() * 16.0;
        double distToCenter = Math.hypot(cam.x - cx, cam.z - cz);
        double distToWall = Math.abs(distToCenter - radius);
        boolean insideEye = distToCenter <= radius;

        if (!insideEye && distToWall >= d0) {
            return;
        }

        double proximity = 1.0 - distToWall / d0;
        proximity = Math.pow(proximity, 4.0);
        proximity = Mth.clamp(proximity, 0.0, 1.0);
        if (insideEye) {
            proximity = Math.max(proximity, 0.85);
        }

        double d4 = mc.gameRenderer.getDepthFar();
        double viewWorldMin = cam.y - d4;
        double viewWorldMax = Math.min(cam.y + d4, StormConfig.wallTopY);
        if (viewWorldMax <= viewWorldMin + 0.5) {
            return;
        }

        float proximityAlpha = (float) proximity;
        float r = StormConfig.wallColorR / 255f;
        float g = StormConfig.wallColorG / 255f;
        float b = StormConfig.wallColorB / 255f;

        float scroll = (float) (Util.getMillis() % 3000L) / 3000.0F;
        float vBase = (float) (-Mth.frac(cam.y * 0.5));

        setupRenderState();

        double solidTop = Math.min(viewWorldMax, StormConfig.wallFadeStartY);
        if (solidTop > viewWorldMin + 0.5) {
            drawShell(
                    cx, cz, radius, cam, d0, insideEye, d4, scroll, vBase,
                    viewWorldMin, solidTop,
                    proximityAlpha * StormConfig.wallPeakAlpha,
                    r, g, b
            );
        }

        double fadeSpan = StormConfig.wallTopY - StormConfig.wallFadeStartY;
        if (fadeSpan > 0.5 && viewWorldMax > StormConfig.wallFadeStartY) {
            for (int shell = 0; shell < FADE_SHELLS; shell++) {
                double t0 = shell / (double) FADE_SHELLS;
                double t1 = (shell + 1) / (double) FADE_SHELLS;

                double wy0 = StormConfig.wallFadeStartY + fadeSpan * t0;
                double wy1 = StormConfig.wallFadeStartY + fadeSpan * t1;

                if (wy1 <= viewWorldMin || wy0 >= viewWorldMax) {
                    continue;
                }

                wy0 = Math.max(wy0, viewWorldMin);
                wy1 = Math.min(wy1, viewWorldMax);
                if (wy1 <= wy0 + 0.1) {
                    continue;
                }

                float shellAlpha = shellAlpha(shell, proximityAlpha);
                if (shellAlpha < 0.02f) {
                    continue;
                }

                drawShell(
                        cx, cz, radius, cam, d0, insideEye, d4, scroll, vBase,
                        wy0, wy1,
                        shellAlpha,
                        r, g, b
                );
            }
        }

        teardownRenderState();
    }

    static float outsideGap(net.minecraft.world.phys.Vec3 position) {
        if (!ClientStormState.shouldRender()) {
            return 0f;
        }
        return (float) StormBoundaryMath.outsideGap(
                position,
                ClientStormState.centerX,
                ClientStormState.centerZ,
                ClientStormState.radius
        );
    }

    static float vignetteStrength(net.minecraft.world.phys.Vec3 position) {
        if (!ClientStormState.shouldRender()) {
            return 0f;
        }
        return StormBoundaryMath.vignetteStrength(StormBoundaryMath.distanceToWall(
                position,
                ClientStormState.centerX,
                ClientStormState.centerZ,
                ClientStormState.radius
        ));
    }

    private static float shellAlpha(int shellIndex, float proximityAlpha) {
        double fadeSpan = StormConfig.wallTopY - StormConfig.wallFadeStartY;
        float worldY = (float) (StormConfig.wallFadeStartY + fadeSpan * (shellIndex + 0.5) / FADE_SHELLS);
        return proximityAlpha * verticalAlpha(worldY);
    }

    private static float verticalAlpha(float worldY) {
        if (worldY >= StormConfig.wallTopY) {
            return 0f;
        }
        if (worldY <= StormConfig.wallFadeStartY) {
            return StormConfig.wallPeakAlpha;
        }
        float t = (worldY - StormConfig.wallFadeStartY)
                / Math.max(1f, StormConfig.wallTopY - StormConfig.wallFadeStartY);
        float smooth = t * t * (3f - 2f * t);
        return StormConfig.wallPeakAlpha * (1f - smooth);
    }

    private static void drawShell(
            double cx,
            double cz,
            double radius,
            Vec3 cam,
            double viewRange,
            boolean insideEye,
            double depthFar,
            float scroll,
            float vBase,
            double worldY0,
            double worldY1,
            float alpha,
            float r,
            float g,
            float b
    ) {
        float yCam0 = (float) (worldY0 - cam.y);
        float yCam1 = (float) (worldY1 - cam.y);
        float uv0 = uvForCamY(yCam0, scroll, vBase, depthFar);
        float uv1 = uvForCamY(yCam1, scroll, vBase, depthFar);

        RenderSystem.setShaderColor(r, g, b, alpha);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        appendCylinderRing(
                buffer,
                cx,
                cz,
                radius,
                cam.x,
                cam.z,
                viewRange,
                insideEye,
                scroll,
                uv1,
                uv0,
                yCam0,
                yCam1
        );

        var mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static float uvForCamY(float yCam, float scroll, float vBase, double depthFar) {
        return scroll + vBase + (float) ((depthFar - yCam) * 0.5);
    }

    private static void setupRenderState() {
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        RenderSystem.setShaderTexture(0, FORCEFIELD);
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.polygonOffset(-3.0F, -3.0F);
        RenderSystem.enablePolygonOffset();
        RenderSystem.disableCull();
    }

    private static void teardownRenderState() {
        RenderSystem.enableCull();
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
    }

    private static void appendCylinderRing(
            BufferBuilder buffer,
            double cx,
            double cz,
            double radius,
            double camX,
            double camZ,
            double viewRange,
            boolean insideEye,
            float scroll,
            float uvTop,
            float uvBottom,
            float yBottom,
            float yTop
    ) {
        double circumference = Math.PI * 2.0 * radius;
        int segments = Math.max(MIN_CYLINDER_SEGMENTS, (int) Math.ceil(circumference));
        // World border uses 0.5 UV per block; forcefield tiles every 1.0 UV.
        // Snap total wraps to an integer tile count so 0 and 2π share the same texture phase.
        float tilesAround = Math.max(1f, Math.round((float) (circumference * 0.5)));
        float uvPerRadian = tilesAround / (float) (Math.PI * 2.0);

        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;

            double wx0 = cx + Math.sin(a0) * radius;
            double wz0 = cz + Math.cos(a0) * radius;
            double wx1 = cx + Math.sin(a1) * radius;
            double wz1 = cz + Math.cos(a1) * radius;

            if (!insideEye) {
                double midX = (wx0 + wx1) * 0.5;
                double midZ = (wz0 + wz1) * 0.5;
                if (Math.hypot(midX - camX, midZ - camZ) > viewRange + 32.0) {
                    continue;
                }
            }

            float rx0 = (float) (wx0 - camX);
            float rz0 = (float) (wz0 - camZ);
            float rx1 = (float) (wx1 - camX);
            float rz1 = (float) (wz1 - camZ);

            float u0 = scroll - (float) a0 * uvPerRadian;
            float u1 = scroll - (float) a1 * uvPerRadian;

            buffer.addVertex(rx0, yBottom, rz0).setUv(u0, uvBottom);
            buffer.addVertex(rx0, yTop, rz0).setUv(u0, uvTop);
            buffer.addVertex(rx1, yTop, rz1).setUv(u1, uvTop);
            buffer.addVertex(rx1, yBottom, rz1).setUv(u1, uvBottom);
        }
    }
}
