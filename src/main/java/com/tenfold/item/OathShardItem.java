package com.tenfold.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * "守护他人的决意" — dropped when a tamed creature is killed by a hostile mob.
 * Brewed into the IRA potion.
 */
public class OathShardItem extends Item {

    public OathShardItem() {
        super(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tenfold.oath_shard.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tenfold.oath_shard.brew")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
