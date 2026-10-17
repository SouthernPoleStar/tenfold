package com.tenfold;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * Custom damage types. The demon gun's self-kill uses its own type so the
 * death message reads 「xxx 使用魔弹枪贯穿了自己」. The type data itself is
 * provided by the datapack JSON at data/tenfold/damage_type/magic_bullet.json.
 */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> MAGIC_BULLET =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    new ResourceLocation(Tenfold.MOD_ID, "magic_bullet"));

    private ModDamageTypes() {
    }

    /** Builds the damage source from the datapack-loaded type. */
    public static net.minecraft.world.damagesource.DamageSource of(
            net.minecraft.world.level.Level level, ResourceKey<DamageType> key) {
        var holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key);
        return new net.minecraft.world.damagesource.DamageSource(holder);
    }
}
