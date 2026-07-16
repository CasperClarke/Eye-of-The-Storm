package com.eyeofthestorm.storm;

import com.eyeofthestorm.StormConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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

        // Upgrade legacy saves that still lack a Fourier path.
        if (!data.hasPath()) {
            regeneratePath(level.random, data, level.random.nextLong(), data.centerX, data.centerY, data.centerZ);
        }

        data.ticksAlive++;
        stepMotion(data);
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

    /**
     * Advance path parameter so world-space travel equals {@code speed · s(u)} blocks this tick.
     * That divides every circle's effective rotation rate by {|z'| / desired}, keeping shape fixed.
     */
    private static void stepMotion(StormData data) {
        data.speedPhase += StormConfig.speedPhaseRatePerTick;
        double scale = StormFourierPath.speedScale(data.speedPhase);
        double desiredDistance = data.speed * scale;
        if (desiredDistance <= 0.0 || !data.hasPath()) {
            return;
        }

        StormFourierPath.Vec2 deriv = StormFourierPath.derivative(data.circles, data.pathParam);
        double mag = Math.max(deriv.length(), StormConfig.pathDerivEpsilon);
        data.pathParam += desiredDistance / mag;

        data.applyPathPosition();

        StormFourierPath.Vec2 tangent = StormFourierPath.derivative(data.circles, data.pathParam);
        if (tangent.length() > StormConfig.pathDerivEpsilon) {
            data.yawRad = StormFourierPath.yawFromVelocity(tangent.x(), tangent.z());
        }
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
        data.ticksAlive = 0;
        data.centerY = pos.y;
        regeneratePath(level.random, data, level.random.nextLong(), pos.x, pos.y, pos.z);
        updateWorldSpawn(level, data);
        data.setDirty();
        StormEvents.syncToDimension(level, data);
    }

    public static void teleportTo(ServerLevel level, StormData data, Vec3 pos) {
        data.centerY = pos.y;
        data.rebasePathOriginTo(pos.x, pos.z);
        data.applyPathPosition();
        // Keep yaw from current tangent if possible
        if (data.hasPath()) {
            StormFourierPath.Vec2 tangent = StormFourierPath.derivative(data.circles, data.pathParam);
            if (tangent.length() > StormConfig.pathDerivEpsilon) {
                data.yawRad = StormFourierPath.yawFromVelocity(tangent.x(), tangent.z());
            }
        }
        updateWorldSpawn(level, data);
        data.setDirty();
        StormEvents.syncToDimension(level, data);
    }

    /** Build a new sum-of-circles path anchored so the storm sits at {@code (x,y,z)}. */
    public static void regeneratePath(
            RandomSource random,
            StormData data,
            long seed,
            double worldX,
            double worldY,
            double worldZ
    ) {
        RandomSource pathRandom = RandomSource.create(seed);
        data.pathSeed = seed;
        data.circles = StormFourierPath.generateDefault(pathRandom);
        data.pathParam = 0.0;
        data.speedPhase = StormFourierPath.initialSpeedPhase();
        data.centerY = worldY;
        data.rebasePathOriginTo(worldX, worldZ);
        data.applyPathPosition();

        StormFourierPath.Vec2 tangent = StormFourierPath.derivative(data.circles, data.pathParam);
        if (tangent.length() > StormConfig.pathDerivEpsilon) {
            data.yawRad = StormFourierPath.yawFromVelocity(tangent.x(), tangent.z());
        } else {
            data.yawRad = random.nextDouble() * Math.PI * 2.0;
        }
        data.setDirty();
    }

    /** Instantaneous linear speed this tick (blocks/tick), after the speed scale curve. */
    public static double instantaneousSpeed(StormData data) {
        return data.speed * StormFourierPath.speedScale(data.speedPhase);
    }
}
