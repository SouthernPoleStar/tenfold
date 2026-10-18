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
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

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
    /** 血量降到最大值的 2/3（原设计 5000/7500）时摘下面具、进入二阶段。 */
    public static final float UNMASK_RATIO = 2.0F / 3.0F;
    private static final int SWAP_INTERVAL = 20 * 10;

    private boolean unmasked = false;

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

    /* ------------------------------------------------------------------ */
    /* Spawning                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData, tag);
        // 刷怪蛋 / /summon / 自然生成统一按满血出生。
        this.setHealth(this.getMaxHealth());
        return result;
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
        // 里恩不真的把假面穿在头盔槽上：假面贴图是全透明的，穿上去只会多一层
        // 盔甲渲染（旧版还会因为材质名缺命名空间而渲染成黑紫棋盘头套）。
        // "戴假面 / 摘假面" 现在只是 unmasked 标记，不影响任何装备槽。
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

    /** At 2/3 HP: tear the mask off, gain one round of Contract Addiction. */
    private void checkUnmask() {
        // 前 5 秒不判定：生成瞬间的血量读数可能还是脏的。
        if (this.unmasked || this.tickCount < 100 || this.getHealth() <= 0.0F) {
            return;
        }
        // 比例阈值：即使 max_health 被原版 1024 上限压过，也落在同样的血量百分比上。
        if (this.getHealth() > this.getMaxHealth() * UNMASK_RATIO) {
            return;
        }
        this.unmasked = true;
        // 一阶段他与玩家一样，可以正常获得「指令成瘾」（不再被 canBeAffected
        // 拒绝）；二阶段额外获得一次，持续 30 秒。
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
        // 标记"血量已经迁移过"，避免每次读档都把低血量误判成旧版本 clamp 损坏。
        tag.putBoolean("HealthMigrated", true);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.unmasked = tag.getBoolean("LienUnmasked");
        // 旧存档的 max_health 曾被原版 1024 上限压过，血量也跟着被压到 1024；
        // 只在"第一次读档且没有迁移标记"时重置一次，避免战斗中重载区块无限回血。
        if (!tag.getBoolean("HealthMigrated")
                && this.getHealth() <= 1024.0F && this.getMaxHealth() > 1024.0F) {
            this.setHealth(this.getMaxHealth());
        }
        // 旧存档里里恩可能真的穿着假面；现在统一不戴，避免任何头部盔甲层渲染。
        if (this.hasItemInSlot(EquipmentSlot.HEAD)) {
            this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }
}
