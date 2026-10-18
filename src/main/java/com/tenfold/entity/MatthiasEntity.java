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
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

/**
 * 马蒂亚斯 — a zombie-styled boss wielding the Levandin.
 * <p>
 * 2000 HP. As his own health drops he unwraps the Levandin the same way the
 * player does: below 80% the first wrapping comes off (stage 1), below 60%
 * the second (stage 2), below 30% the last (stage 3). At that point he also
 * gains 过人的毅力: every hit against him is capped at 3, he deals double
 * damage, and the gift has its own 300-point pool — once the pool is spent the
 * buff breaks for good and normal damage applies again.
 */
public class MatthiasEntity extends Zombie {

    private static final EntityDataAccessor<Integer> DATA_STAGE =
            SynchedEntityData.defineId(MatthiasEntity.class, EntityDataSerializers.INT);

    /**
     * 「毅力」的总耐久：每次受到的伤害都会消耗它，打空后马蒂亚斯失去减伤与
     * 双倍伤害。300 意味着无 buff 的 13 点平砍要砍二十多刀才撕得开，而
     * 满 buff 的一百多点爆发三四刀就能打穿——爆发流因此有意义。
     */
    public static final float GRIT_ABSORPTION = 300.0F;

    private boolean gritApplied = false;
    /** 毅力剩余耐久。 */
    private float gritPool = 0.0F;
    /** 毅力是否已经被打碎（碎了就不再恢复）。 */
    private boolean gritBroken = false;

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
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData, tag);
        // 刷怪蛋 / /summon / 自然生成统一按满血出生。
        this.setHealth(this.getMaxHealth());
        return result;
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
            gritPool = GRIT_ABSORPTION;
            gritBroken = false;
            // 无限时长：毅力现在有耐久，不再靠 10 分钟倒计时自己消失（那样反而
            // 让"拖到 buff 过期再爆发"成为最优解）。
            this.addEffect(new MobEffectInstance(ModEffects.PERSEVERANCE.get(),
                    MobEffectInstance.INFINITE_DURATION, 0, false, true, true));
            announce("tenfold.boss.grit");
        }
    }

    private void unwrap(String key) {
        setBossStage(bossStage() + 1);
        this.heal(0.0F); // no-op, just refreshes equipment visual
        announce(key);
    }

    /**
     * 消耗毅力耐久。
     *
     * @return {@code true} 表示毅力还在，这一刀该被压到 3 点；
     *         {@code false} 表示这一刀把毅力打碎了（或本来就没有毅力），
     *         攻击应当按真实伤害结算。
     */
    public boolean grindDownGrit(float damage) {
        if (this.gritBroken || this.gritPool <= 0.0F) {
            return false;
        }
        this.gritPool -= damage;
        if (this.gritPool > 0.0F) {
            return true;
        }
        this.gritPool = 0.0F;
        this.gritBroken = true;
        this.removeEffect(ModEffects.PERSEVERANCE.get());
        announce("tenfold.boss.grit_broken");
        return false;
    }

    public boolean isGritBroken() {
        return this.gritBroken;
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
        tag.putFloat("GritPool", gritPool);
        tag.putBoolean("GritBroken", gritBroken);
        // 标记"血量已经迁移过"，避免每次读档都把低血量误判成旧版本 clamp 损坏。
        tag.putBoolean("HealthMigrated", true);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_STAGE, tag.getInt("BossStage"));
        this.gritApplied = tag.getBoolean("GritApplied");
        this.gritPool = tag.getFloat("GritPool");
        this.gritBroken = tag.getBoolean("GritBroken");
        // 旧存档的 max_health 曾被原版 1024 上限压过，血量也跟着被压到 1024；
        // 只在"第一次读档且没有迁移标记"时重置一次，避免战斗中重载区块无限回血。
        if (!tag.getBoolean("HealthMigrated")
                && this.getHealth() <= 1024.0F && this.getMaxHealth() > 1024.0F) {
            this.setHealth(this.getMaxHealth());
        }
        if (this.gritApplied && !this.gritBroken && this.gritPool <= 0.0F) {
            // 旧存档没有 GritPool 字段：补满，避免"毅力还在但没有耐久"的死状态。
            this.gritPool = GRIT_ABSORPTION;
        }
        if (this.gritBroken) {
            this.removeEffect(ModEffects.PERSEVERANCE.get());
        }
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
