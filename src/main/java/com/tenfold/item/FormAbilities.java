package com.tenfold.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Per-form behaviour applied on a successful hit.
 * Kept in one place so the ten forms stay comparable and tunable.
 */
public final class FormAbilities {
    private FormAbilities() {
    }

    public static void onHit(Player player, ItemStack stack, LivingEntity primary) {
        WeaponForm form = WeaponData.form(stack);
        switch (form) {
            case GREATSWORD -> cleave(player, primary, 60.0, 3.2, 3, 0.6F);
            case SCYTHE -> {
                // Reap: everything within 2 blocks of the victim takes half the hit.
                if (player.level() instanceof ServerLevel server) {
                    float splash = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F;
                    for (LivingEntity around : server.getEntitiesOfClass(LivingEntity.class,
                            primary.getBoundingBox().inflate(2.0),
                            e -> e != player && e != primary && e.isAlive() && !e.isAlliedTo(player))) {
                        around.hurt(player.damageSources().playerAttack(player), splash);
                    }
                }
            }
            case RAPIER -> rapierStacks(player);
            case AXE -> {
                if (primary instanceof Player victim) {
                    victim.getCooldowns().addCooldown(net.minecraft.world.item.Items.SHIELD, 8 * 20);
                    victim.stopUsingItem();
                }
            }
            case HAMMER -> {
                if (!primary.level().isClientSide) {
                    primary.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true));
                    Vec3 push = primary.position().subtract(player.position()).normalize().scale(0.45);
                    primary.push(push.x, 0.18, push.z);
                    primary.hurtMarked = true;
                }
            }
            case WHIP -> {
                if (!primary.level().isClientSide) {
                    Vec3 pull = player.position().subtract(primary.position()).normalize().scale(0.85);
                    primary.push(pull.x, 0.25, pull.z);
                    primary.hurtMarked = true;
                }
            }
            case HALFSWORD -> {
                // handled through the block path below
            }
            case SPEAR -> pierce(player, primary);
            case CONE -> {
                if (primary instanceof Mob mob) {
                    var armor = mob.getAttribute(Attributes.ARMOR);
                    if (armor != null) {
                        armor.removeModifier(ARMOR_SHRED);
                        armor.addTransientModifier(new AttributeModifier(ARMOR_SHRED, "tenfold_armor_shred",
                                -armor.getValue() * 0.5, AttributeModifier.Operation.ADDITION));
                        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0, false, false));
                    }
                }
            }
            case SHOVEL -> {
                // no bonus: the shovel is the penalty form
            }
        }
        if (player.level() instanceof ServerLevel server && form != WeaponForm.SHOVEL) {
            server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    primary.getX(), primary.getY(0.6), primary.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static final java.util.UUID ARMOR_SHRED = java.util.UUID.fromString("1f3a9c62-2c4a-4d3f-8f2a-1a2b3c4d5e6f");

    /**
     * Absolute damage for a form, in half-hearts.
     * <p>
     * These are final weapon damages, not bonuses: the item suppresses the
     * player's own attack-damage attribute so the number on the tooltip is the
     * number that lands. Scythe is the ceiling at 15, shovel the floor at 6.
     */
    public static float damage(WeaponForm form) {
        return switch (form) {
            case GREATSWORD -> 13.0F;
            case SCYTHE -> 15.0F;
            case RAPIER -> 9.0F;
            case AXE -> 12.0F;
            case HAMMER -> 14.0F;
            case WHIP -> 8.0F;
            case HALFSWORD -> 10.0F;
            case SPEAR -> 12.0F;
            case CONE -> 11.0F;
            case SHOVEL -> 6.0F;
        };
    }

    private static void cleave(Player player, LivingEntity primary, double arcDegrees, double range,
                               int maxExtra, float damageScale) {
        if (!(player.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 look = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z).normalize();
        AABB box = player.getBoundingBox().inflate(range);
        List<LivingEntity> found = server.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e != primary && e.isAlive() && !e.isAlliedTo(player));
        int hits = 0;
        for (LivingEntity extra : found) {
            if (hits >= maxExtra) {
                break;
            }
            Vec3 raw = extra.position().subtract(player.position());
            Vec3 dir = new Vec3(raw.x, 0, raw.z).normalize();
            double dot = look.dot(dir);
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
            if (angle <= arcDegrees / 2.0) {
                extra.hurt(player.damageSources().playerAttack(player),
                        (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * damageScale);
                hits++;
            }
        }
    }

    private static void pierce(Player player, LivingEntity primary) {
        if (!(player.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().inflate(4.6, 2.0, 4.6);
        for (LivingEntity other : server.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player))) {
            Vec3 to = other.position().subtract(player.getEyePosition());
            double along = to.dot(look);
            if (along <= 2.0 || along >= 4.6) {
                continue;
            }
            double perp = to.subtract(look.scale(along)).horizontalDistance();
            if (perp < 1.1) {
                other.hurt(player.damageSources().playerAttack(player),
                        (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.85F);
            }
        }
    }

    private static void rapierStacks(Player player) {
        var tag = player.getPersistentData();
        long now = player.level().getGameTime();
        long last = tag.getLong("tenfoldRapierTime");
        int stacks = tag.getInt("tenfoldRapierStacks");
        stacks = (now - last <= 60) ? Math.min(4, stacks + 1) : 1;
        tag.putInt("tenfoldRapierStacks", stacks);
        tag.putLong("tenfoldRapierTime", now);
    }

    public static int rapierBonusPercent(Player player) {
        long now = player.level().getGameTime();
        if (now - player.getPersistentData().getLong("tenfoldRapierTime") > 60) {
            return 0;
        }
        return player.getPersistentData().getInt("tenfoldRapierStacks") * 12;
    }
}
