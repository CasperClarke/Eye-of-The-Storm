package com.eyeofthestorm.item;

import com.eyeofthestorm.network.ClientItemHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Toggles the storm radar HUD when used. The radar itself is controlled client-side.
 */
public class StormMapItem extends Item {
    public StormMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            ClientItemHooks.toggleStormRadar.accept(player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
