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

    /**
     * 情感 (Emotion): 长剑每次命中叠加一层，最多 15 层。到 15 层后可以用
     * Shift+右键消耗全部层数换取「不稳定的神备」。
     */
    public static final RegistryObject<MobEffect> EMOTION = EFFECTS.register("emotion",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xE04060) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 不稳定的神备 (Unstable Gear): 长剑的爆发形态。伤害 +20%、受伤 +15%、
     * 移速 +30%；强制换上一套不显示模型的护甲，长剑攻击力 +30 并附带灼烧，
     * 期间第一次致命伤害会被一次「不死图腾」抵消。属性与护甲逻辑在
     * {@code UnstableGearManager} 里结算。
     */
    public static final RegistryObject<MobEffect> UNSTABLE_GEAR = EFFECTS.register("unstable_gear",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xB02040) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 黎明 (Dawn): 黎明长剑每次命中叠加一层，每层攻击力 +3；满 10 层后
     * 自动消耗全部层数转化为「天将拂晓」。攻击力修正由 {@code DawnManager} 结算。
     */
    public static final RegistryObject<MobEffect> DAWN = EFFECTS.register("dawn",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xF0C060) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 天将拂晓 (Dawn Ascension): 黎明满层后的升华形态。攻击 +30%、
     * 受到伤害 -20%，且近战攻击必定暴击。规则在 {@code CommonEvents} 里结算。
     */
    public static final RegistryObject<MobEffect> DAWN_ASCENSION = EFFECTS.register("dawn_ascension",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFFB020) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 恐水症：血魔的常驻负面状态。沾水持续扣血（中毒式），潜水直接窒息，
     * 具体伤害结算在 {@code BloodDemonEvents#onPlayerTick}。
     */
    public static final RegistryObject<MobEffect> HYDROPHOBIA = EFFECTS.register("hydrophobia",
            () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x1F5F8A) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 渴血：血魔的吸血条（饱食度）清零时获得的负面状态。
     * 自身攻击翻倍、受到的伤害翻 3 倍，并附赠一层缓慢。
     * 伤害修正在 {@code BloodDemonEvents#onLivingHurt} 结算，喝血后自动解除。
     */
    public static final RegistryObject<MobEffect> BLOODTHIRST = EFFECTS.register("bloodthirst",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6B0F14) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 撕裂的心：同时穿戴狂猎棺材、手持狂猎大剑时的常驻状态。
     * 造成伤害 +50%、受到伤害 +10%、移速 +3%、生命上限 +6。
     * 属性与伤害修正在 {@code WildHuntManager} / {@code WildHuntEvents} 结算。
     */
    public static final RegistryObject<MobEffect> TORN_HEART = EFFECTS.register("torn_heart",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xB01E1E) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * EGO 绽放 · 亢龙有悔：关刀的情感满 10 层且血量低于一半时 Shift+右键进入，持续 1 分钟。
     * 生命上限翻倍、力量 V、抗性 II、开始 30 秒生命恢复 III，期间强制穿着「燃烧的羽毛」。
     */
    public static final RegistryObject<MobEffect> EGO_BLOOM = EFFECTS.register("ego_bloom",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xE8B93A) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 情感（关刀）：关刀专属的情感层数，与长剑的「情感」完全独立，
     * 避免两把武器的层数互相串着触发 EGO。上限 10 层。
     */
    public static final RegistryObject<MobEffect> GLAIVE_EMOTION = EFFECTS.register("glaive_emotion",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xD98A2B) {
                @Override
                public boolean isDurationEffectTick(int duration, int amplifier) {
                    return false;
                }
            });

    /**
     * 一代~十代血魔：十个独立效果，让「x代血魔」能作为效果名直接显示。
     * 玩家由二代起跳（被血魔老祖转化的都是二代），往上依次传染。
     */
    public static final RegistryObject<MobEffect> BLOOD_GEN_1 = registerBloodGen("blood_gen_1");
    public static final RegistryObject<MobEffect> BLOOD_GEN_2 = registerBloodGen("blood_gen_2");
    public static final RegistryObject<MobEffect> BLOOD_GEN_3 = registerBloodGen("blood_gen_3");
    public static final RegistryObject<MobEffect> BLOOD_GEN_4 = registerBloodGen("blood_gen_4");
    public static final RegistryObject<MobEffect> BLOOD_GEN_5 = registerBloodGen("blood_gen_5");
    public static final RegistryObject<MobEffect> BLOOD_GEN_6 = registerBloodGen("blood_gen_6");
    public static final RegistryObject<MobEffect> BLOOD_GEN_7 = registerBloodGen("blood_gen_7");
    public static final RegistryObject<MobEffect> BLOOD_GEN_8 = registerBloodGen("blood_gen_8");
    public static final RegistryObject<MobEffect> BLOOD_GEN_9 = registerBloodGen("blood_gen_9");
    public static final RegistryObject<MobEffect> BLOOD_GEN_10 = registerBloodGen("blood_gen_10");

    private static RegistryObject<MobEffect> registerBloodGen(String id) {
        return EFFECTS.register(id, () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x8B1A1A) {
            @Override
            public boolean isDurationEffectTick(int duration, int amplifier) {
                return false;
            }
        });
    }

    /** 按代数取对应的「x代血魔」效果（越界截断到 1~10）。 */
    public static MobEffect bloodGenEffect(int generation) {
        int clamped = Math.max(1, Math.min(10, generation));
        return switch (clamped) {
            case 1 -> BLOOD_GEN_1.get();
            case 2 -> BLOOD_GEN_2.get();
            case 3 -> BLOOD_GEN_3.get();
            case 4 -> BLOOD_GEN_4.get();
            case 5 -> BLOOD_GEN_5.get();
            case 6 -> BLOOD_GEN_6.get();
            case 7 -> BLOOD_GEN_7.get();
            case 8 -> BLOOD_GEN_8.get();
            case 9 -> BLOOD_GEN_9.get();
            default -> BLOOD_GEN_10.get();
        };
    }

    private ModEffects() {
    }
}
