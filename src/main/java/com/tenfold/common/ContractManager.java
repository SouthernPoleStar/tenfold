package com.tenfold.common;

import com.tenfold.ModEffects;
import com.tenfold.Tenfold;
import com.tenfold.ModSounds;
import com.tenfold.item.WeaponData;
import com.tenfold.item.WeaponForm;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Contract logic: picking targets, counting kills, punishing breaches,
 * degrading the blade. All of it is server side only.
 */
public final class ContractManager {
    public static final double TARGET_RANGE = 8.0D;

    private ContractManager() {
    }

    /* ------------------------------------------------------------------ */
    /* Right click: contract, or shift+right click: cycle forward          */
    /* ------------------------------------------------------------------ */

    /**
     * Plain (non-sneaking) right click: issue or refresh a contract.
     * Sneaking is routed to {@link #cycleForm} before this is ever called.
     */
    public static void onBladeUse(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ServerPlayer server = serverPlayer;
        long now = server.level().getGameTime();

        // Contracts are throttled so a fumbled double click cannot issue and
        // then immediately breach the same contract.
        if (now - WeaponData.lastUse(stack) < 4) {
            return;
        }
        WeaponData.setLastUse(stack, now);

        if (WeaponData.isLocked(stack, now)) {
            // While degraded the weapon cannot issue contracts; it is just a shovel.
            server.displayClientMessage(Component.translatable("tenfold.locked.use",
                    WeaponData.lockSecondsLeft(stack, now)).withStyle(ChatFormatting.DARK_RED), true);
            return;
        }

        LivingEntity found = nearestTarget(server);
        if (found == null) {
            // Nothing to name. Dropping the current contract is a cancellation,
            // not a breach: a fumbled right click must not cost a third of the blade.
            UUID current = WeaponData.target(stack);
            if (current != null) {
                cancel(server, stack, "tenfold.contract.cancel");
            } else {
                server.displayClientMessage(Component.translatable("tenfold.contract.none")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }

        UUID current = WeaponData.target(stack);
        if (current != null && current.equals(found.getUUID())) {
            // Re-confirm same target: refresh the deadline, keep the streak.
            WeaponData.setTarget(stack, found.getUUID(), now + WeaponData.CONTRACT_TIMEOUT_TICKS);
            server.displayClientMessage(Component.translatable("tenfold.contract.refresh",
                    found.getDisplayName()).withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        if (current != null) {
            // Re-targeting is also a cancellation. Only a wrong kill or a
            // timeout counts as a breach, so the blade cannot degrade on
            // accidental right clicks while walking around.
            cancel(server, stack, "tenfold.contract.switch");
        }
        issue(server, stack, found, now);
    }

    /** 特殊指令的出现概率。 */
    public static final float SPECIAL_CHANCE = 0.35F;
    /** 可以作为特殊指令要求的九种形态（铲子不算）。 */
    private static final WeaponForm[] SPECIAL_FORMS = {
            WeaponForm.AXE, WeaponForm.CONE, WeaponForm.HALFSWORD, WeaponForm.RAPIER,
            WeaponForm.HAMMER, WeaponForm.GREATSWORD, WeaponForm.SPEAR, WeaponForm.WHIP,
            WeaponForm.SCYTHE
    };

    private static void issue(ServerPlayer player, ItemStack stack, LivingEntity target, long now) {
        WeaponData.setTarget(stack, target.getUUID(), now + WeaponData.CONTRACT_TIMEOUT_TICKS);
        // 特殊指令：有概率要求用某一种形态完成击杀
        WeaponForm special = player.getRandom().nextFloat() < SPECIAL_CHANCE
                ? SPECIAL_FORMS[player.getRandom().nextInt(SPECIAL_FORMS.length)]
                : null;
        WeaponData.setContractWeapon(stack, special);
        // The contract-target buff: marked + Slowness IV (-50% speed) for the whole window.
        target.addEffect(new MobEffectInstance(ModEffects.MARKED.get(), WeaponData.CONTRACT_TIMEOUT_TICKS,
                0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WeaponData.CONTRACT_TIMEOUT_TICKS,
                3, false, true, true));
        target.setGlowingTag(true);
        int issued = WeaponData.bumpIssued(player);
        ContractTriggers.fire(ContractTriggers.CONTRACT_ISSUED, player, issued);

        player.level().playSound(null, player.blockPosition(), ModSounds.CONTRACT_MADE.get(),
                SoundSource.PLAYERS, 0.8F, 1.0F);
        if (special != null) {
            player.displayClientMessage(Component.translatable("tenfold.contract.special",
                            Component.translatable("tenfold.lien.swap." + special.key),
                            special.display(), target.getDisplayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        player.displayClientMessage(Component.translatable("tenfold.contract.issued",
                target.getDisplayName(), WeaponData.streak(stack), WeaponData.STREAK_GOAL)
                .withStyle(ChatFormatting.AQUA), true);
    }

    @Nullable
    public static LivingEntity nearestTarget(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(TARGET_RANGE);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> isValidTarget(player, e));
        if (candidates.isEmpty()) {
            return null;
        }
        // 优先选"正看着、并且看得见"的那一只：混战里只按距离选，会经常选到
        // 贴身的小怪，导致续不上 Boss 的标记。正前方 60° 内没有目标时才退回最近的一只。
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        return candidates.stream()
                .filter(e -> {
                    Vec3 dir = e.getEyePosition().subtract(eye);
                    if (dir.lengthSqr() < 1.0E-4D) {
                        return true;
                    }
                    return dir.normalize().dot(look) >= 0.5D && player.hasLineOfSight(e);
                })
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                        .orElse(null));
    }

    private static boolean isValidTarget(ServerPlayer player, Entity e) {
        if (!(e instanceof LivingEntity living) || !living.isAlive() || living == player) {
            return false;
        }
        if (living.isSpectator() || living.isInvisible()) {
            return false;
        }
        // Other players count in multiplayer, unless they are creative/spectator
        // or cannot be harmed by this player.
        if (living instanceof Player other) {
            if (other.isCreative() || other.isSpectator()) {
                return false;
            }
            if (!player.canHarmPlayer(other)) {
                return false;
            }
        }
        if (living.isAlliedTo(player)) {
            return false;
        }
        // Contract creatures: players, monsters, and neutrals. Passive animals
        // are skipped so accidentally hitting a cow does not count as a breach.
        return living instanceof Player || living instanceof Monster || living instanceof NeutralMob;
    }

    /* ------------------------------------------------------------------ */
    /* Kill bookkeeping                                                    */
    /* ------------------------------------------------------------------ */

    public static void onTargetKilled(Player player, ItemStack stack, LivingEntity victim) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ServerPlayer server = serverPlayer;
        UUID current = WeaponData.target(stack);
        long now = server.level().getGameTime();

        if (current != null && current.equals(victim.getUUID())) {
            // 特殊指令：必须用指定的形态补最后一刀
            WeaponForm required = WeaponData.contractWeapon(stack);
            if (required != null && WeaponData.form(stack) != required) {
                fail(server, stack, "tenfold.contract.special_wrong");
                return;
            }
            int streak = WeaponData.streak(stack) + 1;
            server.level().playSound(null, player.blockPosition(), ModSounds.CONTRACT_DONE.get(),
                    SoundSource.PLAYERS, 0.9F, 1.1F);

            if (streak >= WeaponData.STREAK_GOAL) {
                WeaponData.setStreak(stack, 0);
                WeaponData.setBreach(stack, 0);
                // Clearing the per-player run as well, so "three in a row" really
                // means three consecutive refusals without a completed contract.
                WeaponData.setBreachRun(server, 0);
                grantStrength(server);

                int chained = WeaponData.chain(server) + 1;
                WeaponData.setChain(server, chained);
                onContractCompleted(server, chained);

                server.displayClientMessage(Component.translatable("tenfold.contract.complete")
                        .withStyle(ChatFormatting.GOLD), false);
                if (!WeaponData.isLocked(stack, now)) {
                    advance(server, stack, true, false);
                }
            } else {
                WeaponData.setStreak(stack, streak);
                server.displayClientMessage(Component.translatable("tenfold.contract.progress",
                        streak, WeaponData.STREAK_GOAL).withStyle(ChatFormatting.AQUA), true);
                // Immediately offer the next nearest creature, so the rhythm continues.
                LivingEntity next = nearestTarget(server);
                if (next != null) {
                    issue(server, stack, next, now);
                } else {
                    WeaponData.setTarget(stack, null, 0);
                }
            }
        } else if (victim instanceof Mob || victim instanceof Player) {
            // Killing something that is not the contract target is still a breach
            // if a contract was active. Wrong-player kills count too now that
            // players can be targets. (This branch never sees the target itself.)
            if (current != null) {
                fail(server, stack, "tenfold.contract.other");
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Punishment                                                          */
    /* ------------------------------------------------------------------ */

    public static void fail(ServerPlayer player, ItemStack stack, String reasonKey) {
        // The counter lives on the blade itself and nowhere else. It used to be
        // mirrored into the player's persistent data as well, and that copy was
        // never reset, so a single public breach could land on a stack that
        // already carried two stale ones and collapse the weapon instantly.
        int breach = WeaponData.breach(stack) + 1;
        WeaponData.setBreach(stack, breach);
        WeaponData.setBreachRun(player, breach);
        WeaponData.setStreak(stack, 0);
        WeaponData.setTarget(stack, null, 0);
        WeaponData.setContractWeapon(stack, null);

        int currentAmp = player.hasEffect(MobEffects.WEAKNESS)
                ? player.getEffect(MobEffects.WEAKNESS).getAmplifier() : -1;
        int amp = Math.min(2, currentAmp + 1);
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60 * 20, Math.max(0, amp), false, true));

        // Any breach resets the chain of completed contracts.
        WeaponData.setChain(player, 0);

        player.level().playSound(null, player.blockPosition(), ModSounds.CONTRACT_FAILED.get(),
                SoundSource.PLAYERS, 0.9F, 0.7F);
        player.displayClientMessage(Component.translatable(reasonKey).withStyle(ChatFormatting.RED), true);

        ContractTriggers.fire(ContractTriggers.CONTRACT_BROKEN, player, breach);

        if (breach >= WeaponData.BREACH_LIMIT) {
            degrade(player, stack);
        } else {
            player.displayClientMessage(Component.translatable("tenfold.contract.breach_count",
                    breach, WeaponData.BREACH_LIMIT, WeaponData.BREACH_LIMIT - breach)
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    /**
     * Drops the current contract without punishing the player. Used for
     * swinging at air and for pointing the blade somewhere else.
     */
    private static void cancel(ServerPlayer player, ItemStack stack, String reasonKey) {
        WeaponData.setTarget(stack, null, 0);
        WeaponData.setContractWeapon(stack, null);
        WeaponData.setStreak(stack, 0);
        player.displayClientMessage(Component.translatable(reasonKey).withStyle(ChatFormatting.GRAY), true);
    }

    private static void degrade(ServerPlayer player, ItemStack stack) {
        long now = player.level().getGameTime();
        WeaponData.setForm(stack, WeaponForm.SHOVEL);
        WeaponData.setBreach(stack, 0);
        WeaponData.setBreachRun(player, 0);
        WeaponData.setStreak(stack, 0);
        WeaponData.setTarget(stack, null, 0);
        WeaponData.setContractWeapon(stack, null);
        WeaponData.setDegradeUntil(stack, now + WeaponData.DEGRADE_TICKS);

        player.level().playSound(null, player.blockPosition(), ModSounds.DEGRADE.get(),
                SoundSource.PLAYERS, 1.0F, 0.5F);
        player.displayClientMessage(Component.translatable("tenfold.contract.degrade",
                WeaponData.DEGRADE_TICKS / 20 / 60).withStyle(ChatFormatting.DARK_RED), false);
    }

    /**
     * Vanilla Strength (DAMAGE_BOOST), not a custom effect.
     * Every three fulfilled contracts adds one level, capped at Strength V.
     */
    /**
     * Called every time a full contract (three kills) is fulfilled.
     * Four in a row grant Contract Addiction: +10 attack damage for 5 minutes.
     */
    private static void onContractCompleted(ServerPlayer player, int chained) {
        ContractTriggers.fire(ContractTriggers.CONTRACT_DONE, player, chained);

        if (chained >= WeaponData.ADDICTION_CHAIN) {
            player.addEffect(new MobEffectInstance(ModEffects.ADDICTION.get(), 300 * 20, 0, false, true, true));
            player.displayClientMessage(Component.translatable("tenfold.addiction.gained")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            WeaponData.setChain(player, 0);
        }
    }

    private static void grantStrength(ServerPlayer player) {
        MobEffectInstance existing = player.getEffect(MobEffects.DAMAGE_BOOST);
        int amp = existing == null ? 0 : Math.min(4, existing.getAmplifier() + 1);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120 * 20, amp, false, true, true));
    }

    /* ------------------------------------------------------------------ */
    /* Form cycling                                                        */
    /* ------------------------------------------------------------------ */

    public static boolean cycleForm(Player player, ItemStack stack, boolean forward) {
        if (!(player instanceof ServerPlayer server)) {
            return false;
        }
        long now = player.level().getGameTime();
        Tenfold.LOGGER.debug("[tenfold] cycle form {} -> {}", WeaponData.form(stack),
                forward ? WeaponData.form(stack).next() : WeaponData.form(stack).previous());
        if (WeaponData.isLocked(stack, now)) {
            server.displayClientMessage(Component.translatable("tenfold.locked.use",
                    WeaponData.lockSecondsLeft(stack, now)).withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        advance(server, stack, forward, true);
        return true;
    }

    private static void advance(ServerPlayer player, ItemStack stack, boolean forward, boolean announce) {
        WeaponForm form = WeaponData.form(stack);
        WeaponForm next = forward ? form.next() : form.previous();
        WeaponData.setForm(stack, next);
        player.level().playSound(null, player.blockPosition(), ModSounds.FORM_SHIFT.get(),
                SoundSource.PLAYERS, 0.7F, 1.2F);
        if (announce) {
            player.displayClientMessage(Component.literal("\u25C6 ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(next.display()).append(" ").append(next.describe()), true);
        }
    }

    /** Called once per second from the server tick to police deadlines and lock expiry. */
    public static void tick(ServerPlayer player, ItemStack stack) {
        long now = player.level().getGameTime();

        long until = WeaponData.degradeUntil(stack);
        if (until > 0 && now >= until) {
            WeaponData.setDegradeUntil(stack, 0);
            WeaponData.setBreach(stack, 0);
            WeaponData.setBreachRun(player, 0);
            WeaponData.setForm(stack, WeaponForm.GREATSWORD);
            player.displayClientMessage(Component.translatable("tenfold.locked.expired")
                    .withStyle(ChatFormatting.GREEN), false);
        }

        UUID target = WeaponData.target(stack);
        if (target == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Entity e = level.getEntity(target);
        if (!(e instanceof LivingEntity living) || !living.isAlive() || living.level() != level) {
            WeaponData.setTarget(stack, null, 0);
            return;
        }
        living.setGlowingTag(true);
        if (now > WeaponData.targetDeadline(stack)) {
            living.setGlowingTag(false);
            fail(player, stack, "tenfold.contract.timeout");
        }
    }
}
