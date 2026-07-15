package com.eyeofthestorm.client;

import com.eyeofthestorm.StormConfig;
import net.minecraft.util.Mth;

/**
 * Toroidal 3D Voronoi baked into layer textures at startup.
 * Each cell gets a random interior shade; cell walls (far from seed points) are denser/darker.
 */
public final class StormWallVoronoi {
    private static final long SEED = 0x0156A11CL;

    private StormWallVoronoi() {}

    /** Bakes a seamless 2D slice of the 3D field at {@code w}. Returns density 0..1. */
    public static float[][] bakeSlice(int size, float w) {
        float[][] density = new float[size][size];
        float inv = 1.0f / size;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = (x + 0.5f) * inv;
                float v = (y + 0.5f) * inv;
                density[x][y] = cellDensity(u, v, w);
            }
        }
        return density;
    }

    /**
     * Classic Voronoi shading: all pixels in a cell share a random interior value;
     * boundaries (equidistant from two seeds) get high density like dark connecting walls.
     */
    static float cellDensity(float x, float y, float z) {
        float scale = Math.max(1.0f, StormConfig.wallVoronoiCellsPerTile);
        int periodCells = Math.max(1, Math.round(scale));
        float period = periodCells;
        x *= period;
        y *= period;
        z *= period;

        float f1 = Float.MAX_VALUE;
        float f2 = Float.MAX_VALUE;
        int nearestCx = 0;
        int nearestCy = 0;
        int nearestCz = 0;
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        int iz = Mth.floor(z);

        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = ix + dx;
                    int cy = iy + dy;
                    int cz = iz + dz;

                    float px = cx + hash01(wrapCell(cx, periodCells), wrapCell(cy, periodCells), wrapCell(cz, periodCells), 0);
                    float py = cy + hash01(wrapCell(cx, periodCells), wrapCell(cy, periodCells), wrapCell(cz, periodCells), 1);
                    float pz = cz + hash01(wrapCell(cx, periodCells), wrapCell(cy, periodCells), wrapCell(cz, periodCells), 2);

                    float distSq = toroidalDistSq(x, y, z, px, py, pz, period);
                    if (distSq < f1) {
                        f2 = f1;
                        f1 = distSq;
                        nearestCx = cx;
                        nearestCy = cy;
                        nearestCz = cz;
                    } else if (distSq < f2) {
                        f2 = distSq;
                    }
                }
            }
        }

        float cellRoll = hash01(wrapCell(nearestCx, periodCells), wrapCell(nearestCy, periodCells), wrapCell(nearestCz, periodCells), 7);
        // Every cell has visible fill; edges stack extra density on top (not replacing fill).
        float fill = Mth.lerp(0.32f, 0.52f, cellRoll);

        float edgeDist = (float) Math.sqrt(f2) - (float) Math.sqrt(f1);
        float edgeWidth = StormConfig.wallVoronoiEdgeWidth;
        float edgeFactor = 1.0f - smoothstep(0.0f, edgeWidth, edgeDist);
        float edgeBoost = Mth.lerp(0.70f, 0.90f, hash01(
                wrapCell(nearestCx, periodCells),
                wrapCell(nearestCy, periodCells),
                wrapCell(nearestCz, periodCells),
                8
        ));

        return Mth.clamp(fill + edgeFactor * edgeBoost * (1.0f - fill), 0.0f, 1.0f);
    }

    private static float toroidalDistSq(
            float x,
            float y,
            float z,
            float px,
            float py,
            float pz,
            float period
    ) {
        float dx = wrapDelta(x - px, period);
        float dy = wrapDelta(y - py, period);
        float dz = wrapDelta(z - pz, period);
        return dx * dx + dy * dy + dz * dz;
    }

    private static float wrapDelta(float d, float period) {
        d /= period;
        d -= Mth.floor(d);
        if (d > 0.5f) {
            d -= 1.0f;
        }
        return d * period;
    }

    private static int wrapCell(int c, int period) {
        return Math.floorMod(c, period);
    }

    private static float hash01(int x, int y, int z, int channel) {
        long h = SEED;
        h = h * 6364136223846793005L + (long) x * 374761393L;
        h = h * 6364136223846793005L + (long) y * 668265263L;
        h = h * 6364136223846793005L + (long) z * 2147483647L;
        h = h * 6364136223846793005L + channel * 1442695040888963407L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h & 0xFFFFFL) / (float) 0xFFFFF;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
