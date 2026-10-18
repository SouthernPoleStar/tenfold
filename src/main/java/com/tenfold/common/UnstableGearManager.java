package com.tenfold.common;

import com.tenfold.ModEffects;
import com.tenfold.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 「长剑 · 不稳定的神备」的全部状态管理。
 * <p>
 * 进入：情感满 15 层且没有 IRA 完全解放时，把玩家四件护甲存进持久化数据，
 * 强制换上「血雷 / 蜡翼 / 融化的蜡 / 皮鞋」，并获得 30 秒的神备。
 * <p>
 * 期间：每 tick 保证套装穿在槽位上、维护攻击力 +30（仅持长剑）与移速 +30%；
 * 第一次致命伤害由一次「不死图腾」抵消（每次进入只能触发一次）。
 * <p>
 * 退出：恢复进入前保存的四件护甲，清掉属性修正。
 */
public final class UnstableGearManager {

    public static final int EMOTION_MAX = 15;
    /** 神备持续时间：30 秒。 */
    private static final int GEAR_DURATION = 20 * 30;

    private static final UUID GEAR_DAMAGE_UUID =
            UUID.fromString("D7F3A1C5-2B48-4E61-9C07-5A8B1D4E6F21");
    private static final UUID GEAR_SPEED_UUID =
            UUID.fromString("D7F3A1C5-2B48-4E61-9C07-5A8B1D4E6F22");

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[] SAVE_KEYS = {
            "GearSavedHead", "GearSavedChest", "GearSavedLegs", "GearSavedFeet"
    };
    private static final String TOTEM_KEY = "GearTotemUsed";

    private UnstableGearManager() {
    }

    /* ------------------------------------------------------------------ */
    /* Emotion                                                             */
    /* ------------------------------------------------------------------ */

