package com.tenfold.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 天退星刀 — a 7-block-reach melee blade fed with two kinds of rounds.
 * <p>
 * 12 猛虎弹 (15 dmg) and 8 猛虎标弹 (30 dmg). When the active magazine runs
 * dry the blade automatically switches to the other one. Sneak + right-click
 * dumps all remaining 猛虎弹 to force-switch to 猛虎标弹. Once both magazines
 * are empty the blade auto-reloads itself exactly once; after that second
 * magazine runs dry it deals a bare 7 damage until a teammate refills it
 * with the 弹药箱.
 */
public class SkyBladeItem extends Item {

    public static final int TIGER_MAG = 12;
    public static final int MARK_MAG = 8;
    public static final int TIGER_DAMAGE = 15;
    public static final int MARK_DAMAGE = 30;
    public static final int EMPTY_DAMAGE = 7;
    /** Extra reach over the vanilla 3-block melee range. */
    public static final double EXTRA_REACH = 4.0D;
    private static final UUID REACH_UUID = UUID.fromString("a1d5c3f0-6b7e-4c19-9d84-3f0a55c0b7a2");

    public SkyBladeItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /* ------------------------------------------------------------------ */
    /* NBT state                                                           */
    /* ------------------------------------------------------------------ */

    private static CompoundTag root(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains("tenfold", CompoundTag.TAG_COMPOUND)) {
            CompoundTag fresh = new CompoundTag();
            // A brand-new blade arrives fully loaded.
            fresh.putInt("tiger", TIGER_MAG);
            fresh.putInt("mark", MARK_MAG);
            fresh.putInt("mode", 0);
            tag.put("tenfold", fresh);
        }
        return tag.getCompound("tenfold");
    }

    public static int tiger(ItemStack stack) {
        return Math.min(TIGER_MAG, root(stack).getInt("tiger"));
    }

    public static int mark(ItemStack stack) {
        return Math.min(MARK_MAG, root(stack).getInt("mark"));
    }

    /** 0 = 猛虎弹, 1 = 猛虎标弹. */
    public static int mode(ItemStack stack) {
        return root(stack).getInt("mode") == 1 ? 1 : 0;
    }

    public static boolean hasReloaded(ItemStack stack) {
        return root(stack).getBoolean("reloaded");
    }

    public static void setTiger(ItemStack stack, int value) {
        root(stack).putInt("tiger", Math.max(0, Math.min(TIGER_MAG, value)));
    }

    public static void setMark(ItemStack stack, int value) {
        root(stack).putInt("mark", Math.max(0, Math.min(MARK_MAG, value)));
    }

    public static void setMode(ItemStack stack, int mode) {
        root(stack).putInt("mode", mode == 1 ? 1 : 0);
    }

    public static void setReloaded(ItemStack stack, boolean value) {
        root(stack).putBoolean("reloaded", value);
    }

    public static boolean isEmpty(ItemStack stack) {
        return tiger(stack) <= 0 && mark(stack) <= 0;
    }

    /** Current damage per hit: 15 / 30 while loaded, 7 once totally spent. */
    public static int currentDamage(ItemStack stack) {
        if (isEmpty(stack)) {
            return EMPTY_DAMAGE;
        }
        return mode(stack) == 0 ? TIGER_DAMAGE : MARK_DAMAGE;
    }

    /** Full refill (used by the auto-reload and the ammo box). */
    public static void refill(ItemStack stack) {
        setTiger(stack, TIGER_MAG);
        setMark(stack, MARK_MAG);
        setMode(stack, 0);
        setReloaded(stack, true);
    }

    /* ------------------------------------------------------------------ */
    /* Combat stats                                                        */
    /* ------------------------------------------------------------------ */

    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier>
    getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
        com.google.common.collect.ImmutableMultimap.Builder<
                net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier> builder =
                com.google.common.collect.ImmutableMultimap.builder();
        if (slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND) {
            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                    BASE_ATTACK_DAMAGE_UUID, "tenfold_sky_blade",
                    currentDamage(stack) - 1.0D, AttributeModifier.Operation.ADDITION));
            builder.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                    REACH_UUID, "tenfold_sky_blade_reach",
                    EXTRA_REACH, AttributeModifier.Operation.ADDITION));
        }
        return builder.build();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide) {
            // Spend one round of the active magazine.
            if (mode(stack) == 0) {
                setTiger(stack, tiger(stack) - 1);
            } else {
                setMark(stack, mark(stack) - 1);
            }
            // Auto-switch to the other magazine when the active one runs dry.
            if (tiger(stack) <= 0 && mark(stack) > 0) {
                setMode(stack, 1);
            } else if (mark(stack) <= 0 && tiger(stack) > 0) {
                setMode(stack, 0);
            } else if (isEmpty(stack) && !hasReloaded(stack)) {
                // One free auto-reload per lifetime of the magazine cycle.
                refill(stack);
            }
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Sneak + right-click: dump the tiger rounds                          */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || tiger(stack) <= 0) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            setTiger(stack, 0);
            setMode(stack, 1);
            level.playSound(null, player.blockPosition(),
                    SoundEvents.ARMOR_STAND_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
            player.displayClientMessage(Component.translatable("tenfold.skyblade.dumped")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /* ------------------------------------------------------------------ */
    /* Tooltip                                                             */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tenfold.skyblade.rounds",
                tiger(stack), mark(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tenfold.skyblade.damage",
                currentDamage(stack)).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tenfold.skyblade.reach",
                (int) (3 + EXTRA_REACH)).withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tenfold.skyblade.help").withStyle(ChatFormatting.DARK_GRAY));
        if (!hasReloaded(stack)) {
            tooltip.add(Component.translatable("tenfold.skyblade.reload_ready")
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
