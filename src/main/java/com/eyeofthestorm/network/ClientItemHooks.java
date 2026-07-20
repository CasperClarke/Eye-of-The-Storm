package com.eyeofthestorm.network;

import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

/** Common-side hooks for client-only item interactions. */
public final class ClientItemHooks {
    private ClientItemHooks() {}

    public static Consumer<Player> toggleStormRadar = player -> {};
}
