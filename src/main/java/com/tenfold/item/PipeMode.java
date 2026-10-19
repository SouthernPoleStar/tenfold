package com.tenfold.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 烟斗的四种模式，Shift+右键循环切换。
 */
public enum PipeMode {

    /** 红色：变成长剑，攻击力 20，命中附加 10 秒凋零。 */
    RED(0, "red", ChatFormatting.DARK_RED),
    /** 白色：蓄力 3 秒，给周围友方 10 秒生命恢复 IV + 30 秒抗性 III。 */
    WHITE(1, "white", ChatFormatting.WHITE),
    /** 紫色：蓄力 5 秒，给半径 7 格内所有敌人 30 秒虚弱 X。 */
    PURPLE(2, "purple", ChatFormatting.DARK_PURPLE),
    /** 红点：蓄力 20 秒，扣掉自身一半血量，发射穿透 30 格的红点。 */
    RED_DOT(3, "red_dot", ChatFormatting.RED);

    public final int index;
    public final String key;
    public final ChatFormatting color;

    PipeMode(int index, String key, ChatFormatting color) {
        this.index = index;
        this.key = key;
        this.color = color;
    }

    public String displayKey() {
        return "pipe.tenfold.mode." + key;
    }

    public Component display() {
        return Component.translatable(displayKey()).withStyle(color);
    }

    /** 需要按住右键蓄力的时长（tick）；0 表示不蓄力。 */
    public int chargeTicks() {
        return switch (this) {
            case WHITE -> 60;
            case PURPLE -> 100;
            case RED_DOT -> 400;
            case RED -> 0;
        };
    }

    public PipeMode next() {
        return VALUES[(index + 1) % VALUES.length];
    }

    public static final PipeMode[] VALUES = values();

    public static PipeMode of(int index) {
        if (index < 0 || index >= VALUES.length) {
            return RED;
        }
        return VALUES[index];
    }
}
