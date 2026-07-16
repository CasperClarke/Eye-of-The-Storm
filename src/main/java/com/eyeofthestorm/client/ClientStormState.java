package com.eyeofthestorm.client;

import com.eyeofthestorm.network.StormSyncPayload;
import com.eyeofthestorm.storm.StormFourierPath;
import net.minecraft.util.RandomSource;

public final class ClientStormState {
    public static boolean initialized;
    public static boolean active;
    public static boolean paused;
    public static double centerX;
    public static double centerY;
    public static double centerZ;
    public static double radius;
    /** Instantaneous linear speed in blocks/tick (after speed curve). */
    public static double speed;
    public static float wallSpinDeg;
    public static float yawRad;

    public static long pathSeed;
    public static double pathParam;
    public static double pathOriginX;
    public static double pathOriginZ;
    public static double speedPhase;
    public static double peakSpeed;
    public static int pathCircleCount;
    public static double pathRadiusScale;
    public static double speedPhaseRatePerTick;

    public static StormFourierPath.Circle[] circles = new StormFourierPath.Circle[0];

    private static long cachedPathSeed;
    private static int cachedCircleCount = -1;
    private static double cachedRadiusScale = Double.NaN;

    private ClientStormState() {}

    public static void update(StormSyncPayload payload) {
        initialized = payload.initialized();
        active = payload.active() && !payload.paused();
        paused = payload.paused();
        centerX = payload.centerX();
        centerY = payload.centerY();
        centerZ = payload.centerZ();
        radius = payload.radius();
        speed = payload.speed();
        wallSpinDeg = payload.wallSpinDeg();
        yawRad = payload.yawRad();

        long prevSeed = pathSeed;
        int prevCount = pathCircleCount;
        double prevScale = pathRadiusScale;

        pathSeed = payload.pathSeed();
        pathParam = payload.pathParam();
        pathOriginX = payload.pathOriginX();
        pathOriginZ = payload.pathOriginZ();
        speedPhase = payload.speedPhase();
        peakSpeed = payload.peakSpeed();
        pathCircleCount = payload.pathCircleCount();
        pathRadiusScale = payload.pathRadiusScale();
        speedPhaseRatePerTick = payload.speedPhaseRatePerTick();
        rebuildCirclesIfNeeded();

        // Drop the old radar trail when Apply/regen replaces the path identity.
        if (prevSeed != pathSeed
                || prevCount != pathCircleCount
                || Double.compare(prevScale, pathRadiusScale) != 0) {
            StormPathHistory.reset();
        }

        if (shouldRender()) {
            StormPathHistory.record(centerX, centerZ);
        }
    }

    public static void rebuildCirclesIfNeeded() {
        if (pathCircleCount <= 0) {
            circles = new StormFourierPath.Circle[0];
            cachedCircleCount = 0;
            return;
        }
        if (circles.length > 0
                && cachedPathSeed == pathSeed
                && cachedCircleCount == pathCircleCount
                && Double.compare(cachedRadiusScale, pathRadiusScale) == 0) {
            return;
        }
        circles = StormFourierPath.generate(
                RandomSource.create(pathSeed),
                pathCircleCount,
                pathRadiusScale
        );
        cachedPathSeed = pathSeed;
        cachedCircleCount = pathCircleCount;
        cachedRadiusScale = pathRadiusScale;
    }

    public static void reset() {
        initialized = false;
        active = false;
        paused = false;
        centerX = 0.0;
        centerY = 0.0;
        centerZ = 0.0;
        radius = 0.0;
        speed = 0.0;
        wallSpinDeg = 0.0f;
        yawRad = 0.0f;
        pathSeed = 0L;
        pathParam = 0.0;
        pathOriginX = 0.0;
        pathOriginZ = 0.0;
        speedPhase = 0.0;
        peakSpeed = 0.0;
        pathCircleCount = 0;
        pathRadiusScale = 0.0;
        speedPhaseRatePerTick = 0.0;
        circles = new StormFourierPath.Circle[0];
        cachedPathSeed = 0L;
        cachedCircleCount = -1;
        cachedRadiusScale = Double.NaN;
        StormPathHistory.reset();
    }

    public static boolean shouldRender() {
        return initialized && active && !paused;
    }

    public static boolean hasPath() {
        return initialized && circles != null && circles.length > 0;
    }
}
