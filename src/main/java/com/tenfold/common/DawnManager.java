package com.tenfold.common;

import com.tenfold.ModEffects;
import com.tenfold.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 「黎明长剑 / 炎蝶之棺」的状态维护。
 * <p>
 * 黎明：每层攻击力 +3，满 10 层自动消耗全部层数转化为「天将拂晓」。
 * 炎蝶之棺：穿戴时常驻抗性提升 III + 夜视 I；手持黎明长剑时额外常驻力量 IV。
 * <p>
 * 天将拂晓的 +30% 伤害、-20% 受伤与必暴击在 {@code CommonEvents#onLivingHurt} 里结算。
 */
public final class DawnManager {

    public static final int DAWN_MAX = 10;
    private static final int DAWN_DURATION = 20 * 30;
    private static final int ASCENSION_DURATION = 20 * 60;
    private static final int BUFF_REFRESH = 40;

    private static final UUID DAWN_DAMAGE_UUID =
            UUID.fromString("F1A2B3C4-0001-4D50-9E21-4567890ABCDE");

    private DawnManager() {
    }

    public static int dawnLevel(Player player) {
        MobEffectInstance effect = player.getEffect(ModEffects.DAWN.get());
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    /** 黎明长剑命中：黎明 +1；到 10 层时消耗全部层数转化为天将拂晓。 */
    public static void addDawn(ServerPlayer player) {
        int level = Math.min(DAWN_MAX, dawnLevel(player) + 1);
        if (level >= DAWN_MAX) {
            player.removeEffect(ModEffects.DAWN.get());
            player.addEffect(new MobEffectInstance(ModEffects.DAWN_ASCENSION.get(),
                    ASCENSION_DURATION, 0, false, true, true));
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 1.0F, 1.4F);
            player.displayClientMessage(Component.translatable("tenfold.dawn.ascend")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.DAWN.get(), DAWN_DURATION,
                level - 1, false, true, true));
    }

    /** 每 tick：维护黎明攻击力修正与炎蝶之棺的常驻效果。 */
    public static void tickPlayer(ServerPlayer player) {
        setModifier(player, Attributes.ATTACK_DAMAGE, DAWN_DAMAGE_UUID,
                3.0D * dawnLevel(player));
        tickMothCoffin(player);
    }

    private static void tickMothCoffin(ServerPlayer player) {
        boolean wearing = player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.MOTH_COFFIN.get());
        if (!wearing || player.tickCount % BUFF_REFRESH != 0) {
            return;
        }
        // 抗性提升 III（amplifier 2）与夜视 I；短时长定期刷新，避免效果栏闪烁。
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 2, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, false));
        if (player.getMainHandItem().is(ModItems.DAWN_LONGSWORD.get())) {
            // 力量 IV（amplifier 3）。
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 80, 3, false, false, false));
        }
    }

    private static void setModifier(ServerPlayer player, Attribute attribute, UUID id, double amount) {
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
        instance.addTransientModifier(new AttributeModifier(id, "tenfold_dawn", amount,
                AttributeModifier.Operation.ADDITION));
    }
}
