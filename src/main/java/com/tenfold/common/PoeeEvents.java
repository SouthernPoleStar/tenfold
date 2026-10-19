package com.tenfold.common;

import com.tenfold.ModEffects;
import com.tenfold.ModItems;
import com.tenfold.entity.PoeeEntity;
import com.tenfold.entity.PoeyEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Poee 相关的事件：
 * <ul>
 *   <li>神秘的匕首对 Poey 伤害 ×11。</li>
 *   <li>带着「抛弃」死去的 Poey → 变成 Poee。</li>
 *   <li>「我是 Poey」：受伤 +100%、造成伤害 -100%、禁止回血。</li>
 *   <li>Poee 被击杀：聊天播报两句遗言 + 发放进度「你都做了什么？！」。</li>
 * </ul>
 */
public class PoeeEvents {

    /** 匕首对 Poey 的伤害倍率。 */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        // 「我是 Poey」：受伤翻倍
        if (event.getEntity().hasEffect(ModEffects.I_AM_POEY.get())) {
            event.setAmount(event.getAmount() * 2.0F);
        }
        // 「我是 Poey」：造成伤害 -100%
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity striker
                && striker.hasEffect(ModEffects.I_AM_POEY.get())) {
            event.setAmount(0.0F);
            return;
        }
        // 神秘的匕首打 Poey：+1000%
        if (event.getEntity() instanceof PoeyEntity
                && event.getSource().getEntity() instanceof Player player
                && player.getMainHandItem().is(ModItems.MYSTERIOUS_DAGGER.get())) {
            event.setAmount(event.getAmount() * 11.0F);
        }
    }

    /** 「我是 Poey」期间禁止任何回血。 */
    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(ModEffects.I_AM_POEY.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        // Poey + 抛弃 → 变成 Poee
        if (event.getEntity() instanceof PoeyEntity poey
                && poey.hasEffect(ModEffects.ABANDONED.get())) {
            transformToPoee(poey);
            return;
        }
        // Poee 死亡：遗言 + 进度
        if (event.getEntity() instanceof PoeeEntity poee) {
            onPoeeKilled(poee, event.getSource());
        }
    }

    private void transformToPoee(PoeyEntity poey) {
        if (!(poey.level() instanceof ServerLevel level)) {
            return;
        }
        PoeeEntity poee = com.tenfold.ModEntities.POEE.get().create(level);
        if (poee == null) {
            return;
        }
        poee.moveTo(poey.getX(), poey.getY(), poey.getZ(), poey.getYRot(), poey.getXRot());
        poee.setHealth((float) PoeeEntity.MAX_HP);
        poee.addEffect(new MobEffectInstance(ModEffects.ABANDONED.get(), -1, 0, false, false, false));
        level.addFreshEntity(poee);
        poey.discard();

        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                poey.getX(), poey.getY() + 1.0D, poey.getZ(), 80, 0.6D, 1.2D, 0.6D, 0.03D);
        level.playSound(null, poey.blockPosition(), net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.5F);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("tenfold.poee.transformed").withStyle(ChatFormatting.DARK_PURPLE),
                false);
    }

    private void onPoeeKilled(PoeeEntity poee, net.minecraft.world.damagesource.DamageSource source) {
        if (!(poee.level() instanceof ServerLevel level)) {
            return;
        }
        var server = level.getServer();
        if (server == null) {
            return;
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("<Poee> ").append(Component.translatable("tenfold.poee.death1"))
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("<Poee> ").append(Component.translatable("tenfold.poee.death2"))
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        if (source.getEntity() instanceof ServerPlayer killer) {
            ModAdvancements.award(killer, ModAdvancements.POEE_KILLED);
        }
    }
}
