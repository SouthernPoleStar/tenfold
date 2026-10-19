package com.tenfold.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 烟斗：Shift+右键循环切换四种模式。
 * <ul>
 *   <li>**红色**：化成长剑，攻击力 20，命中附加 10 秒凋零。</li>
 *   <li>**白色**：按住右键 3 秒，周围友方获得 10 秒生命恢复 IV + 30 秒抗性 III。</li>
 *   <li>**紫色**：按住右键 5 秒，半径 7 格内敌人获得 30 秒虚弱 X。</li>
 *   <li>**红点**：按住右键 20 秒，扣掉自身一半血量，发射穿透 30 格的红点，
 *       每命中一个生物造成「扣掉的血量 × 30」伤害，之后自身获得 30 秒虚弱 II。</li>
 * </ul>
 */
public class PipeItem extends SwordItem {

    public static final String MODE_KEY = "PipeMode";
    public static final float RED_DAMAGE = 20.0F;
    public static final int WITHER_TICKS = 20 * 10;
    public static final double RED_DOT_RANGE = 30.0D;
    public static final float RED_DOT_MULTIPLIER = 30.0F;
    public static final double FRIENDLY_RADIUS = 8.0D;
    public static final double PURPLE_RADIUS = 7.0D;

    private static final java.util.UUID DAMAGE_UUID =
            java.util.UUID.fromString("B7C8D9EA-0010-4FA5-9BC6-7890123456A1");
    private static final java.util.UUID SPEED_UUID =
            java.util.UUID.fromString("B7C8D9EA-0011-4FA5-9BC6-7890123456A2");
    private static final java.util.UUID REACH_UUID =
            java.util.UUID.fromString("B7C8D9EA-0012-4FA5-9BC6-7890123456A3");

    public PipeItem() {
        super(Tiers.NETHERITE, 3, -2.4F, new net.minecraft.world.item.Item.Properties().durability(2000));
    }

    /* ------------------------------------------------------------------ */
    /* 模式                                                                */
    /* ------------------------------------------------------------------ */

