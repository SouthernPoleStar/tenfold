package com.tenfold.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 杜拉奇：无头的坐骑。由「狂猎大剑」右键召唤，骑乘状态下撞到生物造成 10 点伤害。
 * <p>
 * 「死亡冲撞」产生的三匹虚影也用这个实体，只是 {@code phantom=true}：
 * 无 AI、无敌、3 秒后自动消失。
 */
public class DullahanEntity extends Horse {

    /** 虚影存活时间（3 秒）。 */
    private static final int PHANTOM_LIFETIME = 60;
    /** 撞击伤害。 */
    public static final float TRAMPLE_DAMAGE = 10.0F;
    /** 同一目标两次撞击的最小间隔（tick）。 */
    private static final int TRAMPLE_COOLDOWN = 20;

    private boolean phantom;
    private int lifeTicks = PHANTOM_LIFETIME;
    private final Map<UUID, Integer> trampleCooldowns = new HashMap<>();

    public DullahanEntity(EntityType<? extends Horse> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractHorse.createBaseHorseAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.JUMP_STRENGTH, 0.9D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D);
    }

    public boolean isPhantom() {
        return phantom;
    }

    public void setPhantom(boolean phantom) {
        this.phantom = phantom;
        this.lifeTicks = PHANTOM_LIFETIME;
        if (phantom) {
            this.setInvulnerable(true);
            this.setNoAi(true);
            this.setPersistenceRequired();
        }
    }

    /* ------------------------------------------------------------------ */
    /* 基础行为：永远驯服、自带马鞍效果、不接受喂食 / 驯服                  */
    /* ------------------------------------------------------------------ */

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isTamed() {
        return true;
    }

    @Override
    public boolean isSaddled() {
        return true;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 不允许喂食 / 上鞍之类的原版交互。
        return InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isFood(net.minecraft.world.item.ItemStack stack) {
        return false;
    }

    /* ------------------------------------------------------------------ */
    /* 每 tick：虚影寿命 + 撞击                                             */
    /* ------------------------------------------------------------------ */

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (phantom) {
            if (--lifeTicks <= 0) {
                this.discard();
                return;
            }
            return;
        }
        if (this.tickCount % 2 == 0) {
            trample();
        }
    }

    /** 骑乘中高速移动时撞到的生物扣 10 点血。 */
    private void trample() {
        if (!(this.getControllingPassenger() instanceof Player rider)) {
            return;
        }
        if (this.getDeltaMovement().horizontalDistanceSqr() < 0.02D) {
            return;
        }
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(0.8D, 0.5D, 0.8D),
                entity -> entity != rider && entity != this && entity.isAlive()
                        && !(entity instanceof DullahanEntity))) {
            Integer last = trampleCooldowns.get(target.getUUID());
            if (last != null && this.tickCount - last < TRAMPLE_COOLDOWN) {
                continue;
            }
            trampleCooldowns.put(target.getUUID(), this.tickCount);
            target.hurt(this.damageSources().mobAttack(this), TRAMPLE_DAMAGE);
            this.playSound(SoundEvents.HORSE_ANGRY, 0.6F, 0.7F);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DullahanPhantom", phantom);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("DullahanPhantom")) {
            this.setPhantom(true);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
