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
                        output.accept(ModItems.LEIHENG_SPAWN_EGG.get());
                        output.accept(ModItems.LIEN_SPAWN_EGG.get());
                        output.accept(ModItems.POEY_SPAWN_EGG.get());
                        output.accept(ModItems.MAGIC_GUN.get());
                        output.accept(ModItems.MASK.get());
                        output.accept(ModItems.DEMON_CONTRACT.get());
                        output.accept(ModItems.BULLET.get());
                        output.accept(ModItems.SKY_BLADE.get());
                        output.accept(ModItems.LONGSWORD.get());
                        output.accept(ModItems.GEAR_HELMET.get());
                        output.accept(ModItems.GEAR_CHEST.get());
                        output.accept(ModItems.GEAR_LEGS.get());
                        output.accept(ModItems.GEAR_BOOTS.get());
                        output.accept(ModItems.DAWN_LONGSWORD.get());
                        output.accept(ModItems.MOTH_COFFIN.get());
                        output.accept(ModItems.BLOOD_FANG.get());
                        output.accept(ModItems.BLOOD_BOTTLE.get());
                        output.accept(ModItems.HARD_BLOOD_BOTTLE.get());
                        output.accept(ModItems.SACRIFICIAL_DAGGER.get());
                        output.accept(ModItems.HARD_BLOOD_WEAPON.get());
                        output.accept(ModItems.BLOOD_ANCESTOR_SPAWN_EGG.get());
                        output.accept(ModItems.ENGAGEMENT_RING.get());
                        output.accept(ModItems.FRAGMENT.get());
                        output.accept(ModItems.WILD_HUNT_COFFIN.get());
                        output.accept(ModItems.WILD_HUNT_GREATSWORD.get());
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
