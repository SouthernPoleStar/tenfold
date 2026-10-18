package com.tenfold.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 遗迹的实际方块搭建 + Boss 生成。
 * <p>
 * 形状完全由代码算出来：{@link #mask} 是一张 sizeX × sizeZ 的布尔表，
 * 只有 true 的格子才会铺地板，边缘格子才砌墙。这样可以做出不规则的平面轮廓，
 * 而不是死板的矩形。
 */
public class RuinPiece extends StructurePiece {

    private static final String TAG_KIND = "Kind";
    private static final String TAG_SIZE_X = "SizeX";
    private static final String TAG_SIZE_Z = "SizeZ";
    private static final String TAG_SHAPE_SEED = "ShapeSeed";
    private static final String TAG_BOSS = "BossPos";

    private final RuinKind kind;
    private final int sizeX;
    private final int sizeZ;
    private final long shapeSeed;
    private final BlockPos bossPos;
    private final boolean[][] mask;

    /* ------------------------------------------------------------------ */
    /* 构造                                                               */
    /* ------------------------------------------------------------------ */

    /** 世界生成时调用：随机尺寸、随机轮廓、随机 Boss 落点。 */
    public static RuinPiece create(RuinKind kind, int centerX, int surfaceY, int centerZ,
                                   RandomSource random) {
        int sizeX;
        int sizeZ;
        switch (kind) {
            case SPIDER_NEST_ROOF -> {
                // 20×20 ~ 40×40，横竖独立随机，再随机削角，所以不会总是正方形
                sizeX = 20 + random.nextInt(21);
                sizeZ = 20 + random.nextInt(21);
            }
            case IRON_RAIL_TEMPLE -> {
                // 10×15 的长方形，随机决定长边朝哪个方向
                if (random.nextBoolean()) {
                    sizeX = 15;
                    sizeZ = 10;
                } else {
                    sizeX = 10;
                    sizeZ = 15;
                }
            }
            case SPIDER_NEST_CORRIDOR -> {
                // 走廊：5~7 宽，20~30 长，随机横竖
                int width = 5 + random.nextInt(3);
                int length = 20 + random.nextInt(11);
                if (random.nextBoolean()) {
                    sizeX = length;
                    sizeZ = width;
                } else {
                    sizeX = width;
                    sizeZ = length;
                }
            }
            default -> {
                sizeX = 16;
                sizeZ = 16;
            }
        }

        int x0 = centerX - sizeX / 2;
        int z0 = centerZ - sizeZ / 2;
        long shapeSeed = random.nextLong();
        boolean[][] mask = buildMask(kind, sizeX, sizeZ, shapeSeed);
        BlockPos boss = pickBossPos(mask, sizeX, sizeZ, x0, surfaceY + 1, z0);
        if (boss == null) {
            return null;
        }

        BoundingBox box = new BoundingBox(
                x0, surfaceY, z0,
                x0 + sizeX - 1, surfaceY + kind.wallHeight + 1, z0 + sizeZ - 1);
        return new RuinPiece(kind, box, sizeX, sizeZ, shapeSeed, boss, mask);
    }

    private RuinPiece(RuinKind kind, BoundingBox box, int sizeX, int sizeZ,
                      long shapeSeed, BlockPos bossPos, boolean[][] mask) {
        super(ModStructures.RUIN_PIECE.get(), 0, box);
        this.kind = kind;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.shapeSeed = shapeSeed;
        this.bossPos = bossPos;
        this.mask = mask;
    }

    /** 读档时重建；形状用同一个 seed 重新算，保证与生成时一致。 */
    public RuinPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.RUIN_PIECE.get(), tag);
        this.kind = RuinKind.valueOf(tag.getString(TAG_KIND));
        this.sizeX = tag.getInt(TAG_SIZE_X);
        this.sizeZ = tag.getInt(TAG_SIZE_Z);
        this.shapeSeed = tag.getLong(TAG_SHAPE_SEED);
        this.bossPos = BlockPos.of(tag.getLong(TAG_BOSS));
        this.mask = buildMask(kind, sizeX, sizeZ, shapeSeed);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString(TAG_KIND, kind.name());
        tag.putInt(TAG_SIZE_X, sizeX);
        tag.putInt(TAG_SIZE_Z, sizeZ);
        tag.putLong(TAG_SHAPE_SEED, shapeSeed);
        tag.putLong(TAG_BOSS, bossPos.asLong());
    }

    /* ------------------------------------------------------------------ */
    /* 轮廓                                                               */
    /* ------------------------------------------------------------------ */

    private static boolean[][] buildMask(RuinKind kind, int sizeX, int sizeZ, long seed) {
        RandomSource random = RandomSource.create(seed);
        boolean[][] mask = new boolean[sizeX][sizeZ];

        if (kind == RuinKind.SPIDER_NEST_ROOF) {
            // 四个角各自随机削掉一块，再加一条随机凹口 —— 轮廓不是单纯的正方形
            int cutTopLeft = random.nextInt(7);
            int cutTopRight = random.nextInt(7);
            int cutBottomLeft = random.nextInt(7);
            int cutBottomRight = random.nextInt(7);

            int notchSide = random.nextInt(4);
            int notchDepth = 1 + random.nextInt(4);
            int notchStart = random.nextInt(Math.max(1, Math.min(sizeX, sizeZ) / 2));
            int notchLength = 3 + random.nextInt(5);

            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    boolean inside =
                            x + z >= cutTopLeft
                            && (sizeX - 1 - x) + z >= cutTopRight
                            && x + (sizeZ - 1 - z) >= cutBottomLeft
                            && (sizeX - 1 - x) + (sizeZ - 1 - z) >= cutBottomRight;

                    if (inside) {
                        boolean inNotch = switch (notchSide) {
                            case 0 -> z < notchDepth && x >= notchStart && x < notchStart + notchLength;
                            case 1 -> z >= sizeZ - notchDepth && x >= notchStart && x < notchStart + notchLength;
                            case 2 -> x < notchDepth && z >= notchStart && z < notchStart + notchLength;
                            default -> x >= sizeX - notchDepth && z >= notchStart && z < notchStart + notchLength;
                        };
                        if (inNotch) {
                            inside = false;
                        }
                    }
                    mask[x][z] = inside;
                }
            }
        } else {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    mask[x][z] = true;
                }
            }
        }
        return mask;
    }

    private boolean isEdge(int x, int z) {
        if (x <= 0 || z <= 0 || x >= sizeX - 1 || z >= sizeZ - 1) {
            return true;
        }
        return !mask[x - 1][z] || !mask[x + 1][z] || !mask[x][z - 1] || !mask[x][z + 1];
    }

    private static BlockPos pickBossPos(boolean[][] mask, int sizeX, int sizeZ,
                                        int x0, int y, int z0) {
        double cx = (sizeX - 1) / 2.0D;
        double cz = (sizeZ - 1) / 2.0D;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                if (!mask[x][z]) {
                    continue;
                }
                double dx = x - cx;
                double dz = z - cz;
                double dist = dx * dx + dz * dz;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new BlockPos(x0 + x, y, z0 + z);
                }
            }
        }
        return best;
    }

    /* ------------------------------------------------------------------ */
    /* 放方块                                                             */
    /* ------------------------------------------------------------------ */

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                            ChunkPos chunkPos, BlockPos origin) {
        int x0 = this.boundingBox.minX();
        int y0 = this.boundingBox.minY();
        int z0 = this.boundingBox.minZ();

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                if (!mask[x][z]) {
                    continue;
                }
                int worldX = x0 + x;
                int worldZ = z0 + z;

                place(level, chunkBox, worldX, y0, worldZ, kind.floor);
                // 地板下面垫一层，避免斜坡上出现悬空的一格
                place(level, chunkBox, worldX, y0 - 1, worldZ, kind.wall);

                if (isEdge(x, z)) {
                    for (int dy = 1; dy <= kind.wallHeight; dy++) {
                        place(level, chunkBox, worldX, y0 + dy, worldZ, kind.wallAt(dy));
                    }
                } else {
                    // 掏空内部，免得山体/树叶把场地塞住
                    for (int dy = 1; dy <= kind.wallHeight; dy++) {
                        place(level, chunkBox, worldX, y0 + dy, worldZ, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        if (chunkPos.equals(new ChunkPos(bossPos))) {
            spawnBoss(level);
        }
    }

    private void place(WorldGenLevel level, BoundingBox chunkBox, int x, int y, int z,
                       BlockState state) {
        if (!chunkBox.isInside(x, y, z)) {
            return;
        }
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    /* ------------------------------------------------------------------ */
    /* 守关 Boss                                                          */
    /* ------------------------------------------------------------------ */

    private void spawnBoss(WorldGenLevel level) {
        try {
            ServerLevel server = level.getLevel();
            EntityType<?> type = kind.boss.get();

            // 区块重新生成时不要叠出第二只
            AABB area = new AABB(bossPos).inflate(8.0D);
            List<Entity> nearby = server.getEntities((Entity) null, area,
                    entity -> entity.getType() == type);
            if (!nearby.isEmpty()) {
                return;
            }

            Entity entity = type.create(server);
            if (entity == null) {
                return;
            }
            entity.moveTo(bossPos.getX() + 0.5D, bossPos.getY(), bossPos.getZ() + 0.5D,
                    server.random.nextFloat() * 360.0F, 0.0F);
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, server.getCurrentDifficultyAt(bossPos),
                        MobSpawnType.STRUCTURE, null, null);
                mob.setPersistenceRequired();
            }
            server.addFreshEntity(entity);
        } catch (Exception exception) {
            com.tenfold.Tenfold.LOGGER.warn("Failed to spawn ruin boss for {}", kind.id, exception);
        }
    }
}
