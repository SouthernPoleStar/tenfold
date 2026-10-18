package com.tenfold.common;

import com.tenfold.ModEffects;
import com.tenfold.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * 血魔体系的核心数据与规则。
 * <p>
 * 代数存在玩家持久数据里（死亡、下线都不丢）：
 * <ul>
 *   <li>{@code tenfoldBloodGen}：0 = 普通玩家；玩家血魔从 2 起跳（二代），上限 10。</li>
 *   <li>{@code tenfoldBloodConverts}：还能向下传染几名玩家（初始 2）。</li>
 * </ul>
 * 血魔老祖的代数恒为 1。
 */
public final class BloodDemonManager {

    public static final int MAX_GENERATION = 10;
    public static final int CONVERSIONS_PER_DEMON = 2;

    public static final String KEY_GEN = "tenfoldBloodGen";
    public static final String KEY_CONVERTS = "tenfoldBloodConverts";

    /** 低阶打高阶：基础 -90%，每再差一代再减半（×0.5）。 */
    public static final float LOW_VS_HIGH_BASE = 0.1F;
    /** 高阶打低阶：基础 +90%，每再差一代再乘 1.5。 */
    public static final float HIGH_VS_LOW_BASE = 1.9F;
    public static final float PER_GAP_STEP = 0.5F;

    private BloodDemonManager() {
    }

    /* ------------------------------------------------------------------ */
    /* 代数                                                                */
    /* ------------------------------------------------------------------ */

