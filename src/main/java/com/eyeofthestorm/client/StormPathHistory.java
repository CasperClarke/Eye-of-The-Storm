package com.eyeofthestorm.client;

import net.minecraft.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Recent storm center positions for the radar path trail.
 * Samples are taken on a fixed time interval and expire after a time window.
 */
public final class StormPathHistory {
    private static final long RECORD_INTERVAL_MS = 250L;

    private static final List<Sample> SAMPLES = new ArrayList<>();

    private StormPathHistory() {}

    public static void record(double x, double z) {
        long now = Util.getMillis();
        prune(now);

        if (!SAMPLES.isEmpty()) {
            Sample last = SAMPLES.get(SAMPLES.size() - 1);
            if (now - last.timeMs() < RECORD_INTERVAL_MS) {
                return;
            }
        }

        SAMPLES.add(new Sample(x, z, now));
    }

    public static List<Sample> snapshot() {
        prune(Util.getMillis());
        return List.copyOf(SAMPLES);
    }

    public static void reset() {
        SAMPLES.clear();
    }

    public static long windowMs() {
        return StormRadarConfig.trailDurationSeconds() * 1_000L;
    }

    public static void pruneExpiredSamples() {
        prune(Util.getMillis());
    }

    private static void prune(long now) {
        SAMPLES.removeIf(sample -> now - sample.timeMs() > windowMs());
    }

    public record Sample(double x, double z, long timeMs) {}
}