    public static PipeMode mode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(MODE_KEY, CompoundTag.TAG_INT)) {
            return PipeMode.of(tag.getInt(MODE_KEY));
        }
        return PipeMode.RED;
    }

    public static void setMode(ItemStack stack, PipeMode mode) {
        stack.getOrCreateTag().putInt(MODE_KEY, mode.index);
        syncModel(stack, mode);
    }

    private static void syncModel(ItemStack stack, PipeMode mode) {
        CompoundTag tag = stack.getOrCreateTag();
        int current = tag.contains("CustomModelData", CompoundTag.TAG_INT)
                ? tag.getInt("CustomModelData") : -1;
        if (current != mode.index) {
            tag.putInt("CustomModelData", mode.index);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        syncModel(stack, mode(stack));
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    /* ------------------------------------------------------------------ */
    /* 右键：切模式 / 开始蓄力                                              */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                PipeMode next = mode(stack).next();
                setMode(stack, next);
                player.displayClientMessage(Component.translatable("pipe.tenfold.switch")
                        .append(" ").append(next.display()), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (mode(stack).chargeTicks() <= 0) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        use(context.getLevel(), player, context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return mode(stack).chargeTicks();
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        PipeMode current = mode(stack);
        int total = current.chargeTicks();
        if (total <= 0) {
            return;
        }
        int elapsed = total - remaining;
        if (elapsed % 10 == 0 && elapsed > 0) {
            int percent = Math.min(100, (int) (elapsed * 100.0F / total));
            player.displayClientMessage(Component.translatable("pipe.tenfold.charging")
                    .append(" ").append(current.display())
                    .append(Component.literal(" " + percent + "%")), true);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        int total = mode(stack).chargeTicks();
        if (total > 0 && timeLeft > 0) {
            player.displayClientMessage(Component.translatable("pipe.tenfold.cancelled"), true);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 蓄力完成：按模式生效                                                 */
    /* ------------------------------------------------------------------ */

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return stack;
        }
        switch (mode(stack)) {
            case WHITE -> whitePulse(player);
            case PURPLE -> purplePulse(player);
            case RED_DOT -> fireRedDot(player);
            default -> {
            }
        }
        return stack;
    }

    /* ------------------------------------------------------------------ */
    /* 白色：友方回复                                                 */
    /* ------------------------------------------------------------------ */

    private void whitePulse(ServerPlayer player) {
        int healed = 0;
        for (LivingEntity ally : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(FRIENDLY_RADIUS),
                entity -> entity != player && isFriendly(player, entity))) {
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 3, false, true, true));
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 30, 2, false, true, true));
            healed++;
        }
        // 自己也吃一份
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 3, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 30, 2, false, true, true));
        player.displayClientMessage(Component.translatable("pipe.tenfold.white.used", healed + 1), true);
        playPulse(player, ParticleTypes.HEART);
    }

    /* ------------------------------------------------------------------ */
    /* 紫色：范围虚弱                                                 */
    /* ------------------------------------------------------------------ */

    private void purplePulse(ServerPlayer player) {
        int affected = 0;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(PURPLE_RADIUS),
                entity -> entity != player && isEnemy(player, entity))) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 30, 9, false, true, true));
            affected++;
        }
        player.displayClientMessage(Component.translatable("pipe.tenfold.purple.used", affected), true);
        playPulse(player, ParticleTypes.WITCH);
    }

    /* ------------------------------------------------------------------ */
    /* 红点：扣血 + 穿透射线                                               */
    /* ------------------------------------------------------------------ */

    private void fireRedDot(ServerPlayer player) {
        float before = player.getHealth();
        float cost = before / 2.0F;
        if (cost <= 0.0F) {
            return;
        }
        // 直接扣血（不走伤害结算，避免触发免死/减伤）
        player.setHealth(Math.max(0.5F, before - cost));
        float damage = cost * RED_DOT_MULTIPLIER;

        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        Set<LivingEntity> hit = new HashSet<>();
        for (double travelled = 0.5D; travelled <= RED_DOT_RANGE; travelled += 0.5D) {
            Vec3 point = eye.add(dir.scale(travelled));
            if (level instanceof ServerLevel server) {
                server.sendParticles(new DustParticleOptions(new org.joml.Vector3f(1.0F, 0.1F, 0.1F), 0.9F),
                        point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            AABB area = new AABB(point.x - 0.8D, point.y - 0.8D, point.z - 0.8D,
                    point.x + 0.8D, point.y + 0.8D, point.z + 0.8D);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                    entity -> entity != player && entity.isAlive())) {
                if (!hit.add(target)) {
                    continue;
                }
                target.hurt(player.damageSources().playerAttack(player), damage);
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 30, 1, false, true, true));
        player.displayClientMessage(Component.translatable("pipe.tenfold.red_dot.used",
                String.format("%.1f", cost), String.format("%.0f", damage)), true);
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    /* ------------------------------------------------------------------ */
    /* 目标筛选                                                            */
    /* ------------------------------------------------------------------ */

    private static boolean isFriendly(Player source, LivingEntity entity) {
        if (entity instanceof Player other) {
            return other != source;
        }
        if (entity instanceof TamableAnimal tameable) {
            return tameable.isTame();
        }
        if (entity instanceof com.tenfold.entity.PoeyEntity) {
            return true;
        }
        return false;
    }

    private static boolean isEnemy(Player source, LivingEntity entity) {
        if (entity instanceof Mob mob) {
            return mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
        }
        return entity instanceof Player other && other != source;
    }

    private static void playPulse(ServerPlayer player, net.minecraft.core.particles.ParticleOptions particle) {
        if (player.level() instanceof ServerLevel server) {
            Vec3 pos = player.position();
            server.sendParticles(particle, pos.x, pos.y + 1.0D, pos.z, 24, 1.5D, 1.0D, 1.5D, 0.1D);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    /* ------------------------------------------------------------------ */
    /* 攻击：红色模式附加凋零                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide && mode(stack) == PipeMode.RED) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0, false, true, true));
        }
        return result;
    }

    /* ------------------------------------------------------------------ */
    /* 属性：红色模式才是 20 攻长剑                                         */
    /* ------------------------------------------------------------------ */

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.MAINHAND) {
            return base;
        }
        PipeMode current = mode(stack);
        float damage = current == PipeMode.RED ? RED_DAMAGE : 2.0F;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        base.forEach((attribute, modifier) -> {
            if (attribute != Attributes.ATTACK_DAMAGE && attribute != Attributes.ATTACK_SPEED) {
                builder.put(attribute, modifier);
            }
        });
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                DAMAGE_UUID, "tenfold_pipe_damage", damage - 1.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                SPEED_UUID, "tenfold_pipe_speed", -2.4D, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    /* ------------------------------------------------------------------ */
    /* 提示                                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        PipeMode current = mode(stack);
        tooltip.add(Component.literal("\u25C6 ").withStyle(ChatFormatting.DARK_GRAY).append(current.display()));
        tooltip.add(Component.translatable("pipe.tenfold.tooltip." + current.key)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("pipe.tenfold.tooltip.switch")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public @Nullable String getCreatorModId(ItemStack itemStack) {
        return com.tenfold.Tenfold.MOD_ID;
    }
}
