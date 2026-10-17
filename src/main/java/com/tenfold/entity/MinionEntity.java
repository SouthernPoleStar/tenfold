package com.tenfold.entity;

import com.tenfold.ModItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 雷横's minion — a baby zombie wearing a 弹药箱.
 * <p>
 * Lives for exactly 3 seconds (60 ticks). Killed in time, it curses its
 * master with two levels of weakness; outlasted, it fades away and grants
 * Lei Heng two extra stacks of Strength.
 */
public class MinionEntity extends Zombie {

    /** 3 seconds of life. */
    public static final int LIFETIME_TICKS = 60;

    @Nullable
    private UUID hostId = null;

    public MinionEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setBaby(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D);
    }

    public void setHost(LeiHengEntity host) {
        this.hostId = host.getUUID();
    }

    @Nullable
    private LeiHengEntity host() {
        if (this.hostId == null || !(this.level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return null;
        }
        return server.getEntity(this.hostId) instanceof LeiHengEntity h && h.isAlive() ? h : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        // Wear the ammo box from birth.
        if (!this.hasItemInSlot(EquipmentSlot.CHEST)) {
            this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.AMMO_BOX.get()));
        }
        LeiHengEntity master = host();
        if (master == null) {
            // Master gone: no point lingering.
            this.discard();
            return;
        }
        if (this.tickCount >= LIFETIME_TICKS) {
            master.onMinionTimedOut();
            this.discard();
        }
    }

    @Override
    public void die(DamageSource source) {
        LeiHengEntity master = host();
        if (!this.level().isClientSide && master != null && this.tickCount < LIFETIME_TICKS) {
            master.onMinionSlain();
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.hostId != null) {
            tag.putUUID("Host", this.hostId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Host")) {
            this.hostId = tag.getUUID("Host");
        }
    }
}
