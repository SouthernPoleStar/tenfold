package com.tenfold.entity;

import com.tenfold.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Poee —— 带着「抛弃」死去的 Poey 变成的敌对 BOSS。
 * <ul>
 *   <li>血量 **5000**，攻击时给目标挂 **90 秒**的「卑贱之物」。</li>
 *   <li>每次命中叠一层「脉动」，最多 **20 层**。</li>
 *   <li>血量低于一半时获得永久「我是 Poey」：受到伤害 +100%、造成伤害 -100%、禁止回血，
 *       并在玩家状态栏反复显示「p... ...Poey，请用那个名字，呼唤我」。</li>
 * </ul>
 */
public class PoeeEntity extends Monster {

    public static final double MAX_HP = 5000.0D;
    /** 脉动最多 20 层。 */
    public static final int PULSE_MAX_LEVEL = 20;
    private static final int PULSE_DURATION = 20 * 30;
    /** 卑贱之物持续时间：1 分半。 */
    public static final int ABJECT_TICKS = 20 * 90;
    private static final int BASE_HEAL_INTERVAL = 80;
    /** 脉动每层的移速加成（比 Poey 低，避免追不上）。 */
    private static final double PULSE_SPEED_PER_LEVEL = 0.03D;
    /** 移速硬上限，无论如何都不会超过这个值。 */
    private static final double MAX_MOVE_SPEED = 0.34D;

    private static final UUID PULSE_SPEED_UUID =
            UUID.fromString("A1B2C3D4-0020-4FB6-8CD7-8901234567B1");
    private static final UUID PULSE_DAMAGE_UUID =
            UUID.fromString("A1B2C3D4-0021-4FB6-8CD7-8901234567B2");

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    private int healTicker = BASE_HEAL_INTERVAL;
    private int lastPulseLevel = -1;
    private boolean phaseTwo = false;

