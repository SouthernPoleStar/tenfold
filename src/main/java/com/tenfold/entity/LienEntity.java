package com.tenfold.entity;

import com.tenfold.ModEffects;
import com.tenfold.ModItems;
import com.tenfold.item.WeaponData;
import com.tenfold.item.WeaponForm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 里恩 — a zombie boss wearing the 遮盖伤口的假面 and wielding the 双蛇杖.
 * <p>
 * 7500 HP. He swaps the blade's form every 10 seconds (broadcast to the
 * server). His contracts mark targets with the red glow but never slow them.
 * At 5000 HP he tears the mask off and gains one round of Contract Addiction.
 * Immune to sunlight; carries a boss bar.
 */
public class LienEntity extends Zombie {

    public static final double MAX_HP = 7500.0D;
    public static final float UNMASK_THRESHOLD = 5000.0F;
    private static final int SWAP_INTERVAL = 20 * 10;

    private boolean unmasked = false;
    private boolean addictionPending = false;

    private final net.minecraft.server.level.ServerBossEvent bossEvent =
            new net.minecraft.server.level.ServerBossEvent(this.getDisplayName(),
                    BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    public LienEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D);
    }

    /* ------------------------------------------------------------------ */
    /* Boss bar                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            this.bossEvent.setProgress(Math.max(0.0F, this.getHealth() / this.getMaxHealth()));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Zombie heritage                                                     */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        // Addiction is granted once, by himself, below 5000 HP.
        if (effect.getEffect() == ModEffects.ADDICTION.get() && !this.addictionPending) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    /* ------------------------------------------------------------------ */
    /* Tick                                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        // First spawn: wear the mask (unless it was torn off for good).
        if (!this.unmasked && !this.hasItemInSlot(EquipmentSlot.HEAD)) {
            this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.MASK.get()));
        }
        ItemStack held = this.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(held.getItem() instanceof com.tenfold.item.TenfoldBladeItem)) {
            held = new ItemStack(ModItems.TENFOLD_BLADE.get());
            WeaponData.setForm(held, WeaponForm.GREATSWORD);
            this.setItemSlot(EquipmentSlot.MAINHAND, held);
        }

        // Form swap every 10 seconds, announced server-wide.
        if (this.tickCount % SWAP_INTERVAL == 0) {
            swapForm(held);
        }

        checkUnmask();
    }

    private void swapForm(ItemStack held) {
        WeaponForm next = WeaponForm.of((WeaponData.form(held).index + 1) % WeaponForm.values().length);
        WeaponData.setForm(held, next);
        if (this.level() instanceof ServerLevel server) {
            server.players().forEach(p -> p.sendSystemMessage(
                    Component.translatable("tenfold.lien.swap",
                            this.getDisplayName(),
                            Component.translatable("form.tenfold." + next.key))
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)));
        }
    }

    /** At 5000 HP: tear the mask off, gain one round of Contract Addiction. */
    private void checkUnmask() {
        // Same spawn-window guard as Lei Heng: never judge during the first
        // seconds, and only when the health readout is meaningful.
        if (this.unmasked || this.tickCount < 100
                || this.getHealth() <= 0.0F
                || this.getHealth() >= UNMASK_THRESHOLD
                || this.getHealth() > this.getMaxHealth() * 0.995F) {
            return;
        }
        this.unmasked = true;
        this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        this.addictionPending = true;
        // One round of addiction, then it wears off and can never come back.
        this.addEffect(new MobEffectInstance(ModEffects.ADDICTION.get(), 20 * 30, 0, false, true, true));
        announce("tenfold.lien.unmask");
    }

    /* ------------------------------------------------------------------ */
    /* Combat: mark without slowness                                       */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide && target instanceof LivingEntity living) {
            // The blade's contract: mark + glow, but the mask dulls the curse —
            // no Slowness attached.
            living.addEffect(new MobEffectInstance(ModEffects.MARKED.get(),
                    WeaponData.CONTRACT_TIMEOUT_TICKS, 0, false, false, true));
            living.setGlowingTag(true);
            if (this.level() instanceof ServerLevel server) {
                server.players().forEach(p -> p.sendSystemMessage(
                        Component.translatable("tenfold.lien.contract",
                                this.getDisplayName(), living.getDisplayName())
                                .withStyle(net.minecraft.ChatFormatting.AQUA)));
            }
        }
        return hit;
    }

    private void announce(String key) {
        if (this.level() instanceof ServerLevel server) {
            server.players().forEach(p -> p.sendSystemMessage(
                    Component.translatable(key, this.getDisplayName())
                            .withStyle(net.minecraft.ChatFormatting.GOLD)));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Persistence                                                         */
    /* ------------------------------------------------------------------ */

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("LienUnmasked", unmasked);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.unmasked = tag.getBoolean("LienUnmasked");
        if (this.unmasked) {
            this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }
}
