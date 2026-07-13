package com.eyeofthestorm.storm;

import com.eyeofthestorm.StormConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Signed distance and scaling helpers matching vanilla {@link net.minecraft.world.level.border.WorldBorder}
 * and {@link net.minecraft.client.gui.Gui#renderVignette}.
 */
public final class StormBoundaryMath {
    private StormBoundaryMath() {}

    /** Positive inside the eye, negative outside — same sign convention as {@code WorldBorder#getDistanceToBorder}. */
    public static double distanceToWall(Vec3 position, double centerX, double centerZ, double radius) {
        double dist = Math.hypot(position.x - centerX, position.z - centerZ);
        return radius - dist;
    }

    public static double distanceToWall(Vec3 position, StormData data) {
        return distanceToWall(position, data.centerX, data.centerZ, data.radius);
    }

    public static double outsideGap(Vec3 position, double centerX, double centerZ, double radius) {
        return -distanceToWall(position, centerX, centerZ, radius);
    }

    /** World-border vignette curve: ramps up within {@link StormConfig#vignetteWarningBlocks} of the wall. */
    public static float vignetteStrength(double distanceToWall) {
        double warning = Math.max(StormConfig.vignetteWarningBlocks, 1.0);
        if (distanceToWall >= warning) {
            return 0f;
        }
        return Mth.clamp(1f - (float) (distanceToWall / warning), 0f, 1f);
    }

    /**
     * Vanilla outside-border damage: safe zone first, then {@code damagePerBlock} beyond that.
     * Returns 0 when no damage should be dealt this tick.
     */
    public static float borderDamage(double distanceToWall) {
        double margin = distanceToWall + StormConfig.damageSafeZone;
        if (margin >= 0.0 || StormConfig.damagePerBlock <= 0.0) {
            return 0f;
        }
        return (float) Math.max(1, Mth.floor(-margin * StormConfig.damagePerBlock));
    }
}
