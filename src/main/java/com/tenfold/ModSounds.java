package com.tenfold;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Tenfold.MOD_ID);

    public static final RegistryObject<SoundEvent> FORM_SHIFT = register("form_shift");
    public static final RegistryObject<SoundEvent> CONTRACT_MADE = register("contract_made");
    public static final RegistryObject<SoundEvent> CONTRACT_FAILED = register("contract_failed");
    public static final RegistryObject<SoundEvent> CONTRACT_DONE = register("contract_done");
    public static final RegistryObject<SoundEvent> DEGRADE = register("degrade");

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(Tenfold.MOD_ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {
    }
}
