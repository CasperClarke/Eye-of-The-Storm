package com.eyeofthestorm.client;

import com.eyeofthestorm.StormConfig;
import net.minecraft.util.Mth;

/**
 * Shared CPU bake for storm wall alpha (no NativeImage / GL).
 * Used by {@link StormWallPreviewMain} to bake the atlas asset loaded by {@link StormWallTextures}.
 */
public final class StormWallBake {
    private static final int ALPHA_BLUR_RADIUS = 1;

    private StormWallBake() {}

    /** Toroidal-blurred alpha tile at normalized W. Values in {@code [0, 1]}. */
    public static float[][] bakeAlpha(int size, float w) {
        float[][] density = StormWallVoronoi.bakeSlice(size, w);
        float minA = StormConfig.wallTextureMinAlpha;
        float maxA = StormConfig.wallTextureMaxAlpha;

        float[][] alpha = new float[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float n = Mth.clamp(density[x][y], 0.0f, 1.0f);
                alpha[x][y] = minA + n * (maxA - minA);
            }
        }
        return blurAlphaToroidal(alpha, size, ALPHA_BLUR_RADIUS);
    }

    public static int toByte(float alpha) {
        return Mth.clamp((int) (alpha * 255.0f + 0.5f), 0, 255);
    }

    /**
     * Continuity of the unit-cube period: {@code f(0)-f(1)} on each axis should be ~0.
     * Opposite texel rows differing is normal (they are neighbors across the wrap, not duplicates).
     */
    public static float maxPeriodError(int size, float wSample) {
        float inv = 1.0f / size;
        float max = 0.0f;
        for (int i = 0; i < size; i++) {
            float t = (i + 0.5f) * inv;
            max = Math.max(max, Math.abs(
                    StormWallVoronoi.cellDensity(0.0f, t, wSample) - StormWallVoronoi.cellDensity(1.0f, t, wSample)));
            max = Math.max(max, Math.abs(
                    StormWallVoronoi.cellDensity(t, 0.0f, wSample) - StormWallVoronoi.cellDensity(t, 1.0f, wSample)));
            max = Math.max(max, Math.abs(
                    StormWallVoronoi.cellDensity(t, t, 0.0f) - StormWallVoronoi.cellDensity(t, t, 1.0f)));
        }
        return max;
    }

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
}
