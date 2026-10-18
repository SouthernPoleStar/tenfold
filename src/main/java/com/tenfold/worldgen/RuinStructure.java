package com.tenfold.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * 代码生成的遗迹结构。
 * <p>
 * 三种遗迹共用这一个类，靠 {@link RuinKind} 区分外观与守关 Boss；各自的
 * StructureType 只负责绑定一个把 kind 写死的 codec，因此 datapack 里不需要
 * 额外的 kind 字段。
 */
public class RuinStructure extends Structure {

    public static final Codec<RuinStructure> NEST_ROOF_CODEC =
            simpleCodec(settings -> new RuinStructure(settings, RuinKind.SPIDER_NEST_ROOF));

    public static final Codec<RuinStructure> IRON_TEMPLE_CODEC =
            simpleCodec(settings -> new RuinStructure(settings, RuinKind.IRON_RAIL_TEMPLE));

    public static final Codec<RuinStructure> NEST_CORRIDOR_CODEC =
            simpleCodec(settings -> new RuinStructure(settings, RuinKind.SPIDER_NEST_CORRIDOR));

    private final RuinKind kind;

    public RuinStructure(StructureSettings settings, RuinKind kind) {
        super(settings);
        this.kind = kind;
    }

    public RuinKind kind() {
        return kind;
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        // 结构以区块中心为原点，落在地表。
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int y = context.chunkGenerator().getBaseHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());

        // 场地要占十几格高，贴近建造上限就放弃，免得被切掉
        if (y < context.heightAccessor().getMinBuildHeight() + 2
                || y > context.heightAccessor().getMaxBuildHeight() - 16) {
            return Optional.empty();
        }

        RandomSource random = context.random();
        RuinPiece piece = RuinPiece.create(kind, x, y, z, random);
        if (piece == null) {
            return Optional.empty();
        }

        BlockPos origin = new BlockPos(x, y, z);
        return Optional.of(new Structure.GenerationStub(origin, builder -> builder.addPiece(piece)));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.typeFor(kind);
    }
}
