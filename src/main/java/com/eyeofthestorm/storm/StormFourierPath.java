package com.eyeofthestorm.storm;

import com.eyeofthestorm.StormConfig;
import net.minecraft.util.RandomSource;

/**
 * Shape as a sum of circles: {@code z(t) = Σ r·exp(i(ωt+φ))}, sampled with harmonic decay.
 * Linear speed is factored out by advancing {@code t} with
 * {@code Δt = desiredDistance / |z'(t)|}, which scales all circle rotation rates equally.
 */
public final class StormFourierPath {
    private StormFourierPath() {}

    public record Circle(double r, double w, double p) {}

    public record Vec2(double x, double z) {
        public double length() {
            return Math.hypot(x, z);
        }
    }

    public static Circle[] generateDefault(RandomSource random) {
        return generate(random, StormConfig.pathCircleCount, StormConfig.pathRadiusScale);
    }

    public static Circle[] generate(RandomSource random, int count, double radiusScale) {
        return harmonicDecay(random, Math.max(1, count), radiusScale);
    }

    /**
     * Harmonic decay: {@code r_k = R/k}, {@code ω_k = (-1)^{k+1} k}, random phase.
     * Integer frequencies close every {@code 2π} in {@code t}; {@link StormConfig#pathRadiusScale}
     * is sized so one loop is longer than the target campaign distance.
     */
    private static Circle[] harmonicDecay(RandomSource random, int n, double R) {
        Circle[] out = new Circle[n];
        for (int k = 1; k <= n; k++) {
            double r = R / k;
            double w = Math.pow(-1, k + 1) * k;
            double p = random.nextDouble() * Math.PI * 2.0;
            out[k - 1] = new Circle(r, w, p);
        }
        return out;
    }

    public static Vec2 position(Circle[] circles, double t) {
        double x = 0.0;
        double z = 0.0;
        for (Circle c : circles) {
            double a = c.w * t + c.p;
            x += c.r * Math.cos(a);
            z += c.r * Math.sin(a);
        }
        return new Vec2(x, z);
    }

    /** Parameter-space derivative z'(t). World speed = |z'| · |dt/d(real)|. */
    public static Vec2 derivative(Circle[] circles, double t) {
        double dx = 0.0;
        double dz = 0.0;
        for (Circle c : circles) {
            double a = c.w * t + c.p;
            dx += -c.r * c.w * Math.sin(a);
            dz += c.r * c.w * Math.cos(a);
        }
        return new Vec2(dx, dz);
    }

    /**
     * Speed curve {@code ((1-cos(u-0.5))/2)^2}.
     */
    public static double speedScale(double u) {
        double raised = (1.0 - Math.cos(u - 0.5)) * 0.5;
        return raised * raised;
    }

    /**
     * Phase used when a path is (re)generated — a speed peak so the storm starts moving immediately.
     * Preview LUTs / ETA must use the same value to match live motion after Apply.
     */
    public static double initialSpeedPhase() {
        return 0.5 + Math.PI;
    }

    /** One full speed-pulse cycle in phase units. */
    public static double speedPhasePeriod() {
        return Math.PI * 2.0;
    }

    /**
     * Speed phase from an offset in turns of the pulse ({@code 0} = peak start,
     * {@code 0.5} ≈ opposite side of the cycle).
     */
    public static double speedPhaseFromOffsetTurns(double turns) {
        return initialSpeedPhase() + turns * speedPhasePeriod();
    }

    /** Offset in turns of the pulse relative to {@link #initialSpeedPhase()}, wrapped to {@code [0, 1)}. */
    public static double offsetTurnsFromSpeedPhase(double phase) {
        double turns = (phase - initialSpeedPhase()) / speedPhasePeriod();
        turns -= Math.floor(turns);
        return turns;
    }

    /**
     * Heading yaw (0 = +Z) matching {@link StormData#forward()} for velocity (vx, vz).
     */
    public static double yawFromVelocity(double vx, double vz) {
        return Math.atan2(-vx, vz);
    }

    /**
     * Fundamental period in path parameter for integer harmonic frequencies ({@code ω_k = ±k}).
     */
    public static double fundamentalPeriod() {
        return Math.PI * 2.0;
    }

    /**
     * Arc length of one closed loop: {@code ∫|z'(t)| dt} over {@link #fundamentalPeriod()}.
     * Uses a fixed-step Riemann sum; accurate enough for tuning feedback.
     */
    public static double estimateLoopLength(Circle[] circles) {
        return estimateLoopLength(circles, 4096);
    }

    public static double estimateLoopLength(Circle[] circles, int samples) {
        if (circles == null || circles.length == 0 || samples < 1) {
            return 0.0;
        }
        double period = fundamentalPeriod();
        double dt = period / samples;
        double length = 0.0;
        for (int i = 0; i < samples; i++) {
            length += derivative(circles, i * dt).length() * dt;
        }
        return length;
    }
}
