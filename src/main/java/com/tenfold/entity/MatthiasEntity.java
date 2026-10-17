package com.tenfold.entity;

import com.tenfold.ModEffects;
import com.tenfold.item.LevandinItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 马蒂亚斯 — a zombie-styled boss wielding the Levandin.
 * <p>
 * 2000 HP. As his own health drops he unwraps the Levandin the same way the
 * player does: below 80% the first wrapping comes off (stage 1), below 60%
 * the second (stage 2), below 40% the third (stage 3). Below 30% he gains
 * 过人的毅力: every single hit against him is capped at 3, and he deals
 * double damage.
 */
public class MatthiasEntity extends Zombie {

    private static final EntityDataAccessor<Integer> DATA_STAGE =
            SynchedEntityData.defineId(MatthiasEntity.class, EntityDataSerializers.INT);

    private boolean gritApplied = false;

    private final net.minecraft.server.level.ServerBossEvent bossEvent =
            new net.minecraft.server.level.ServerBossEvent(this.getDisplayName(),
                    net.minecraft.world.BossEvent.BossBarColor.RED,
                    net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);

    @Override
    public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
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

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 2000.0D)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 8.0D)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, 8.0D)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.30D);
    }

    public MatthiasEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static MutableComponent displayName() {
        return Component.translatable("entity.tenfold.matthias");
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STAGE, 0);
    }

    public int bossStage() {
        return this.entityData.get(DATA_STAGE);
    }

    private void setBossStage(int stage) {
        this.entityData.set(DATA_STAGE, Math.min(3, Math.max(0, stage)));
        this.setItemSlot(EquipmentSlot.MAINHAND, LevandinItem.stagedStack(stage));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        // First spawn: equip the wrapped Levandin.
        if (!this.hasItemInSlot(EquipmentSlot.MAINHAND)) {
            this.setItemSlot(EquipmentSlot.MAINHAND, LevandinItem.stagedStack(bossStage()));
        }
        if (bossStage() < 3) {
            syncHeldStage();
        }
        checkUnwrapThresholds();
    }

    /** Keeps the held Levandin's NBT in line with the boss's stage. */
    private void syncHeldStage() {
        ItemStack held = this.getItemBySlot(EquipmentSlot.MAINHAND);
        if (held.getItem() instanceof LevandinItem && LevandinItem.stage(held).index != bossStage()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, LevandinItem.stagedStack(bossStage()));
        }
    }

    private void checkUnwrapThresholds() {
        float frac = this.getHealth() / this.getMaxHealth();
        int target;
        if (frac < 0.30F) {
            target = 3;
        } else if (frac < 0.40F) {
            target = 2;
        } else if (frac < 0.60F) {
            target = 1;
        } else if (frac < 0.80F) {
            target = 0;
        } else {
            return;
        }
        // Thresholds unwrap stage by stage; each pass below advances one step.
        if (target >= 1 && bossStage() < 1 && frac < 0.80F) {
            unwrap("tenfold.boss.unwrap1");
        }
        if (target >= 2 && bossStage() < 2 && frac < 0.60F) {
            unwrap("tenfold.boss.unwrap2");
        }
        if (target >= 3 && bossStage() < 3 && frac < 0.40F) {
            unwrap("tenfold.boss.unwrap3");
        }
        if (frac < 0.30F && !gritApplied) {
            gritApplied = true;
            this.addEffect(new MobEffectInstance(ModEffects.PERSEVERANCE.get(),
                    20 * 60 * 10, 0, false, true, true));
            announce("tenfold.boss.grit");
        }
    }

    private void unwrap(String key) {
        setBossStage(bossStage() + 1);
        this.heal(0.0F); // no-op, just refreshes equipment visual
        announce(key);
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
        tag.putInt("BossStage", bossStage());
        tag.putBoolean("GritApplied", gritApplied);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_STAGE, tag.getInt("BossStage"));
        this.gritApplied = tag.getBoolean("GritApplied");
        this.setItemSlot(EquipmentSlot.MAINHAND, LevandinItem.stagedStack(bossStage()));
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
    protected SoundEvent getAmbientSound() {
        return super.getAmbientSound();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return true;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        // Perseverance must always land — the boss grants it to himself below 30%.
        if (effect.getEffect() == ModEffects.PERSEVERANCE.get()) {
            return true;
        }
        return super.canBeAffected(effect);
    }
}
