package com.tenfold.common;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 进度发放的小工具：这几个进度的触发条件没法用原版触发器表达，
 * JSON 里用 {@code minecraft:impossible} 占位，再由代码显式 award。
 */
public final class ModAdvancements {

    /** 心意相悖：伴侣死亡并掉出残片。 */
    public static final String MARRIAGE_FRAGMENT = "marriage_fragment";
    /** 我们的心意终与不再相悖：穿狂猎棺材 + 手持狂猎大剑。 */
    public static final String SOUL_UNITED = "soul_united";
    /** 你都做了什么？！：击杀 Poee。 */
    public static final String POEE_KILLED = "poee_killed";

    private ModAdvancements() {
    }

    public static void award(ServerPlayer player, String path) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Advancement advancement = server.getAdvancements()
                .getAdvancement(new ResourceLocation("tenfold", path));
        if (advancement == null) {
            return;
        }
        player.getAdvancements().award(advancement, "unlocked");
    }
}
