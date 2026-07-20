package com.eyeofthestorm.client;

import com.eyeofthestorm.network.RadarPlayerEntry;

import java.util.Collections;
import java.util.List;

/** Client cache of synced radar player markers. */
public final class ClientRadarPlayers {
    private static List<RadarPlayerEntry> players = List.of();

    private ClientRadarPlayers() {}

    public static void update(List<RadarPlayerEntry> next) {
        players = List.copyOf(next);
    }

    public static List<RadarPlayerEntry> snapshot() {
        return players;
    }

    public static void reset() {
        players = Collections.emptyList();
    }
}
