package com.tenfold.entity;

import com.tenfold.ModEffects;
import com.tenfold.ModItems;
import com.tenfold.item.SkyBladeItem;
import com.tenfold.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 雷横 — a zombie-styled boss swinging the 天退星刀.
 * <p>
 * 8000 HP. Every successful strike grants one stack of 钢气 (+10% damage per
 * stack, max 255). His blade reloads itself endlessly. Below 5000 HP his
 * attack rises by 30% and he may call two 弹药箱-clad minion zombies: kill
 * them within 3 seconds and he weakens, fail and he grows stronger while
 * they vanish. Cannot burn in sunlight. Has a proper boss health bar.
 */
public class LeiHengEntity extends Zombie {

    /** 8000 HP. */
    public static final double MAX_HP = 8000.0D;
    /** Below this health the boss enters his second phase. */
    public static final float RAGE_THRESHOLD = 5000.0F;
    private static final int MAX_STEEL_QI_LEVEL = 255;
    private static final UUID RAGE_UUID = UUID.fromString("b7e2f4a1-3c8d-4e52-9a10-6d3f55c0b7a3");

    private boolean rageApplied = false;
    private int minionCooldown = 0;
    private UUID activeMinionA = null;
    private UUID activeMinionB = null;

    private final net.minecraft.server.level.ServerBossEvent bossEvent =
            new net.minecraft.server.level.ServerBossEvent(this.getDisplayName(),
                    BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);

    public LeiHengEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
    }

    public static MutableComponent displayName() {
        return Component.translatable("entity.tenfold.leiheng");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D);
    }

    /* ------------------------------------------------------------------ */
    /* Boss bar                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            this.bossEvent.setProgress(Math.max(0.0F, this.getHealth() / this.getMaxHealth()));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Zombie heritage                                                     */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean isBaby() {
        return false;
    }

    /** Bosses never catch fire in sunlight. */
    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        // Steel Qi is his own to stack.
        if (effect.getEffect() == ModEffects.STEEL_QI.get()) {
            return true;
        }
        return super.canBeAffected(effect);
    }

    /* ------------------------------------------------------------------ */
    /* Tick                                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        // First spawn: draw the 天退星刀, fully loaded.
        ItemStack held = this.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        if (!(held.getItem() instanceof SkyBladeItem)) {
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new ItemStack(ModItems.SKY_BLADE.get()));
        } else if (SkyBladeItem.isEmpty(held)) {
            // Endless self-reload.
            SkyBladeItem.refill(held);
        }
        if (this.minionCooldown > 0) {
            this.minionCooldown--;
        }
        checkRage();
    }

    /** Below 5000 HP: +30% attack once, and from then on he may call minions. */
    private void checkRage() {
        if (this.rageApplied || this.getHealth() >= RAGE_THRESHOLD) {
            return;
        }
        this.rageApplied = true;
        var attr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr != null && attr.getModifier(RAGE_UUID) == null) {
            attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    RAGE_UUID, "tenfold_leiheng_rage", 0.30D,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        announce("tenfold.leiheng.rage");
    }

    /* ------------------------------------------------------------------ */
    /* Combat: steel qi stacks + minion summons                            */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide) {
            stackSteelQi();
            maybeSummonMinions();
        }
        return hit;
    }

    private void stackSteelQi() {
        MobEffectInstance current = this.getEffect(ModEffects.STEEL_QI.get());
        int nextLevel = Math.min(MAX_STEEL_QI_LEVEL, (current == null ? 0 : current.getAmplifier() + 1) + 1);
        this.removeEffect(ModEffects.STEEL_QI.get());
        // 10 seconds per application; the level is what matters.
        this.addEffect(new MobEffectInstance(ModEffects.STEEL_QI.get(), 20 * 10,
                nextLevel - 1, false, true, true));
    }

    private void maybeSummonMinions() {
        if (this.level() instanceof ServerLevel server
                && this.rageApplied
                && this.minionCooldown <= 0
                && this.random.nextFloat() < 0.25F) {
            boolean alive = (this.activeMinionA != null && findMinion(server, this.activeMinionA) != null)
                    || (this.activeMinionB != null && findMinion(server, this.activeMinionB) != null);
            if (!alive) {
                spawnMinionPair(server);
                this.minionCooldown = 20 * 10;
            }
        }
    }

    private void spawnMinionPair(ServerLevel server) {
        this.activeMinionA = spawnOneMinion(server);
        this.activeMinionB = spawnOneMinion(server);
        if (this.activeMinionA != null || this.activeMinionB != null) {
            announce("tenfold.leiheng.minions");
        }
    }

    private UUID spawnOneMinion(ServerLevel server) {
        MinionEntity minion = ModEntities.MINION.get().create(server);
        if (minion == null) {
            return null;
        }
        minion.moveTo(this.getX() + (this.random.nextDouble() - 0.5D) * 4.0D,
                this.getY(), this.getZ() + (this.random.nextDouble() - 0.5D) * 4.0D,
                this.getYRot(), 0.0F);
        minion.setHost(this);
        server.addFreshEntity(minion);
        return minion.getUUID();
    }

    /** Called by minions when their 3-second timer runs out untouched. */
    void onMinionTimedOut() {
        MobEffectInstance current = this.getEffect(MobEffects.DAMAGE_BOOST);
        int amplifier = (current == null ? -1 : current.getAmplifier()) + 2;
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 20, amplifier, false, true, true));
        announce("tenfold.leiheng.minion_timeout");
    }

    /** Called by minions killed within their 3-second window. */
    void onMinionSlain() {
        this.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 10, 1, false, true, true));
        announce("tenfold.leiheng.minion_slain");
    }

    @javax.annotation.Nullable
    private MinionEntity findMinion(ServerLevel server, UUID id) {
        var entity = server.getEntity(id);
        return entity instanceof MinionEntity m && m.isAlive() ? m : null;
    }

    private void announce(String key) {
        if (this.level() instanceof ServerLevel server) {
            server.players().forEach(p -> p.sendSystemMessage(
                    Component.translatable(key, this.getDisplayName())
                            .withStyle(net.minecraft.ChatFormatting.GOLD)));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Persistence                                                         */
    /* ------------------------------------------------------------------ */

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("LeiRage", rageApplied);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.rageApplied = tag.getBoolean("LeiRage");
        if (this.rageApplied) {
            var attr = this.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attr != null && attr.getModifier(RAGE_UUID) == null) {
                attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        RAGE_UUID, "tenfold_leiheng_rage", 0.30D,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

}
