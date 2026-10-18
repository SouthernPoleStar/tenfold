package com.tenfold.item;

import com.tenfold.common.BloodDemonManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 血魔老祖的牙：右键使用，把自己 100% 转化为二代血魔。
 * 已经是血魔的话没有效果。
 */
public class BloodFangItem extends Item {

    public BloodFangItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (BloodDemonManager.isBloodDemon(serverPlayer)) {
                serverPlayer.displayClientMessage(
                        Component.translatable("tenfold.blood.fang_already"), false);
            } else {
                BloodDemonManager.convert(serverPlayer, 2);
                if (!serverPlayer.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }
}
