package com.eyeofthestorm.client;

/** Client radar open/closed state, persisted via {@link StormRadarConfig}. */
public final class StormRadarState {
    private StormRadarState() {}

    public static boolean enabled() {
        return StormRadarConfig.open();
    }

    public static void toggle() {
        StormRadarConfig.setOpen(!StormRadarConfig.open());
    }
}
