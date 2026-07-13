package com.eyeofthestorm.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.eyeofthestorm.StormConfig;
import com.eyeofthestorm.registry.ModItems;
import com.eyeofthestorm.storm.StormData;
import com.eyeofthestorm.storm.StormLogic;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
                .then(Commands.literal("set_turn_rate")
                        .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.0))
                                .executes(StormCommands::setTurnRate)))
                .then(Commands.literal("set_damage")
                        .then(Commands.argument("hp_per_block", DoubleArgumentType.doubleArg(0.0))
                                .executes(StormCommands::setDamage)))
                .then(Commands.literal("pause").executes(StormCommands::pause))
                .then(Commands.literal("resume").executes(StormCommands::resume))
                .then(Commands.literal("status").executes(StormCommands::status))
                .then(Commands.literal("give_compass").executes(StormCommands::giveCompass))
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
        d.setCenter(
                DoubleArgumentType.getDouble(ctx, "x"),
                DoubleArgumentType.getDouble(ctx, "y"),
                DoubleArgumentType.getDouble(ctx, "z")
        );
        StormLogic.updateWorldSpawn(overworld(ctx.getSource()), d);
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
        ctx.getSource().sendSuccess(() -> Component.literal("Speed = " + d.speed + " blocks/tick"), true);
        return 1;
    }

    private static int setTurnRate(CommandContext<CommandSourceStack> ctx) {
        StormConfig.turnRate = DoubleArgumentType.getDouble(ctx, "rate");
        ctx.getSource().sendSuccess(
                () -> Component.literal("Turn rate = " + StormConfig.turnRate + " rad/tick noise (runtime; not persisted yet)"),
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
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Storm %s | center (%.2f, %.2f, %.2f) | r=%.1f | speed=%.4f | turn=%.5f | yaw=%.1f° | dmg=%.2f/block (safe=%.0f)",
                state, d.centerX, d.centerY, d.centerZ, d.radius, d.speed,
                StormConfig.turnRate, Math.toDegrees(d.yawRad), StormConfig.damagePerBlock, StormConfig.damageSafeZone
        )), false);
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
                /storm set_radius <r> | set_speed <blocks/tick> | set_turn_rate <rad/tick> | set_damage <hp/block>
                /storm pause | resume | status | give_compass | toggle_immunity
                """), false);
        return 1;
    }
}
