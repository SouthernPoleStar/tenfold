package com.tenfold.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The ten forms. Order matters: the blade advances along this list.
 * <p>
 * Damage is an absolute value in half-hearts, not a bonus. The item suppresses
 * the player's own attack-damage attribute while held, so the number here is
 * exactly what lands. Scythe tops the table at 15, shovel bottoms it at 6.
 */
public enum WeaponForm {
    //                 伤害值  攻速   触及  挖掘
    GREATSWORD(0, "greatsword", ChatFormatting.DARK_RED, 13.0F, -3.2F, 3.2F, 0.0F),
    SCYTHE(1, "scythe", ChatFormatting.DARK_PURPLE, 15.0F, -3.0F, 3.6F, 0.0F),
    RAPIER(2, "rapier", ChatFormatting.AQUA, 9.0F, -1.2F, 2.8F, 0.0F),
    AXE(3, "axe", ChatFormatting.GOLD, 12.0F, -2.9F, 3.0F, 0.0F),
    HAMMER(4, "hammer", ChatFormatting.RED, 14.0F, -3.5F, 3.0F, 0.0F),
    WHIP(5, "whip", ChatFormatting.YELLOW, 8.0F, -1.0F, 5.5F, 0.0F),
    HALFSWORD(6, "halfsword", ChatFormatting.GRAY, 10.0F, -2.0F, 3.0F, 0.0F),
    SPEAR(7, "spear", ChatFormatting.BLUE, 12.0F, -2.7F, 4.5F, 0.0F),
    CONE(8, "cone", ChatFormatting.LIGHT_PURPLE, 11.0F, -2.2F, 2.4F, 0.0F),
    SHOVEL(9, "shovel", ChatFormatting.DARK_GRAY, 6.0F, -2.0F, 3.0F, 6.0F);

    public final int index;
    public final String key;
    public final ChatFormatting color;
    /** Final damage in half-hearts, applied directly on hit. */
    public final float damage;
    /** Attack speed modifier, shown in the tooltip. */
    public final float attackSpeed;
    public final float reach;
    /** Dig speed, only meaningful for the shovel. */
    public final float digSpeed;

    WeaponForm(int index, String key, ChatFormatting color,
               float damage, float attackSpeed, float reach, float digSpeed) {
        this.index = index;
        this.key = key;
        this.color = color;
        this.damage = damage;
        this.attackSpeed = attackSpeed;
        this.reach = reach;
        this.digSpeed = digSpeed;
    }

    public String displayKey() {
        return "form.tenfold." + key;
    }

    public String descKey() {
        return "form.tenfold." + key + ".desc";
    }

    public Component display() {
        return Component.translatable(displayKey()).withStyle(color);
    }

    public Component describe() {
        return Component.translatable(descKey()).withStyle(ChatFormatting.DARK_GRAY);
    }

    public WeaponForm next() {
        return VALUES[(index + 1) % VALUES.length];
    }

    public WeaponForm previous() {
        return VALUES[(index + VALUES.length - 1) % VALUES.length];
    }

    public static final WeaponForm[] VALUES = values();

    public static WeaponForm of(int index) {
        if (index < 0 || index >= VALUES.length) {
            return GREATSWORD;
        }
        return VALUES[index];
    }
}
