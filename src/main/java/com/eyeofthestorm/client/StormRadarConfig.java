package com.eyeofthestorm.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only settings for the storm radar widget. */
public final class StormRadarConfig {
    public enum CenterIndicator {
        ARROW,
        TRAIL
    }

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue OPEN;
    private static final ModConfigSpec.EnumValue<CenterIndicator> CENTER_INDICATOR;
    private static final ModConfigSpec.IntValue TRAIL_DURATION_SECONDS;
    private static final ModConfigSpec.IntValue PLAYER_COLOR_R;
    private static final ModConfigSpec.IntValue PLAYER_COLOR_G;
    private static final ModConfigSpec.IntValue PLAYER_COLOR_B;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("radar");
        OPEN = builder
                .comment("Whether the storm radar HUD is open.")
                .translation("config.eyeofthestorm.radar.open")
                .define("open", false);
        CENTER_INDICATOR = builder
                .comment("The storm motion indicator displayed at the center of the radar.")
                .translation("config.eyeofthestorm.radar.center_indicator")
                .defineEnum("centerIndicator", CenterIndicator.ARROW);
        TRAIL_DURATION_SECONDS = builder
                .comment("How long the storm's position trail remains visible, in seconds.")
                .translation("config.eyeofthestorm.radar.trail_duration_seconds")
                .defineInRange("trailDurationSeconds", 30, 5, 120);
        PLAYER_COLOR_R = builder
                .comment("Red channel (0-255) for your player marker on the storm radar.")
                .translation("config.eyeofthestorm.radar.player_color_r")
                .defineInRange("playerColorR", 255, 0, 255);
        PLAYER_COLOR_G = builder
                .comment("Green channel (0-255) for your player marker on the storm radar.")
                .translation("config.eyeofthestorm.radar.player_color_g")
                .defineInRange("playerColorG", 255, 0, 255);
        PLAYER_COLOR_B = builder
                .comment("Blue channel (0-255) for your player marker on the storm radar.")
                .translation("config.eyeofthestorm.radar.player_color_b")
                .defineInRange("playerColorB", 255, 0, 255);
        builder.pop();
        SPEC = builder.build();
    }

    private StormRadarConfig() {}

    public static boolean open() {
        return OPEN.get();
    }

    public static void setOpen(boolean open) {
        OPEN.set(open);
        save();
    }

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

    public static int playerColorR() {
        return PLAYER_COLOR_R.get();
    }

    public static int playerColorG() {
        return PLAYER_COLOR_G.get();
    }

    public static int playerColorB() {
        return PLAYER_COLOR_B.get();
    }

    public static void setPlayerColor(int r, int g, int b) {
        PLAYER_COLOR_R.set(Math.max(0, Math.min(255, r)));
        PLAYER_COLOR_G.set(Math.max(0, Math.min(255, g)));
        PLAYER_COLOR_B.set(Math.max(0, Math.min(255, b)));
    }

    public static void save() {
        SPEC.save();
    }
}
