package com.tenfold.common;

import com.tenfold.ModEffects;
import com.tenfold.ModEntities;
import com.tenfold.ModItems;
import com.tenfold.entity.BloodAncestorEntity;
import com.tenfold.item.HardBloodForm;
import com.tenfold.item.HardBloodWeaponItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 血魔体系的事件端：
 * <ul>
 *   <li>转化：杀死老祖 / 用牙 / 被老祖杀死 5% / 血魔击杀传染（配额 2）。</li>
 *   <li>代际伤害倍率、硬血武器的血魔专属限制。</li>
 *   <li>恐水症：沾水中毒式扣血，潜水直接窒息。</li>
 *   <li>血魔禁食普通食物；空瓶吸血与祭祀匕首取血仪式。</li>
 *   <li>永久 Buff 在登录 / 重生时重刷。</li>
 * </ul>
 * <p>
 * 自然生成走数据包：{@code data/tenfold/forge/biome_modifier/add_blood_ancestor_spawns.json}
 * （1.20.1 的 Forge 没有 BiomeLoadingEvent，改用 biome_modifier）。
 */
public class BloodDemonEvents {

    /* ------------------------------------------------------------------ */
    /* 永久 Buff 维护                                                      */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BloodDemonManager.applyPermanentEffects(player);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BloodDemonManager.applyPermanentEffects(player);
        }
    }

    /**
     * 死亡重生 / 跨维度时手动搬血魔数据：原版 {@code restoreFrom} 不会复制
     * 写在 {@code getPersistentData()} 顶层的键，不搬的话复活后就不再是血魔，
     * 连带吸血条（靠恐水症效果判断）和硬血瓶仪式都会失效。
     */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        BloodDemonManager.copyAcrossRespawn(event.getOriginal(), event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            BloodDemonManager.applyPermanentEffects(player);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 恐水症 + 吸血条（渴血）                                              */
    /* ------------------------------------------------------------------ */

    /** 长枪突刺正在结算中（防递归触发）。 */
    private static final Set<UUID> PIERCING = new HashSet<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        if (!(player instanceof ServerPlayer serverPlayer) || player.isCreative() || player.isSpectator()) {
            return;
        }

        // 恐水症。
        if (player.hasEffect(ModEffects.HYDROPHOBIA.get()) && player.tickCount % 20 == 0) {
            if (player.isEyeInFluid(FluidTags.WATER)) {
                // 潜水：直接窒息，每秒 2 点溺水伤害。
                player.hurt(player.damageSources().drown(), 2.0F);
            } else if (player.isInWater()) {
                // 沾水（半个身子在水里）：像中毒一样持续扣血，无视护甲。
                player.hurt(player.damageSources().magic(), 1.0F);
            }
        }

        // 吸血条与渴血。
        if (BloodDemonManager.isBloodDemon(serverPlayer)) {
            tickBloodBar(serverPlayer);
            // 每秒兜底：四件套（x代血魔 / 力量 II / 抗性 I / 恐水症）掉了就补回来。
            // 死亡、跨维度、原版清效果等情况下都能自动恢复。
            if (player.tickCount % 20 == 0
                    && !BloodDemonManager.hasAllPermanentEffects(serverPlayer)) {
                BloodDemonManager.applyPermanentEffects(serverPlayer);
            }
        }
    }

    /**
     * 血魔的饱食度就是「吸血条」，下降完全走原版饱食度机制（疾跑、跳跃、
     * 挖掘、自然恢复等消耗疲劳值 → 掉饥饿），不再额外扣减；喝血瓶回复。
     * 清零时获得「渴血」（攻击 ×2、受伤 ×3、缓慢 I），喝血后自动解除。
     */
    private void tickBloodBar(ServerPlayer player) {
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() <= 0) {
            if (player.tickCount % 20 == 0) {
                player.addEffect(new MobEffectInstance(ModEffects.BLOODTHIRST.get(),
                        40, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        30, 0, false, false, true));
            }
        } else if (player.hasEffect(ModEffects.BLOODTHIRST.get())) {
            player.removeEffect(ModEffects.BLOODTHIRST.get());
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 伤害规则：代际倍率 + 渴血 + 双持长刀 + 长枪突刺 + 硬血武器限制       */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        // 血魔不吃饥饿伤害：饿的是血，不是肚子。
        if (event.getEntity() instanceof Player starving
                && BloodDemonManager.isBloodDemon(starving)
                && "starve".equals(event.getSource().getMsgId())) {
            event.setAmount(0.0F);
            return;
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            // 硬血武器只有血魔能用：非血魔挥不动，伤害直接压成 0。
            if (attacker.getMainHandItem().is(ModItems.HARD_BLOOD_WEAPON.get())
                    && !BloodDemonManager.isBloodDemon(attacker)) {
                event.setAmount(0.0F);
                if (attacker instanceof ServerPlayer denied && denied.tickCount % 20 == 0) {
                    denied.displayClientMessage(
                            Component.translatable("tenfold.hard_blood.denied"), true);
                }
                return;
            }
            // 低阶打高阶 -90% 起，高阶打低阶 +90% 起，每再差一代乘 0.5 / 1.5。
            float multiplier = BloodDemonManager.interGenerationMultiplier(attacker, event.getEntity());
            if (multiplier != 1.0F) {
                event.setAmount(event.getAmount() * multiplier);
            }
        }

        // 渴血：自身攻击 ×2，受到的伤害 ×3。
        if (event.getSource().getEntity() instanceof LivingEntity thirsty
                && thirsty.hasEffect(ModEffects.BLOODTHIRST.get())) {
            event.setAmount(event.getAmount() * 2.0F);
        }
        if (event.getEntity().hasEffect(ModEffects.BLOODTHIRST.get())) {
            event.setAmount(event.getAmount() * 3.0F);
        }

        // 双持长刀：13 → 17。
        if (event.getSource().getDirectEntity() instanceof Player dualWielder
                && dualWielder.getMainHandItem().is(ModItems.HARD_BLOOD_WEAPON.get())
                && HardBloodWeaponItem.form(dualWielder.getMainHandItem()) == HardBloodForm.LONG_BLADE
                && HardBloodWeaponItem.isDualWieldingLongBlades(dualWielder)) {
            event.setAmount(event.getAmount()
                    * (HardBloodWeaponItem.DUAL_LONG_BLADE_DAMAGE / HardBloodForm.LONG_BLADE.damage));
        }

        // 长枪突刺：这一击同时刺中直线上的其他敌人。
        if (event.getSource().getDirectEntity() instanceof Player spearUser
                && !PIERCING.contains(spearUser.getUUID())
                && spearUser.getMainHandItem().is(ModItems.HARD_BLOOD_WEAPON.get())
                && HardBloodWeaponItem.form(spearUser.getMainHandItem()) == HardBloodForm.SPEAR) {
            pierceLine(spearUser, event.getEntity());
        }
    }

    /** 长枪：把正前方直线 5 格内、视线 ±30° 的敌人一并刺到。 */
    private void pierceLine(Player player, LivingEntity primary) {
        PIERCING.add(player.getUUID());
        try {
            Vec3 look = player.getLookAngle();
            Vec3 eye = player.getEyePosition();
            List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(6.0D),
                    entity -> entity != player && entity != primary && entity.isAlive());
            for (LivingEntity target : targets) {
                Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(eye);
                double distance = to.length();
                if (distance > 5.0D) {
                    continue;
                }
                if (to.normalize().dot(look) < 0.85D) {
                    continue;
                }
                target.hurt(player.damageSources().playerAttack(player), HardBloodForm.SPEAR.damage);
            }
        } finally {
            PIERCING.remove(player.getUUID());
        }
    }

    /* ------------------------------------------------------------------ */
    /* 长刀双持格挡：伤害减半，30% 反弹                                    */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack used = player.getUseItem();
        if (!used.is(ModItems.HARD_BLOOD_WEAPON.get())
                || HardBloodWeaponItem.form(used) != HardBloodForm.LONG_BLADE) {
            return;
        }
        float original = event.getBlockedDamage();
        event.setBlockedDamage(original * 0.5F);
        event.setShieldTakesDamage(false);
        if (event.getDamageSource().getEntity() instanceof LivingEntity attacker
                && attacker != player) {
            attacker.hurt(player.damageSources().thorns(player), original * 0.3F);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 转化                                                                */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();

        // 击杀血魔老祖不再直接转化：它只会掉「血魔老祖的牙」，
        // 拿牙 Shift+右键 使用才会 100% 变成二代血魔（见 BloodFangItem）。

        // 被血魔老祖杀死 → 5% 概率被转化为二代血魔。
        if (victim instanceof ServerPlayer dead
                && source.getEntity() instanceof BloodAncestorEntity) {
            if (dead.getRandom().nextFloat() < 0.05F) {
                BloodDemonManager.convert(dead, 2);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Shift+右键 其他玩家：发起转化请求（需要对方在聊天栏点同意）          */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getSide().isClient() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer) || !player.isShiftKeyDown()) {
            return;
        }
        ItemStack held = player.getMainHandItem();

        // 对着另一名玩家 Shift+右键：发起转化请求（空手或非玻璃瓶都可以）。
        if (event.getTarget() instanceof ServerPlayer targetPlayer
                && targetPlayer != serverPlayer
                && !held.is(Items.GLASS_BOTTLE)) {
            BloodDemonManager.requestConversion(serverPlayer, targetPlayer);
            event.setCanceled(true);
            return;
        }

        if (!held.is(Items.GLASS_BOTTLE)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target) || target == player) {
            return;
        }
        if (!BloodDemonManager.isBloodDemon(serverPlayer)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("tenfold.blood.need_demon"), true);
            return;
        }
        // 吸血：扣目标 2 点血，瓶子变血瓶。
        target.hurt(target.damageSources().magic(), 2.0F);
        if (!serverPlayer.getAbilities().instabuild) {
            held.shrink(1);
        }
        ItemStack blood = new ItemStack(ModItems.BLOOD_BOTTLE.get());
        if (!serverPlayer.addItem(blood)) {
            target.spawnAtLocation(blood);
        }
        serverPlayer.displayClientMessage(
                Component.translatable("tenfold.blood.extracted"), true);
        event.setCanceled(true);
    }

    /* ------------------------------------------------------------------ */
    /* 祭祀仪式 + 血魔禁食普通食物（挂在 RightClickItem 上，两侧都会触发）  */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getSide().isClient() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        // 祭祀仪式：副手匕首 + 主手空瓶 + Shift+右键 → 扣自己 1 点生命，凝成硬血瓶。
        // （1.20.1 的 Forge 不触发 RightClickEmpty，所以这里挂在 RightClickItem 上。）
        if (player.isShiftKeyDown()
                && stack.is(Items.GLASS_BOTTLE)
                && player.getOffhandItem().is(ModItems.SACRIFICIAL_DAGGER.get())) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (!BloodDemonManager.isBloodDemon(serverPlayer)) {
                serverPlayer.displayClientMessage(
                        Component.translatable("tenfold.blood.need_demon"), true);
                return;
            }
            // 扣自己 1 点生命（无视护甲的魔法伤害），凝成硬血瓶。
            serverPlayer.hurt(serverPlayer.damageSources().magic(), 1.0F);
            if (!serverPlayer.getAbilities().instabuild) {
                stack.shrink(1);
                player.getOffhandItem().hurtAndBreak(1, serverPlayer, p -> {
                });
            }
            ItemStack hardBlood = new ItemStack(ModItems.HARD_BLOOD_BOTTLE.get());
            if (!serverPlayer.addItem(hardBlood)) {
                serverPlayer.spawnAtLocation(hardBlood, 1);
            }
            serverPlayer.displayClientMessage(
                    Component.translatable("tenfold.blood.ritual"), true);
            event.setCanceled(true);
            return;
        }

        // 血魔不能再吃普通食物（带增益效果的食品除外）。
        if (!BloodDemonManager.isBloodDemon(player)) {
            return;
        }
        if (isBlockedFood(stack, player)) {
            if (player.tickCount % 40 == 0) {
                player.displayClientMessage(
                        Component.translatable("tenfold.blood.no_food"), true);
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isClient()) {
            return;
        }
        Player player = event.getEntity();
        if (!BloodDemonManager.isBloodDemon(player)) {
            return;
        }
        if (isBlockedFood(event.getItemStack(), player)) {
            event.setCanceled(true);
        }
    }

    /**
     * 血魔禁食的判定：
     * <ul>
     *   <li>血瓶：放行（血魔的主食）。</li>
     *   <li>带**增益效果**的食品（金苹果、附魔金苹果、正面效果的迷之炖菜等）：放行。</li>
     *   <li>其余普通食物：禁止。</li>
     * </ul>
     * 蜂蜜瓶这类"纯回饥饿、没有增益"的普通食物仍然算被禁食。
     */
    private static boolean isBlockedFood(ItemStack stack, Player player) {
        if (!stack.isEdible() || stack.is(ModItems.BLOOD_BOTTLE.get())) {
            return false;
        }
        net.minecraft.world.food.FoodProperties food =
                stack.getItem().getFoodProperties(stack, player);
        if (food == null) {
            return false;
        }
        for (var pair : food.getEffects()) {
            if (pair.getFirst().getEffect().getCategory()
                    == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL) {
                return false;
            }
        }
        return true;
    }
}
