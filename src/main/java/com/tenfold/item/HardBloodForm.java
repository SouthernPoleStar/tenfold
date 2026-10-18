package com.tenfold.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 硬血武器的三种形态。没有指令系统，纯粹靠潜行+右键循环切换。
 * 伤害是绝对终值（半颗心），与双蛇杖同一套"压制玩家自身攻击力"的处理。
 */
public enum HardBloodForm {

    /** 长枪：超长触及，直线穿刺。 */
    SPEAR(0, "spear", ChatFormatting.RED, 14.0F, -2.6F, 4.5F),
    /** 长刀：重击型，最高单发伤害。 */
    LONG_BLADE(1, "long_blade", ChatFormatting.DARK_RED, 20.0F, -3.0F, 3.2F),
    /** 匕首：轻快型，攻速极快但伤害低。 */
    DAGGER(2, "dagger", ChatFormatting.DARK_GRAY, 9.0F, -1.0F, 2.6F);

    public final int index;
    public final String key;
    public final ChatFormatting color;
    /** 最终伤害（半颗心）。 */
    public final float damage;
    /** 攻速修正，叠加在原版基础 4.0 上。 */
    public final float attackSpeed;
    /** 触及距离（格）。 */
    public final float reach;

    HardBloodForm(int index, String key, ChatFormatting color,
                  float damage, float attackSpeed, float reach) {
        this.index = index;
        this.key = key;
        this.color = color;
        this.damage = damage;
        this.attackSpeed = attackSpeed;
        this.reach = reach;
    }

    public String displayKey() {
        return "hard_blood.form." + key;
    }

    public Component display() {
        return Component.translatable(displayKey()).withStyle(color);
    }

    public HardBloodForm next() {
        return VALUES[(index + 1) % VALUES.length];
    }

    public static final HardBloodForm[] VALUES = values();

    public static HardBloodForm of(int index) {
        if (index < 0 || index >= VALUES.length) {
            return SPEAR;
        }
        return VALUES[index];
    }
}
