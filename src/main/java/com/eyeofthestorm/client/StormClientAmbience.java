package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.StormConfig;
import com.eyeofthestorm.storm.StormBoundaryMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Local storm ambience when hugging the wall or just outside it.
 * Deep inside the safe zone ({@link StormConfig#soundSilentInsideBlocks}) is fully silent.
 */
@EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT)
public final class StormClientAmbience {
    private static final SoundEvent[] STORM_AMBIENCE = {
            SoundEvents.CONDUIT_AMBIENT_SHORT,
            SoundEvents.CONDUIT_AMBIENT,
            SoundEvents.WARDEN_HEARTBEAT,
            SoundEvents.LIGHTNING_BOLT_THUNDER
    };

    private static int outsideCooldown;
    private static int insideCooldown;

    private StormClientAmbience() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ClientStormState.shouldRender()) {
            outsideCooldown = 0;
            insideCooldown = 0;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        if (mc.player.getTags().contains("storm_immune") || mc.player.isSpectator() || mc.player.isCreative()) {
            return;
        }

        if (outsideCooldown > 0) {
            outsideCooldown--;
        }
        if (insideCooldown > 0) {
            insideCooldown--;
        }

        Vec3 position = mc.player.position();
        float gap = StormWallRenderer.outsideGap(position);
        double distToWall = StormBoundaryMath.distanceToWall(
                position,
                ClientStormState.centerX,
                ClientStormState.centerZ,
                ClientStormState.radius
        );

        if (gap <= 0f && distToWall > StormConfig.soundSilentInsideBlocks) {
            stopStormAmbience(mc.getSoundManager());
            outsideCooldown = 0;
            insideCooldown = 0;
            return;
        }

        if (gap > 0f) {
            if (gap > StormConfig.soundOutsideRange) {
                stopStormAmbience(mc.getSoundManager());
                return;
            }
            tickOutsideAmbience(mc, gap);
            return;
        }

        if (distToWall > StormConfig.soundInsideRange) {
            stopStormAmbience(mc.getSoundManager());
            return;
        }

        tickInsideWallHum(mc, distToWall);
    }

    private static void tickOutsideAmbience(Minecraft mc, float gap) {
        if (outsideCooldown > 0) {
            return;
        }

        double maxRange = StormConfig.soundOutsideRange;
        float proximity = (float) Mth.clamp(1.0 - gap / maxRange, 0.0, 1.0);
        if (proximity < 0.2f) {
            return;
        }

        mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(
                SoundEvents.CONDUIT_AMBIENT_SHORT,
                0.04f + proximity * 0.12f,
                0.9f + proximity * 0.1f
        ));
        outsideCooldown = 90 + (int) ((1f - proximity) * 50f);

        if (proximity > 0.65f && gap < 4f) {
            mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(
                    SoundEvents.WARDEN_HEARTBEAT,
                    0.05f + proximity * 0.1f,
                    0.55f
            ));
            outsideCooldown = Math.max(outsideCooldown, 140);
        }
    }

    private static void tickInsideWallHum(Minecraft mc, double distToWall) {
        if (insideCooldown > 0) {
            return;
        }

        double maxRange = StormConfig.soundInsideRange;
        float closeness = (float) Mth.clamp(1.0 - distToWall / maxRange, 0.0, 1.0);
        if (closeness < 0.25f) {
            return;
        }

        mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(
                SoundEvents.CONDUIT_AMBIENT_SHORT,
                0.03f + closeness * 0.1f,
                1.0f + closeness * 0.08f
        ));
        insideCooldown = 100 + (int) ((1f - closeness) * 60f);
    }

    private static void stopStormAmbience(SoundManager soundManager) {
        for (SoundEvent event : STORM_AMBIENCE) {
            soundManager.stop(event.getLocation(), SoundSource.MASTER);
        }
    }
}
