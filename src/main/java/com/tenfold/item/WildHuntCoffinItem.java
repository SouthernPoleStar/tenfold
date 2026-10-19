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
 * 狂猎棺材：胸甲，**20 点护甲 / 18 点盔甲韧性**，穿上不显示模型。
 * <p>
 * 与狂猎大剑一起装备时触发「撕裂的心」（见 {@code WildHuntManager}），
 * 并解锁杜拉奇坐骑与死亡冲撞。
 */
public class WildHuntCoffinItem extends ArmorItem {

    private static final UUID TOUGHNESS_ID =
            UUID.fromString("C3D4E5F6-0002-4A71-8B92-3456789012CD");

    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return 2000;
        }

        @Override
        public int getDefenseForType(Type type) {
            // 只有胸甲会被实例化：20 点护甲。
            return type == Type.CHESTPLATE ? 20 : 0;
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
            return "tenfold:wild_hunt_coffin";
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

    public WildHuntCoffinItem() {
        super(MATERIAL, Type.CHESTPLATE, new net.minecraft.world.item.Item.Properties().stacksTo(1));
    }

    /** 韧性 18 按件补上（材质只能给统一的 0）。 */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.CHEST) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        base.forEach(builder::put);
        builder.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(
                TOUGHNESS_ID, "tenfold_wild_hunt_coffin", 18.0D, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    /** 全透明盔甲层，穿在身上不显示模型。 */
    @Override
    @Nullable
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, @Nullable String type) {
        return "tenfold:textures/models/armor/wild_hunt_coffin_layer_1.png";
    }
}
