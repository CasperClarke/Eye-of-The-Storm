package com.eyeofthestorm.storm;

import com.eyeofthestorm.StormConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Server simulation — written as normal game code, not datapack workarounds.
 */
public final class StormLogic {
    public static final String IMMUNE_TAG = "storm_immune";

    private StormLogic() {}

    public static void tick(ServerLevel level, StormData data) {
        if (!data.isRunning()) {
            return;
        }

        data.ticksAlive++;
        stepMotion(level, data);
        data.wallSpinDeg = Mth.wrapDegrees(data.wallSpinDeg + 1.5f);
        data.setDirty();

        if (data.ticksAlive % StormConfig.spawnUpdateIntervalTicks == 0) {
            updateWorldSpawn(level, data);
        }

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.getTags().contains(IMMUNE_TAG) || player.isCreative()) {
                continue;
            }
            tickPlayerEffects(level, data, player);
        }
    }

    /** Smooth random-walk angular velocity, then integrate position with doubles. */
    private static void stepMotion(ServerLevel level, StormData data) {
        data.angularVelocity += (level.random.nextDouble() * 2.0 - 1.0) * StormConfig.turnRate;
        data.angularVelocity = Mth.clamp(data.angularVelocity, -StormConfig.maxAngularVelocity, StormConfig.maxAngularVelocity);
        data.angularVelocity *= StormConfig.angularDamping;
        data.yawRad += data.angularVelocity;

        Vec3 delta = data.forward().scale(data.speed);
        data.centerX += delta.x;
        data.centerZ += delta.z;
        // Y stays where the storm was placed / teleported — no per-tick surface snapping
    }

    /**
     * Anchor world spawn at the storm center on a high Y so respawns land on local surface.
     * Minecraft searches down from this point in {@code ServerPlayer#adjustSpawnLocation}.
     */
    public static void updateWorldSpawn(ServerLevel level, StormData data) {
        int x = Mth.floor(data.centerX);
        int z = Mth.floor(data.centerZ);
        level.setDefaultSpawnPos(
                new BlockPos(x, StormConfig.spawnAnchorY, z),
                (float) Math.toDegrees(data.yawRad)
        );
    }

    private static void tickPlayerEffects(ServerLevel level, StormData data, ServerPlayer player) {
        double distanceToWall = StormBoundaryMath.distanceToWall(player.position(), data);
        float damage = StormBoundaryMath.borderDamage(distanceToWall);
        if (damage > 0f) {
            player.hurt(level.damageSources().outOfBorder(), damage);
        }
    }

    public static double horizontalDistSq(Vec3 pos, StormData data) {
        double dx = pos.x - data.centerX;
        double dz = pos.z - data.centerZ;
        return dx * dx + dz * dz;
    }

    public static double horizontalDistance(Vec3 pos, StormData data) {
        return Math.sqrt(horizontalDistSq(pos, data));
    }

    public static void initAt(ServerLevel level, StormData data, Vec3 pos) {
        data.initialized = true;
        data.active = true;
        data.paused = false;
        data.radius = StormConfig.defaultRadius;
        data.speed = StormConfig.defaultSpeed;
        data.angularVelocity = 0.0;
        data.yawRad = level.random.nextDouble() * Math.PI * 2.0;
        data.ticksAlive = 0;
        data.setCenter(pos);
        updateWorldSpawn(level, data);
        data.setDirty();
        StormEvents.syncToDimension(level, data);
    }
}
