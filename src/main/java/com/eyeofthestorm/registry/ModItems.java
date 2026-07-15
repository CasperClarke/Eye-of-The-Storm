package com.eyeofthestorm.registry;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.item.StormCompassItem;
import com.eyeofthestorm.item.StormMapItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EyeOfTheStormMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EyeOfTheStormMod.MOD_ID);

    public static final DeferredItem<StormCompassItem> STORM_COMPASS = ITEMS.register("storm_compass",
            () -> new StormCompassItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<StormMapItem> STORM_MAP = ITEMS.register("storm_map",
            () -> new StormMapItem(new Item.Properties().stacksTo(1)));

    public static final net.neoforged.neoforge.registries.DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.eyeofthestorm"))
                    .icon(() -> STORM_COMPASS.get().getDefaultInstance())
                    .displayItems((params, out) -> {
                        out.accept(STORM_COMPASS.get());
                        out.accept(STORM_MAP.get());
                    })
                    .build());

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