    public PoeeEntity(EntityType<? extends PoeeEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 500;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    public static Component displayName() {
        return Component.translatable("entity.tenfold.poee");
    }

    public boolean isPhaseTwo() {
        return phaseTwo;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 自定义追击：寻路失败也会直接朝玩家位移，避免「杵着不动」
        this.goalSelector.addGoal(2, new ChaseAndHitGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // mustSee = false：隔着方块也会锁定玩家
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
        this.getNavigation().setCanFloat(true);
    }

    /** 追击 + 近战：先走寻路，寻路不动就直接朝目标平移，近身即攻击。 */
    private static class ChaseAndHitGoal extends Goal {

        private static final double REACH = 2.6D;
        private final PoeeEntity poee;
        private int cooldown;

        ChaseAndHitGoal(PoeeEntity poee) {
            this.poee = poee;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = poee.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            cooldown = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = poee.getTarget();
            if (target == null) {
                return;
            }
            poee.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distSqr = poee.distanceToSqr(target);
            if (distSqr > REACH * REACH) {
                // 寻路
                if (poee.getNavigation().isDone()) {
                    poee.getNavigation().moveTo(target, 1.0D);
                }
                // 兜底：寻路卡住或走不动时，直接朝目标平移
                boolean stuck = poee.getNavigation().isDone()
                        || poee.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4D;
                if (stuck) {
                    poee.getMoveControl().setWantedPosition(
                            target.getX(), target.getY(), target.getZ(), 1.0D);
                }
                return;
            }
            poee.getNavigation().stop();
            if (cooldown <= 0) {
                poee.doHurtTarget(target);
                poee.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                cooldown = 20;
            } else {
                cooldown--;
            }
        }

        @Override
        public void stop() {
            poee.getNavigation().stop();
            cooldown = 0;
        }
    }

    /* ------------------------------------------------------------------ */
    /* 攻击：卑贱之物 + 脉动                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(ModEffects.ABJECT.get(), ABJECT_TICKS,
                    0, false, true, true));
            addPulse();
        }
        return hit;
    }

    private void addPulse() {
        int level = Math.min(PULSE_MAX_LEVEL, pulseLevel() + 1);
        this.addEffect(new MobEffectInstance(ModEffects.PULSE.get(), PULSE_DURATION,
                level - 1, false, true, true));
    }

    public int pulseLevel() {
        MobEffectInstance effect = this.getEffect(ModEffects.PULSE.get());
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    /* ------------------------------------------------------------------ */
    /* tick                                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        this.bossEvent.setProgress(Math.max(0.0F, this.getHealth() / this.getMaxHealth()));
        applyPulseModifiers();
        tickHealing();
        tickPhase();
    }

    private void applyPulseModifiers() {
        int level = pulseLevel();
        if (level == lastPulseLevel) {
            clampSpeed();
            return;
        }
        lastPulseLevel = level;
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (speed != null) {
            speed.removeModifier(PULSE_SPEED_UUID);
            if (level > 0) {
                speed.addTransientModifier(new AttributeModifier(PULSE_SPEED_UUID,
                        "tenfold_poee_pulse_speed", PULSE_SPEED_PER_LEVEL * level,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        if (damage != null) {
            damage.removeModifier(PULSE_DAMAGE_UUID);
            if (level > 0) {
                damage.addTransientModifier(new AttributeModifier(PULSE_DAMAGE_UUID,
                        "tenfold_poee_pulse_damage", 0.30D * level,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        clampSpeed();
    }

    /** 移速硬上限：叠加脉动也不会超过 {@link #MAX_MOVE_SPEED}，避免「乱飞」追不上。 */
    private void clampSpeed() {
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double base = speed.getBaseValue();
        double current = speed.getValue();
        if (current > MAX_MOVE_SPEED && MAX_MOVE_SPEED > base) {
            speed.removeModifier(PULSE_SPEED_UUID);
            double allowed = (MAX_MOVE_SPEED / base) - 1.0D;
            if (allowed > 0.0D) {
                speed.addTransientModifier(new AttributeModifier(PULSE_SPEED_UUID,
                        "tenfold_poee_pulse_speed", allowed, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

    /** 自然回血；「我是 Poey」期间完全禁止。 */
    private void tickHealing() {
        if (this.hasEffect(ModEffects.I_AM_POEY.get())) {
            return;
        }
        if (--healTicker > 0) {
            return;
        }
        healTicker = Math.max(10, (int) (BASE_HEAL_INTERVAL / (1.0D + 0.2D * pulseLevel())));
        if (this.getHealth() < this.getMaxHealth()) {
            this.heal(1.0F);
        }
    }

    /** 血量低于一半：进入第二阶段（台词只显示一次）。 */
    private void tickPhase() {
        if (phaseTwo || this.getHealth() >= this.getMaxHealth() * 0.5F) {
            return;
        }
        phaseTwo = true;
        this.addEffect(new MobEffectInstance(ModEffects.I_AM_POEY.get(), -1, 0, false, true, true));
        // 二阶段彻底钉在原地：缓慢 255（永久）
        this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, -1, 254, false, true, true));
        this.removeEffect(ModEffects.PULSE.get());
        lastPulseLevel = -1;
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.2D, this.getZ(),
                    60, 0.6D, 1.0D, 0.6D, 0.02D);
            AABB area = this.getBoundingBox().inflate(32.0D);
            for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area)) {
                player.displayClientMessage(Component.translatable("tenfold.poee.quote"), true);
            }
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE, 1.0F, 0.6F);
        this.bossEvent.setName(Component.translatable("entity.tenfold.poee.phase2"));
    }

    /* ------------------------------------------------------------------ */
    /* 死亡 / 受击                                                          */
    /* ------------------------------------------------------------------ */

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.bossEvent.setProgress(0.0F);
        this.bossEvent.removeAllPlayers();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source);
    }

    /* ------------------------------------------------------------------ */
    /* BOSS 血条                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean canRide(net.minecraft.world.entity.Entity vehicle) {
        return false;
    }

    /** 附近的玩家列表（供外部调试用）。 */
    public List<Player> nearbyPlayers(double radius) {
        return this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(radius));
    }

    /** 是否处于禁止回血状态。 */
    public boolean healingDisabled() {
        return this.hasEffect(ModEffects.I_AM_POEY.get());
    }

    /** 攻击附带的效果（避免外部硬编码）。 */
    public static MobEffectInstance abjectEffect() {
        return new MobEffectInstance(ModEffects.ABJECT.get(), ABJECT_TICKS, 0, false, true, true);
    }
}
