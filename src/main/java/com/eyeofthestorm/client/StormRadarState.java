package com.eyeofthestorm.client;

public final class StormRadarState {
    public static boolean enabled;

    private StormRadarState() {}

    public static void toggle() {
        enabled = !enabled;
    }

    public static void reset() {
        enabled = false;
    }
}
