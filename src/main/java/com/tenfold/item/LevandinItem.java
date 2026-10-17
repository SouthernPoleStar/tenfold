package com.tenfold.item;

import com.tenfold.ModItems;
import com.tenfold.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 莱万丁 — a long sword wrapped in sheet iron and chains, unwrapping itself
 * as it is used. Four stages, each revealed by Shift+right-click "unwrapping":
 * <pre>
 * stage 0: iron + chains   10 dmg, 1 hit to unwrap
 * stage 1: iron, no chains 15 dmg, 2 hits to unwrap
 * stage 2: iron, glowing orange edge peeking out, 20 dmg, sets enemies on fire, 3 hits
 * stage 3: fully glowing orange blade, 25 dmg, fire on hit, 15% chance to catch fire itself
 * </pre>
 */
public class LevandinItem extends SwordItem {

    private static final java.util.UUID BASE_ATTACK_DAMAGE_UUID =
            java.util.UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final java.util.UUID BASE_ATTACK_SPEED_UUID =
            java.util.UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    private static final String ROOT = "tenfoldLevandin";

    public LevandinItem() {
        super(Tiers.IRON, 3, -2.4F, new Item.Properties().durability(2031));
    }

    /* ------------------------------------------------------------------ */
    /* Stage state                                                         */
    /* ------------------------------------------------------------------ */

    private static net.minecraft.nbt.CompoundTag root(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(ROOT, net.minecraft.nbt.CompoundTag.TAG_COMPOUND)) {
            net.minecraft.nbt.CompoundTag sub = new net.minecraft.nbt.CompoundTag();
            sub.putInt("stage", 0);
            sub.putInt("hits", 0);
            tag.put(ROOT, sub);
        }
        return tag.getCompound(ROOT);
    }

    public static int stage(ItemStack stack) {
        return Math.min(3, Math.max(0, root(stack).getInt("stage")));
    }

    public static int hits(ItemStack stack) {
        return root(stack).getInt("hits");
    }

    /** Hits still needed before this stage's wrapping can be torn off. */
    public static int hitsNeeded(int stage) {
        return switch (stage) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            default -> Integer.MAX_VALUE;
        };
    }

    public static float damageOf(int stage) {
        return switch (stage) {
            case 0 -> 10.0F;
            case 1 -> 15.0F;
            case 2 -> 20.0F;
            default -> 25.0F;
        };
    }

    private static void syncModel(ItemStack stack) {
        stack.getOrCreateTag().putInt("CustomModelData", stage(stack));
    }

    /* ------------------------------------------------------------------ */
    /* Attributes: the pane shows the current stage's damage               */
    /* ------------------------------------------------------------------ */

    @Override
    public com.google.common.collect.Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            EquipmentSlot slot, ItemStack stack) {
        com.google.common.collect.Multimap<Attribute, AttributeModifier> base =
                super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.MAINHAND) {
            return base;
        }
        com.google.common.collect.ImmutableMultimap.Builder<Attribute, AttributeModifier> builder =
                com.google.common.collect.ImmutableMultimap.builder();
        base.forEach((attribute, modifier) -> {
            if (attribute != Attributes.ATTACK_DAMAGE && attribute != Attributes.ATTACK_SPEED) {
                builder.put(attribute, modifier);
            }
        });
        int stage = stage(stack);
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                damageOf(stage) - 1.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                BASE_ATTACK_SPEED_UUID, "Weapon modifier", -2.4F, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        syncModel(stack);
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    /* ------------------------------------------------------------------ */
    /* Unwrapping: shift+right-click                                       */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player.isShiftKeyDown()) {
            unwrap((net.minecraft.server.level.ServerPlayer) player, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void unwrap(net.minecraft.server.level.ServerPlayer player, ItemStack stack) {
        int stage = stage(stack);
        int need = hitsNeeded(stage);
        if (stage >= 3) {
            player.displayClientMessage(Component.translatable("tenfold.levandin.final")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (hits(stack) < need) {
            player.displayClientMessage(Component.translatable("tenfold.levandin.not_enough",
                    need - hits(stack)).withStyle(ChatFormatting.RED), true);
            return;
        }
        root(stack).putInt("stage", stage + 1);
        root(stack).putInt("hits", 0);
        syncModel(stack);
        player.level().playSound(null, player.blockPosition(), ModSounds.FORM_SHIFT.get(),
                SoundSource.PLAYERS, 1.0F, 0.9F);
        player.displayClientMessage(Component.translatable("tenfold.levandin.unwrapped",
                Component.translatable("form.tenfold.levandin" + (stage + 1)),
                (int) damageOf(stage + 1)).withStyle(ChatFormatting.GOLD), false);
    }

    /* ------------------------------------------------------------------ */
    /* On hit: count hits, apply fire on later stages                      */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide) {
            net.minecraft.nbt.CompoundTag tag = root(stack);
            int stage = stage(stack);
            tag.putInt("hits", Math.min(tag.getInt("hits") + 1, 999));
            if (stage >= 2) {
                target.setSecondsOnFire(5);
            }
            if (stage >= 3 && attacker instanceof Player player
                    && attacker.getRandom().nextFloat() < 0.15F) {
                attacker.setSecondsOnFire(4);
                player.displayClientMessage(Component.translatable("tenfold.levandin.self_burn")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
        return result;
    }

    /* ------------------------------------------------------------------ */
    /* Tooltip                                                             */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        int stage = stage(stack);
        tooltip.add(Component.translatable("form.tenfold.levandin" + stage)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.tenfold.damage",
                String.format("%.0f", damageOf(stage))).withStyle(ChatFormatting.RED));
        if (stage >= 2) {
            tooltip.add(Component.translatable("tenfold.levandin.fire").withStyle(ChatFormatting.DARK_RED));
        }
        if (stage >= 3) {
            tooltip.add(Component.translatable("tenfold.levandin.self_burn_hint").withStyle(ChatFormatting.DARK_RED));
        } else {
            int need = hitsNeeded(stage);
            int left = Math.max(0, need - hits(stack));
            tooltip.add(Component.translatable("tenfold.levandin.next", left, need)
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tenfold.levandin.help").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static boolean isLevandin(ItemStack stack) {
        return stack.is(ModItems.LEVANDIN.get());
    }
}
