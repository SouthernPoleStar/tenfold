package com.tenfold.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 遮盖伤口的假面 — a real helmet so it renders on the face properly.
 * White mask with a golden rim. While worn: permanent Strength II and
 * Resistance I (kept up in CommonEvents), and 指令成瘾 can no longer be
 * applied (blocked there too).
 */
public class MaskItem extends ArmorItem {

    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(net.minecraft.world.item.ArmorItem.Type type) {
            return 200;
        }

        @Override
        public int getDefenseForType(net.minecraft.world.item.ArmorItem.Type type) {
            return 1;
        }

        @Override
        public int getEnchantmentValue() {
            return 10;
        }

        @Override
        public net.minecraft.sounds.SoundEvent getEquipSound() {
            return net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER;
        }

        @Override
        public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
            return net.minecraft.world.item.crafting.Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return "tenfold_mask";
        }

        @Override
        public float getToughness() {
            return 0.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    };

    public MaskItem() {
        super(MATERIAL, Type.HELMET, new Item.Properties().stacksTo(1));
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
