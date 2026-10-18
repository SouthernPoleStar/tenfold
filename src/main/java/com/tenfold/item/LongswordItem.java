package com.tenfold.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.tenfold.ModEffects;
import com.tenfold.common.UnstableGearManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 长剑：基础攻击力 10。每次命中叠加一层「情感」，满 15 层后 Shift+右键消耗
 * 全部情感进入「不稳定的神备」。
 * <p>
 * 神备期间：长剑攻击力 +30、命中使目标灼烧；其他增益/代价在
 * {@link UnstableGearManager} 与 {@code CommonEvents} 里结算。
 */
public class LongswordItem extends SwordItem {

    private static final java.util.UUID DAMAGE_UUID =
            java.util.UUID.fromString("C4E1A7B2-5D38-4F96-8A20-7B1E3C9D5F01");
    private static final java.util.UUID SPEED_UUID =
            java.util.UUID.fromString("C4E1A7B2-5D38-4F96-8A20-7B1E3C9D5F02");

    public LongswordItem() {
        super(Tiers.IRON, 3, -2.4F, new net.minecraft.world.item.Item.Properties().durability(1500));
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
        // ATTACK_DAMAGE 显示值 = 1（徒手）+ 修正值，所以写 9 才能正好是 10。
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                DAMAGE_UUID, "Longsword modifier", 9.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                SPEED_UUID, "Longsword modifier", -2.4D, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide && attacker instanceof ServerPlayer player) {
            UnstableGearManager.addEmotion(player);
            if (player.hasEffect(ModEffects.UNSTABLE_GEAR.get())) {
                target.setSecondsOnFire(4);
            }
        }
        return result;
    }

    /** Shift+右键：情感满 15 层时进入「不稳定的神备」。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            UnstableGearManager.tryEnter(server);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        use(context.getLevel(), player, context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tenfold.longsword.damage")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.tenfold.longsword.emotion")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.tenfold.longsword.gear")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.tenfold.longsword.help")
                .withStyle(ChatFormatting.DARK_GRAY));
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
        return repair.is(net.minecraft.world.item.Items.IRON_INGOT);
    }
}
