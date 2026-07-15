package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.StormConfig;
import com.eyeofthestorm.storm.StormBoundaryMath;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Storm wall as a single cylinder shell tinted to a solid color.
 * 16-block vertical bands for render-distance fog culling; top cap fades near wallTopY.
 */
@EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT)
public final class StormWallRenderer {
    private static final double SOLID_BAND_HEIGHT = 16.0;
    private static final int DEBUG_WIRE_SEGMENTS = 32;
    private static float cachedFogStart;
    private static float cachedFogEnd;
    private static FogShape cachedFogShape;

    private StormWallRenderer() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        // Soft shader always. Iris skips unknown shaders only while the world pass
        // is active; AFTER_LEVEL is after Iris composite/final, so the soft program
        // can blend onto the finished frame without reverting to discard shaders.
        boolean irisPack = IrisCompat.isShaderPackInUse();
        RenderLevelStageEvent.Stage expected = irisPack
                ? RenderLevelStageEvent.Stage.AFTER_LEVEL
                : RenderLevelStageEvent.Stage.AFTER_WEATHER;
        if (event.getStage() != expected) {
            return;
        }
        if (!ClientStormState.shouldRender()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        if (StormWallShader.get() == null) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        double cx = ClientStormState.centerX;
        double cz = ClientStormState.centerZ;
        double radius = ClientStormState.radius;

        float r = StormConfig.wallColorR / 255f;
        float g = StormConfig.wallColorG / 255f;
        float b = StormConfig.wallColorB / 255f;

        if (irisPack) {
            // Composite wrote the final image here; draw soft wall on top of it.
            mc.getMainRenderTarget().bindWrite(false);
        }

        // Iris final pass leaves HUD/identity matrices. Vertices are camera-relative,
        // so restore the world view + projection from this event or the wall sticks to the camera.
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        modelViewStack.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);

        setupRenderState(r, g, b);
        ensureTexturesRegistered();
        cacheFogState(mc);

        double depthFar = mc.gameRenderer.getDepthFar();
        double viewWorldMin = Math.max(mc.level.getMinBuildHeight(), cam.y - depthFar);
        double viewWorldMax = Math.min(StormConfig.wallTopY, cam.y + depthFar);
        if (viewWorldMax <= viewWorldMin) {
            teardownRenderState();
            restoreMatrices(modelViewStack, previousProjection);
            return;
        }

        drawShell(cx, cz, radius, cam, viewWorldMin, viewWorldMax, r, g, b);

        teardownRenderState();
        restoreMatrices(modelViewStack, previousProjection);

