package com.tenfold.common;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * 临时装备的「防刷」处理：神备 / EGO 绽放强制穿上的护甲都打上
 * **绑定诅咒 + 消失诅咒**——玩家脱不下来、死了也带不走，避免被拿去刷装备。
 */
public final class TempGear {

    private TempGear() {
    }

    /** 给一件临时装备附上绑定诅咒与消失诅咒（各 1 级）。 */
    public static ItemStack cursed(ItemStack stack) {
        stack.enchant(Enchantments.BINDING_CURSE, 1);
        stack.enchant(Enchantments.VANISHING_CURSE, 1);
        return stack;
    }

    /** 直接按物品生成一件带诅咒的临时装备。 */
    public static ItemStack cursedCopy(ItemStack source) {
        return cursed(source.copy());
    }
}
