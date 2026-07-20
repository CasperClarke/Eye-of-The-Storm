package com.eyeofthestorm.network;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.storm.RadarPlayerColors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Client → server: player's chosen radar marker RGB. */
public record SetRadarColorPayload(int colorR, int colorG, int colorB) implements CustomPacketPayload {
    public static final Type<SetRadarColorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EyeOfTheStormMod.MOD_ID, "set_radar_color"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRadarColorPayload> STREAM_CODEC =
            StreamCodec.of(SetRadarColorPayload::encode, SetRadarColorPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, SetRadarColorPayload p) {
        buf.writeByte(p.colorR());
        buf.writeByte(p.colorG());
        buf.writeByte(p.colorB());
    }

    private static SetRadarColorPayload decode(RegistryFriendlyByteBuf buf) {
        return new SetRadarColorPayload(
                Byte.toUnsignedInt(buf.readByte()),
                Byte.toUnsignedInt(buf.readByte()),
                Byte.toUnsignedInt(buf.readByte())
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(SetRadarColorPayload::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EyeOfTheStormMod.MOD_ID).versioned("13");
        registrar.playToServer(TYPE, STREAM_CODEC, SetRadarColorPayload::handleServer);
    }

    private static void handleServer(SetRadarColorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            RadarPlayerColors.set(player.getUUID(), payload.colorR(), payload.colorG(), payload.colorB());
        });
    }
}
