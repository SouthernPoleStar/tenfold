package com.tenfold.entity;

import com.tenfold.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.damagesource.DamageSource;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Poey —— 友方支援单位。
 * <p>
 * 行为：
 * <ul>
 *   <li>自动跟随最近的玩家，右键切换坐下 / 起身。</li>
 *   <li>玩家血量低于 5 点时立刻治疗 5 点并给抗性提升 III，冷却 10 分钟。</li>
 *   <li>攻击敌人时给目标挂「卑贱之物」（伤害 -70%、受伤 +110%）。</li>
 *   <li>每次命中获得一层「脉动」：回血 +20%、移速 +10%、伤害 +30%（最多 10 层）。</li>
 *   <li>像吃饱的玩家一样自然回血：基础每 4 秒 1 点，脉动会缩短间隔。</li>
 * </ul>
 */
public class PoeyEntity extends PathfinderMob {

    public static final double MAX_HP = 1000.0D;

    /** 基础回血间隔：4 秒 1 点，和饱食度满的玩家一致。 */
    private static final int BASE_HEAL_INTERVAL = 80;
    /** 救场冷却：10 分钟。 */
    private static final int RESCUE_COOLDOWN = 20 * 60 * 10;
    /** 脉动最多 10 层。 */
    private static final int PULSE_MAX_LEVEL = 10;
    /** 脉动持续 30 秒，每次命中刷新。 */
    private static final int PULSE_DURATION = 20 * 30;

    private static final UUID PULSE_SPEED_UUID =
            UUID.fromString("8F2B6C41-0D3A-4E57-9B21-5C7D9A3E6F10");
    private static final UUID PULSE_DAMAGE_UUID =
            UUID.fromString("8F2B6C41-0D3A-4E57-9B21-5C7D9A3E6F11");

    private int healTicker = BASE_HEAL_INTERVAL;
    private int rescueCooldown = 0;
    private int lastPulseLevel = -1;
    /** 右键切换：坐下后不跟随、不攻击。Poey 不驯服，所以自己维护这个状态。 */
    private boolean sitting = false;

    public PoeyEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static Component displayName() {
        return Component.translatable("entity.tenfold.poey");
    }

    public boolean isSitting() {
        return this.sitting;
    }

    public void setSitting(boolean sitting) {
        this.sitting = sitting;
        if (sitting) {
            this.getNavigation().stop();
            this.setTarget(null);
        }
    }

