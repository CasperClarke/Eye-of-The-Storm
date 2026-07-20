package com.eyeofthestorm.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** One player marker synced for the storm radar HUD. */
public record RadarPlayerEntry(
        UUID uuid,
        double x,
        double z,
        float yawDeg,
        int colorR,
        int colorG,
        int colorB
) {
    public static void encode(FriendlyByteBuf buf, RadarPlayerEntry entry) {
        buf.writeUUID(entry.uuid());
        buf.writeDouble(entry.x());
        buf.writeDouble(entry.z());
        buf.writeFloat(entry.yawDeg());
        buf.writeByte(entry.colorR());
        buf.writeByte(entry.colorG());
        buf.writeByte(entry.colorB());
    }

    public static RadarPlayerEntry decode(FriendlyByteBuf buf) {
        return new RadarPlayerEntry(
                buf.readUUID(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                Byte.toUnsignedInt(buf.readByte()),
                Byte.toUnsignedInt(buf.readByte()),
                Byte.toUnsignedInt(buf.readByte())
        );
    }
}
