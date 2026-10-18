package com.tenfold.entity;

import com.tenfold.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 血魔老祖：一代血魔，在世界各处游荡。玩家被它杀死有 5% 概率转化为
 * 二代血魔；把它杀掉（或使用它掉落的牙）则 100% 转化为二代血魔。
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
