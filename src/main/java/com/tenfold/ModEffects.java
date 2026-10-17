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

    private ModEffects() {
    }
}
