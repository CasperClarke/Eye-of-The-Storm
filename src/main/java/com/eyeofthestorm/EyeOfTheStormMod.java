package com.eyeofthestorm;

import com.eyeofthestorm.command.StormCommands;
import com.eyeofthestorm.network.StormSyncPayload;
import com.eyeofthestorm.registry.ModItems;
import com.eyeofthestorm.storm.StormEvents;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(EyeOfTheStormMod.MOD_ID)
public class EyeOfTheStormMod {
    public static final String MOD_ID = "eyeofthestorm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EyeOfTheStormMod(IEventBus modBus) {
        ModItems.register(modBus);
        StormSyncPayload.register(modBus);
        NeoForge.EVENT_BUS.register(StormEvents.class);
        NeoForge.EVENT_BUS.register(StormCommands.class);
        LOGGER.info("Eye of the Storm mod ready (full simulation + client wall/vignette)");
    }
}
