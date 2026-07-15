package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.StormConfig;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

/** Procedurally generated storm wall textures (baked once at startup). */
public final class StormWallTextures {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int ALPHA_BLUR_RADIUS = 1;

    public static final ResourceLocation ORGANIC =
            ResourceLocation.fromNamespaceAndPath(EyeOfTheStormMod.MOD_ID, "storm_wall_organic");

    private static ResourceLocation[] layerTextures;
    private static boolean registered;

    private StormWallTextures() {}

    public static boolean isRegistered() {
        return registered;
    }

    public static ResourceLocation forLayer(int layerIndex) {
        if (layerTextures != null && layerIndex >= 0 && layerIndex < layerTextures.length) {
            return layerTextures[layerIndex];
        }
        return ORGANIC;
    }

    public static void register() {
        if (registered) {
            return;
        }

        int size = Math.max(64, StormConfig.wallTextureSize);
        int layers = Math.max(1, StormConfig.wallLayerCount);
        layerTextures = new ResourceLocation[layers];
        var textureManager = Minecraft.getInstance().getTextureManager();

        for (int layer = 0; layer < layers; layer++) {
            float wSlice = layer * StormConfig.wallVoronoiLayerSeparation;
            NativeImage image = generateVoronoiSlice(size, wSlice);
            ResourceLocation id = layer == 0
                    ? ORGANIC
                    : ResourceLocation.fromNamespaceAndPath(
                            EyeOfTheStormMod.MOD_ID,
                            "storm_wall_organic_" + layer
                    );
            textureManager.register(id, new RepeatingDynamicTexture(image));
            layerTextures[layer] = id;
        }

        registered = true;
        LOGGER.debug(
                "Registered {} storm wall Voronoi layer textures ({}x{})",
                layers,
                size,
                size
        );
    }

    /**
     * Voronoi density in the alpha channel; RGB white so tint applies cleanly.
     * Fog distance fade is applied separately via float {@code ColorModulator} alpha.
     */
    static NativeImage generateVoronoiSlice(int size, float wSlice) {
        float[][] density = StormWallVoronoi.bakeSlice(size, wSlice);
        float minA = StormConfig.wallTextureMinAlpha;
        float maxA = StormConfig.wallTextureMaxAlpha;

        float[][] alpha = new float[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float n = Mth.clamp(density[x][y], 0.0f, 1.0f);
                alpha[x][y] = minA + n * (maxA - minA);
            }
        }

        alpha = blurAlphaToroidal(alpha, size, ALPHA_BLUR_RADIUS);

        NativeImage image = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int a = Mth.clamp((int) (alpha[x][y] * 255.0f + 0.5f), 0, 255);
                image.setPixelRGBA(x, y, colorAbgr(a, 255, 255, 255));
            }
        }
        return image;
    }

    /** Small toroidal blur on alpha before 8-bit texture upload. */
    private static float[][] blurAlphaToroidal(float[][] source, int size, int radius) {
        float[][] out = new float[size][size];
        int diam = radius * 2 + 1;
        float inv = 1.0f / (diam * diam);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float sum = 0.0f;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int sx = Math.floorMod(x + dx, size);
                        int sy = Math.floorMod(y + dy, size);
                        sum += source[sx][sy];
                    }
                }
                out[x][y] = sum * inv;
            }
        }
        return out;
    }

    /** Legacy chunky pixel noise (kept for the Python preview script). */
    static NativeImage generateOrganicPixelArt(int size) {
        int cell = 2;
        int grid = size / cell;
        java.util.Random random = new java.util.Random(0x0156A11CL);
        float[][] cells = new float[grid][grid];
        for (int gy = 0; gy < grid; gy++) {
            for (int gx = 0; gx < grid; gx++) {
                float base = random.nextFloat();
                float cluster = random.nextFloat() > 0.72f ? 0.35f : 0.0f;
                cells[gx][gy] = Mth.clamp(base * 0.85f + cluster, 0.0f, 1.0f);
            }
        }

        NativeImage image = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int gx = x / cell;
                int gy = y / cell;
                float n = cells[gx][gy];

                float lx = (x % cell) / (float) cell;
                float ly = (y % cell) / (float) cell;
                boolean crack = lx < 0.12f || lx > 0.88f || ly < 0.12f || ly > 0.88f;

                float alpha = 0.28f + n * 0.62f;
                if (crack) {
                    alpha = Math.min(1.0f, alpha + 0.18f);
                }

                int brightness = 170 + (int) (n * 85);
                int a = (int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
                image.setPixelRGBA(x, y, colorAbgr(a, brightness, brightness, brightness));
            }
        }

        return image;
    }

    private static int colorAbgr(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    /** GL_REPEAT + linear filtering so GPU blends between alpha texels. */
    private static final class RepeatingDynamicTexture extends DynamicTexture {
        RepeatingDynamicTexture(NativeImage image) {
            super(image);
        }

        @Override
        public void upload() {
            super.upload();
            bind();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
    }
}
