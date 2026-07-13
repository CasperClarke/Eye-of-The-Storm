package com.eyeofthestorm.storm;

import com.eyeofthestorm.StormConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Persistent Overworld storm state.
 * Center is a continuous Vec3; motion uses yaw + angular velocity (radians) — not scoreboard tiers.
 */
public class StormData extends SavedData {
    public static final String DATA_NAME = "eyeofthestorm_storm";

    public boolean initialized;
    public boolean active;
    public boolean paused;

    public double centerX;
    public double centerY = 64.0;
    public double centerZ;

    public double radius = StormConfig.defaultRadius;
    /** Blocks per tick along current heading. */
    public double speed = StormConfig.defaultSpeed;

    /** Heading in radians (0 = +Z). */
    public double yawRad;
    /** Radians per tick. */
    public double angularVelocity;

    public float wallSpinDeg;
    public int ticksAlive;

    public static StormData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(StormData::new, StormData::load),
                DATA_NAME
        );
    }

    public Vec3 center() {
        return new Vec3(centerX, centerY, centerZ);
    }

    public void setCenter(Vec3 pos) {
        setCenter(pos.x, pos.y, pos.z);
    }

    public void setCenter(double x, double y, double z) {
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        setDirty();
    }

    /** Horizontal unit direction the storm is moving. */
    public Vec3 forward() {
        return new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
    }

    public boolean isRunning() {
        return initialized && active && !paused;
    }

    public static StormData load(CompoundTag tag, HolderLookup.Provider lookup) {
        StormData data = new StormData();
        data.initialized = tag.getBoolean("initialized");
        data.active = tag.getBoolean("active");
        data.paused = tag.getBoolean("paused");
        data.centerX = tag.getDouble("centerX");
        data.centerY = tag.getDouble("centerY");
        data.centerZ = tag.getDouble("centerZ");
        data.radius = tag.contains("radius") ? tag.getDouble("radius") : StormConfig.defaultRadius;
        data.speed = tag.contains("speed") ? tag.getDouble("speed") : StormConfig.defaultSpeed;

        if (tag.contains("yawRad")) {
            data.yawRad = tag.getDouble("yawRad");
        } else if (tag.contains("yaw")) {
            // Migrate old degree-based field from the first mod draft
            data.yawRad = Math.toRadians(tag.getFloat("yaw"));
        }
        data.angularVelocity = tag.getDouble("angularVelocity");
        // Drop legacy headingDelta / turnScale — steering is config-driven now
        data.wallSpinDeg = tag.contains("wallSpin") ? tag.getFloat("wallSpin") : tag.getFloat("wallSpinDeg");
        data.ticksAlive = tag.getInt("ticksAlive");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("initialized", initialized);
        tag.putBoolean("active", active);
        tag.putBoolean("paused", paused);
        tag.putDouble("centerX", centerX);
        tag.putDouble("centerY", centerY);
        tag.putDouble("centerZ", centerZ);
        tag.putDouble("radius", radius);
        tag.putDouble("speed", speed);
        tag.putDouble("yawRad", yawRad);
        tag.putDouble("angularVelocity", angularVelocity);
        tag.putFloat("wallSpinDeg", wallSpinDeg);
        tag.putInt("ticksAlive", ticksAlive);
        return tag;
    }
}
