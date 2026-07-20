package com.eyeofthestorm.network;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.StormConfig;
import com.eyeofthestorm.storm.StormData;
import com.eyeofthestorm.storm.StormLogic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public record StormSyncPayload(
        boolean initialized,
        boolean active,
        boolean paused,
        double centerX,
        double centerY,
        double centerZ,
        double radius,
        double speed,
        float wallSpinDeg,
        float yawRad,
        long pathSeed,
        double pathParam,
        double pathOriginX,
        double pathOriginZ,
        double speedPhase,
        double peakSpeed,
        int pathCircleCount,
        double pathRadiusScale,
        double speedPhaseRatePerTick
) implements CustomPacketPayload {
    public static final Type<StormSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EyeOfTheStormMod.MOD_ID, "sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StormSyncPayload> STREAM_CODEC =
            StreamCodec.of(StormSyncPayload::encode, StormSyncPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, StormSyncPayload p) {
        buf.writeBoolean(p.initialized());
        buf.writeBoolean(p.active());
        buf.writeBoolean(p.paused());
        buf.writeDouble(p.centerX());
        buf.writeDouble(p.centerY());
        buf.writeDouble(p.centerZ());
        buf.writeDouble(p.radius());
        buf.writeDouble(p.speed());
        buf.writeFloat(p.wallSpinDeg());
        buf.writeFloat(p.yawRad());
        buf.writeLong(p.pathSeed());
        buf.writeDouble(p.pathParam());
        buf.writeDouble(p.pathOriginX());
        buf.writeDouble(p.pathOriginZ());
        buf.writeDouble(p.speedPhase());
        buf.writeDouble(p.peakSpeed());
        buf.writeVarInt(p.pathCircleCount());
        buf.writeDouble(p.pathRadiusScale());
        buf.writeDouble(p.speedPhaseRatePerTick());
    }

    private static StormSyncPayload decode(RegistryFriendlyByteBuf buf) {
        return new StormSyncPayload(
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readLong(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble()
        );
    }

    public static StormSyncPayload from(StormData data) {
        return new StormSyncPayload(
                data.initialized,
                data.initialized && data.active,
                data.paused,
                data.centerX,
                data.centerY,
                data.centerZ,
                data.radius,
                StormLogic.instantaneousSpeed(data),
                data.wallSpinDeg,
                (float) data.yawRad,
                data.pathSeed,
                data.pathParam,
                data.pathOriginX,
                data.pathOriginZ,
                data.speedPhase,
                data.speed,
                data.hasPath() ? data.circles.length : StormConfig.pathCircleCount,
                data.hasPath() ? data.circles[0].r() : StormConfig.pathRadiusScale,
                StormConfig.speedPhaseRatePerTick
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(StormSyncPayload::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EyeOfTheStormMod.MOD_ID).versioned("13");
        registrar.playToClient(TYPE, STREAM_CODEC, StormSyncPayload::handleClient);
    }

    private static void handleClient(StormSyncPayload payload, IPayloadContext context) {
        ClientPacketHooks.stormSync.accept(payload, context);
    }
}
