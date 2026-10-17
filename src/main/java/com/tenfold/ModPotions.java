package com.tenfold;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Potions. IRA Full Release is brewed from the Oath Shard.
 */
public final class ModPotions {
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(ForgeRegistries.POTIONS, Tenfold.MOD_ID);

    /** 1 minute of IRA Full Release. */
    public static final RegistryObject<Potion> IRA_RELEASE = POTIONS.register("ira_release",
            () -> new Potion("ira_release",
                    new MobEffectInstance(ModEffects.IRA_RELEASE.get(), 60 * 20, 0)));

    /** 2 minutes, from redstone. */
    public static final RegistryObject<Potion> LONG_IRA_RELEASE = POTIONS.register("long_ira_release",
            () -> new Potion("long_ira_release",
                    new MobEffectInstance(ModEffects.IRA_RELEASE.get(), 2 * 60 * 20, 0)));

    private ModPotions() {
    }
}
