package com.tenfold.entity;

import com.tenfold.ModEffects;
import com.tenfold.common.BloodDemonManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 血魔老祖：一代血魔，在世界各处游荡。玩家被它杀死有 5% 概率转化为
 * 二代血魔；击杀它掉落「血魔老祖的牙」，Shift+右键 使用牙才 100% 转化为二代血魔。
 * <p>
 * 它不会**主动**攻击已经是血魔的玩家（都是它的血脉后裔），但如果先动手打它，
 * 它会还手。
 */
public class BloodAncestorEntity extends Zombie {

    public BloodAncestorEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 400.0D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    /** 血魔不惧阳光。 */
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    /**
     * 不主动锁定血魔玩家：僵尸的目标选择器每 tick 都会调 {@code setTarget}，
     * 这里直接拦掉"目标还是血魔、且不是刚刚打我的那个"的情况。
     * 被血魔先手攻击时放行（{@code getLastHurtByMob()} 会返回那名玩家），所以会还手。
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null
                && target != this.getLastHurtByMob()
                && BloodDemonManager.isBloodDemon(target)) {
            return;
        }
        super.setTarget(target);
    }

    @Override
    public void tick() {
        super.tick();
        // 已经锁定的目标如果之后变成血魔（或刚被转化），同样放弃追击。
        LivingEntity current = this.getTarget();
        if (current != null
                && current != this.getLastHurtByMob()
                && BloodDemonManager.isBloodDemon(current)) {
            super.setTarget(null);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData, tag);
        this.setHealth(this.getMaxHealth());
        if (!level.isClientSide()) {
            this.addEffect(new MobEffectInstance(ModEffects.bloodGenEffect(1),
                    -1, 0, false, false, true));
        }
        return result;
    }
}
