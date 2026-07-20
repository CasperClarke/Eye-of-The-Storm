package com.eyeofthestorm.network;

import com.eyeofthestorm.EyeOfTheStormMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Server → client: apply an authoritative radar marker RGB (e.g. from a command). */
public record PushRadarColorPayload(int colorR, int colorG, int colorB) implements CustomPacketPayload {
    public static final Type<PushRadarColorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EyeOfTheStormMod.MOD_ID, "push_radar_color"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PushRadarColorPayload> STREAM_CODEC =
            StreamCodec.of(PushRadarColorPayload::encode, PushRadarColorPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, PushRadarColorPayload p) {
        buf.writeByte(p.colorR());
        buf.writeByte(p.colorG());
        buf.writeByte(p.colorB());
    }

    private static PushRadarColorPayload decode(RegistryFriendlyByteBuf buf) {
        return new PushRadarColorPayload(
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
        modBus.addListener(PushRadarColorPayload::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EyeOfTheStormMod.MOD_ID).versioned("13");
        registrar.playToClient(TYPE, STREAM_CODEC, PushRadarColorPayload::handleClient);
    }

    private static void handleClient(PushRadarColorPayload payload, IPayloadContext context) {
        ClientPacketHooks.pushRadarColor.accept(payload, context);
    }
}
