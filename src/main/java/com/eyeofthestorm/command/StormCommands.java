package com.eyeofthestorm.command;

import com.eyeofthestorm.StormConfig;
import com.eyeofthestorm.network.OpenStormPathPreviewPayload;
import com.eyeofthestorm.registry.ModItems;
import com.eyeofthestorm.storm.StormData;
import com.eyeofthestorm.storm.StormEvents;
import com.eyeofthestorm.storm.StormFourierPath;
import com.eyeofthestorm.storm.StormLogic;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

public final class StormCommands {
    private StormCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("storm")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("init").executes(StormCommands::initHere)
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(StormCommands::initAt)))))
                .then(Commands.literal("init_here").executes(StormCommands::initHere))
                .then(Commands.literal("teleport")
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(StormCommands::teleport)))))
                .then(Commands.literal("set_radius")
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0))
                                .executes(StormCommands::setRadius)))
                .then(Commands.literal("set_speed")
                        .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.0))
                                .executes(StormCommands::setSpeed)))
                .then(Commands.literal("set_speed_phase_minutes")
                        .then(Commands.argument("minutes", DoubleArgumentType.doubleArg(0.001))
                                .executes(StormCommands::setSpeedPhaseMinutes)))
                .then(Commands.literal("set_speed_phase_rate")
                        .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.0))
                                .executes(StormCommands::setSpeedPhaseRate)))
                .then(Commands.literal("set_speed_phase_offset")
                        .then(Commands.argument("turns", DoubleArgumentType.doubleArg())
                                .executes(StormCommands::setSpeedPhaseOffset)))
                .then(Commands.literal("set_path_scale")
                        .then(Commands.argument("scale", DoubleArgumentType.doubleArg(1.0))
                                .executes(StormCommands::setPathScale)))
                .then(Commands.literal("set_circle_count")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(StormCommands::setCircleCount)))
                .then(Commands.literal("regen_path").executes(StormCommands::regenPathRandom)
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(StormCommands::regenPathSeeded)))
                .then(Commands.literal("set_damage")
                        .then(Commands.argument("hp_per_block", DoubleArgumentType.doubleArg(0.0))
                                .executes(StormCommands::setDamage)))
                .then(Commands.literal("pause").executes(StormCommands::pause))
                .then(Commands.literal("resume").executes(StormCommands::resume))
                .then(Commands.literal("status").executes(StormCommands::status))
                .then(Commands.literal("preview_path").executes(StormCommands::previewPath))
                .then(Commands.literal("give_compass").executes(StormCommands::giveCompass))
                .then(Commands.literal("give_map").executes(StormCommands::giveMap))
                .then(Commands.literal("toggle_immunity").executes(StormCommands::toggleImmunity))
                .then(Commands.literal("help").executes(StormCommands::help))
        );
    }

    private static ServerLevel overworld(CommandSourceStack source) {
        return source.getServer().getLevel(Level.OVERWORLD);
    }

    private static StormData data(CommandSourceStack source) {
        return StormData.get(overworld(source));
    }

    /** Loop length of the live path, or a preview from current config if none exists. */
    private static double estimateLoopLength(StormData d) {
        if (d.hasPath()) {
            return StormFourierPath.estimateLoopLength(d.circles);
        }
        return StormFourierPath.estimateLoopLength(
                StormFourierPath.generateDefault(RandomSource.create(0L))
        );
    }

    private static void regenKeepingSeed(ServerLevel level, StormData d) {
        long seed = d.hasPath() ? d.pathSeed : level.random.nextLong();
        StormLogic.regeneratePath(level.random, d, seed, d.centerX, d.centerY, d.centerZ);
    }

    private static int initHere(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Player required"));
            return 0;
        }
        StormLogic.initAt(overworld(ctx.getSource()), data(ctx.getSource()), player.position());
        ctx.getSource().sendSuccess(() -> Component.literal("Storm initialized at your position."), true);
        return 1;
    }

    private static int initAt(CommandContext<CommandSourceStack> ctx) {
        Vec3 pos = new Vec3(
                DoubleArgumentType.getDouble(ctx, "x"),
                DoubleArgumentType.getDouble(ctx, "y"),
                DoubleArgumentType.getDouble(ctx, "z")
        );
        StormLogic.initAt(overworld(ctx.getSource()), data(ctx.getSource()), pos);
        ctx.getSource().sendSuccess(() -> Component.literal("Storm initialized at " + pos), true);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) {
        StormData d = data(ctx.getSource());
        if (!d.initialized) {
            ctx.getSource().sendFailure(Component.literal("Storm not initialized."));
            return 0;
        }
        Vec3 pos = new Vec3(
                DoubleArgumentType.getDouble(ctx, "x"),
                DoubleArgumentType.getDouble(ctx, "y"),
                DoubleArgumentType.getDouble(ctx, "z")
        );
        StormLogic.teleportTo(overworld(ctx.getSource()), d, pos);
        ctx.getSource().sendSuccess(() -> Component.literal("Storm teleported."), true);
        return 1;
    }

    private static int setRadius(CommandContext<CommandSourceStack> ctx) {
        StormData d = data(ctx.getSource());
        d.radius = DoubleArgumentType.getDouble(ctx, "radius");
        d.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("Radius = " + d.radius), true);
        return 1;
    }

    private static int setSpeed(CommandContext<CommandSourceStack> ctx) {
        StormData d = data(ctx.getSource());
        d.speed = DoubleArgumentType.getDouble(ctx, "speed");
        d.setDirty();
        ctx.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        "Peak speed = %.6f blocks/tick (avg ≈ %.6f with cosine² mean 0.375)",
                        d.speed, d.speed * StormConfig.SPEED_SCALE_MEAN
                )),
                true
        );
        return 1;
    }

    private static int setSpeedPhaseMinutes(CommandContext<CommandSourceStack> ctx) {
        double minutes = DoubleArgumentType.getDouble(ctx, "minutes");
        StormConfig.speedPhaseRatePerTick = (Math.PI * 2.0) / (minutes * 60.0 * 20.0);
        ctx.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        "Speed phase period = %.3f min (rate=%.8f /tick; runtime config, not persisted)",
                        minutes, StormConfig.speedPhaseRatePerTick
                )),
                true
        );
        return 1;
    }

    private static int setSpeedPhaseRate(CommandContext<CommandSourceStack> ctx) {
        double rate = DoubleArgumentType.getDouble(ctx, "rate");
        StormConfig.speedPhaseRatePerTick = rate;
        double minutes = rate > 0.0 ? (Math.PI * 2.0) / (rate * 60.0 * 20.0) : Double.POSITIVE_INFINITY;
        ctx.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        "Speed phase rate = %.8f /tick (period ≈ %.3f min; runtime config, not persisted)",
                        rate, minutes
                )),
                true
        );
        return 1;
    }

    private static int setSpeedPhaseOffset(CommandContext<CommandSourceStack> ctx) {
        StormData d = data(ctx.getSource());
        double turns = DoubleArgumentType.getDouble(ctx, "turns");
        d.speedPhase = StormFourierPath.speedPhaseFromOffsetTurns(turns);
        d.setDirty();
        ServerLevel level = overworld(ctx.getSource());
        if (level != null) {
            StormEvents.syncToDimension(level, d);
        }
        double wrapped = StormFourierPath.offsetTurnsFromSpeedPhase(d.speedPhase);
        double curve = StormFourierPath.speedScale(d.speedPhase);
        ctx.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        "Speed phase offset = %.3f turns (curve=%.3f at current location; 0=peak)",
                        wrapped, curve
                )),
                true
        );
        return 1;
    }

    private static int setPathScale(CommandContext<CommandSourceStack> ctx) {
        double scale = DoubleArgumentType.getDouble(ctx, "scale");
        StormConfig.pathRadiusScale = scale;
        StormData d = data(ctx.getSource());
        if (d.initialized) {
            ServerLevel level = overworld(ctx.getSource());
            regenKeepingSeed(level, d);
            double loop = estimateLoopLength(d);
            ctx.getSource().sendSuccess(
                    () -> Component.literal(String.format(
                            "Path scale = %.1f | circles=%d | estimated loop = %.0f blocks (seed=%d; runtime, not persisted)",
                            scale, d.circles.length, loop, d.pathSeed
                    )),
                    true
            );
        } else {
            double loop = estimateLoopLength(d);
            ctx.getSource().sendSuccess(
                    () -> Component.literal(String.format(
                            "Path scale = %.1f | circles=%d | estimated loop ≈ %.0f blocks (applies on next init/regen; runtime, not persisted)",
                            scale, StormConfig.pathCircleCount, loop
                    )),
                    true
            );
        }
        return 1;
    }

    private static int setCircleCount(CommandContext<CommandSourceStack> ctx) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        StormConfig.pathCircleCount = count;
        StormData d = data(ctx.getSource());
        if (d.initialized) {
            ServerLevel level = overworld(ctx.getSource());
            regenKeepingSeed(level, d);
            double loop = estimateLoopLength(d);
            ctx.getSource().sendSuccess(
                    () -> Component.literal(String.format(
                            "Circle count = %d | path scale = %.1f | estimated loop = %.0f blocks (seed=%d; runtime, not persisted)",
                            count, StormConfig.pathRadiusScale, loop, d.pathSeed
                    )),
                    true
            );
        } else {
            double loop = estimateLoopLength(d);
            ctx.getSource().sendSuccess(
                    () -> Component.literal(String.format(
                            "Circle count = %d | path scale = %.1f | estimated loop ≈ %.0f blocks (applies on next init/regen; runtime, not persisted)",
                            count, StormConfig.pathRadiusScale, loop
                    )),
                    true
            );
        }
        return 1;
    }

    private static int regenPathRandom(CommandContext<CommandSourceStack> ctx) {
        StormData d = data(ctx.getSource());
        if (!d.initialized) {
            ctx.getSource().sendFailure(Component.literal("Storm not initialized."));
            return 0;
        }
        ServerLevel level = overworld(ctx.getSource());
        long seed = level.random.nextLong();
        StormLogic.regeneratePath(level.random, d, seed, d.centerX, d.centerY, d.centerZ);
        double loop = estimateLoopLength(d);
        ctx.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        "Path regenerated (seed=%d, circles=%d, estimated loop=%.0f blocks)",
                        seed, d.circles.length, loop
                )),
                true
        );
        return 1;
    }

    private static int regenPathSeeded(CommandContext<CommandSourceStack> ctx) {
        StormData d = data(ctx.getSource());
        if (!d.initialized) {
            ctx.getSource().sendFailure(Component.literal("Storm not initialized."));
            return 0;
        }
        ServerLevel level = overworld(ctx.getSource());
        long seed = LongArgumentType.getLong(ctx, "seed");
        StormLogic.regeneratePath(level.random, d, seed, d.centerX, d.centerY, d.centerZ);
        double loop = estimateLoopLength(d);
        ctx.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        "Path regenerated (seed=%d, circles=%d, estimated loop=%.0f blocks)",
                        seed, d.circles.length, loop
                )),
                true
        );
        return 1;
    }

    private static int setDamage(CommandContext<CommandSourceStack> ctx) {
        StormConfig.damagePerBlock = (float) DoubleArgumentType.getDouble(ctx, "hp_per_block");
        ctx.getSource().sendSuccess(
                () -> Component.literal("Damage = " + StormConfig.damagePerBlock + " HP/block outside safe zone (runtime; not persisted yet)"),
                true
        );
        return 1;
    }

    private static int pause(CommandContext<CommandSourceStack> ctx) {
        StormData d = data(ctx.getSource());
        d.paused = true;
        d.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("Storm paused."), true);
        return 1;
    }

    private static int resume(CommandContext<CommandSourceStack> ctx) {
        StormData d = data(ctx.getSource());
        d.paused = false;
        d.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("Storm resumed."), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        StormData d = data(ctx.getSource());
        String state = !d.initialized ? "NOT INITIALIZED" : (d.paused ? "PAUSED" : "ACTIVE");
        double inst = StormLogic.instantaneousSpeed(d);
        double speedScale = StormFourierPath.speedScale(d.speedPhase);
        int n = d.hasPath() ? d.circles.length : 0;
        double phaseMinutes = StormConfig.speedPhaseRatePerTick > 0.0
                ? (Math.PI * 2.0) / (StormConfig.speedPhaseRatePerTick * 60.0 * 20.0)
                : Double.POSITIVE_INFINITY;
        double loop = estimateLoopLength(d);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Storm %s | center (%.2f, %.2f, %.2f) | eye r=%.1f | peak=%.4f inst=%.4f (curve=%.3f) | phase=%.2f min (u=%.3f) | pathScale=%.0f circles=%d loop≈%.0f t=%.3f seed=%d | yaw=%.1f° | dmg=%.2f/block",
                state, d.centerX, d.centerY, d.centerZ, d.radius, d.speed, inst, speedScale,
                phaseMinutes, d.speedPhase, StormConfig.pathRadiusScale, n > 0 ? n : StormConfig.pathCircleCount,
                loop, d.pathParam, d.pathSeed, Math.toDegrees(d.yawRad), StormConfig.damagePerBlock
        )), false);
        return 1;
    }

    private static int previewPath(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Player required"));
            return 0;
        }
        ServerLevel level = overworld(ctx.getSource());
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("Overworld unavailable"));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, OpenStormPathPreviewPayload.from(level, data(ctx.getSource())));
        return 1;
    }

    private static int giveMap(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        player.addItem(new ItemStack(ModItems.STORM_MAP.get()));
        ctx.getSource().sendSuccess(() -> Component.literal("Storm Map granted."), false);
        return 1;
    }

    private static int giveCompass(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        StormData d = data(ctx.getSource());
        ItemStack compass = new ItemStack(ModItems.STORM_COMPASS.get());
        if (d.initialized) {
            compass.set(DataComponents.LODESTONE_TRACKER,
                    new LodestoneTracker(Optional.of(GlobalPos.of(Level.OVERWORLD, net.minecraft.core.BlockPos.ZERO)), false));
        }
        player.addItem(compass);
        ctx.getSource().sendSuccess(() -> Component.literal("Storm Compass granted."), false);
        return 1;
    }

    private static int toggleImmunity(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        if (player.getTags().contains(StormLogic.IMMUNE_TAG)) {
            player.removeTag(StormLogic.IMMUNE_TAG);
            ctx.getSource().sendSuccess(() -> Component.literal("Immunity OFF"), false);
        } else {
            player.addTag(StormLogic.IMMUNE_TAG);
            ctx.getSource().sendSuccess(() -> Component.literal("Immunity ON"), false);
        }
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("""
                /storm init_here | init <x> <y> <z>
                /storm teleport <x> <y> <z>
                /storm set_radius <r> | set_speed <blocks/tick peak> | set_damage <hp/block>
                /storm set_speed_phase_minutes <min> | set_speed_phase_rate <per_tick>
                /storm set_speed_phase_offset <turns>   (0=peak, 0.5≈opposite; keeps location)
                /storm set_path_scale <blocks> | set_circle_count <n> | regen_path [seed]
                /storm preview_path
                /storm pause | resume | status | give_compass | give_map | toggle_immunity
                """), false);
        return 1;
    }
}
