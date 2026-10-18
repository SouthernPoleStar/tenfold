package com.tenfold.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 硬血武器：硬血瓶 + 木棍合成。潜行+右键循环切换四形态：
 * 长枪（突刺直线）/ 长刀（双持 17 + 右键格挡）/ 匕首 / 血弓（拉弓射箭）。
 * <p>
 * 只有血魔能挥动：非血魔的攻击会被 {@code BloodDemonEvents#onLivingHurt}
 * 直接压成 0 并提示。
 */
public class HardBloodWeaponItem extends SwordItem {

    public static final String FORM_KEY = "HardBloodForm";
    /** 血弓每支箭的伤害。 */
    public static final double BLOOD_BOW_ARROW_DAMAGE = 12.0D;
    /** 双持长刀的伤害。 */
    public static final float DUAL_LONG_BLADE_DAMAGE = 17.0F;

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

    /** 是否「双持长刀」：主副手都是硬血武器，且都处于长刀形态。 */
    public static boolean isDualWieldingLongBlades(Player player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        return main.is(com.tenfold.ModItems.HARD_BLOOD_WEAPON.get())
                && off.is(com.tenfold.ModItems.HARD_BLOOD_WEAPON.get())
                && form(main) == HardBloodForm.LONG_BLADE
                && form(off) == HardBloodForm.LONG_BLADE;
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
    /* 交互：潜行+右键切形态；长刀双持右键格挡；血弓拉弓                  */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        HardBloodForm current = form(stack);
        boolean sneaking = player.isShiftKeyDown();

        // 双持长刀：右键举刀格挡（减伤与反弹在 ShieldBlockEvent 结算）。
        if (!sneaking && current == HardBloodForm.LONG_BLADE && isDualWieldingLongBlades(player)) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        // 血弓：拉弓。
        if (!sneaking && current == HardBloodForm.BLOOD_BOW) {
            if (hasArrow(player)) {
                player.startUsingItem(hand);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5F, 0.4F);
                return InteractionResultHolder.consume(stack);
            }
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("tenfold.hard_blood.no_arrow"), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide && sneaking) {
            HardBloodForm next = current.next();
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
    /* 血弓：蓄力与放箭                                                    */
    /* ------------------------------------------------------------------ */

    @Override
    public int getUseDuration(ItemStack stack) {
        HardBloodForm current = form(stack);
        if (current == HardBloodForm.BLOOD_BOW || current == HardBloodForm.LONG_BLADE) {
            return 72000;
        }
        return 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return switch (form(stack)) {
            case BLOOD_BOW -> UseAnim.BOW;
            case LONG_BLADE -> UseAnim.BLOCK;
            default -> UseAnim.NONE;
        };
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player) || form(stack) != HardBloodForm.BLOOD_BOW) {
            return;
        }
        int charge = this.getUseDuration(stack) - timeLeft;
        float power = getPowerForTime(charge);
        if (power < 0.1F || level.isClientSide || !consumeArrow(player)) {
            return;
        }
        Arrow arrow = new Arrow(level, player);
        arrow.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(),
                0.0F, power * 3.0F, 1.0F);
        arrow.setBaseDamage(BLOOD_BOW_ARROW_DAMAGE);
        if (power >= 1.0F) {
            arrow.setCritArrow(true);
        }
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        level.addFreshEntity(arrow);
    }

    private static float getPowerForTime(int chargeTicks) {
        float f = chargeTicks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        return Math.min(f, 1.0F);
    }

    private static boolean hasArrow(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.ARROW)) {
                return true;
            }
        }
        return player.getAbilities().instabuild;
    }

    private static boolean consumeArrow(Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.ARROW)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------ */
    /* 长刀双持格挡（vanilla isBlocking 需要 SHIELD_BLOCK 支持）            */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return toolAction == ToolActions.SHIELD_BLOCK
                && form(stack) == HardBloodForm.LONG_BLADE;
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
        HardBloodForm current = form(stack);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        base.forEach((attribute, modifier) -> {
            if (attribute != Attributes.ATTACK_DAMAGE && attribute != Attributes.ATTACK_SPEED) {
                builder.put(attribute, modifier);
            }
        });
        // ATTACK_DAMAGE = 1（拳头）+ modifier，减去 1 后正好等于 form.damage。
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                ATTACK_DAMAGE_UUID, "Hard blood weapon modifier",
                current.damage - 1.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_UUID, "Hard blood weapon modifier",
                current.attackSpeed, AttributeModifier.Operation.ADDITION));
        builder.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                ENTITY_REACH_UUID, "Hard blood weapon modifier",
                current.reach - 3.0D, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    /* ------------------------------------------------------------------ */
    /* 提示                                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        HardBloodForm current = form(stack);
        tooltip.add(Component.literal("\u25C6 ").withStyle(ChatFormatting.DARK_GRAY).append(current.display()));
        if (current == HardBloodForm.BLOOD_BOW) {
            tooltip.add(Component.translatable("tooltip.tenfold.hard_blood.bow",
                            String.format("%.0f", BLOOD_BOW_ARROW_DAMAGE))
                    .withStyle(ChatFormatting.RED));
        } else if (current == HardBloodForm.LONG_BLADE) {
            tooltip.add(Component.translatable("tooltip.tenfold.hard_blood.long_blade",
                            String.format("%.0f", current.damage),
                            String.format("%.0f", DUAL_LONG_BLADE_DAMAGE))
                    .withStyle(ChatFormatting.RED));
        } else if (current == HardBloodForm.SPEAR) {
            tooltip.add(Component.translatable("tooltip.tenfold.damage",
                            String.format("%.1f", current.damage))
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.tenfold.hard_blood.spear")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.tenfold.damage",
                            String.format("%.1f", current.damage))
                    .withStyle(ChatFormatting.RED));
        }
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
