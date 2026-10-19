package com.tenfold.common;

import net.minecraft.server.level.ServerPlayer;
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

    /**
     * 从背包里移除一件与 {@code stack} 完全相同的复制品。
     * <p>
     * 用于「强制换装」结束、把原护甲放回装备槽时清理：正常情况下原护甲只被保存了一份，
     * 背包里不应该还有同样的另一件；如果存在，说明是旧逻辑（或其它模组）复制出来的，清掉避免刷装备。
     *
     * @return 是否真的移除了
     */
    public static boolean removeDuplicate(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack candidate = items.get(i);
            if (candidate.isEmpty() || candidate.getCount() != stack.getCount()) {
                continue;
            }
            if (candidate.is(stack.getItem()) && ItemStack.isSameItemSameTags(candidate, stack)) {
                items.set(i, ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }
}
