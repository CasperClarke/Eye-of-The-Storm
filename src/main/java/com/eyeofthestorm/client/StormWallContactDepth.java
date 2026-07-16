package com.eyeofthestorm.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Copies scene depth so the wall shader can highlight membrane contact.
 * Captured after clouds (AFTER_WEATHER) so Fancy clouds in the main target are included.
 * On Fabulous, clouds use a separate target — that depth is exposed via {@link #cloudTexture()}.
 */
public final class StormWallContactDepth {
    @Nullable
    private static RenderTarget depthCopy;
    private static final DepthTexture DEPTH_VIEW = new DepthTexture();
    private static final DepthTexture CLOUD_DEPTH_VIEW = new DepthTexture();
    private static boolean capturedThisFrame;
    private static boolean cloudDepthAvailable;

    private StormWallContactDepth() {}

    /** Call once per frame after clouds have been rendered (AFTER_WEATHER). */
    public static void capture(Minecraft mc) {
        RenderTarget main = mc.getMainRenderTarget();
        ensureSize(main.width, main.height);
        depthCopy.copyDepthFrom(main);
        DEPTH_VIEW.setGlId(depthCopy.getDepthTextureId());
        // copyDepthFrom leaves FBO 0 bound — restore the game's main target.
        main.bindWrite(false);

        cloudDepthAvailable = false;
        CLOUD_DEPTH_VIEW.setGlId(-1);
        // Fabulous draws clouds into a separate target; Fancy writes them into main already.
        RenderTarget clouds = mc.levelRenderer.getCloudsTarget();
        if (clouds != null && clouds.useDepth) {
            CLOUD_DEPTH_VIEW.setGlId(clouds.getDepthTextureId());
            cloudDepthAvailable = CLOUD_DEPTH_VIEW.hasGlId();
        }

        capturedThisFrame = true;
    }

    public static boolean hasCapture() {
        return capturedThisFrame && depthCopy != null && DEPTH_VIEW.hasGlId();
    }

    /** Fabulous-only separate cloud depth; Fancy clouds are already in {@link #texture()}. */
    public static boolean hasCloudCapture() {
        return hasCapture() && cloudDepthAvailable;
    }

    public static AbstractTexture texture() {
        return DEPTH_VIEW;
    }

    public static AbstractTexture cloudTexture() {
        return CLOUD_DEPTH_VIEW;
    }

    public static int width() {
        return depthCopy != null ? depthCopy.width : 1;
    }

    public static int height() {
        return depthCopy != null ? depthCopy.height : 1;
    }

    private static void ensureSize(int width, int height) {
        if (depthCopy != null && depthCopy.width == width && depthCopy.height == height) {
            return;
        }
        if (depthCopy != null) {
            depthCopy.destroyBuffers();
        }
        depthCopy = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        depthCopy.setClearColor(0f, 0f, 0f, 0f);
        depthCopy.clear(Minecraft.ON_OSX);
    }

    /** Borrows an existing GL depth texture id without owning/deleting it. */
    private static final class DepthTexture extends AbstractTexture {
        void setGlId(int glId) {
            this.id = glId;
        }

        boolean hasGlId() {
            return this.id > 0;
        }

        @Override
        public void load(ResourceManager resourceManager) throws IOException {
            // External depth attachment — nothing to load.
        }

        @Override
        public void releaseId() {
            // Do not delete the RenderTarget's depth texture.
            this.id = -1;
        }
    }
}