        if (StormDebugState.wireframeEnabled) {
            drawDebugWireframe(cx, cz, radius, cam, viewWorldMin, viewWorldMax);
        }
    }

    private static void restoreMatrices(Matrix4fStack modelViewStack, Matrix4f previousProjection) {
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
    }

    /** One draw call; per-vertex fog alpha interpolates across each quad. */
    private static void drawShell(
            double cx,
            double cz,
            double radius,
            Vec3 cam,
            double viewWorldMin,
            double viewWorldMax,
            float r,
            float g,
            float b
    ) {
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR
        );
        forEachVerticalBand(viewWorldMin, viewWorldMax, (wy0, wy1) -> {
            float yCam0 = (float) (wy0 - cam.y);
            float yCam1 = (float) (wy1 - cam.y);
            appendCylinderRing(buffer, cx, cz, radius, cam, wy0, wy1, yCam0, yCam1);
        });

        var mesh = buffer.build();
        if (mesh != null) {
            ShaderInstance shader = StormWallShader.get();
            float stormRelX = (float) (cx - cam.x);
            float stormRelZ = (float) (cz - cam.z);
            StormWallTextures.applyMorphToShader(shader, stormRelX, stormRelZ);
            RenderSystem.setShaderColor(r, g, b, 1.0F);
            BufferUploader.drawWithShader(mesh);
        }
    }

    @FunctionalInterface
    private interface ShellSliceVisitor {
        void accept(double worldY0, double worldY1);
    }

    /** 16-block bands from view min to wall top; fog culls distant bands per vertex. */
    private static void forEachVerticalBand(
            double viewWorldMin,
            double viewWorldMax,
            ShellSliceVisitor visitor
    ) {
        double wallTop = Math.min(StormConfig.wallTopY, viewWorldMax);
        if (wallTop <= viewWorldMin) {
            return;
        }

        for (double bandBottom = viewWorldMin; bandBottom < wallTop; bandBottom += SOLID_BAND_HEIGHT) {
            double bandTop = Math.min(bandBottom + SOLID_BAND_HEIGHT, wallTop);
            visitor.accept(bandBottom, bandTop);
        }
    }

    private static void ensureTexturesRegistered() {
        if (!StormWallTextures.isRegistered()) {
            StormWallTextures.register();
        }
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

    /** Full opacity below the top fade band; smooth falloff to zero at wallTopY. */
    private static float topCapAlpha(float worldY) {
        if (worldY >= StormConfig.wallTopY) {
            return 0f;
        }
        float fadeStart = StormConfig.wallTopY - StormConfig.wallTopFadeBlocks;
        if (worldY <= fadeStart) {
            return StormConfig.wallPeakAlpha;
        }
        float t = (worldY - fadeStart) / Math.max(1f, StormConfig.wallTopFadeBlocks);
        float smooth = t * t * (3f - 2f * t);
        return StormConfig.wallPeakAlpha * (1f - smooth);
    }

    private static float vertexAlpha(float worldY, float x, float y, float z) {
        return topCapAlpha(worldY) * vanillaFogFade(x, y, z);
    }

    /** Match {@link net.minecraft.client.renderer.FogRenderer} terrain fog range. */
    private static void cacheFogState(Minecraft mc) {
        float renderReach = Math.max(32.0F, mc.gameRenderer.getRenderDistance());
        float span = Mth.clamp(renderReach / 10.0F, 4.0F, 64.0F);
        cachedFogEnd = renderReach;
        cachedFogStart = renderReach - span;
        cachedFogShape = FogShape.CYLINDER;
    }

    private static void setupRenderState(float r, float g, float b) {
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        RenderSystem.depthMask(false);
        // Soft alpha (no discard). Safe under Iris when drawn at AFTER_LEVEL.
        RenderSystem.setShader(StormWallShader::get);
        RenderSystem.setShaderColor(r, g, b, 1.0F);
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
            Vec3 cam,
            double worldYBottom,
            double worldYTop,
            float yBottom,
            float yTop
    ) {
        double camX = cam.x;
        double camZ = cam.z;
        int segments = segmentCount(radius);
        double blockSize = Math.max(1.0, StormConfig.wallTextureBlockSize);

        double circumference = Math.PI * 2.0 * radius;
        double repeatsAround = Math.max(1.0, Math.round(circumference / blockSize));
        double vBottom = worldYBottom / blockSize;
        double vTop = worldYTop / blockSize;

        for (int i = 0; i < segments; i++) {
            double t0 = (double) i / segments;
            double t1 = (double) (i + 1) / segments;
            double a0 = Math.PI * 2.0 * t0;
            double a1 = Math.PI * 2.0 * t1;

            double wx0 = cx + Math.sin(a0) * radius;
            double wz0 = cz + Math.cos(a0) * radius;
            double wx1 = cx + Math.sin(a1) * radius;
            double wz1 = cz + Math.cos(a1) * radius;

            float rx0 = (float) (wx0 - camX);
            float rz0 = (float) (wz0 - camZ);
            float rx1 = (float) (wx1 - camX);
            float rz1 = (float) (wz1 - camZ);

            float u0 = (float) (t0 * repeatsAround);
            float u1 = (float) (t1 * repeatsAround);
            float vf = (float) vBottom;
            float vt = (float) vTop;

            float fog00 = vertexAlpha((float) worldYBottom, rx0, yBottom, rz0);
            float fog01 = vertexAlpha((float) worldYTop, rx0, yTop, rz0);
            float fog11 = vertexAlpha((float) worldYTop, rx1, yTop, rz1);
            float fog10 = vertexAlpha((float) worldYBottom, rx1, yBottom, rz1);

            addVertex(buffer, rx0, yBottom, rz0, u0, vf, fog00);
            addVertex(buffer, rx0, yTop, rz0, u0, vt, fog01);
            addVertex(buffer, rx1, yTop, rz1, u1, vt, fog11);
            addVertex(buffer, rx1, yBottom, rz1, u1, vf, fog10);
        }
    }

    private static void addVertex(
            BufferBuilder buffer,
            float x,
            float y,
            float z,
            float u,
            float v,
            float fogAlpha
    ) {
        int a = Mth.clamp((int) (fogAlpha * 255.0f + 0.5f), 0, 255);
        buffer.addVertex(x, y, z)
                .setUv(u, v)
                .setColor(255, 255, 255, a);
    }

    /** Smooth fog falloff; extends slightly past fog end so quads fade out instead of clipping. */
    private static float vanillaFogFade(float x, float y, float z) {
        float distance;
        if (cachedFogShape == FogShape.CYLINDER) {
            distance = Math.max((float) Math.hypot(x, z), Math.abs(y));
        } else {
            distance = (float) Math.sqrt(x * x + y * y + z * z);
        }

        float span = cachedFogEnd - cachedFogStart;
        if (span <= 0.001f) {
            return distance <= cachedFogEnd ? 1.0F : 0.0F;
        }

        float fadeStart = cachedFogStart;
        float fadeEnd = cachedFogEnd + span * 0.25F;

        if (distance <= fadeStart) {
            return 1.0F;
        }
        if (distance >= fadeEnd) {
            return 0.0F;
        }

        float t = (distance - fadeStart) / (fadeEnd - fadeStart);
        return 1.0F - t * t * (3.0F - 2.0F * t);
    }

    private static int segmentCount(double radius) {
        return Math.max(8, StormConfig.wallSegments);
    }

    private static void drawDebugWireframe(
            double cx,
            double cz,
            double baseRadius,
            Vec3 cam,
            double viewWorldMin,
            double viewWorldMax
    ) {
        setupDebugRenderState();

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES,
                DefaultVertexFormat.POSITION_COLOR
        );

        forEachVerticalBand(viewWorldMin, viewWorldMax, (wy0, wy1) -> {
            float midY = (float) ((wy0 + wy1) * 0.5);
            float alpha = topCapAlpha(midY);
            appendShellWireframe(
                    buffer, cx, cz, baseRadius, cam, wy0, wy1,
                    0.95f, 0.92f, 0.2f, 0.9f * alpha
            );
        });

        var mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        teardownDebugRenderState();
    }

    private static void appendShellWireframe(
            BufferBuilder buffer,
            double cx,
            double cz,
            double radius,
            Vec3 cam,
            double worldY0,
            double worldY1,
            float cr,
            float cg,
            float cb,
            float ca
    ) {
        double camX = cam.x;
        double camY = cam.y;
        double camZ = cam.z;
        float y0 = (float) (worldY0 - camY);
        float y1 = (float) (worldY1 - camY);

        for (int i = 0; i < DEBUG_WIRE_SEGMENTS; i++) {
            double a0 = Math.PI * 2.0 * i / DEBUG_WIRE_SEGMENTS;
            double a1 = Math.PI * 2.0 * (i + 1) / DEBUG_WIRE_SEGMENTS;

            double wx0 = cx + Math.sin(a0) * radius;
            double wz0 = cz + Math.cos(a0) * radius;
            double wx1 = cx + Math.sin(a1) * radius;
            double wz1 = cz + Math.cos(a1) * radius;

            float rx0 = (float) (wx0 - camX);
            float rz0 = (float) (wz0 - camZ);
            float rx1 = (float) (wx1 - camX);
            float rz1 = (float) (wz1 - camZ);

            addDebugLine(buffer, rx0, y0, rz0, rx1, y0, rz1, cr, cg, cb, ca);
            addDebugLine(buffer, rx0, y1, rz0, rx1, y1, rz1, cr, cg, cb, ca);
            addDebugLine(buffer, rx0, y0, rz0, rx0, y1, rz0, cr, cg, cb, ca);
        }
    }

    private static void setupDebugRenderState() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void teardownDebugRenderState() {
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void addDebugLine(
            BufferBuilder buffer,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float r,
            float g,
            float b,
            float a
    ) {
        buffer.addVertex(x0, y0, z0).setColor(r, g, b, a);
        buffer.addVertex(x1, y1, z1).setColor(r, g, b, a);
    }
}
