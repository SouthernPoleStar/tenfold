package com.tenfold.common;

import com.tenfold.ModEffects;
import com.tenfold.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 「关刀 · EGO 绽放：亢龙有悔」的状态管理。
 * <ul>
 *   <li>进入条件（常态）：情感 ≥ 10 层 且 血量低于上限一半，Shift+右键。</li>
 *   <li>强制进入：伴侣死亡、且自己手持关刀（无视情感与血量）。</li>
 *   <li>持续 1 分钟：生命上限翻倍、力量 V、抗性 II、开局 30 秒生命恢复 III；
 *       护甲被强制清空并换上「燃烧的羽毛」；关刀攻击力 +40；期间攻击不再叠情感。</li>
 *   <li>结束时把进入前的四件护甲原样还回去。</li>
 * </ul>
 */
public final class EgoManager {

    /** EGO 持续时间：1 分钟。 */
    public static final int EGO_DURATION = 20 * 60;
    /** 关刀的情感层数上限。 */
    public static final int GLAIVE_EMOTION_MAX = 10;
    /** EGO 期间关刀攻击力加成。 */
    public static final double EGO_BLADE_BONUS = 40.0D;

    private static final String UNTIL_KEY = "EgoUntil";
    private static final String[] SAVE_KEYS = {
            "EgoSavedHead", "EgoSavedChest", "EgoSavedLegs", "EgoSavedFeet"
    };
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final UUID BLADE_UUID =
            UUID.fromString("F1A2B3C4-0006-4D93-8EA4-5678901234EF");
    private static final UUID HEALTH_UUID =
            UUID.fromString("F1A2B3C4-0007-4D93-8EA4-5678901234F0");

    private static final String[] LINE_KEYS = {
            "tenfold.ego.line1", "tenfold.ego.line2", "tenfold.ego.line3",
            "tenfold.ego.line4", "tenfold.ego.line5"
    };

    private EgoManager() {
    }

    /* ------------------------------------------------------------------ */
    /* 状态查询                                                            */
    /* ------------------------------------------------------------------ */

    public static boolean isActive(Player player) {
        return player.hasEffect(ModEffects.EGO_BLOOM.get());
    }

    public static boolean isHoldingGlaive(Player player) {
        return player.getMainHandItem().is(ModItems.GUAN_DAO.get());
    }

    public static int emotionLevel(Player player) {
        MobEffectInstance effect = player.getEffect(ModEffects.GLAIVE_EMOTION.get());
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    /** 关刀命中：情感 +1（最高 10 层），EGO 期间不再叠加。 */
    public static void addGlaiveEmotion(ServerPlayer player) {
        if (isActive(player)) {
            return;
        }
        int level = Math.min(GLAIVE_EMOTION_MAX, emotionLevel(player) + 1);
        player.addEffect(new MobEffectInstance(ModEffects.GLAIVE_EMOTION.get(), 20 * 60 * 2,
                level - 1, false, true, true));
    }

    /* ------------------------------------------------------------------ */
    /* 进入                                                                */
    /* ------------------------------------------------------------------ */

    /** Shift+右键：满足「情感 ≥ 10 且血量 < 50%」时进入 EGO 绽放。 */
    public static boolean tryEnter(ServerPlayer player) {
        if (isActive(player)) {
            player.displayClientMessage(Component.translatable("tenfold.ego.already")
                    .withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        int emotion = emotionLevel(player);
        boolean lowHealth = player.getHealth() < player.getMaxHealth() * 0.5F;
        if (emotion < GLAIVE_EMOTION_MAX || !lowHealth) {
            player.displayClientMessage(Component.translatable("tenfold.ego.need",
                    emotion, GLAIVE_EMOTION_MAX).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        return enter(player);
    }

    /** 强制进入（伴侣死亡 + 手持关刀）：无视情感与血量。 */
    public static boolean forceEnter(ServerPlayer player) {
        if (isActive(player)) {
            return false;
        }
        return enter(player);
    }

    private static boolean enter(ServerPlayer player) {
        saveArmor(player);
        clearArmor(player);
        equipFeather(player);
        player.removeEffect(ModEffects.GLAIVE_EMOTION.get());

        long until = player.level().getGameTime() + EGO_DURATION;
        player.getPersistentData().putLong(UNTIL_KEY, until);

        player.addEffect(new MobEffectInstance(ModEffects.EGO_BLOOM.get(), EGO_DURATION,
                0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30,
                2, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EGO_DURATION,
                4, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EGO_DURATION,
                1, false, true, true));
        applyHealthModifier(player, true);

        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 0.5F);
        if (player.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 60, 0.5D, 0.9D, 0.5D, 0.05D);
        }
        player.displayClientMessage(Component.translatable("tenfold.ego.enter")
                .withStyle(ChatFormatting.GOLD), false);
        broadcastLines(player);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* 每 tick 维护                                                        */
    /* ------------------------------------------------------------------ */

    public static void tick(ServerPlayer player) {
        long until = player.getPersistentData().getLong(UNTIL_KEY);
        boolean active = isActive(player);
        if (until <= 0L) {
            if (active) {
                end(player);
            }
            return;
        }
        if (player.level().getGameTime() >= until) {
            end(player);
            return;
        }

        // 期间：强制维持 EGO 状态、羽毛胸甲与属性
        if (!active) {
            player.addEffect(new MobEffectInstance(ModEffects.EGO_BLOOM.get(),
                    (int) Math.max(20L, until - player.level().getGameTime()), 0, false, true, true));
        }
        ensureFeatherOnly(player);
        applyHealthModifier(player, true);
        applyBladeModifier(player, isHoldingGlaive(player));
    }

    private static void end(ServerPlayer player) {
        player.getPersistentData().remove(UNTIL_KEY);
        player.removeEffect(ModEffects.EGO_BLOOM.get());
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        applyHealthModifier(player, false);
        applyBladeModifier(player, false);
        restoreArmor(player);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        player.displayClientMessage(Component.translatable("tenfold.ego.end")
                .withStyle(ChatFormatting.GRAY), false);
    }

    /** 玩家死亡：先把护甲还回去，避免掉落变成羽毛。 */
    public static void onPlayerDeath(Player player) {
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        server.getPersistentData().remove(UNTIL_KEY);
        applyHealthModifier(server, false);
        applyBladeModifier(server, false);
        restoreArmor(server);
    }

    /* ------------------------------------------------------------------ */
    /* 护甲                                                                */
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

    /** 清空四件护甲：能塞回背包就塞回去，塞不下就丢在原地（不凭空消失）。 */
    private static void clearArmor(ServerPlayer player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack current = player.getItemBySlot(slot);
            if (current.isEmpty()) {
                continue;
            }
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(current.copy())) {
                player.drop(current.copy(), false);
            }
        }
    }

    private static void equipFeather(ServerPlayer player) {
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.BURNING_FEATHER.get())) {
            return;
        }
        // 临时胸甲打上绑定诅咒 + 消失诅咒，防止被拿下来刷装备。
        player.setItemSlot(EquipmentSlot.CHEST,
                TempGear.cursed(new ItemStack(ModItems.BURNING_FEATHER.get())));
    }

