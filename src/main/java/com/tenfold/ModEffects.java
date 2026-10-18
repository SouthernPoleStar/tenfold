package com.tenfold;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Tenfold.MOD_ID);

    /** Marked: applied to contract targets so the player can see who to kill. */
    public static final RegistryObject<MobEffect> MARKED = EFFECTS.register("marked",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x8A2B2B) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * Contract Addiction: +10 attack damage (5 hearts) on every strike.
     * Granted after four contracts completed back to back.
     * The attribute work happens in CommonEvents so it survives relogging.
     */
    public static final RegistryObject<MobEffect> ADDICTION = EFFECTS.register("addiction",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x9B1B5A) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * IRA Full Release: the player cannot die, deals +90% damage, takes 70%
     * less damage. All damage taken during the effect is stored and settles at
     * one tenth once the effect ends. The mechanics live in CommonEvents.
     */
    public static final RegistryObject<MobEffect> IRA_RELEASE = EFFECTS.register("ira_release",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xB01030) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 过人的毅力 (Unyielding Grit): Matthias's last-stand buff. Any single hit
     * against the bearer is forced down to at most 3 damage, while the bearer
     * deals double damage. Damage rules live in CommonEvents.
     */
    public static final RegistryObject<MobEffect> PERSEVERANCE = EFFECTS.register("perseverance",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xC03018) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 过人的毅力（我方版）: holding a fully unwrapped Levandin at 30% HP or
     * below grants Absorption worth 200% of the missing health. Same icon as
     * the boss version, but a separate effect so the two never interfere.
     */
    public static final RegistryObject<MobEffect> PERSEVERANCE_ALLY = EFFECTS.register("perseverance_ally",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xC03018) {
                @Override
                public void applyEffectTick(net.minecraft.world.entity.LivingEntity entity, int amplifier) {
                    // 与原版 AbsorptionEffect 相同：每级 4 点吸收（2 颗黄心），只增不减
                    entity.setAbsorptionAmount(
                            Math.max(entity.getAbsorptionAmount(), 4.0F * (amplifier + 1)));
                }

                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return true; // 每 tick 执行 applyEffectTick，保证黄心被顶掉后立刻补回
                }

                @Override
                public void removeAttributeModifiers(net.minecraft.world.entity.LivingEntity entity,
                        net.minecraft.world.entity.ai.attributes.AttributeMap map, int amplifier) {
                    super.removeAttributeModifiers(entity, map, amplifier);
                    // 效果结束时按原版规则回收溢出的黄心
                    entity.setAbsorptionAmount(Math.min(entity.getAbsorptionAmount(), entity.getHealth()));
                }
            });

    /**
     * 钢气 (Steel Qi): Lei Heng stacks this on every successful strike, +10%
     * damage per level, up to 255 levels. Damage rule lives in CommonEvents.
     */
    public static final RegistryObject<MobEffect> STEEL_QI = EFFECTS.register("steel_qi",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x4A6FA5) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 卑贱之物 (Abject Thing): Poey's mark. The bearer deals 30% damage and
     * takes 210% damage. Both multipliers live in CommonEvents.
     */
    public static final RegistryObject<MobEffect> ABJECT = EFFECTS.register("abject",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6B2FA0) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 脉动 (Pulse): Poey gains one stack per successful hit. Each stack means
     * +20% healing speed, +10% movement speed and +30% attack damage; the
     * attribute side is applied by PoeyEntity itself.
     */
    public static final RegistryObject<MobEffect> PULSE = EFFECTS.register("pulse",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFF6FA8) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    private ModEffects() {
    }
}