    public static int emotionLevel(Player player) {
        MobEffectInstance effect = player.getEffect(ModEffects.EMOTION.get());
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    /** 长剑命中：情感 +1，最高 15 层；已满时只刷新持续时间。 */
    public static void addEmotion(ServerPlayer player) {
        int level = Math.min(EMOTION_MAX, emotionLevel(player) + 1);
        player.addEffect(new MobEffectInstance(ModEffects.EMOTION.get(), 20 * 20,
                level - 1, false, true, true));
    }

    /* ------------------------------------------------------------------ */
    /* Enter / tick / exit                                                 */
    /* ------------------------------------------------------------------ */

    /** Shift+右键：尝试进入神备。返回是否成功。 */
    public static boolean tryEnter(ServerPlayer player) {
        if (player.hasEffect(ModEffects.IRA_RELEASE.get())) {
            player.displayClientMessage(Component.translatable("tenfold.longsword.ira")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
            return false;
        }
        if (player.hasEffect(ModEffects.UNSTABLE_GEAR.get())) {
            player.displayClientMessage(Component.translatable("tenfold.longsword.active")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return false;
        }
        int level = emotionLevel(player);
        if (level < EMOTION_MAX) {
            player.displayClientMessage(Component.translatable("tenfold.longsword.need_emotion",
                    level, EMOTION_MAX).withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return false;
        }
        player.removeEffect(ModEffects.EMOTION.get());
        return enter(player);
    }

    /**
     * 强制进入神备（炎蝶之棺 / 天将拂晓的持有者死在持剑者 7 格内时触发）。
     * 跳过情感层数检查，但 IRA 互斥与"已在神备中"仍然生效。
     */
    public static boolean forceEnter(ServerPlayer player) {
        if (player.hasEffect(ModEffects.IRA_RELEASE.get())
                || player.hasEffect(ModEffects.UNSTABLE_GEAR.get())) {
            return false;
        }
        return enter(player);
    }

    /** 真正进入神备：保存护甲、换装、重置图腾、加效果。 */
    private static boolean enter(ServerPlayer player) {
        saveArmor(player);
        equipSet(player);
        player.getPersistentData().putBoolean(TOTEM_KEY, false);
        player.addEffect(new MobEffectInstance(ModEffects.UNSTABLE_GEAR.get(), GEAR_DURATION,
                0, false, true, true));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        if (player.level() instanceof ServerLevel server) {
            Vec3 pos = player.position();
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y + 1.0D, pos.z, 40, 0.4D, 0.8D, 0.4D, 0.02D);
        }
        player.displayClientMessage(Component.translatable("tenfold.longsword.enter")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
        return true;
    }

    /** 每 tick：神备在就维护套装与属性，不在就把保存的护甲还回去。 */
    public static void tickPlayer(ServerPlayer player) {
        if (player.hasEffect(ModEffects.UNSTABLE_GEAR.get())) {
            equipSet(player);
            applyModifiers(player);
        } else {
            restoreIfSaved(player);
            clearModifiers(player);
        }
    }

    /** 玩家死亡：先把原护甲还回去，让死亡掉落按原装备结算。 */
    public static void onPlayerDeath(Player player) {
        if (player instanceof ServerPlayer server) {
            restoreIfSaved(server);
            clearModifiers(server);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Lethal hit / totem                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * 神备期间第一次致命伤害：用一次「不死图腾」抵消。
     *
     * @return 是否触发了免死
     */
    public static boolean handleLethalHit(ServerPlayer player) {
        if (!player.hasEffect(ModEffects.UNSTABLE_GEAR.get())) {
            return false;
        }
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(TOTEM_KEY)) {
            return false;
        }
        data.putBoolean(TOTEM_KEY, true);
        player.setHealth(1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 5, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 45, 1, false, true, true));
        // 和原版图腾一样清掉负面效果（神备是增益，不受影响）。
        for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
            if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                player.removeEffect(effect.getEffect());
            }
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player.level() instanceof ServerLevel server) {
            Vec3 pos = player.position();
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    pos.x, pos.y + 1.0D, pos.z, 60, 0.5D, 1.0D, 0.5D, 0.3D);
        }
        player.displayClientMessage(Component.translatable("tenfold.gear.totem")
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Armour swap                                                         */
    /* ------------------------------------------------------------------ */

    private static void saveArmor(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            data.put(SAVE_KEYS[i], player.getItemBySlot(ARMOR_SLOTS[i]).save(new CompoundTag()));
        }
    }

    private static boolean hasSavedArmor(ServerPlayer player) {
        return player.getPersistentData().contains(SAVE_KEYS[0], Tag.TAG_COMPOUND);
    }

    private static void restoreIfSaved(ServerPlayer player) {
        if (!hasSavedArmor(player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            CompoundTag saved = data.getCompound(SAVE_KEYS[i]);
            ItemStack stack = saved.isEmpty() ? ItemStack.EMPTY : ItemStack.of(saved);
            player.setItemSlot(ARMOR_SLOTS[i], stack);
            data.remove(SAVE_KEYS[i]);
        }
    }

    private static void equipSet(ServerPlayer player) {
        equipIfMissing(player, EquipmentSlot.HEAD, ModItems.GEAR_HELMET.get());
        equipIfMissing(player, EquipmentSlot.CHEST, ModItems.GEAR_CHEST.get());
        equipIfMissing(player, EquipmentSlot.LEGS, ModItems.GEAR_LEGS.get());
        equipIfMissing(player, EquipmentSlot.FEET, ModItems.GEAR_BOOTS.get());
    }

    private static void equipIfMissing(ServerPlayer player, EquipmentSlot slot, Item item) {
        if (!player.getItemBySlot(slot).is(item)) {
            player.setItemSlot(slot, new ItemStack(item));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Attributes                                                          */
    /* ------------------------------------------------------------------ */

    private static void applyModifiers(ServerPlayer player) {
        // 长剑 +30 只对持剑时生效；移速 +30% 常驻。
        boolean holdingSword = player.getMainHandItem().is(ModItems.LONGSWORD.get());
        setModifier(player, Attributes.ATTACK_DAMAGE, GEAR_DAMAGE_UUID,
                holdingSword ? 30.0D : 0.0D, AttributeModifier.Operation.ADDITION);
        setModifier(player, Attributes.MOVEMENT_SPEED, GEAR_SPEED_UUID,
                0.30D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    private static void clearModifiers(ServerPlayer player) {
        setModifier(player, Attributes.ATTACK_DAMAGE, GEAR_DAMAGE_UUID, 0.0D,
                AttributeModifier.Operation.ADDITION);
        setModifier(player, Attributes.MOVEMENT_SPEED, GEAR_SPEED_UUID, 0.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    private static void setModifier(ServerPlayer player, Attribute attribute, UUID id,
                                    double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (amount <= 0.0D) {
            if (existing != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (existing != null && Math.abs(existing.getAmount() - amount) < 1.0E-6D) {
            return;
        }
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, "tenfold_unstable_gear",
                amount, operation));
    }
}
