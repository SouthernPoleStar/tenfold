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

    /** 3 minutes of IRA Full Release. */
    public static final RegistryObject<Potion> IRA_RELEASE = POTIONS.register("ira_release",
            () -> new Potion("ira_release",
                    new MobEffectInstance(ModEffects.IRA_RELEASE.get(), 3 * 60 * 20, 0)));

    /** 8 minutes, from redstone. */
    public static final RegistryObject<Potion> LONG_IRA_RELEASE = POTIONS.register("long_ira_release",
            () -> new Potion("long_ira_release",
                    new MobEffectInstance(ModEffects.IRA_RELEASE.get(), 8 * 60 * 20, 0)));

    private ModPotions() {
    }
}
