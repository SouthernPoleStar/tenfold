package com.tenfold.common;

import com.tenfold.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Oath Shard drop: when a tamed creature is killed by a hostile mob, it leaves
 * behind one "守护他人的决意".
 * <p>
 * Rules:
 * <ul>
 * <li>The victim must be tamed ({@link TamableAnimal#isTame()}).</li>
 * <li>The killer must be a hostile mob ({@link Enemy}), not a player and not
 * the environment. Lava, fall damage and suffocation drop nothing.</li>
 * <li>The owner, if online, gets a message so the loss is not silent.</li>
 * </ul>
 */
public final class OathDropEvent {

    @SubscribeEvent
    public void onTamedDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof TamableAnimal tamed) || !tamed.isTame()) {
            return;
        }
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }

        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof Enemy)) {
            return;
        }

        ItemEntity drop = new ItemEntity(level, victim.getX(), victim.getY() + 0.4D, victim.getZ(),
                new ItemStack(ModItems.OATH_SHARD.get()));
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);

        if (tamed.getOwnerUUID() != null) {
            Player owner = level.getPlayerByUUID(tamed.getOwnerUUID());
            if (owner != null) {
                owner.displayClientMessage(Component.translatable("tenfold.oath.dropped",
                        victim.getDisplayName()).withStyle(ChatFormatting.RED), false);
            }
        }
    }

    public OathDropEvent() {
    }
}
