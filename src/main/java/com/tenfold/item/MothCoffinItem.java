package com.tenfold.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 炎蝶之棺：胸甲，10 点护甲、12 点盔甲韧性，穿上不显示模型。
 * <p>
 * 常驻抗性提升 III + 夜视 I；手持黎明长剑时额外常驻力量 IV。
 * 这些效果的刷新在 {@code DawnManager#tickPlayer} 里。
 */
public class MothCoffinItem extends ArmorItem {

    private static final UUID TOUGHNESS_ID =
            UUID.fromString("B2C3D4E5-0001-4F60-9A81-2345678901BC");

    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return 1500;
        }

        @Override
        public int getDefenseForType(Type type) {
            // 只有胸甲会被实例化；这里给胸甲 10 点护甲。
            return type == Type.CHESTPLATE ? 10 : 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 15;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_NETHERITE;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            // 带命名空间：盔甲层解析到 tenfold:textures/models/armor/moth_coffin_layer_1.png（全透明）。
            return "tenfold:moth_coffin";
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

    public MothCoffinItem() {
        super(MATERIAL, Type.CHESTPLATE, new net.minecraft.world.item.Item.Properties().stacksTo(1));
    }

    /** 韧性 12 按件补上（材质只能给统一的 0）。 */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.CHEST) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        base.forEach(builder::put);
        builder.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(
                TOUGHNESS_ID, "tenfold_moth_coffin", 12.0D, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    /** 直接给出完整贴图路径，避免材质名解析到错误命名空间后变成紫黑棋盘。 */
    @Override
    @Nullable
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, @Nullable String type) {
        return "tenfold:textures/models/armor/moth_coffin_layer_1.png";
    }
}
