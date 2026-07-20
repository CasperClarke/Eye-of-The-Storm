package com.eyeofthestorm.storm;

import com.eyeofthestorm.StormConfig;
import com.eyeofthestorm.network.RadarPlayersPayload;
import com.eyeofthestorm.network.StormSyncPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class StormEvents {
    private StormEvents() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        StormData data = StormData.get(level);
        StormLogic.tick(level, data);

        if (level.getGameTime() % StormConfig.syncIntervalTicks == 0L) {
            syncToDimension(level, data);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, StormSyncPayload.from(StormData.get(level)));
        PacketDistributor.sendToPlayer(player, RadarPlayersPayload.from(level));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadarPlayerColors.remove(player.getUUID());
        }
    }

    public static void syncToDimension(ServerLevel level, StormData data) {
        PacketDistributor.sendToPlayersInDimension(level, StormSyncPayload.from(data));
        PacketDistributor.sendToPlayersInDimension(level, RadarPlayersPayload.from(level));
    }
}
