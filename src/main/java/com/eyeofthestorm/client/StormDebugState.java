package com.eyeofthestorm.client;

public final class StormDebugState {
    public static boolean wireframeEnabled;

    private StormDebugState() {}

    public static void toggleWireframe() {
        wireframeEnabled = !wireframeEnabled;
    }

    public static void reset() {
        wireframeEnabled = false;
    }
}
