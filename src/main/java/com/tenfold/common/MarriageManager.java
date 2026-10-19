package com.tenfold.common;

import com.tenfold.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 订婚戒指 / 夫妻关系：
 * <ul>
 *   <li>两名玩家各持一枚「订婚戒指」，其中一人右键对方即可结为夫妻。</li>
 *   <li>夫妻任意一方死亡，有 40% 概率掉落两个「残片」（掉落逻辑在 {@code WildHuntEvents}）。</li>
 * </ul>
 * 配偶 UUID 存在玩家持久数据的 {@code tenfoldSpouse} 里。
 */
public final class MarriageManager {

    public static final String KEY_SPOUSE = "tenfoldSpouse";
    /** 配偶死亡掉落残片的概率。 */
    public static final float FRAGMENT_DROP_CHANCE = 0.40F;

    private MarriageManager() {
    }

    @Nullable
    public static UUID spouseOf(net.minecraft.world.entity.player.Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KEY_SPOUSE, CompoundTag.TAG_STRING)) {
            return null;
        }
        try {
            return UUID.fromString(data.getString(KEY_SPOUSE));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** 是否已经结婚。 */
    public static boolean isMarried(net.minecraft.world.entity.player.Player player) {
        return spouseOf(player) != null;
    }

    /**
     * 结为夫妻：双方都必须是未婚，调用方需自行确认两人都手持戒指。
     */
    public static boolean marry(ServerPlayer first, ServerPlayer second) {
        if (first == second) {
            return false;
        }
        if (isMarried(first) || isMarried(second)) {
            first.displayClientMessage(Component.translatable("tenfold.marriage.already"), false);
            return false;
        }
        first.getPersistentData().putString(KEY_SPOUSE, second.getUUID().toString());
        second.getPersistentData().putString(KEY_SPOUSE, first.getUUID().toString());
        first.displayClientMessage(Component.translatable("tenfold.marriage.married",
                second.getName().getString()), false);
        second.displayClientMessage(Component.translatable("tenfold.marriage.married",
                first.getName().getString()), false);
        return true;
    }

    /** 跨重生保留夫妻关系。 */
    public static void copyAcrossRespawn(net.minecraft.world.entity.player.Player from,
                                         net.minecraft.world.entity.player.Player to) {
        CompoundTag oldData = from.getPersistentData();
        if (oldData.contains(KEY_SPOUSE, CompoundTag.TAG_STRING)) {
            to.getPersistentData().putString(KEY_SPOUSE, oldData.getString(KEY_SPOUSE));
        }
    }

    /** 残片物品（方便统一引用）。 */
    public static net.minecraft.world.item.Item fragmentItem() {
        return ModItems.FRAGMENT.get();
    }
}
