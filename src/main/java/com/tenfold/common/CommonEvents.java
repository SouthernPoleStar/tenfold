package com.tenfold.common;

import com.tenfold.ModEffects;
import com.tenfold.ModItems;
import com.tenfold.entity.MatthiasEntity;
import com.tenfold.item.WeaponData;
import com.tenfold.item.WeaponForm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-side glue: contract ticking, the rapier ramp and the half-sword block.
 * Damage itself comes from the item's per-form attribute modifiers.
 */
public class CommonEvents {

    private static final java.util.UUID ADDICTION_BONUS =
            java.util.UUID.fromString("6a1c2f10-1e6b-4c33-9a51-8d0f4b1a2c05");

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer server = (ServerPlayer) player;

        if (player.tickCount % 20 == 0) {
            tickContractBlades(server);
        }

        // Contract Addiction: flat +10 damage while the effect lasts.
        syncAddiction(player);

        // IRA Full Release: settle the damage ledger once the effect is gone.
        settleIraDebt(player);

        // 遮盖伤口的假面: keep Strength II and Resistance I topped up while worn.
        syncMask(player);

        // 莱万丁（完全拆封）: low health grants 过人的毅力（我方版）absorption.
        syncLevandinGrit(player);

        // 长剑 · 不稳定的神备：抢装套装与属性维护 / 退出恢复。
        UnstableGearManager.tickPlayer(server);
    }

    /**
     * 契约计时跟着刀走，而不是只跟主手：刀收进背包/副手时，超时同样要判违约，
     * 目标的高亮也不会因为换手而永远挂着。
     */
    private void tickContractBlades(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.TENFOLD_BLADE.get())) {
                ContractManager.tick(player, stack);
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ModItems.TENFOLD_BLADE.get())) {
                ContractManager.tick(player, stack);
            }
        }
    }

    /**
     * 过人的毅力（我方版）: while holding a fully unwrapped (stage 4) Levandin
     * at 30% health or below, grant Absorption worth 200% of the missing
     * health. Refreshed every second while the conditions hold.
     */
    private void syncLevandinGrit(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!com.tenfold.item.LevandinItem.isLevandin(held)
                || com.tenfold.item.LevandinItem.stage(held)
                        != com.tenfold.item.LevandinItem.Stage.BLAZING) {
            return;
        }
        float max = player.getMaxHealth();
        if (max <= 0.0F || player.getHealth() > max * 0.30F) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        float missing = max - player.getHealth();
        // 伤害吸收：每级 4 点（2 颗黄心），需要 2×失去血量 的吸收值
        int points = Math.round(missing * 2.0F);
        int levels = points / 4;
        int amplifier = Math.max(0, Math.min(255, levels - 1));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                ModEffects.PERSEVERANCE_ALLY.get(), 100, amplifier, false, true, true));
    }

    private void syncMask(Player player) {
        if (!player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                .is(com.tenfold.ModItems.MASK.get())) {
            return;
        }
        boolean topUp = player.tickCount % 40 == 0;
        if (topUp) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 60, 1, false, false, false));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 60, 0, false, false, false));
        }
    }

    /**
     * 遮盖伤口的假面 blocks 指令成瘾: any attempt to apply it to a masked
     * player is silently rejected.
     */
    @SubscribeEvent
    public void onEffectApplicable(net.minecraftforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof Player player)
                || event.getEffectInstance() == null
                || event.getEffectInstance().getEffect() != ModEffects.ADDICTION.get()) {
            return;
        }
        if (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                .is(com.tenfold.ModItems.MASK.get())) {
            event.setCanceled(true);
        }
    }

    /** Welcome banner, shown in chat every time a player enters the world. */
    @SubscribeEvent
    public void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String[] keys = {
                "tenfold.welcome.1", "tenfold.welcome.2", "tenfold.welcome.3",
                "tenfold.welcome.4", "tenfold.welcome.5", "tenfold.welcome.6",
                "tenfold.welcome.7", "tenfold.welcome.8", "tenfold.welcome.9"
        };
        for (String key : keys) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key)
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        }
    }

    /**
     * IRA ledger settlement runs from the tick loop instead of the
     * MobEffectEvent.Expired hook: that event only fires when the potion times
     * out naturally, but the debt can also outlive the effect being cleared by
     * milk, death, commands or a shorter re-applied potion. Checking the ledger
     * itself covers every way the effect can end, exactly once.
     */
    private void settleIraDebt(Player player) {
        var data = player.getPersistentData();
        float debt = data.getFloat("tenfoldIraDebt");
        if (debt <= 0.0F) {
            return;
        }
        if (player.hasEffect(ModEffects.IRA_RELEASE.get()) || !player.isAlive()) {
            return;
        }
        data.putFloat("tenfoldIraDebt", 0.0F);
        player.hurt(player.damageSources().magic(), debt / 10.0F);
    }

    /**
     * Applies/suspends the addiction bonus. Rebuilt every tick from the live
     * effect so it also cleans itself up when the buff expires or is milked away.
     */
    private void syncAddiction(Player player) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return;
        }
        boolean active = player.hasEffect(ModEffects.ADDICTION.get());
        boolean present = attack.getModifier(ADDICTION_BONUS) != null;
        if (active && !present) {
            attack.addTransientModifier(new AttributeModifier(ADDICTION_BONUS, "tenfold_addiction",
                    10.0D, AttributeModifier.Operation.ADDITION));
        } else if (!active && present) {
            attack.removeModifier(ADDICTION_BONUS);
        }
    }

    /**
     * Rapier only: consecutive hits within 3 seconds scale the strike.
     * The base number already arrives through the item's attributes, so this
     * multiplies that value rather than adding a fixed amount.
     */
    /**
     * Damage modifiers:
     * <ul>
     * <li>Rapier ramp for the wielder.</li>
     * <li>A creature with the contract-target buff takes +90% damage from the
     * Caduceus.</li>
     * <li>A creature with the contract-target buff deals -20% damage to a
     * player wielding the Caduceus.</li>
     * </ul>
     * The base number already arrives through the item's attributes, so this
     * multiplies that value rather than adding a fixed amount.
     */
    @SubscribeEvent
    public void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        float amount = event.getAmount();

        // Blade wielder striking the contract target: +90%.
        if (event.getSource().getEntity() instanceof Player player
                && player.getMainHandItem().is(ModItems.TENFOLD_BLADE.get())
                && event.getEntity().hasEffect(ModEffects.MARKED.get())) {
            amount *= 1.9F;
        }

        // Contract target striking the wielder: -20%.
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && attacker.hasEffect(ModEffects.MARKED.get())
                && event.getEntity() instanceof Player victim
                && victim.getMainHandItem().is(ModItems.TENFOLD_BLADE.get())) {
            amount *= 0.8F;
        }

        // IRA Full Release: +90% damage dealt by the released player.
        if (event.getSource().getEntity() instanceof Player player
                && player.hasEffect(ModEffects.IRA_RELEASE.get())) {
            amount *= 1.9F;
        }

        // IRA Full Release: -70% damage taken by the released player.
        if (event.getEntity() instanceof Player victim
                && victim.hasEffect(ModEffects.IRA_RELEASE.get())) {
            amount *= 0.3F;
        }

        // 卑贱之物：持有者造成的伤害 -70%，受到的伤害 +110%。
        if (event.getSource().getEntity() instanceof LivingEntity striker
                && striker.hasEffect(ModEffects.ABJECT.get())) {
            amount *= 0.3F;
        }
        if (event.getEntity().hasEffect(ModEffects.ABJECT.get())) {
            amount *= 2.1F;
        }

        // Rapier ramp (wielder bonus, independent of the contract).
        if (event.getSource().getEntity() instanceof Player player) {
            ItemStack held = player.getMainHandItem();
            if (held.is(ModItems.TENFOLD_BLADE.get()) && WeaponData.form(held) == WeaponForm.RAPIER) {
                int bonus = com.tenfold.item.FormAbilities.rapierBonusPercent(player);
                if (bonus > 0) {
                    amount *= 1.0F + bonus / 100.0F;
                }
            }
        }

        // 过人的毅力: the bearer deals double damage.
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && attacker.hasEffect(ModEffects.PERSEVERANCE.get())) {
            amount *= 2.0F;
        }

        // 钢气: +10% damage per stack (amplifier 0 = level 1), capped at 10 levels.
        if (event.getSource().getEntity() instanceof LivingEntity striker) {
            var steel = striker.getEffect(ModEffects.STEEL_QI.get());
            if (steel != null) {
                amount *= 1.0F + 0.1F * (steel.getAmplifier() + 1);
            }
        }

        // 锥子: ignore half the target's armor. This event runs *before* vanilla
        // applies armor, and the vanilla armor formula is not linear in the damage
        // value (low hits get pinned by the reduction floor), so we solve for the
        // raw amount whose post-armor result is exactly "half the reduction".
        if (event.getSource().getEntity() instanceof Player coneWielder
                && coneWielder.getMainHandItem().is(ModItems.TENFOLD_BLADE.get())
                && WeaponData.form(coneWielder.getMainHandItem()) == WeaponForm.CONE) {
            LivingEntity victim = event.getEntity();
            float armor = victim.getArmorValue();
            if (armor > 0.0F) {
                float toughness = (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
                float afterArmor = CombatRules.getDamageAfterAbsorb(amount, armor, toughness);
                if (afterArmor > 0.0F && afterArmor < amount) {
                    float desired = amount - (amount - afterArmor) * 0.5F;
                    amount = rawForPostArmor(desired, armor, toughness);
                }
            }
        }

        // 不稳定的神备：造成伤害 +20%，受到的伤害 +15%。
        if (event.getSource().getEntity() instanceof LivingEntity gearStriker
                && gearStriker.hasEffect(ModEffects.UNSTABLE_GEAR.get())) {
            amount *= 1.2F;
        }
        if (event.getEntity().hasEffect(ModEffects.UNSTABLE_GEAR.get())) {
            amount *= 1.15F;
        }

        // 过人的毅力 is resolved in LivingDamageEvent: the 3-point cap must sit
        // *after* armor and absorption to be the final number that lands.
        event.setAmount(amount);
    }

    /**
     * 求一个原始伤害值，使它在 {@link CombatRules#getDamageAfterAbsorb} 之后正好
     * 等于 {@code desired}。护甲公式分段且带 clamp，直接反解很啰嗦，二分足够快。
     */
    private static float rawForPostArmor(float desired, float armor, float toughness) {
        float lo = 0.0F;
        float hi = Math.max(desired * 8.0F, 1.0F);
        for (int i = 0; i < 16; i++) {
            float mid = (lo + hi) * 0.5F;
            if (CombatRules.getDamageAfterAbsorb(mid, armor, toughness) < desired) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    /**
     * IRA Full Release — immortality + damage ledger.
     * <p>
     * While the effect lasts the player cannot die: any hit that would be
     * lethal is clamped so at least half a heart remains. The full amount of
     * every hit taken (after the -70%) is stored as a debt on the player.
     * When the effect expires the debt settles at one tenth as a single hit.
     */
    @SubscribeEvent
    public void onIraDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)
                || !player.hasEffect(ModEffects.IRA_RELEASE.get())) {
            return;
        }
        float applied = event.getAmount();

        // Log the hit, then keep the player alive.
        var data = player.getPersistentData();
        data.putFloat("tenfoldIraDebt", data.getFloat("tenfoldIraDebt") + applied);
        if (applied >= player.getHealth()) {
            event.setAmount(Math.max(0.0F, player.getHealth() - 1.0F));
        }
    }

    /**
     * 马蒂亚斯的「过人的毅力」。
     * <p>
     * 单次伤害封顶 3 点，但这份毅力有总耐久（{@link MatthiasEntity#GRIT_ABSORPTION}）：
     * 每次受到的伤害都会消耗耐久，耐久被打空的那一刀按真实伤害结算，之后他永久
     * 失去减伤与双倍伤害。这样堆 buff 的爆发流能更快撕开毅力，而不是被无限墙挡住。
     * <p>
     * 放在 {@code LivingDamageEvent}（护甲/吸收结算之后）而不是
     * {@code LivingHurtEvent}，封顶的 3 点才是真正落在他身上的数字。
     */
    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        // 不稳定的神备：每次进入后的第一次致命伤害由一次「不死图腾」抵消。
        if (event.getEntity() instanceof ServerPlayer gearPlayer
                && gearPlayer.hasEffect(ModEffects.UNSTABLE_GEAR.get())
                && event.getAmount() >= gearPlayer.getHealth()
                && UnstableGearManager.handleLethalHit(gearPlayer)) {
            event.setAmount(0.0F);
            return;
        }
        if (!(event.getEntity() instanceof MatthiasEntity matthias)
                || !matthias.hasEffect(ModEffects.PERSEVERANCE.get())) {
            return;
        }
        float raw = event.getAmount();
        if (raw <= 0.0F) {
            return;
        }
        // 耐久还在：这一刀被压到 3；打空的那一刀吃满伤害。
        if (matthias.grindDownGrit(raw)) {
            event.setAmount(Math.min(raw, 3.0F));
        }
    }

    /**
     * 半手剑格挡。
     * <p>
     * 玩家举剑后会进入 vanilla 的 isBlocking 状态，原版把伤害全额挡下并触发本事件。
     * 这里把挡下的部分压到一半（即"受到伤害减半"），再把原伤害的 30% 反弹给攻击者。
     * 剑不是盾牌，所以不让它掉耐久。
     */
    @SubscribeEvent
    public void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack used = player.getUseItem();
        if (!used.is(ModItems.TENFOLD_BLADE.get())
                || WeaponData.form(used) != WeaponForm.HALFSWORD) {
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

    /**
     * Contract bookkeeping has to happen here, not in {@code Item#hurtEnemy}:
     * {@code Player#attack} calls {@code target.hurt(...)} first and
     * {@code ItemStack#hurtEnemy} afterwards, so by the time hurtEnemy runs the
     * creature is already dead and {@code isAlive()} is false.
     * <p>
     * Also clears the glow marker.
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Mob mob) {
            mob.setGlowingTag(false);
        }
        if (victim.level().isClientSide) {
            return;
        }
        // 神备期间死亡：先把进入前保存的护甲还回去，让掉落按原装备结算。
        if (victim instanceof ServerPlayer deadPlayer) {
            UnstableGearManager.onPlayerDeath(deadPlayer);
        }
        // 恶魔契约: plain zombies drop it 30% of the time.
        if (victim.getType() == net.minecraft.world.entity.EntityType.ZOMBIE
                && victim.getRandom().nextFloat() < 0.30F) {
            victim.spawnAtLocation(new net.minecraft.world.item.ItemStack(com.tenfold.ModItems.DEMON_CONTRACT.get()));
        }
        // Only direct melee kills with the blade count; a burning target or an
        // arrow that finishes the job must not complete a contract.
        if (!(event.getSource().getDirectEntity() instanceof Player player)
                || !(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ModItems.TENFOLD_BLADE.get())) {
                ContractManager.onTargetKilled(player, stack, victim);
                return;
            }
        }
    }
}
