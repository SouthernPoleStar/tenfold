package com.tenfold.item;

import net.minecraft.world.item.Item;

/**
 * 硬血瓶：祭祀仪式（副手祭祀匕首 + 主手空瓶 + 潜行右键）以自己的
 * 1 点生命为代价凝成的血块。不可饮用，用来合成硬血武器。
 */
public class HardBloodBottleItem extends Item {

    public HardBloodBottleItem() {
        super(new Item.Properties().stacksTo(16));
    }
}
