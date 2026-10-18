package com.tenfold.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 硬血武器：硬血瓶 + 木棍合成。与双蛇杖一样可以切形态（潜行+右键循环），
 * 但没有指令系统，只有 长枪 / 长刀 / 匕首 三种形态。
 * <p>
 * 只有血魔能挥动：非血魔的攻击会被 {@code BloodDemonEvents#onLivingHurt}
 * 直接压成 0 并提示。
 */
public class HardBloodWeaponItem extends SwordItem {

    public static final String FORM_KEY = "HardBloodForm";

    private static final java.util.UUID ATTACK_DAMAGE_UUID =
            java.util.UUID.fromString("E71A9C2B-77A4-4D11-9B6E-6C5D4E3F2A11");
    private static final java.util.UUID ATTACK_SPEED_UUID =
            java.util.UUID.fromString("E71A9C2B-77A4-4D11-9B6E-6C5D4E3F2A12");
    private static final java.util.UUID ENTITY_REACH_UUID =
            java.util.UUID.fromString("E71A9C2B-77A4-4D11-9B6E-6C5D4E3F2A13");

    public HardBloodWeaponItem() {
        super(Tiers.NETHERITE, 3, -2.4F, new Item.Properties().durability(2500));
    }

    /* ------------------------------------------------------------------ */
    /* 形态                                                                */
    /* ------------------------------------------------------------------ */

    public static HardBloodForm form(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(FORM_KEY, CompoundTag.TAG_INT)) {
            return HardBloodForm.of(tag.getInt(FORM_KEY));
        }
        return HardBloodForm.SPEAR;
    }

    public static void setForm(ItemStack stack, HardBloodForm form) {
        stack.getOrCreateTag().putInt(FORM_KEY, form.index);
        syncModel(stack, form);
    }

    private static void syncModel(ItemStack stack, HardBloodForm form) {
        CompoundTag tag = stack.getOrCreateTag();
        int current = tag.contains("CustomModelData", CompoundTag.TAG_INT)
                ? tag.getInt("CustomModelData") : -1;
        if (current != form.index) {
            tag.putInt("CustomModelData", form.index);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        syncModel(stack, form(stack));
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    /* ------------------------------------------------------------------ */
    /* 交互：潜行+右键切形态                                              */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player.isShiftKeyDown()) {
            HardBloodForm next = form(stack).next();
            setForm(stack, next);
            player.displayClientMessage(Component.translatable("tenfold.hard_blood.form_switched")
                    .append(" ").append(next.display()), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        use(context.getLevel(), player, context.getHand());
        return InteractionResult.CONSUME;
    }

    /* ------------------------------------------------------------------ */
    /* 属性：按形态给伤害 / 攻速 / 触及                                    */
    /* ------------------------------------------------------------------ */

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.MAINHAND) {
            return base;
        }
        HardBloodForm form = form(stack);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        base.forEach((attribute, modifier) -> {
            if (attribute != Attributes.ATTACK_DAMAGE && attribute != Attributes.ATTACK_SPEED) {
                builder.put(attribute, modifier);
            }
        });
        // ATTACK_DAMAGE = 1（拳头）+ modifier，减去 1 后正好等于 form.damage。
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                ATTACK_DAMAGE_UUID, "Hard blood weapon modifier",
                form.damage - 1.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_UUID, "Hard blood weapon modifier",
                form.attackSpeed, AttributeModifier.Operation.ADDITION));
        builder.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                ENTITY_REACH_UUID, "Hard blood weapon modifier",
                form.reach - 3.0D, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    /* ------------------------------------------------------------------ */
    /* 提示                                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        HardBloodForm form = form(stack);
        tooltip.add(Component.literal("\u25C6 ").withStyle(ChatFormatting.DARK_GRAY).append(form.display()));
        tooltip.add(Component.translatable("tooltip.tenfold.damage",
                        String.format("%.1f", form.damage))
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.tenfold.hard_blood.only_demons")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.tenfold.hard_blood.help")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public @Nullable String getCreatorModId(ItemStack itemStack) {
        return com.tenfold.Tenfold.MOD_ID;
    }
}
