package com.tenfold.common;

import com.tenfold.ModEffects;
import com.tenfold.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 狂猎套装与订婚戒指的事件端：
 * <ul>
 *   <li>穿戴棺材 + 手持大剑时维护「撕裂的心」与属性加成（{@link WildHuntManager}）。</li>
 *   <li>撕裂的心：造成伤害 +50%、受到伤害 +10%。</li>
 *   <li>订婚戒指：两人各持戒指，右键对方结为夫妻；一方死亡 40% 掉落两个残片。</li>
 * </ul>
 */
public class WildHuntEvents {

    /* ------------------------------------------------------------------ */
    /* 套装状态维护                                                        */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 == 0) {
            WildHuntManager.tick(player);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 撕裂的心：伤害倍率                                                  */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity striker
                && striker.hasEffect(ModEffects.TORN_HEART.get())) {
            event.setAmount(event.getAmount() * 1.5F);
        }
        if (event.getEntity().hasEffect(ModEffects.TORN_HEART.get())) {
            event.setAmount(event.getAmount() * 1.1F);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 订婚戒指：右键另一名持戒玩家结为夫妻                                 */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getSide().isClient()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer target) || target == player) {
            return;
        }
        if (!holdsRing(player)) {
            return;
        }
        if (!holdsRing(target)) {
            player.displayClientMessage(Component.translatable("tenfold.marriage.target_no_ring"), true);
            event.setCanceled(true);
            return;
        }
        MarriageManager.marry(player, target);
        event.setCanceled(true);
    }

    private static boolean holdsRing(Player player) {
        return player.getMainHandItem().is(ModItems.ENGAGEMENT_RING.get())
                || player.getOffhandItem().is(ModItems.ENGAGEMENT_RING.get());
    }

    /* ------------------------------------------------------------------ */
    /* 配偶死亡：40% 掉落两个残片                                          */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer dead) || dead.level().isClientSide()) {
            return;
        }
        java.util.UUID spouse = MarriageManager.spouseOf(dead);
        if (spouse == null) {
            return;
        }
        if (dead.getRandom().nextFloat() >= MarriageManager.FRAGMENT_DROP_CHANCE) {
            return;
        }
        net.minecraft.world.level.block.Block.popResource(dead.level(), dead.blockPosition(),
                new ItemStack(MarriageManager.fragmentItem(), 2));

        ServerPlayer partner = dead.getServer() == null ? null
                : dead.getServer().getPlayerList().getPlayer(spouse);
        if (partner != null) {
            partner.displayClientMessage(Component.translatable("tenfold.marriage.fragment_drop",
                    dead.getName().getString()), false);
            ModAdvancements.award(partner, ModAdvancements.MARRIAGE_FRAGMENT);
            // 伴侣死亡 + 手持关刀 → 无视情感与血量，强制进入 EGO 绽放
            if (partner.getMainHandItem().is(ModItems.GUAN_DAO.get())) {
                EgoManager.forceEnter(partner);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* 夫妻关系跨重生保留                                                   */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        MarriageManager.copyAcrossRespawn(event.getOriginal(), event.getEntity());
    }
}
