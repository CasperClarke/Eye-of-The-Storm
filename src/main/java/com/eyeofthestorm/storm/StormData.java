package com.eyeofthestorm.storm;

import com.eyeofthestorm.StormConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Persistent Overworld storm state.
 * Motion shape is a sum of circles; linear speed is applied by advancing the path parameter.
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
    /** Peak linear speed in blocks/tick (scaled by the speed function each tick). */
    public double speed = StormConfig.defaultSpeed;

    /** Heading in radians (0 = +Z), derived from path tangent. */
    public double yawRad;

    public float wallSpinDeg;
    public int ticksAlive;

    /** World-space origin such that center = origin + z(pathParam). */
    public double pathOriginX;
    public double pathOriginZ;
    /** Shared epicycle parameter t. */
    public double pathParam;
    /** Real-time phase for the speed scale function. */
    public double speedPhase;
    public long pathSeed;
    public StormFourierPath.Circle[] circles = new StormFourierPath.Circle[0];

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

    public boolean hasPath() {
        return circles != null && circles.length > 0;
    }

    /**
     * Rebase path origin so {@code origin + z(pathParam)} equals the given world XZ
     * (keeps shape/parameter; used after teleport).
     */
    public void rebasePathOriginTo(double worldX, double worldZ) {
        if (!hasPath()) {
            pathOriginX = worldX;
            pathOriginZ = worldZ;
            return;
        }
        StormFourierPath.Vec2 local = StormFourierPath.position(circles, pathParam);
        pathOriginX = worldX - local.x();
        pathOriginZ = worldZ - local.z();
    }

    public void applyPathPosition() {
        if (!hasPath()) {
            return;
        }
        StormFourierPath.Vec2 local = StormFourierPath.position(circles, pathParam);
        centerX = pathOriginX + local.x();
        centerZ = pathOriginZ + local.z();
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
            data.yawRad = Math.toRadians(tag.getFloat("yaw"));
        }

        data.wallSpinDeg = tag.contains("wallSpin") ? tag.getFloat("wallSpin") : tag.getFloat("wallSpinDeg");
        data.ticksAlive = tag.getInt("ticksAlive");

        data.pathOriginX = tag.contains("pathOriginX") ? tag.getDouble("pathOriginX") : data.centerX;
        data.pathOriginZ = tag.contains("pathOriginZ") ? tag.getDouble("pathOriginZ") : data.centerZ;
        data.pathParam = tag.getDouble("pathParam");
        data.speedPhase = tag.getDouble("speedPhase");
        data.pathSeed = tag.getLong("pathSeed");
        data.circles = readCircles(tag);

        // Legacy worlds: no Fourier path yet — keep center; path regenerated on next init/regen.
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
        tag.putFloat("wallSpinDeg", wallSpinDeg);
        tag.putInt("ticksAlive", ticksAlive);

        tag.putDouble("pathOriginX", pathOriginX);
        tag.putDouble("pathOriginZ", pathOriginZ);
        tag.putDouble("pathParam", pathParam);
        tag.putDouble("speedPhase", speedPhase);
        tag.putLong("pathSeed", pathSeed);
        writeCircles(tag, circles);
        return tag;
    }

    private static StormFourierPath.Circle[] readCircles(CompoundTag tag) {
        if (!tag.contains("circles", Tag.TAG_LIST)) {
            return new StormFourierPath.Circle[0];
        }
        ListTag list = tag.getList("circles", Tag.TAG_COMPOUND);
        StormFourierPath.Circle[] out = new StormFourierPath.Circle[list.size()];
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            out[i] = new StormFourierPath.Circle(c.getDouble("r"), c.getDouble("w"), c.getDouble("p"));
        }
        return out;
    }

    private static void writeCircles(CompoundTag tag, StormFourierPath.Circle[] circles) {
        ListTag list = new ListTag();
        if (circles != null) {
            for (StormFourierPath.Circle circle : circles) {
                CompoundTag c = new CompoundTag();
                c.putDouble("r", circle.r());
                c.putDouble("w", circle.w());
                c.putDouble("p", circle.p());
                list.add(c);
            }
        }
        tag.put("circles", list);
    }
}
