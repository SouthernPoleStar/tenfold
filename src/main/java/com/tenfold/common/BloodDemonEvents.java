package com.tenfold.common;

import com.tenfold.ModEffects;
import com.tenfold.ModEntities;
import com.tenfold.ModItems;
import com.tenfold.entity.BloodAncestorEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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

    /* ------------------------------------------------------------------ */
    /* 恐水症                                                              */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        if (!player.hasEffect(ModEffects.HYDROPHOBIA.get()) || player.isCreative() || player.isSpectator()) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        if (player.isEyeInFluid(FluidTags.WATER)) {
            // 潜水：直接窒息，每秒 2 点溺水伤害。
            player.hurt(player.damageSources().drown(), 2.0F);
        } else if (player.isInWater()) {
            // 沾水（半个身子在水里）：像中毒一样持续扣血，无视护甲。
            player.hurt(player.damageSources().magic(), 1.0F);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 伤害规则：代际倍率 + 硬血武器限制                                    */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
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

        // 1) 直接击杀血魔老祖 → 100% 转化为二代血魔。
        if (victim.getType() == ModEntities.BLOOD_ANCESTOR.get()
                && source.getEntity() instanceof ServerPlayer killer
                && BloodDemonManager.generation(killer) == 0) {
            BloodDemonManager.convert(killer, 2);
        }

        // 2) 被血魔老祖杀死 → 5% 概率被转化为二代血魔。
        if (victim instanceof ServerPlayer dead
                && source.getEntity() instanceof BloodAncestorEntity) {
            if (dead.getRandom().nextFloat() < 0.05F) {
                BloodDemonManager.convert(dead, 2);
            }
        }

        // 3) 血魔玩家击杀普通玩家 → 传染出下一代（每名血魔限两名后裔）。
        if (victim instanceof ServerPlayer deadPlayer
                && source.getEntity() instanceof ServerPlayer killerPlayer) {
            BloodDemonManager.tryInfect(killerPlayer, deadPlayer);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 空瓶吸血：Shift+右键 任意生物                                       */
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
    /* 祭祀仪式：副手匕首 + 主手空瓶 + Shift+右键 → 硬血瓶                  */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getSide().isClient() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer) || !player.isShiftKeyDown()) {
            return;
        }
        ItemStack bottle = player.getMainHandItem();
        ItemStack dagger = player.getOffhandItem();
        if (!bottle.is(Items.GLASS_BOTTLE) || !dagger.is(ModItems.SACRIFICIAL_DAGGER.get())) {
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
            bottle.shrink(1);
            dagger.hurtAndBreak(1, serverPlayer, p -> {
            });
        }
        ItemStack hardBlood = new ItemStack(ModItems.HARD_BLOOD_BOTTLE.get());
        if (!serverPlayer.addItem(hardBlood)) {
            serverPlayer.spawnAtLocation(hardBlood, 1);
        }
        serverPlayer.displayClientMessage(
                Component.translatable("tenfold.blood.ritual"), true);
        event.setCanceled(true);
    }

    /* ------------------------------------------------------------------ */
    /* 血魔不能再吃普通食物                                                */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getSide().isClient()) {
            return;
        }
        Player player = event.getEntity();
        if (!BloodDemonManager.isBloodDemon(player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEdible() && !stack.is(ModItems.BLOOD_BOTTLE.get())) {
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
        ItemStack stack = event.getItemStack();
        if (stack.isEdible() && !stack.is(ModItems.BLOOD_BOTTLE.get())) {
            event.setCanceled(true);
        }
    }
}
