package com.tenfold.item;

import com.tenfold.ModEntities;
import com.tenfold.common.WildHuntManager;
import com.tenfold.entity.DullahanEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
 * 狂猎大剑：基础伤害 10；**穿着狂猎棺材时 +40**（由 {@code WildHuntManager} 补属性）。
 * <p>
 * 穿棺材 + 手持本剑时：
 * <ul>
 *   <li>普通右键：召唤并骑上无头坐骑「杜拉奇」。</li>
 *   <li>Shift + 右键：死亡冲撞——背后浮现三匹杜拉奇虚影，向前冲锋 20 格，
 *       沿途生物各受 15 点伤害。</li>
 * </ul>
 */
public class WildHuntGreatswordItem extends SwordItem {

    public static final float BASE_DAMAGE = 10.0F;
    public static final float COFFIN_DAMAGE_BONUS = 40.0F;
    public static final double CHARGE_DISTANCE = 20.0D;
    public static final float CHARGE_DAMAGE = 15.0F;
    /** 死亡冲撞冷却：6 秒。 */
    public static final int CHARGE_COOLDOWN_TICKS = 120;

    public WildHuntGreatswordItem() {
        // SwordItem 的最终伤害 = 1 + attackDamageModifier，所以填 9 得到 10。
        super(Tiers.NETHERITE, 9, -2.8F, new net.minecraft.world.item.Item.Properties().durability(2500));
    }

    /* ------------------------------------------------------------------ */
    /* 右键：召唤坐骑 / 死亡冲撞                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!WildHuntManager.isWearingCoffin(player)) {
            player.displayClientMessage(Component.translatable("tenfold.wild_hunt.need_coffin"), true);
            return InteractionResultHolder.fail(stack);
        }

        if (player.isShiftKeyDown()) {
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(stack);
            }
            player.getCooldowns().addCooldown(this, CHARGE_COOLDOWN_TICKS);
            deathCharge(player);
        } else {
            summonDullahan(player);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
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

    /* ------------------------------------------------------------------ */
    /* 杜拉奇坐骑                                                          */
    /* ------------------------------------------------------------------ */

    private void summonDullahan(Player player) {
        if (player.getVehicle() instanceof DullahanEntity) {
            player.stopRiding();
            return;
        }
        Level level = player.level();
        DullahanEntity mount = ModEntities.DULLAHAN.get().create(level);
        if (mount == null) {
            return;
        }
        Vec3 look = player.getLookAngle();
        double x = player.getX() + look.x * 2.0D;
        double z = player.getZ() + look.z * 2.0D;
        mount.moveTo(x, player.getY(), z, player.getYRot(), 0.0F);
        mount.setTamed(true);
        mount.setOwnerUUID(player.getUUID());
        level.addFreshEntity(mount);
        player.startRiding(mount, true);
        level.playSound(null, x, player.getY(), z, SoundEvents.HORSE_ANGRY, SoundSource.PLAYERS, 1.0F, 0.6F);
        shout(player);
    }

    /** 以使用者身份在聊天框喊一句台词。 */
    private static void shout(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var server = serverLevel.getServer();
        if (server == null) {
            return;
        }
        Component line = Component.literal("<" + player.getGameProfile().getName() + "> ")
                .append(Component.translatable("tenfold.wild_hunt.shout"));
        server.getPlayerList().broadcastSystemMessage(line, false);
    }

    /* ------------------------------------------------------------------ */
    /* 死亡冲撞                                                            */
    /* ------------------------------------------------------------------ */

    private void deathCharge(Player player) {
        Level level = player.level();
        Vec3 flat = player.getLookAngle();
        flat = new Vec3(flat.x, 0.0D, flat.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        Vec3 dir = flat.normalize();

        // 背后三匹杜拉奇虚影
        for (int i = 0; i < 3; i++) {
            DullahanEntity ghost = ModEntities.DULLAHAN.get().create(level);
            if (ghost == null) {
                break;
            }
            double side = (i - 1) * 1.7D;
            Vec3 perpendicular = new Vec3(-dir.z, 0.0D, dir.x).scale(side);
            Vec3 spawn = player.position().subtract(dir.scale(2.5D + i * 1.3D)).add(perpendicular);
            ghost.moveTo(spawn.x, spawn.y, spawn.z, player.getYRot(), 0.0F);
            ghost.setPhantom(true);
            level.addFreshEntity(ghost);
        }

        // 沿途伤害：20 格路径上，每个生物只吃一次 15 点
        Set<LivingEntity> hit = new HashSet<>();
        for (int step = 1; step <= (int) CHARGE_DISTANCE; step++) {
            Vec3 point = player.position().add(dir.scale(step));
            AABB area = new AABB(point.x - 1.8D, point.y - 1.0D, point.z - 1.8D,
                    point.x + 1.8D, point.y + 2.0D, point.z + 1.8D);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
                    entity -> entity != player && entity.isAlive()
                            && !(entity instanceof DullahanEntity));
            for (LivingEntity target : targets) {
                if (!hit.add(target)) {
                    continue;
                }
                target.hurt(player.damageSources().playerAttack(player), CHARGE_DAMAGE);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.CRIT,
                            target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                            8, 0.3D, 0.3D, 0.3D, 0.05D);
                }
            }
        }

        // 玩家自己向前推 20 格（遇到障碍就停在最后一个安全位置）
        Vec3 origin = player.position();
        Vec3 destination = origin;
        for (int step = 1; step <= (int) CHARGE_DISTANCE; step++) {
            Vec3 next = origin.add(dir.scale(step));
            if (!level.noCollision(player, player.getBoundingBox().move(next.subtract(origin)))) {
                break;
            }
            destination = next;
        }
        player.teleportTo(destination.x, destination.y, destination.z);
        player.hurtMarked = true;

        level.playSound(null, destination.x, destination.y, destination.z,
                SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0F, 0.7F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL,
                    destination.x, destination.y + 1.0D, destination.z,
                    30, 0.6D, 0.8D, 0.6D, 0.02D);
        }
        shout(player);
    }

    /* ------------------------------------------------------------------ */
    /* 提示                                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tenfold.wild_hunt.base",
                String.format("%.0f", BASE_DAMAGE)).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.tenfold.wild_hunt.bonus",
                String.format("%.0f", COFFIN_DAMAGE_BONUS)).withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.tenfold.wild_hunt.summon")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tenfold.wild_hunt.charge",
                String.format("%.0f", CHARGE_DAMAGE)).withStyle(ChatFormatting.GOLD));
    }

    @Override
    public @Nullable String getCreatorModId(ItemStack itemStack) {
        return com.tenfold.Tenfold.MOD_ID;
    }
}
