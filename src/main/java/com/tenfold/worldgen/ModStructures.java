package com.tenfold.worldgen;

import com.mojang.serialization.Codec;
import com.tenfold.Tenfold;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 三座「遗迹」的结构注册。
 * <p>
 * 结构由 {@link RuinStructure} 用代码生成（不依赖 .nbt 模板），所以只需要注册
 * 一个 StructureType + 一个 StructurePieceType，再配上 datapack 里的
 * {@code worldgen/structure} 与 {@code worldgen/structure_set}。
 */
public final class ModStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Tenfold.MOD_ID);

    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Tenfold.MOD_ID);

    /** 蜘蛛巢的屋顶（里恩）。 */
    public static final RegistryObject<StructureType<RuinStructure>> SPIDER_NEST_ROOF =
            STRUCTURE_TYPES.register("spider_nest_roof",
                    () -> type(RuinStructure.NEST_ROOF_CODEC));

    /** 铁栏寺（雷横）。 */
    public static final RegistryObject<StructureType<RuinStructure>> IRON_RAIL_TEMPLE =
            STRUCTURE_TYPES.register("iron_rail_temple",
                    () -> type(RuinStructure.IRON_TEMPLE_CODEC));

    /** 蜘蛛巢的走廊（马蒂亚斯）。 */
    public static final RegistryObject<StructureType<RuinStructure>> SPIDER_NEST_CORRIDOR =
            STRUCTURE_TYPES.register("spider_nest_corridor",
                    () -> type(RuinStructure.NEST_CORRIDOR_CODEC));

    public static final RegistryObject<StructurePieceType> RUIN_PIECE =
            PIECE_TYPES.register("ruin_piece", () -> RuinPiece::new);

    private static StructureType<RuinStructure> type(Codec<RuinStructure> codec) {
        return () -> codec;
    }

    public static StructureType<?> typeFor(RuinKind kind) {
        return switch (kind) {
            case SPIDER_NEST_ROOF -> SPIDER_NEST_ROOF.get();
            case IRON_RAIL_TEMPLE -> IRON_RAIL_TEMPLE.get();
            case SPIDER_NEST_CORRIDOR -> SPIDER_NEST_CORRIDOR.get();
        };
    }

    private ModStructures() {
    }
}
