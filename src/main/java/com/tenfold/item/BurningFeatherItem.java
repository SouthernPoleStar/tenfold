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
 * 燃烧的羽毛：EGO 绽放期间强制穿上的胸甲。
 * **25 点护甲 / 20 点盔甲韧性**，穿上不显示模型。
 */
public class BurningFeatherItem extends ArmorItem {

    private static final UUID TOUGHNESS_ID =
            UUID.fromString("A9B8C7D6-0008-4E94-9FB5-6789012345FA");

    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return 2500;
        }

        @Override
        public int getDefenseForType(Type type) {
            return type == Type.CHESTPLATE ? 25 : 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 18;
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
            return "tenfold:burning_feather";
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

    public BurningFeatherItem() {
        super(MATERIAL, Type.CHESTPLATE, new net.minecraft.world.item.Item.Properties().stacksTo(1));
    }

    /** 韧性 20 按件补上。 */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.CHEST) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        base.forEach(builder::put);
        builder.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(
                TOUGHNESS_ID, "tenfold_burning_feather", 20.0D, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    /** 全透明盔甲层。 */
    @Override
    @Nullable
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, @Nullable String type) {
        return "tenfold:textures/models/armor/burning_feather_layer_1.png";
    }
}
