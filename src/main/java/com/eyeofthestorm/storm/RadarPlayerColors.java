package com.eyeofthestorm.storm;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side map of player UUID → chosen radar marker RGB. */
public final class RadarPlayerColors {
    private static final Map<UUID, int[]> COLORS = new ConcurrentHashMap<>();
    private static final int[] DEFAULT_COLOR = {255, 255, 255};

    private RadarPlayerColors() {}

    public static void set(UUID uuid, int r, int g, int b) {
        COLORS.put(uuid, new int[] {
                clampChannel(r),
                clampChannel(g),
                clampChannel(b)
        });
    }

    public static int[] get(UUID uuid) {
        return COLORS.getOrDefault(uuid, DEFAULT_COLOR);
    }

    public static void remove(UUID uuid) {
        COLORS.remove(uuid);
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
