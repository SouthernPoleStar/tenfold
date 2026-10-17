package com.tenfold;

import com.tenfold.item.TenfoldBladeItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Tenfold.MOD_ID);

    public static final RegistryObject<Item> TENFOLD_BLADE = ITEMS.register("tenfold_blade",
            () -> new TenfoldBladeItem());

    /**
     * Dedicated advancement icons. Advancement display icons can only be items,
     * so each custom milestone image gets a small hidden item: not in any
     * creative tab, no recipe, never seen in normal play.
     */
    public static final RegistryObject<Item> ADV_ISSUED = ITEMS.register("adv_issued",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ADV_OBEYED = ITEMS.register("adv_obeyed",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ADV_ADDICT = ITEMS.register("adv_addict",
            () -> new Item(new Item.Properties()));

    /** 守护他人的决意: dropped when a tamed creature is killed by a hostile mob. */
    public static final RegistryObject<Item> OATH_SHARD = ITEMS.register("oath_shard",
            () -> new com.tenfold.item.OathShardItem());

    /** 句点事务所·狙击枪: hitscan rifle with two internal magazines. */
    public static final RegistryObject<Item> SNIPER = ITEMS.register("sniper",
            () -> new com.tenfold.item.SniperItem());

    /** 肩挂式弹药箱: +10 armor in the off hand, refills teammates' empty snipers. */
    public static final RegistryObject<Item> AMMO_BOX = ITEMS.register("ammo_box",
            () -> new com.tenfold.item.AmmoBoxItem());

    private ModItems() {
    }
}
