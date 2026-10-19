package com.tenfold.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 订婚戒指：两名玩家**各自手持**这枚戒指，由其中一人右键对方即可结为夫妻。
 * 结为夫妻后，任意一方死亡有 40% 概率掉落两个「残片」。
 */
public class EngagementRingItem extends Item {

    public EngagementRingItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tenfold.ring.help")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.tenfold.ring.death")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
