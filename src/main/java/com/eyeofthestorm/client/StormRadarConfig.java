package com.eyeofthestorm.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only settings for the storm radar widget. */
public final class StormRadarConfig {
    public enum CenterIndicator {
        ARROW,
        TRAIL
    }

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<CenterIndicator> CENTER_INDICATOR;
    private static final ModConfigSpec.IntValue TRAIL_DURATION_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("radar");
        CENTER_INDICATOR = builder
                .comment("The storm motion indicator displayed at the center of the radar.")
                .translation("config.eyeofthestorm.radar.center_indicator")
                .defineEnum("centerIndicator", CenterIndicator.ARROW);
        TRAIL_DURATION_SECONDS = builder
                .comment("How long the storm's position trail remains visible, in seconds.")
                .translation("config.eyeofthestorm.radar.trail_duration_seconds")
                .defineInRange("trailDurationSeconds", 30, 5, 120);
        builder.pop();
        SPEC = builder.build();
    }

    private StormRadarConfig() {}

    public static CenterIndicator centerIndicator() {
        return CENTER_INDICATOR.get();
    }

    public static void setCenterIndicator(CenterIndicator indicator) {
        CENTER_INDICATOR.set(indicator);
    }

    public static int trailDurationSeconds() {
        return TRAIL_DURATION_SECONDS.get();
    }

    public static void setTrailDurationSeconds(int seconds) {
        TRAIL_DURATION_SECONDS.set(seconds);
        StormPathHistory.pruneExpiredSamples();
    }

    public static void save() {
        SPEC.save();
    }
}
