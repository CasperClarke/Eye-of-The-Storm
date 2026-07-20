package com.eyeofthestorm.network;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.storm.RadarPlayerColors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/** Server → client: all overworld players' radar positions and colors. */
public record RadarPlayersPayload(List<RadarPlayerEntry> players) implements CustomPacketPayload {
    public static final Type<RadarPlayersPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EyeOfTheStormMod.MOD_ID, "radar_players"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadarPlayersPayload> STREAM_CODEC =
            StreamCodec.of(RadarPlayersPayload::encode, RadarPlayersPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, RadarPlayersPayload p) {
        buf.writeVarInt(p.players().size());
        for (RadarPlayerEntry entry : p.players()) {
            RadarPlayerEntry.encode(buf, entry);
        }
    }

    private static RadarPlayersPayload decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<RadarPlayerEntry> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            players.add(RadarPlayerEntry.decode(buf));
        }
        return new RadarPlayersPayload(players);
    }

    public static RadarPlayersPayload from(ServerLevel level) {
        List<RadarPlayerEntry> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            int[] color = RadarPlayerColors.get(player.getUUID());
            players.add(new RadarPlayerEntry(
                    player.getUUID(),
                    player.getX(),
                    player.getZ(),
                    player.getYRot(),
                    color[0],
                    color[1],
                    color[2]
            ));
        }
        return new RadarPlayersPayload(players);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RadarPlayersPayload::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EyeOfTheStormMod.MOD_ID).versioned("13");
        registrar.playToClient(TYPE, STREAM_CODEC, RadarPlayersPayload::handleClient);
    }

    private static void handleClient(RadarPlayersPayload payload, IPayloadContext context) {
        ClientPacketHooks.radarPlayers.accept(payload, context);
    }
}