    /** 玩家代数；普通玩家返回 0。 */
    public static int generation(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KEY_GEN, CompoundTag.TAG_INT)) {
            return 0;
        }
        int gen = data.getInt(KEY_GEN);
        return gen < 2 ? 0 : gen;
    }

    /**
     * 任意生物的代数：玩家看持久数据，血魔老祖恒为 1，其余生物靠「x代血魔」效果兜底。
     */
    public static int generation(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            return generation(player);
        }
        if (entity.getType() == ModEntities.BLOOD_ANCESTOR.get()) {
            return 1;
        }
        // 兜底：任何挂着「x代血魔」效果的生物按效果推算代数。
        for (int gen = 1; gen <= MAX_GENERATION; gen++) {
            if (entity.hasEffect(ModEffects.bloodGenEffect(gen))) {
                return gen;
            }
        }
        return 0;
    }

    public static boolean isBloodDemon(LivingEntity entity) {
        return generation(entity) > 0;
    }

    /** 还剩多少次向下传染的额度。 */
    public static int conversionsLeft(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KEY_CONVERTS, CompoundTag.TAG_INT)) {
            return 0;
        }
        return data.getInt(KEY_CONVERTS);
    }

    /* ------------------------------------------------------------------ */
    /* 转化                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 把玩家变成指定代数的血魔（重复转化取更高的代数，不降级），
     * 并立刻刷上永久 Buff。
     */
    public static void convert(ServerPlayer player, int generation) {
        int clamped = Math.max(2, Math.min(MAX_GENERATION, generation));
        int current = generation(player);
        if (current >= clamped) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.putInt(KEY_GEN, clamped);
        data.putInt(KEY_CONVERTS, CONVERSIONS_PER_DEMON);
        applyPermanentEffects(player);
        player.displayClientMessage(Component.translatable("tenfold.blood.converted", clamped), false);
    }

    /**
     * 血魔玩家把另一名普通玩家转化为「自己的代数 + 1」。
     * 每个血魔只能转化出两个后裔；目标已经是血魔时不生效。
     */
    public static boolean tryInfect(ServerPlayer source, ServerPlayer target) {
        int sourceGen = generation(source);
        if (sourceGen <= 0 || sourceGen >= MAX_GENERATION) {
            return false;
        }
        if (isBloodDemon(target)) {
            return false;
        }
        CompoundTag data = source.getPersistentData();
        int left = data.contains(KEY_CONVERTS, CompoundTag.TAG_INT)
                ? data.getInt(KEY_CONVERTS) : 0;
        if (left <= 0) {
            return false;
        }
        data.putInt(KEY_CONVERTS, left - 1);
        convert(target, sourceGen + 1);
        source.displayClientMessage(Component.translatable("tenfold.blood.infect", target.getName().getString()), false);
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* 转化请求（Shift+右键 发起，对方点击聊天栏按钮同意）                  */
    /* ------------------------------------------------------------------ */

    /** 请求有效期：60 秒。 */
    public static final long REQUEST_TIMEOUT_TICKS = 60L * 20L;

    private static final java.util.Map<java.util.UUID, PendingRequest> PENDING = new java.util.HashMap<>();

    private record PendingRequest(java.util.UUID source, long expireAt) {
    }

    /**
     * 血魔对另一名玩家发起转化请求：对方会在聊天栏收到「同意 / 拒绝」按钮。
     * 只有血魔、还有转化名额、目标还不是血魔时才发得出去。
     */
    public static boolean requestConversion(ServerPlayer source, ServerPlayer target) {
        int sourceGen = generation(source);
        if (sourceGen <= 0) {
            source.displayClientMessage(Component.translatable("tenfold.blood.need_demon"), true);
            return false;
        }
        if (sourceGen >= MAX_GENERATION) {
            source.displayClientMessage(Component.translatable("tenfold.blood.too_deep"), true);
            return false;
        }
        if (isBloodDemon(target)) {
            source.displayClientMessage(Component.translatable("tenfold.blood.target_already"), true);
            return false;
        }
        if (conversionsLeft(source) <= 0) {
            source.displayClientMessage(Component.translatable("tenfold.blood.no_quota"), true);
            return false;
        }

        long now = source.level().getGameTime();
        PENDING.put(target.getUUID(), new PendingRequest(source.getUUID(), now + REQUEST_TIMEOUT_TICKS));

        source.displayClientMessage(Component.translatable("tenfold.blood.request_sent",
                target.getName().getString()), false);

        target.displayClientMessage(Component.translatable("tenfold.blood.request_received",
                source.getName().getString(), String.valueOf(sourceGen + 1)), false);
        Component accept = Component.translatable("tenfold.blood.accept_button")
                .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/tenfold blood accept")));
        Component deny = Component.translatable("tenfold.blood.deny_button")
                .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.RED)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/tenfold blood deny")));
        target.displayClientMessage(accept.copy().append("   ").append(deny.copy()), false);
        return true;
    }

    /** 取出并校验目标的待处理请求（过期或不存在返回 null）。 */
    private static PendingRequest takeValid(ServerPlayer target) {
        PendingRequest pending = PENDING.remove(target.getUUID());
        if (pending == null) {
            return null;
        }
        long now = target.level().getGameTime();
        if (now > pending.expireAt()) {
            return null;
        }
        return pending;
    }

    /** 目标同意：扣掉发起者一个名额并完成转化。 */
    public static boolean acceptRequest(ServerPlayer target) {
        PendingRequest pending = takeValid(target);
        if (pending == null) {
            target.displayClientMessage(Component.translatable("tenfold.blood.no_request"), false);
            return false;
        }
        var server = target.getServer();
        ServerPlayer source = server == null ? null
                : server.getPlayerList().getPlayer(pending.source());
        if (source == null) {
            target.displayClientMessage(Component.translatable("tenfold.blood.source_gone"), false);
            return false;
        }
        if (!tryInfect(source, target)) {
            target.displayClientMessage(Component.translatable("tenfold.blood.request_failed"), false);
            return false;
        }
        target.displayClientMessage(Component.translatable("tenfold.blood.accepted",
                String.valueOf(generation(target))), false);
        return true;
    }

    /** 目标拒绝：只清掉请求并通知双方。 */
    public static boolean denyRequest(ServerPlayer target) {
        PendingRequest pending = takeValid(target);
        if (pending == null) {
            target.displayClientMessage(Component.translatable("tenfold.blood.no_request"), false);
            return false;
        }
        var server = target.getServer();
        ServerPlayer source = server == null ? null
                : server.getPlayerList().getPlayer(pending.source());
        String sourceName = source != null ? source.getName().getString() : "未知血魔";
        target.displayClientMessage(Component.translatable("tenfold.blood.denied_target",
                sourceName), false);
        if (source != null) {
            source.displayClientMessage(Component.translatable("tenfold.blood.denied_source",
                    target.getName().getString()), false);
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* 永久 Buff（死亡也不清除）                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 刷上四件套：x代血魔、力量 II、抗性提升 I、恐水症。全部无限时长。
     * 死亡会清效果，所以登录与重生时都要重刷。
     */
    public static void applyPermanentEffects(ServerPlayer player) {
        int gen = generation(player);
        if (gen <= 0) {
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.bloodGenEffect(gen),
                -1, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                -1, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                -1, 0, false, false, true));
        player.addEffect(new MobEffectInstance(ModEffects.HYDROPHOBIA.get(),
                -1, 0, false, false, true));
    }

    /* ------------------------------------------------------------------ */
    /* 代际伤害                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * 血魔之间的伤害倍率。**代数越小阶级越高**（一代=老祖最高，二代次之，三代再次之…），
     * 因为二代能转化出三代、三代转化出四代，辈分越往下代数越大。
     * <ul>
     *   <li>同代 / 非血魔之间：×1。</li>
     *   <li>高阶（代数小）打低阶（代数大）：×1.9 × 1.5^(差-1)。</li>
     *   <li>低阶（代数大）打高阶（代数小）：×0.1 × 0.5^(差-1)。</li>
     * </ul>
     * 例：二代打老祖（差 1）＝ ×0.1；老祖打二代 ＝ ×1.9。
     */
    public static float interGenerationMultiplier(LivingEntity attacker, LivingEntity victim) {
        int a = generation(attacker);
        int v = generation(victim);
        if (a <= 0 || v <= 0 || a == v) {
            return 1.0F;
        }
        int gap = Math.abs(a - v);
        if (a < v) {
            // 攻击者代数更小 = 阶级更高
            return HIGH_VS_LOW_BASE * (float) Math.pow(1.0D + PER_GAP_STEP, gap - 1);
        }
        // 攻击者代数更大 = 阶级更低
        return LOW_VS_HIGH_BASE * (float) Math.pow(PER_GAP_STEP, gap - 1);
    }
}