    /** 期间维持「只穿燃烧的羽毛」：其他槽位有东西就挪走。 */
    private static void ensureFeatherOnly(ServerPlayer player) {
        equipFeather(player);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (slot == EquipmentSlot.CHEST) {
                continue;
            }
            ItemStack current = player.getItemBySlot(slot);
            if (current.isEmpty()) {
                continue;
            }
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(current.copy())) {
                player.drop(current.copy(), false);
            }
        }
    }

    private static void restoreArmor(ServerPlayer player) {
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

    /* ------------------------------------------------------------------ */
    /* 属性                                                                */
    /* ------------------------------------------------------------------ */

    private static void applyHealthModifier(ServerPlayer player, boolean enabled) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            return;
        }
        if (instance.getModifier(HEALTH_UUID) != null) {
            instance.removeModifier(HEALTH_UUID);
        }
        if (enabled) {
            instance.addTransientModifier(new AttributeModifier(HEALTH_UUID,
                    "tenfold_ego_max_health", 1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void applyBladeModifier(ServerPlayer player, boolean enabled) {
        AttributeInstance instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (instance == null) {
            return;
        }
        if (instance.getModifier(BLADE_UUID) != null) {
            instance.removeModifier(BLADE_UUID);
        }
        if (enabled) {
            instance.addTransientModifier(new AttributeModifier(BLADE_UUID,
                    "tenfold_ego_glaive", EGO_BLADE_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    /* ------------------------------------------------------------------ */
    /* 台词                                                                */
    /* ------------------------------------------------------------------ */

    /** 以玩家身份把五句台词广播到全服聊天。 */
    public static void broadcastLines(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var server = serverLevel.getServer();
        if (server == null) {
            return;
        }
        for (String key : LINE_KEYS) {
            Component line = Component.literal("<" + player.getGameProfile().getName() + "> ")
                    .append(Component.translatable(key));
            server.getPlayerList().broadcastSystemMessage(line, false);
        }
    }

    /** 附近的玩家能看到 EGO 特效（保留给后续扩展用）。 */
    public static void spawnAura(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5D, pos.getY() + 1.0D,
                pos.getZ() + 0.5D, 4, 0.4D, 0.6D, 0.4D, 0.01D);
    }

    /** 供外部查询剩余时间（秒）。 */
    public static int secondsLeft(Player player) {
        long until = player.getPersistentData().getLong(UNTIL_KEY);
        if (until <= 0L) {
            return 0;
        }
        return (int) Math.max(0L, (until - player.level().getGameTime()) / 20L);
    }

    /** 收集当前所有 EGO 状态（调试用）。 */
    public static java.util.List<String> describe(ServerPlayer player) {
        java.util.List<String> out = new ArrayList<>();
        out.add("ego=" + isActive(player) + " left=" + secondsLeft(player) + "s");
        return out;
    }
}
