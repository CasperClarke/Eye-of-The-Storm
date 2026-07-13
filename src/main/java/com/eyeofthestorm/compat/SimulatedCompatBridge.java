package com.eyeofthestorm.compat;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Strips simulated lodestone tracker data that breaks vanilla compass rendering in inventory.
 */
public final class SimulatedCompatBridge {
    private static DataComponentType<?> lodestoneTrackerComponent;

    private SimulatedCompatBridge() {}

    public static void clearBrokenLodestoneTracker(ItemStack stack) {
        if (!ModList.get().isLoaded("simulated")) {
            return;
        }
        DataComponentType<?> component = lodestoneTrackerComponent();
        if (component != null && stack.has(component)) {
            stack.remove(component);
        }
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<?> lodestoneTrackerComponent() {
        if (lodestoneTrackerComponent == null) {
            try {
                Class<?> simData = Class.forName("dev.simulated_team.simulated.index.SimDataComponents");
                lodestoneTrackerComponent = (DataComponentType<?>) simData.getField("LODESTONE_COMPASS_SUBLEVEL_TRACKER").get(null);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return lodestoneTrackerComponent;
    }
}
