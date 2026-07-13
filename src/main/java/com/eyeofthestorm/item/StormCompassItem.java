package com.eyeofthestorm.item;

import com.eyeofthestorm.compat.SimulatedCompatBridge;
import com.eyeofthestorm.storm.StormData;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Lodestone-style compass that tracks the storm eye.
 * Extends {@link CompassItem} so Create Aeronautics can treat it like a normal compass.
 */
public class StormCompassItem extends CompassItem {
    public StormCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        SimulatedCompatBridge.clearBrokenLodestoneTracker(stack);

        if (stack.has(DataComponents.LODESTONE_TRACKER)) {
            return;
        }

        ServerLevel overworld = serverLevel.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        StormData data = StormData.get(overworld);
        if (!data.initialized) {
            return;
        }

        // Link once so the item shows as tracking; client needle uses live sync data instead.
        GlobalPos target = GlobalPos.of(Level.OVERWORLD, net.minecraft.core.BlockPos.ZERO);
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), false));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(DataComponents.LODESTONE_TRACKER);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return stack.has(DataComponents.LODESTONE_TRACKER)
                ? "item.eyeofthestorm.storm_compass"
                : super.getDescriptionId(stack);
    }
}
