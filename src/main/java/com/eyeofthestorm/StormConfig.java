package com.eyeofthestorm;

/**
 * Tunables that used to be awkward scoreboard/storage constants in the datapack.
 */
public final class StormConfig {
    private StormConfig() {}

    public static float damagePerBlock = 0.2f;

    /** Blocks outside the wall before storm damage begins (vanilla border safe zone). */
    public static float damageSafeZone = 5f;
    public static double defaultRadius = 100.0;
    public static double defaultSpeed = 0.05;
    /** Random-walk steering noise added to angular velocity each tick (radians/tick). */
    public static double turnRate = 0.0025;
    public static double maxAngularVelocity = 0.04;
    public static double angularDamping = 0.98;

    /** World Y where the storm wall ends. */
    public static float wallTopY = 300f;

    /** Blocks below {@link #wallTopY} where the top cap fades to transparent. */
    public static float wallTopFadeBlocks = 40f;

    /** Per-band opacity multiplier (fog/top cap; texture carries most fill). */
    public static float wallPeakAlpha = 0.45f;

    /** Number of concentric wall copies at increasing radii (1 = single wall only). */
    public static int wallLayerCount = 1;

    /** Outward gap before the first outer shell, in blocks (then each gap scales up). */
    public static float wallLayerSpacingStart = 5f;

    /** Each successive shell gap is multiplied by this (5 → 10 → 20 → 40 …). */
    public static float wallLayerSpacingMultiplier = 2f;

    /** Cumulative outward offset for a shell layer (0 = storm radius boundary). */
    public static double shellRadiusOffset(int layerIndex) {
        if (layerIndex <= 0) {
            return 0.0;
        }
        double offset = 0.0;
        double gap = wallLayerSpacingStart;
        for (int i = 0; i < layerIndex; i++) {
            offset += gap;
            gap *= wallLayerSpacingMultiplier;
        }
        return offset;
    }

    /** World-locked shell radius for the given layer index. */
    public static double shellRadius(double baseRadius, int layerIndex) {
        return baseRadius + shellRadiusOffset(layerIndex);
    }

    /** Cylinder segment count; lower is faster, higher is smoother. */
    public static int wallSegments = 64;

    /** World blocks per texture tile repeat (larger = less visible tiling). */
    public static float wallTextureBlockSize = 100f;

    /** Voronoi texture resolution (power-of-two recommended). */
    public static int wallTextureSize = 512;

    /** Baked texture alpha at lightest cell interiors (after fill+edge bake). */
    public static float wallTextureMinAlpha = 0.38f;

    /** Baked texture alpha at dense cell walls / edges. */
    public static float wallTextureMaxAlpha = 1.0f;

    /** Voronoi cells across one texture tile (integer recommended for seamless wrap). */
    public static float wallVoronoiCellsPerTile = 5f;

    /** Width of dense cell-wall bands in Voronoi space. */
    public static float wallVoronoiEdgeWidth = 0.20f;

    /** W separation between concentric layers in the pseudo-3D field (baked into layer textures). */
    public static float wallVoronoiLayerSeparation = 0.37f;

    /** Storm wall solid tint (RGB 0-255). */
    public static int wallColorR = 255;
    public static int wallColorG = 0;
    public static int wallColorB = 0;

    /** Vignette ramps within this many blocks of the storm wall (vanilla default: 5). */
    public static float vignetteWarningBlocks = 5f;

    /** Inside-wall warning hum only plays within this many blocks of the radius edge. */
    public static double soundInsideRange = 8.0;

    /** No storm audio at all when farther than this many blocks inside the wall. */
    public static double soundSilentInsideBlocks = 20.0;

    /** Outside storm ambience only plays within this many blocks past the wall. */
    public static double soundOutsideRange = 10.0;

    /** High Y anchor for world spawn — respawn logic searches down to surface at storm center. */
    public static int spawnAnchorY = 512;

    public static int syncIntervalTicks = 1;
    public static int spawnUpdateIntervalTicks = 20;
}
