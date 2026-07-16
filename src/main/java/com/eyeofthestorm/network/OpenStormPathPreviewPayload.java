package com.eyeofthestorm.network;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.StormConfig;
import com.eyeofthestorm.client.StormPathPreviewScreen;
import com.eyeofthestorm.storm.StormData;
import com.eyeofthestorm.storm.StormStrongholds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/** Server → client: open the storm path preview menu with live path + strongholds. */
public record OpenStormPathPreviewPayload(
        long seed,
        int circleCount,
        double pathRadiusScale,
        double peakSpeed,
        double speedPhaseRatePerTick,
        double pathParam,
        double speedPhase,
        double pathOriginX,
        double pathOriginZ,
        double eyeRadius,
        double[] strongholdX,
        double[] strongholdZ
) implements CustomPacketPayload {
    public static final Type<OpenStormPathPreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EyeOfTheStormMod.MOD_ID, "open_path_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenStormPathPreviewPayload> STREAM_CODEC =
            StreamCodec.of(OpenStormPathPreviewPayload::encode, OpenStormPathPreviewPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, OpenStormPathPreviewPayload p) {
        buf.writeLong(p.seed());
        buf.writeVarInt(p.circleCount());
        buf.writeDouble(p.pathRadiusScale());
        buf.writeDouble(p.peakSpeed());
        buf.writeDouble(p.speedPhaseRatePerTick());
        buf.writeDouble(p.pathParam());
        buf.writeDouble(p.speedPhase());
        buf.writeDouble(p.pathOriginX());
        buf.writeDouble(p.pathOriginZ());
        buf.writeDouble(p.eyeRadius());
        int n = Math.min(p.strongholdX().length, p.strongholdZ().length);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeDouble(p.strongholdX()[i]);
            buf.writeDouble(p.strongholdZ()[i]);
        }
    }

    private static OpenStormPathPreviewPayload decode(RegistryFriendlyByteBuf buf) {
        long seed = buf.readLong();
        int circles = buf.readVarInt();
        double scale = buf.readDouble();
        double peak = buf.readDouble();
        double phaseRate = buf.readDouble();
        double pathParam = buf.readDouble();
        double speedPhase = buf.readDouble();
        double originX = buf.readDouble();
        double originZ = buf.readDouble();
        double eyeRadius = buf.readDouble();
        int n = buf.readVarInt();
        double[] shX = new double[n];
        double[] shZ = new double[n];
        for (int i = 0; i < n; i++) {
            shX[i] = buf.readDouble();
            shZ[i] = buf.readDouble();
        }
        return new OpenStormPathPreviewPayload(
                seed, circles, scale, peak, phaseRate,
                pathParam, speedPhase, originX, originZ, eyeRadius, shX, shZ
        );
    }

    public static OpenStormPathPreviewPayload from(ServerLevel level, StormData data) {
        long seed = data.hasPath() ? data.pathSeed : System.nanoTime();
        int circles = data.hasPath() ? data.circles.length : StormConfig.pathCircleCount;
        double scale = data.hasPath()
                ? data.circles[0].r()
                : StormConfig.pathRadiusScale;
        double peak = data.initialized && data.speed > 1e-9 ? data.speed : StormConfig.defaultSpeed;
        double phaseRate = StormConfig.speedPhaseRatePerTick > 1e-15
                ? StormConfig.speedPhaseRatePerTick
                : (Math.PI * 2.0) / (30.0 * 60.0 * 20.0);
        double eye = data.initialized && data.radius > 1.0 ? data.radius : StormConfig.defaultRadius;

        List<StormStrongholds.Pos> strongholds = StormStrongholds.locateAll(level);
        double[][] packed = StormStrongholds.toArrays(strongholds);

        return new OpenStormPathPreviewPayload(
                seed,
                circles,
                scale,
                peak,
                phaseRate,
                data.pathParam,
                data.speedPhase,
                data.pathOriginX,
                data.pathOriginZ,
                eye,
                packed[0],
                packed[1]
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(OpenStormPathPreviewPayload::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EyeOfTheStormMod.MOD_ID).versioned("11");
        registrar.playToClient(TYPE, STREAM_CODEC, OpenStormPathPreviewPayload::handleClient);
    }

    private static void handleClient(OpenStormPathPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new StormPathPreviewScreen(
                payload.seed(),
                payload.circleCount(),
                payload.pathRadiusScale(),
                payload.peakSpeed(),
                payload.speedPhaseRatePerTick(),
                payload.pathParam(),
                payload.speedPhase(),
                payload.pathOriginX(),
                payload.pathOriginZ(),
                payload.eyeRadius(),
                payload.strongholdX(),
                payload.strongholdZ()
        )));
    }
}
