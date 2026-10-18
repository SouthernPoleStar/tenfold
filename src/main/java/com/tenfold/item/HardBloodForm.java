package com.tenfold.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 硬血武器的四种形态。没有指令系统，潜行+右键循环切换。
 * <p>
 * 伤害是绝对终值（半颗心），与双蛇杖同一套「压制玩家自身攻击力」的处理。
 * 长刀单持 13、双持 17（双持时右键格挡）；血弓的近战伤害无意义（1），
 * 实际输出是箭矢伤害（见 HardBloodWeaponItem#BLOOD_BOW_ARROW_DAMAGE）。
 */
public enum HardBloodForm {

    /** 长枪：突刺，命中直线上的所有敌人。 */
    SPEAR(0, "spear", ChatFormatting.RED, 10.0F, -2.6F, 4.5F),
    /** 长刀：单持 13；双持 17 且右键格挡。 */
    LONG_BLADE(1, "long_blade", ChatFormatting.DARK_RED, 13.0F, -3.0F, 3.2F),
    /** 匕首：轻快型，攻速极快。 */
    DAGGER(2, "dagger", ChatFormatting.DARK_GRAY, 9.0F, -1.0F, 2.6F),
    /** 血弓：拉弓射箭（消耗箭矢），近战无意义。 */
    BLOOD_BOW(3, "blood_bow", ChatFormatting.DARK_PURPLE, 1.0F, -2.4F, 3.0F);

    public final int index;
    public final String key;
    public final ChatFormatting color;
    /** 最终近战伤害（半颗心）；血弓此项无效。 */
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
