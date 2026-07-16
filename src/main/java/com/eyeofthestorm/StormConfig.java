package com.eyeofthestorm;

/**
 * Tunables that used to be awkward scoreboard/storage constants in the datapack.
 */
public final class StormConfig {
    private StormConfig() {}

    public static float damagePerBlock = 0.2f;

    /** Blocks outside the wall before storm damage begins (vanilla border safe zone). */
    public static float damageSafeZone = 5f;
    public static double defaultRadius = 1000.0;

    /**
     * Default campaign tuning: cover roughly this much path length in this many hours.
     * Instantaneous speed is {@code peak · ((1-cos(u-0.5))/2)^2}; that scale averages to 3/8,
     * so peak is chosen as {@code distance / (meanScale · ticks)}.
     */
    public static final double TARGET_PATH_BLOCKS = 2_000_000.0;
    public static final double TARGET_DURATION_HOURS = 100.0;
    /** Mean of {@code ((1-cos(u-0.5))/2)^2} over one period. */
    public static final double SPEED_SCALE_MEAN = 0.375;

    /**
     * Peak linear speed in blocks/tick (~0.741 → ~5.56 blocks/s average → 2M blocks / 100h).
     * Per-storm override lives on {@code StormData.speed}.
     */
    public static double defaultSpeed =
            TARGET_PATH_BLOCKS / (SPEED_SCALE_MEAN * TARGET_DURATION_HOURS * 3600.0 * 20.0);

    /** How many epicycles to sample when generating a path. */
    public static int pathCircleCount = 15;

    /**
     * Overall amplitude scale for harmonic radii {@code r_k = scale / k} (world blocks).
     * With integer frequencies the shape closes every {@code 2π} in path parameter; loop arc
     * length is computed from the live circles via {@code StormFourierPath.estimateLoopLength}.
     * Keep that above {@link #TARGET_PATH_BLOCKS} so the storm does not lap during the campaign.
     */
    public static double pathRadiusScale = 170_000.0;

    /**
     * How fast the speed-function phase {@code u} advances per tick.
     * Speed scale is {@code ((1-cos(u-0.5))/2)^2}; period is {@code 2π / rate} ticks (~30 minutes).
     */
    public static double speedPhaseRatePerTick = (Math.PI * 2.0) / (30.0 * 60.0 * 20.0);

    /** Floor on |z'(t)| so parameter steps stay finite near cusps. */
    public static double pathDerivEpsilon = 1e-4;

    /** World Y where the storm wall ends. */
    public static float wallTopY = 300f;

    /** Blocks below {@link #wallTopY} where the top cap fades to transparent. */
    public static float wallTopFadeBlocks = 40f;

    /** Per-band opacity multiplier (fog/top cap; texture carries most fill). */
    public static float wallPeakAlpha = 0.45f;

    /** Cylinder segment count; lower is faster, higher is smoother. */
    public static int wallSegments = 64;

    /** World blocks per texture tile repeat (larger = less visible tiling). */
    public static float wallTextureBlockSize = 100f;

    /** Tile resolution used when baking {@code wall_morph_atlas.png} (runtime loads the asset). */
    public static int wallTextureSize = 256;

    /** Baked texture alpha at lightest cell interiors (after fill+edge bake). */
    public static float wallTextureMinAlpha = 0.38f;

    /** Baked texture alpha at dense cell walls / edges. */
    public static float wallTextureMaxAlpha = 1.0f;

    /** Voronoi cells across one texture tile (integer recommended for seamless wrap). */
    public static float wallVoronoiCellsPerTile = 5f;

    /** Width of dense cell-wall bands in Voronoi space. */
    public static float wallVoronoiEdgeWidth = 0.20f;

    /**
     * W-slices in the pre-baked morph atlas (must match {@code wall_morph_atlas.png}).
     * Prefer matching anim frame count for even W steps.
     */
    public static int wallVoronoiMorphSliceCount = 128;

    /** Seconds for one full loop through all morph slices. */
    public static float wallVoronoiMorphCycleSeconds = 32f;

    /**
     * World-unit thickness of the depth-buffer contact highlight
     * (where the wall nearly intersects terrain / caves / blocks).
     */
    public static float wallContactWidth = 1.75f;

    /** Extra emissive strength of the contact rim (0 = off). */
    public static float wallContactStrength = 1.15f;

    /** Storm wall solid tint (RGB 0-255). */
    public static int wallColorR = 255;
    public static int wallColorG = 0;
    public static int wallColorB = 0;

    /** Vignette ramps within this many blocks of the storm wall (vanilla default: 5). */
    public static float vignetteWarningBlocks = 5f;

    /**
     * Full-screen storm veil when outside the wall (0–1 peak opacity).
     * Soft translucent wash; morph atlas only gently modulates it.
     */
    public static float outsideOverlayAlpha = 0.32f;

    /** Blocks inside the wall where the storm veil begins ramping (full at the wall & outside). */
    public static float outsideOverlayFadeBlocks = 3f;

    /** How many Voronoi tiles span the screen width for the outside veil. */
    public static float outsideOverlayTiles = 2.5f;

    /**
     * How strongly the morph pattern modulates the veil (0 = flat tint, 1 = full density map).
     * Keep low — high values read as opaque Voronoi splatters.
     */
    public static float outsideOverlayContrast = 0.62f;

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
