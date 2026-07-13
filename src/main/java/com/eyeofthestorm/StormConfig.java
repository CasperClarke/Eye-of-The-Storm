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

    /** World Y where the storm wall fully fades out. */
    public static float wallTopY = 300f;

    /** World Y where vertical transparency begins. */
    public static float wallFadeStartY = 240f;

    /** Peak wall opacity below the fade band (0-1). */
    public static float wallPeakAlpha = 0.82f;

    /** Storm wall tint over the forcefield texture (RGB 0-255). */
    public static int wallColorR = 95;
    public static int wallColorG = 155;
    public static int wallColorB = 255;

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
