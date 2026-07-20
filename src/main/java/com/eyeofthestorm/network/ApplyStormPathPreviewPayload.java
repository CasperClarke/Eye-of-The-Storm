package com.eyeofthestorm.network;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.StormConfig;
import com.eyeofthestorm.storm.StormData;
import com.eyeofthestorm.storm.StormEvents;
import com.eyeofthestorm.storm.StormFourierPath;
import com.eyeofthestorm.storm.StormLogic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Client → server: apply previewed path + speed settings to the live storm. */
public record ApplyStormPathPreviewPayload(
        long seed,
        int circleCount,
        double pathRadiusScale,
        double peakSpeed,
        double speedPhaseRatePerTick,
        double speedPhaseOffsetTurns,
        /** Path parameter to start at (preview marker / clicked hover point). */
        double pathParam
) implements CustomPacketPayload {
    public static final Type<ApplyStormPathPreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EyeOfTheStormMod.MOD_ID, "apply_path_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyStormPathPreviewPayload> STREAM_CODEC =
            StreamCodec.of(ApplyStormPathPreviewPayload::encode, ApplyStormPathPreviewPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ApplyStormPathPreviewPayload p) {
        buf.writeLong(p.seed());
        buf.writeVarInt(p.circleCount());
        buf.writeDouble(p.pathRadiusScale());
        buf.writeDouble(p.peakSpeed());
        buf.writeDouble(p.speedPhaseRatePerTick());
        buf.writeDouble(p.speedPhaseOffsetTurns());
        buf.writeDouble(p.pathParam());
    }

    private static ApplyStormPathPreviewPayload decode(RegistryFriendlyByteBuf buf) {
        return new ApplyStormPathPreviewPayload(
                buf.readLong(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ApplyStormPathPreviewPayload::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EyeOfTheStormMod.MOD_ID).versioned("13");
        registrar.playToServer(TYPE, STREAM_CODEC, ApplyStormPathPreviewPayload::handleServer);
    }

    private static void handleServer(ApplyStormPathPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                return;
            }
            ServerLevel level = player.serverLevel().getServer().getLevel(Level.OVERWORLD);
            if (level == null) {
                return;
            }
            StormData data = StormData.get(level);
            if (!data.initialized) {
                player.sendSystemMessage(Component.literal("Storm not initialized."));
                return;
            }

            int circles = Math.max(1, Math.min(64, payload.circleCount()));
            double scale = Math.max(1.0, payload.pathRadiusScale());
            StormConfig.pathCircleCount = circles;
            StormConfig.pathRadiusScale = scale;
            if (payload.speedPhaseRatePerTick() > 1e-15) {
                StormConfig.speedPhaseRatePerTick = payload.speedPhaseRatePerTick();
            }
            if (payload.peakSpeed() > 1e-9) {
                data.speed = payload.peakSpeed();
            }

            double worldX = data.centerX;
            double worldY = data.centerY;
            double worldZ = data.centerZ;
            double startT = payload.pathParam();

            boolean samePath = data.hasPath()
                    && data.pathSeed == payload.seed()
                    && data.circles.length == circles
                    && Double.compare(data.circles[0].r(), scale) == 0;
            if (!samePath) {
                // New geometry: keep world XZ, start at the preview marker parameter.
                StormLogic.regeneratePath(
                        level.random,
                        data,
                        payload.seed(),
                        worldX,
                        worldY,
                        worldZ
                );
            }
            // Live or new: keep the storm where it is; re-anchor so pathParam is the start.
            data.pathParam = startT;
            data.rebasePathOriginTo(worldX, worldZ);
            data.applyPathPosition();
            StormFourierPath.Vec2 tangent = StormFourierPath.derivative(data.circles, data.pathParam);
            if (tangent.length() > StormConfig.pathDerivEpsilon) {
                data.yawRad = StormFourierPath.yawFromVelocity(tangent.x(), tangent.z());
            }
            // Offset speed pulse at the current location (does not move the storm).
            data.speedPhase = StormFourierPath.speedPhaseFromOffsetTurns(payload.speedPhaseOffsetTurns());
            data.setDirty();

            StormEvents.syncToDimension(level, data);
            double phaseMin = StormConfig.speedPhaseRatePerTick > 0.0
                    ? (Math.PI * 2.0) / (StormConfig.speedPhaseRatePerTick * 60.0 * 20.0)
                    : Double.POSITIVE_INFINITY;
            double turns = StormFourierPath.offsetTurnsFromSpeedPhase(data.speedPhase);
            player.sendSystemMessage(Component.literal(String.format(
                    "Applied path preview (seed=%d, circles=%d, scale=%.1f, peak=%.6f, pulse=%.2f min, phase=%.3f turns, t=%.3f)",
                    payload.seed(),
                    StormConfig.pathCircleCount,
                    StormConfig.pathRadiusScale,
                    data.speed,
                    phaseMin,
                    turns,
                    data.pathParam
            )));
        });
    }
}
