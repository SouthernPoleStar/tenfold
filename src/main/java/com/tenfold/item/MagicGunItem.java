package com.tenfold.item;

import com.tenfold.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 魔弹枪 — a demon-possessed ranged weapon that eats its own wielder.
 * <p>
 * The first shot deals 10 damage, each following shot adds 5. After the 7th
 * shot the demon collects: the shooter dies to a custom damage type whose
 * death message reads 「xxx 使用魔弹枪贯穿了自己」. The stack counter lives on
 * the player, so dying (any way) starts the count from scratch. Pulling the
 * trigger on the 7th shell is a choice; the gun will not refuse.
 */
public class MagicGunItem extends Item {

    public static final float BASE_DAMAGE = 10.0F;
    public static final float STEP_DAMAGE = 5.0F;
    public static final int MAX_SHOTS = 7;
    public static final double RANGE = 64.0D;
    private static final String COUNT_KEY = "tenfoldDemonCount";

    public MagicGunItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /** Shots already fired by this player since their last death. */
    public static int count(Player player) {
        return Math.min(MAX_SHOTS, player.getPersistentData().getInt(COUNT_KEY));
    }

    public static void setCount(Player player, int value) {
        player.getPersistentData().putInt(COUNT_KEY, Math.max(0, Math.min(MAX_SHOTS, value)));
    }

    /** Damage of the next shot: 10, 15, 20 … up to 40. */
    public static float nextDamage(Player player) {
        return BASE_DAMAGE + STEP_DAMAGE * count(player);
    }

    /* ------------------------------------------------------------------ */
    /* Firing                                                              */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(this, 10);
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        int shots = count(server);
        float damage = nextDamage(server);
        setCount(server, shots + 1);

        level.playSound(null, server.blockPosition(), ModSounds.CONTRACT_MADE.get(),
                SoundSource.PLAYERS, 1.0F, 0.6F);

        LivingEntity hit = raycast(server);
        if (hit != null) {
            // Enemies die to a normal player kill; only the wielder's 7th
            // shell uses the demon's own death message.
            hit.hurt(server.damageSources().playerAttack(server), damage);
        }
        if (level instanceof ServerLevel serverLevel) {
            Vec3 from = server.getEyePosition();
            Vec3 end = hit != null ? hit.getEyePosition() : from.add(server.getLookAngle().scale(RANGE));
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    end.x, end.y, end.z, 8, 0.2D, 0.2D, 0.2D, 0.01D);
        }

        server.displayClientMessage(Component.translatable("tenfold.magicgun.shot",
                String.format("%.0f", damage), shots + 1, MAX_SHOTS)
                .withStyle(ChatFormatting.DARK_PURPLE), true);

        // The 7th shell belongs to the demon.
        if (shots + 1 >= MAX_SHOTS && server.isAlive()) {
            setCount(server, 0);
            server.hurt(com.tenfold.ModDamageTypes.of(level, com.tenfold.ModDamageTypes.MAGIC_BULLET),
                    Float.MAX_VALUE);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** First living entity whose body the centre ray passes through. */
    @Nullable
    private static LivingEntity raycast(ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(RANGE));
        BlockHitResult wall = player.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() == HitResult.Type.BLOCK) {
            to = wall.getLocation();
        }
        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;
        for (Entity e : player.level().getEntities(player, player.getBoundingBox()
                .inflate(RANGE), this_ -> this_ instanceof LivingEntity l && l.isAlive() && l != player)) {
            AABB box = e.getBoundingBox().inflate(0.3D);
            var clip = box.clip(from, to);
            if (clip.isPresent()) {
                double t = clip.get().distanceToSqr(from);
                if (t < bestT) {
                    bestT = t;
                    best = (LivingEntity) e;
                }
            }
        }
        return best;
    }

    /* ------------------------------------------------------------------ */
    /* Tooltip                                                             */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tenfold.magicgun.desc",
                String.format("%.0f", BASE_DAMAGE), String.format("%.0f", STEP_DAMAGE), MAX_SHOTS)
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tenfold.magicgun.warn")
                .withStyle(ChatFormatting.DARK_RED));
    }
}
