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

    /** 莱万丁: 一把拆开层层包装后不断觉醒的长剑。 */
    public static final RegistryObject<Item> LEVANDIN = ITEMS.register("levandin",
            () -> new com.tenfold.item.LevandinItem());

    /** 马蒂亚斯刷怪蛋。 */
    public static final RegistryObject<Item> MATTHIAS_SPAWN_EGG = ITEMS.register("matthias_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.MATTHIAS,
                    0x3A5F3A, 0xC03018, new Item.Properties()));

    /** 雷横刷怪蛋。 */
    public static final RegistryObject<Item> LEIHENG_SPAWN_EGG = ITEMS.register("leiheng_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.LEIHENG,
                    0x2F4F6F, 0x4A6FA5, new Item.Properties()));

    /** 遮盖伤口的假面: 头盔，佩戴常驻力量II+抗性I，且无法获得指令成瘾。 */
    public static final RegistryObject<Item> MASK = ITEMS.register("mask",
            () -> new com.tenfold.item.MaskItem());

    /** 里恩刷怪蛋。 */
    public static final RegistryObject<Item> LIEN_SPAWN_EGG = ITEMS.register("lien_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.LIEN,
                    0x4A3F5F, 0xC9A648, new Item.Properties()));

    /** Poey 刷怪蛋：恶魂之泪 + 鸡蛋 + 下界合金碎片（1.20.1 没有"失水恶魂"，用恶魂之泪代替）。 */
    public static final RegistryObject<Item> POEY_SPAWN_EGG = ITEMS.register("poey_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.POEY,
                    0xEDEDED, 0x1C1C1C, new Item.Properties()));

    /** 恶魔契约: 僵尸30%掉落，锻造魔弹枪的材料。 */
    public static final RegistryObject<Item> DEMON_CONTRACT = ITEMS.register("demon_contract",
            () -> new Item(new Item.Properties()));

    /** 子弹: 合成天退星刀的材料。 */
    public static final RegistryObject<Item> BULLET = ITEMS.register("bullet",
            () -> new Item(new Item.Properties()));

    /** 魔弹枪: 远程，伤害递增，第7发强制击杀持有者。 */
    public static final RegistryObject<Item> MAGIC_GUN = ITEMS.register("magic_gun",
            () -> new com.tenfold.item.MagicGunItem());

    /** 天退星刀: 7格近战，双弹匣（猛虎弹/猛虎标弹），可自动重装一次。 */
    public static final RegistryObject<Item> SKY_BLADE = ITEMS.register("sky_blade",
            () -> new com.tenfold.item.SkyBladeItem());

    /** 长剑：攻击力 10，命中叠「情感」，满 15 层后 Shift+右键进入「不稳定的神备」。 */
    public static final RegistryObject<Item> LONGSWORD = ITEMS.register("longsword",
            () -> new com.tenfold.item.LongswordItem());

    /** 神备套装（只由神备强制穿戴，穿上不显示模型）。 */
    public static final RegistryObject<Item> GEAR_HELMET = ITEMS.register("gear_helmet",
            () -> new com.tenfold.item.GearArmorItem(net.minecraft.world.item.ArmorItem.Type.HELMET,
                    7.0D, "A1B2C3D4-0001-4E5F-8A90-1234567890AB"));
    public static final RegistryObject<Item> GEAR_CHEST = ITEMS.register("gear_chest",
            () -> new com.tenfold.item.GearArmorItem(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
                    8.0D, "A1B2C3D4-0002-4E5F-8A90-1234567890AB"));
    public static final RegistryObject<Item> GEAR_LEGS = ITEMS.register("gear_legs",
            () -> new com.tenfold.item.GearArmorItem(net.minecraft.world.item.ArmorItem.Type.LEGGINGS,
                    6.0D, "A1B2C3D4-0003-4E5F-8A90-1234567890AB"));
    public static final RegistryObject<Item> GEAR_BOOTS = ITEMS.register("gear_boots",
            () -> new com.tenfold.item.GearArmorItem(net.minecraft.world.item.ArmorItem.Type.BOOTS,
                    8.0D, "A1B2C3D4-0004-4E5F-8A90-1234567890AB"));

    private ModItems() {
    }
}
