package com.eyeofthestorm.client;

import com.eyeofthestorm.network.StormSyncPayload;

public final class ClientStormState {
    public static boolean initialized;
    public static boolean active;
    public static boolean paused;
    public static double centerX;
    public static double centerY;
    public static double centerZ;
    public static double radius;
    public static float wallSpinDeg;

    private ClientStormState() {}

    public static void update(StormSyncPayload payload) {
        initialized = payload.initialized();
        active = payload.active() && !payload.paused();
        paused = payload.paused();
        centerX = payload.centerX();
        centerY = payload.centerY();
        centerZ = payload.centerZ();
        radius = payload.radius();
        wallSpinDeg = payload.wallSpinDeg();
    }

    public static void reset() {
        initialized = false;
        active = false;
        paused = false;
        centerX = 0.0;
        centerY = 0.0;
        centerZ = 0.0;
        radius = 0.0;
        wallSpinDeg = 0.0f;
    }

    public static boolean shouldRender() {
        return initialized && active && !paused;
    }
}
