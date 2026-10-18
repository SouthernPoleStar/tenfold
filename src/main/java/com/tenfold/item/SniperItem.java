package com.tenfold.item;

import com.tenfold.ModSounds;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 句点事务所·狙击枪 — a hitscan sniper rifle.
 * <p>
 * Nine normal rounds (8 damage) and five 高速粉碎弹 (12 damage) live in the
 * gun itself; Shift+right-click swaps which magazine the trigger feeds from.
 * Once both magazines run dry the barrel is empty: shots only make a click
 * and deal no damage. There is no reload — an empty rifle is scrap forever.
 */
public class SniperItem extends Item {

    public static final int NORMAL_MAG = 9;
    public static final int SPECIAL_MAG = 5;
    public static final float NORMAL_DAMAGE = 8.0F;
    public static final float SPECIAL_DAMAGE = 12.0F;
    public static final double RANGE = 96.0D;

    private static final String ROOT = "tenfoldSniper";

    public SniperItem() {
        super(new Item.Properties().durability(0).stacksTo(1));
    }

    /** A player holding an empty rifle, paired with the rifle itself. */
    public record Sniper(net.minecraft.world.entity.player.Player player, ItemStack stack) {
    }

    /* ------------------------------------------------------------------ */
    /* NBT state                                                           */
    /* ------------------------------------------------------------------ */

    private static net.minecraft.nbt.CompoundTag root(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(ROOT, net.minecraft.nbt.CompoundTag.TAG_COMPOUND)) {
            net.minecraft.nbt.CompoundTag sub = new net.minecraft.nbt.CompoundTag();
            sub.putInt("normal", NORMAL_MAG);
            sub.putInt("special", SPECIAL_MAG);
            sub.putBoolean("specialMode", false);
            tag.put(ROOT, sub);
        }
        return tag.getCompound(ROOT);
    }

    public static int normal(ItemStack stack) {
        return root(stack).getInt("normal");
    }

    public static int special(ItemStack stack) {
        return root(stack).getInt("special");
    }

    public static boolean specialMode(ItemStack stack) {
        return root(stack).getBoolean("specialMode");
    }

    public static void setNormal(ItemStack stack, int v) {
        root(stack).putInt("normal", v);
    }

    public static void setSpecial(ItemStack stack, int v) {
        root(stack).putInt("special", v);
    }

    public static void setSpecialMode(ItemStack stack, boolean v) {
        root(stack).putBoolean("specialMode", v);
    }

    public static boolean isEmpty(ItemStack stack) {
        return normal(stack) <= 0 && special(stack) <= 0;
    }

    /* ------------------------------------------------------------------ */
    /* Interaction                                                         */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                toggleMode((ServerPlayer) player, stack);
            } else {
                fire((ServerPlayer) player, stack);
            }
        }
        player.getCooldowns().addCooldown(this, 8);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void toggleMode(ServerPlayer player, ItemStack stack) {
        boolean now = !specialMode(stack);
        setSpecialMode(stack, now);
        player.level().playSound(null, player.blockPosition(), ModSounds.FORM_SHIFT.get(),
                SoundSource.PLAYERS, 0.6F, 1.4F);
        player.displayClientMessage(Component.translatable(now
                        ? "tenfold.sniper.mode_special" : "tenfold.sniper.mode_normal")
                .withStyle(now ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.YELLOW), true);
    }

    private static void fire(ServerPlayer player, ItemStack stack) {
        // Dry gun: click, no damage — forever. There is no reload; an empty
        // Period Office rifle is scrap metal.
        if (isEmpty(stack)) {
            player.displayClientMessage(Component.translatable("tenfold.sniper.empty")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return;
        }

        boolean special = specialMode(stack);
        int left = special ? special(stack) : normal(stack);
        if (left <= 0) {
            // Magazine dry but the other one still has rounds: only the toggle
            // (shift+right-click) switches feed; a trigger pull just clicks.
            player.displayClientMessage(Component.translatable("tenfold.sniper.mag_dry")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        if (special) {
            setSpecial(stack, left - 1);
        } else {
            setNormal(stack, left - 1);
        }

        player.level().playSound(null, player.blockPosition(), ModSounds.CONTRACT_MADE.get(),
                SoundSource.PLAYERS, 1.0F, special ? 0.7F : 1.0F);

        LivingEntity hit = raycast(player);
        float damage = special ? SPECIAL_DAMAGE : NORMAL_DAMAGE;
        if (hit != null) {
            hit.hurt(player.damageSources().playerAttack(player), damage);
        }
        if (player.level() instanceof ServerLevel server) {
            Vec3 from = player.getEyePosition();
            Vec3 end = hit != null ? hit.getEyePosition() : from.add(player.getLookAngle().scale(RANGE));
            drawTracer(server, from, end);
        }
        player.displayClientMessage(Component.translatable("tenfold.sniper.shot",
                special ? Component.translatable("tenfold.sniper.round_special") :
                        Component.translatable("tenfold.sniper.round_normal"),
                String.format("%.0f", damage),
                normal(stack), special(stack)).withStyle(ChatFormatting.GRAY), true);
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

    private static void drawTracer(ServerLevel level, Vec3 from, Vec3 end) {
        Vec3 delta = end.subtract(from);
        double len = delta.length();
        if (len < 0.5D) {
            return;
        }
        Vec3 step = delta.normalize().scale(0.8D);
        Vec3 p = from;
        for (double d = 0; d < len; d += 0.8D) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    p.x, p.y, p.z, 1, 0, 0, 0, 0);
            p = p.add(step);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Tooltip                                                             */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(specialMode(stack)
                        ? "tenfold.sniper.mode_special" : "tenfold.sniper.mode_normal")
                .withStyle(specialMode(stack) ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tenfold.sniper.ammo",
                normal(stack), NORMAL_MAG, special(stack), SPECIAL_MAG).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tenfold.sniper.no_reload").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tenfold.sniper.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
