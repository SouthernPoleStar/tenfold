package com.tenfold.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.UUID;

/**
 * 「不稳定的神备」期间强制穿上的套装：血雷 / 蜡翼 / 融化的蜡 / 皮鞋。
 * <p>
 * 每件护甲值与韧性不同（韧性无法在 {@link ArmorMaterial} 里按部位区分，所以由
 * 物品自己的属性修正补上）。材质名是 {@code tenfold:gear}，对应的盔甲层
 * {@code textures/models/armor/gear_layer_1.png} 是全透明的，所以穿上不显示模型。
 */
public class GearArmorItem extends ArmorItem {

    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return 1200;
        }

        @Override
        public int getDefenseForType(Type type) {
            return switch (type) {
                case HELMET -> 5;
                case CHESTPLATE -> 5;
                case LEGGINGS -> 6;
                case BOOTS -> 7;
            };
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
            // 带命名空间：盔甲层会解析到 tenfold:textures/models/armor/gear_layer_1.png（全透明）。
            return "tenfold:gear";
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

    private final double toughness;
    private final UUID toughnessId;

    public GearArmorItem(Type type, double toughness, String uuid) {
        super(MATERIAL, type, new net.minecraft.world.item.Item.Properties().stacksTo(1));
        this.toughness = toughness;
        this.toughnessId = UUID.fromString(uuid);
    }

    /** 盔甲韧性按件补上（材质只能给统一的 0）。 */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != this.getType().getSlot()) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        base.forEach(builder::put);
        builder.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(
                this.toughnessId, "tenfold_gear_toughness", this.toughness,
                AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    /** 神备套装不允许被丢出去，避免强制换装时复制物品。 */
    @Override
    public boolean onDroppedByPlayer(ItemStack item, net.minecraft.world.entity.player.Player player) {
        return false;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }
}
