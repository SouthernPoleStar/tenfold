package com.tenfold.item;

import net.minecraft.world.item.Item;

/**
 * 祭祀匕首：下界合金碎片 + 木棍。配合血瓶举行取血仪式：
 * 副手拿匕首、主手拿瓶子、潜行右键，扣自己 1 点生命，凝成硬血瓶。
 */
public class SacrificialDaggerItem extends Item {

    public SacrificialDaggerItem() {
        super(new Item.Properties().stacksTo(1).durability(64));
    }
}
