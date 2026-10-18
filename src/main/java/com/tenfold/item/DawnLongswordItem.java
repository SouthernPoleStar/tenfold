package com.tenfold.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.tenfold.common.DawnManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 黎明长剑：基础攻击力 20。每次命中获得一层「黎明」（每层攻击力 +3），
 * 满 10 层后自动转化为「天将拂晓」。
 */
public class DawnLongswordItem extends SwordItem {

    private static final java.util.UUID DAMAGE_UUID =
            java.util.UUID.fromString("E5F6A7B8-0001-4C90-8D12-34567890ABCD");
    private static final java.util.UUID SPEED_UUID =
            java.util.UUID.fromString("E5F6A7B8-0002-4C90-8D12-34567890ABCD");

    public DawnLongswordItem() {
        super(Tiers.NETHERITE, 3, -2.4F, new net.minecraft.world.item.Item.Properties().durability(2000));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.MAINHAND) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        base.forEach((attribute, modifier) -> {
            if (attribute != Attributes.ATTACK_DAMAGE && attribute != Attributes.ATTACK_SPEED) {
                builder.put(attribute, modifier);
            }
        });
        // ATTACK_DAMAGE 显示值 = 1（徒手）+ 修正值，所以写 19 才能正好是 20。
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                DAMAGE_UUID, "Dawn longsword modifier", 19.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                SPEED_UUID, "Dawn longsword modifier", -2.4D, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide && attacker instanceof ServerPlayer player) {
            DawnManager.addDawn(player);
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tenfold.dawn.damage").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.tenfold.dawn.stack").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.tenfold.dawn.ascension").withStyle(ChatFormatting.RED));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(net.minecraft.world.item.Items.NETHERITE_INGOT);
    }
}
