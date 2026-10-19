package com.tenfold.common;

import com.tenfold.ModEffects;
import com.tenfold.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 狂猎套装（狂猎棺材 + 狂猎大剑）的状态维护：
 * <ul>
 *   <li>穿着棺材时，狂猎大剑攻击力 **+40**（基础 10 → 50）。</li>
 *   <li>同时穿着棺材并手持大剑：常驻「撕裂的心」——造成伤害 +50%、
 *       受到伤害 +10%、移速 +3%、生命上限 +6。</li>
 * </ul>
 * 伤害倍率在 {@code WildHuntEvents#onLivingHurt} 结算。
 */
public final class WildHuntManager {

    private static final UUID SWORD_DAMAGE_ID =
            UUID.fromString("D4E5F6A7-0003-4B82-9CA3-4567890123DE");
    private static final UUID SPEED_ID =
            UUID.fromString("D4E5F6A7-0004-4B82-9CA3-4567890123DF");
    private static final UUID HEALTH_ID =
            UUID.fromString("D4E5F6A7-0005-4B82-9CA3-4567890123E0");

    /** 撕裂的心：移速 +3%。 */
    public static final double TORN_HEART_SPEED = 0.03D;
    /** 撕裂的心：生命上限 +6。 */
    public static final double TORN_HEART_HEALTH = 6.0D;

    private WildHuntManager() {
    }

    public static boolean isWearingCoffin(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.WILD_HUNT_COFFIN.get());
    }

    public static boolean isHoldingSword(LivingEntity entity) {
        return entity.getMainHandItem().is(ModItems.WILD_HUNT_GREATSWORD.get())
                || entity.getOffhandItem().is(ModItems.WILD_HUNT_GREATSWORD.get());
    }

    /** 棺材 + 大剑同时装备。 */
    public static boolean isFullyEquipped(LivingEntity entity) {
        return isWearingCoffin(entity) && isHoldingSword(entity);
    }

    /** 每 20 tick 调用一次：装备齐全就刷状态，不齐就清干净。 */
    public static void tick(ServerPlayer player) {
        boolean equipped = isFullyEquipped(player);
        boolean hasEffect = player.hasEffect(ModEffects.TORN_HEART.get());

        if (equipped) {
            if (!hasEffect) {
                player.addEffect(new MobEffectInstance(ModEffects.TORN_HEART.get(),
                        -1, 0, false, false, true));
            }
            ensureModifier(player, Attributes.ATTACK_DAMAGE, SWORD_DAMAGE_ID,
                    "tenfold_wild_hunt_sword", WildHuntGreatswordBonus(), AttributeModifier.Operation.ADDITION);
            ensureModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID,
                    "tenfold_torn_heart_speed", TORN_HEART_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL);
            ensureModifier(player, Attributes.MAX_HEALTH, HEALTH_ID,
                    "tenfold_torn_heart_health", TORN_HEART_HEALTH, AttributeModifier.Operation.ADDITION);
            // 我们的心意终与不再相悖：穿棺材 + 持大剑
            ModAdvancements.award(player, ModAdvancements.SOUL_UNITED);
            return;
        }

        if (hasEffect) {
            player.removeEffect(ModEffects.TORN_HEART.get());
        }
        removeModifier(player, Attributes.ATTACK_DAMAGE, SWORD_DAMAGE_ID);
        removeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        boolean hadHealth = hasModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
        removeModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
        if (hadHealth && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static double WildHuntGreatswordBonus() {
        return com.tenfold.item.WildHuntGreatswordItem.COFFIN_DAMAGE_BONUS;
    }

    private static boolean hasModifier(ServerPlayer player, Attribute attribute, UUID id) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance != null && instance.getModifier(id) != null;
    }

    private static void ensureModifier(ServerPlayer player, Attribute attribute, UUID id, String name,
                                       double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null || instance.getModifier(id) != null) {
            return;
        }
        instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }

    private static void removeModifier(ServerPlayer player, Attribute attribute, UUID id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (instance.getModifier(id) != null) {
            for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                if (modifier.getId().equals(id)) {
                    instance.removeModifier(modifier);
                }
            }
        }
    }
}
