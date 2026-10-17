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
 * 遮盖伤口的假面 — a white mask with a golden rim, worn over the upper third
 * of the face. While worn: permanent Strength II and Resistance I, and the
 * player can no longer receive 指令成瘾 (the interception lives in
 * CommonEvents' MobEffectEvent.Applicable handler).
 */
public class MaskItem extends Item {

    public MaskItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public net.minecraft.world.entity.EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return net.minecraft.world.entity.EquipmentSlot.HEAD;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tenfold.mask.buffs")
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tenfold.mask.block")
                .withStyle(ChatFormatting.DARK_RED));
    }
}