    /* ------------------------------------------------------------------ */
    /* AI                                                                  */
    /* ------------------------------------------------------------------ */

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25D, true) {
            @Override
            public boolean canUse() {
                return !PoeyEntity.this.isSitting() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !PoeyEntity.this.isSitting() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(2, new FollowNearbyPlayerGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        // 只打敌对生物；Enemy 接口不包含玩家，所以不会误伤玩家。
        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
                (LivingEntity target) -> target instanceof Enemy && !(target instanceof PoeyEntity)));
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
        if (this.isSitting() && this.getTarget() != null) {
            this.setTarget(null);
        }
        tickPulse();
        tickHealing();
        tickRescue();
    }

    /** 按脉动层数更新移速 / 攻击力修正；层数没变就不动。 */
    private void tickPulse() {
        MobEffectInstance pulse = this.getEffect(ModEffects.PULSE.get());
        int levels = pulse == null ? 0 : pulse.getAmplifier() + 1;
        if (levels == this.lastPulseLevel) {
            return;
        }
        this.lastPulseLevel = levels;
        applyPulseModifier(Attributes.MOVEMENT_SPEED, PULSE_SPEED_UUID, 0.10D * levels);
        applyPulseModifier(Attributes.ATTACK_DAMAGE, PULSE_DAMAGE_UUID, 0.30D * levels);
    }

    private void applyPulseModifier(Attribute attribute, UUID id, double amount) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(id, "tenfold_pulse", amount,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** 自然回血：基础 4 秒 1 点，每层脉动 +20% 速度。 */
    private void tickHealing() {
        if (--this.healTicker > 0) {
            return;
        }
        MobEffectInstance pulse = this.getEffect(ModEffects.PULSE.get());
        int levels = pulse == null ? 0 : pulse.getAmplifier() + 1;
        this.healTicker = Math.max(20, (int) (BASE_HEAL_INTERVAL / (1.0F + 0.2F * levels)));
        if (this.getHealth() < this.getMaxHealth()) {
            this.heal(1.0F);
        }
    }

    /** 玩家血量低于 5 时救场：生命恢复 V + 抗性提升 III（各 10 秒），冷却 10 分钟。 */
    private void tickRescue() {
        if (this.rescueCooldown > 0) {
            this.rescueCooldown--;
            return;
        }
        if (this.isSitting()) {
            return;
        }
        Player player = this.level().getNearestPlayer(this, 16.0D);
        if (player == null || player.getHealth() >= 5.0F) {
            return;
        }
        // 生命恢复 V（amplifier 4）+ 抗性提升 III（amplifier 2），持续 10 秒。
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 4, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 10, 2, false, true, true));
        this.rescueCooldown = RESCUE_COOLDOWN;
        this.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 1.3F);
        player.displayClientMessage(Component.translatable("tenfold.poey.rescue"), true);
    }

    /* ------------------------------------------------------------------ */
    /* Combat                                                              */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide && target instanceof LivingEntity living) {
            // 卑贱之物：目标伤害 -70%，受伤 +110%。
            living.addEffect(new MobEffectInstance(ModEffects.ABJECT.get(), 20 * 10, 0, false, true, true));
            this.gainPulse();
        }
        return hit;
    }

    private void gainPulse() {
        MobEffectInstance current = this.getEffect(ModEffects.PULSE.get());
        int level = Math.min(PULSE_MAX_LEVEL, (current == null ? 0 : current.getAmplifier() + 1) + 1);
        this.addEffect(new MobEffectInstance(ModEffects.PULSE.get(), PULSE_DURATION, level - 1,
                false, true, true));
    }

    /* ------------------------------------------------------------------ */
    /* Interaction                                                         */
    /* ------------------------------------------------------------------ */

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            boolean sit = !this.isSitting();
            this.setSitting(sit);
            if (!sit) {
                this.getNavigation().stop();
            }
            player.displayClientMessage(Component.translatable(
                    sit ? "tenfold.poey.sit" : "tenfold.poey.stand"), true);
            this.level().playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.NEUTRAL, 0.8F, sit ? 0.8F : 1.3F);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /* ------------------------------------------------------------------ */
    /* Misc                                                                */
    /* ------------------------------------------------------------------ */

    /** 友方单位：玩家打不到它，但 /kill 之类的世界伤害仍然有效。 */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // 只有手持「神秘的匕首」的玩家能伤到 Poey（其余玩家伤害一律免疫）。
        if (source.getEntity() instanceof Player player
                && player.getMainHandItem().is(com.tenfold.ModItems.MYSTERIOUS_DAGGER.get())) {
            return super.isInvulnerableTo(source);
        }
        return source.getEntity() instanceof Player || super.isInvulnerableTo(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RescueCooldown", this.rescueCooldown);
        tag.putBoolean("Sitting", this.sitting);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.rescueCooldown = tag.getInt("RescueCooldown");
        this.sitting = tag.getBoolean("Sitting");
    }

    /** 跟随最近的玩家，直到对方离得太远或自己坐下。 */
    private static final class FollowNearbyPlayerGoal extends Goal {
        private final PoeyEntity poey;
        @Nullable
        private Player target;
        private int retryTicker;

        private FollowNearbyPlayerGoal(PoeyEntity poey) {
            this.poey = poey;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.poey.isSitting() || this.poey.getTarget() != null) {
                return false;
            }
            if (--this.retryTicker > 0) {
                return false;
            }
            this.retryTicker = 20;
            this.target = this.poey.level().getNearestPlayer(this.poey, 24.0D);
            return this.target != null && this.poey.distanceToSqr(this.target) > 25.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.poey.isSitting()
                    && this.target != null
                    && this.target.isAlive()
                    && this.poey.distanceToSqr(this.target) > 9.0D;
        }

        @Override
        public void start() {
            if (this.target != null) {
                this.poey.getNavigation().moveTo(this.target, 1.0D);
            }
        }

        @Override
        public void tick() {
            if (this.target != null) {
                this.poey.getNavigation().moveTo(this.target, 1.0D);
                this.poey.getLookControl().setLookAt(this.target, 10.0F, 10.0F);
            }
        }

        @Override
        public void stop() {
            this.target = null;
            this.poey.getNavigation().stop();
        }
    }
}
