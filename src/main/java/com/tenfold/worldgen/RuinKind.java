package com.tenfold.worldgen;

import com.tenfold.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 三座遗迹的外观、尺寸与守卫 Boss。
 * <p>
 * 尺寸不固定：蜘蛛巢的屋顶是 20~40 的随机多边形，铁栏寺是 10×15 的长方形，
 * 蜘蛛巢的走廊是 5~7 宽、20~30 长的长条。
 */
public enum RuinKind {

    /** 蜘蛛巢的屋顶：黑曜石地板 + 强化深板岩墙，里恩把守，掉落双蛇杖。 */
    SPIDER_NEST_ROOF("spider_nest_roof",
            Blocks.OBSIDIAN.defaultBlockState(),
            Blocks.REINFORCED_DEEPSLATE.defaultBlockState(),
            null, 4,
            () -> ModEntities.LIEN.get()),

    /** 铁栏寺：橡木木板地板 + 朱红色混凝土墙 + 黄色混凝土顶，雷横把守。 */
    IRON_RAIL_TEMPLE("iron_rail_temple",
            Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.RED_CONCRETE.defaultBlockState(),
            Blocks.YELLOW_CONCRETE.defaultBlockState(), 5,
            () -> ModEntities.LEIHENG.get()),

    /** 蜘蛛巢的走廊：白桦木板地板 + 哭泣的黑曜石墙，马蒂亚斯把守。 */
    SPIDER_NEST_CORRIDOR("spider_nest_corridor",
            Blocks.BIRCH_PLANKS.defaultBlockState(),
            Blocks.CRYING_OBSIDIAN.defaultBlockState(),
            null, 3,
            () -> ModEntities.MATTHIAS.get());

    public final String id;
    public final BlockState floor;
    public final BlockState wall;
    /** 墙顶那一层换成这个方块；null 表示墙顶就是墙本身。 */
    @Nullable
    public final BlockState top;
    /** 墙高（不含地板那一层）。 */
    public final int wallHeight;
    public final Supplier<EntityType<?>> boss;

    RuinKind(String id, BlockState floor, BlockState wall, @Nullable BlockState top,
             int wallHeight, Supplier<EntityType<?>> boss) {
        this.id = id;
        this.floor = floor;
        this.wall = wall;
        this.top = top;
        this.wallHeight = wallHeight;
        this.boss = boss;
    }

    public BlockState wallAt(int dy) {
        if (top != null && dy == wallHeight) {
            return top;
        }
        return wall;
    }
}
