package com.tenfold;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Tenfold.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tenfold"))
                    .icon(() -> new ItemStack(ModItems.TENFOLD_BLADE.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.TENFOLD_BLADE.get());
                        output.accept(ModItems.SNIPER.get());
                        output.accept(ModItems.AMMO_BOX.get());
                        output.accept(ModItems.OATH_SHARD.get());
                        output.accept(ModItems.LEVANDIN.get());
                        output.accept(ModItems.MATTHIAS_SPAWN_EGG.get());
                        output.accept(potion(Items.POTION, ModPotions.IRA_RELEASE.get()));
                        output.accept(potion(Items.POTION, ModPotions.LONG_IRA_RELEASE.get()));
                        output.accept(potion(Items.SPLASH_POTION, ModPotions.IRA_RELEASE.get()));
                        output.accept(potion(Items.SPLASH_POTION, ModPotions.LONG_IRA_RELEASE.get()));
                    })
                    .build());

    private static ItemStack potion(net.minecraft.world.item.Item container,
                                    net.minecraft.world.item.alchemy.Potion potion) {
        return PotionUtils.setPotion(new ItemStack(container), potion);
    }

    private ModTabs() {
    }
}
