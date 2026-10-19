package com.tenfold.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * All mutable state of the blade lives on the stack, so a player can own
 * several blades with independent contracts.
 * <p>
 * 1.20.1 still uses NBT on the stack (no DataComponents), and
 * {@link CompoundTag#getCompound} returns the live child, so read/write are cheap.
 */
public final class WeaponData {
    private static final String ROOT = "tenfold";

    public static final int STREAK_GOAL = 3;
    public static final int BREACH_LIMIT = 3;
    public static final int CONTRACT_TIMEOUT_TICKS = 30 * 20;
    public static final int DEGRADE_TICKS = 180 * 20;
    /** Contracts that must be completed back to back to gain Contract Addiction. */
    public static final int ADDICTION_CHAIN = 4;

    private WeaponData() {
    }

    private static CompoundTag root(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(ROOT, CompoundTag.TAG_COMPOUND)) {
            tag.put(ROOT, new CompoundTag());
        }
        return tag.getCompound(ROOT);
    }

    public static WeaponForm form(ItemStack stack) {
        return WeaponForm.of(root(stack).getInt("form"));
    }

    public static void setForm(ItemStack stack, WeaponForm form) {
        root(stack).putInt("form", form.index);
        // Keep the model in sync immediately instead of waiting for inventoryTick,
        // so the texture changes the moment the form does.
        stack.getOrCreateTag().putInt("CustomModelData", form.index);
    }

    public static int streak(ItemStack stack) {
        return root(stack).getInt("streak");
    }

    public static void setStreak(ItemStack stack, int value) {
        root(stack).putInt("streak", Math.max(0, value));
    }

    public static int breach(ItemStack stack) {
        return root(stack).getInt("breach");
    }

    public static void setBreach(ItemStack stack, int value) {
        root(stack).putInt("breach", Math.max(0, value));
    }

    public static long degradeUntil(ItemStack stack) {
        return root(stack).getLong("degradeUntil");
    }

    public static void setDegradeUntil(ItemStack stack, long time) {
        root(stack).putLong("degradeUntil", time);
    }

    public static boolean isLocked(ItemStack stack, long gameTime) {
        long until = degradeUntil(stack);
        return until > 0 && gameTime < until;
    }

    public static int lockSecondsLeft(ItemStack stack, long gameTime) {
        long until = degradeUntil(stack);
        if (until <= 0) {
            return 0;
        }
        long left = until - gameTime;
        return left <= 0 ? 0 : (int) ((left + 19) / 20);
    }

    @Nullable
    public static UUID target(ItemStack stack) {
        CompoundTag tag = root(stack);
        if (!tag.contains("targetUUID", CompoundTag.TAG_STRING)) {
            return null;
        }
        try {
            return UUID.fromString(tag.getString("targetUUID"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void setTarget(ItemStack stack, @Nullable UUID uuid, long deadline) {
        CompoundTag tag = root(stack);
        if (uuid == null) {
            tag.remove("targetUUID");
            tag.remove("targetDeadline");
        } else {
            tag.putString("targetUUID", uuid.toString());
            tag.putLong("targetDeadline", deadline);
        }
    }

    public static long targetDeadline(ItemStack stack) {
        return root(stack).getLong("targetDeadline");
    }

    /* ------------------------------------------------------------------ */
    /* Special contract requirement                                        */
    /* ------------------------------------------------------------------ */

    /**
     * 特殊指令要求使用的形态；{@code null} 表示普通指令（任何武器都行）。
     */
    @Nullable
    public static WeaponForm contractWeapon(ItemStack stack) {
        CompoundTag tag = root(stack);
        if (!tag.contains("contractWeapon", CompoundTag.TAG_STRING)) {
            return null;
        }
        String key = tag.getString("contractWeapon");
        for (WeaponForm form : WeaponForm.VALUES) {
            if (form.key.equals(key)) {
                return form;
            }
        }
        return null;
    }

    public static void setContractWeapon(ItemStack stack, @Nullable WeaponForm form) {
        CompoundTag tag = root(stack);
        if (form == null) {
            tag.remove("contractWeapon");
        } else {
            tag.putString("contractWeapon", form.key);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Cross-blade, per-player counters (stored on the player, not the blade) */
    /* ------------------------------------------------------------------ */

    /** Completed contracts in a row without a breach. */
    public static int chain(Player player) {
        return player.getPersistentData().getInt("tenfoldChain");
    }

    public static void setChain(Player player, int value) {
        player.getPersistentData().putInt("tenfoldChain", Math.max(0, value));
    }

    /** Lifetime counters, used as advancement criteria. */
    public static int issuedTotal(Player player) {
        return player.getPersistentData().getInt("tenfoldIssued");
    }

    public static int bumpIssued(Player player) {
        int value = issuedTotal(player) + 1;
        player.getPersistentData().putInt("tenfoldIssued", value);
        return value;
    }

    /** Breaches since the last fulfilled contract: mirror of the blade's own counter. */
    public static int breachRun(Player player) {
        return player.getPersistentData().getInt("tenfoldBreachRun");
    }

    public static void setBreachRun(Player player, int value) {
        player.getPersistentData().putInt("tenfoldBreachRun", Math.max(0, value));
    }

    public static long lastUse(ItemStack stack) {
        return root(stack).getLong("lastUse");
    }

    public static void setLastUse(ItemStack stack, long time) {
        root(stack).putLong("lastUse", time);
    }
}
