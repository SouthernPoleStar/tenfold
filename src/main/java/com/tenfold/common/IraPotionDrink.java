package com.tenfold.common;

import com.tenfold.ModPotions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Drinking the IRA potion.
 * <p>
 * A plain {@code minecraft:potion} carrying the {@code tenfold:ira_release}
 * potion already works out of the box. This handler adds the one thing vanilla
 * has no hook for: the "守护他人的决意" advancement on the first drink.
 * <p>
 * The effect is applied here explicitly rather than relying on the item's own
 * food component, so the milestone and the buff always happen together.
 */
public final class IraPotionDrink {

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.POTION)) {
            return;
        }
        var potion = PotionUtils.getPotion(stack);
        if (potion != ModPotions.IRA_RELEASE.get() && potion != ModPotions.LONG_IRA_RELEASE.get()) {
            return;
        }
        if (!(player instanceof ServerPlayer server)) {
            return;
        }

        for (MobEffectInstance effect : PotionUtils.getMobEffects(stack)) {
            server.addEffect(new MobEffectInstance(effect.getEffect(), effect.getDuration(),
                    effect.getAmplifier(), false, true, true));
        }
        server.level().playSound(null, server.getX(), server.getY(), server.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.5F,
                0.4F / (server.level().getRandom().nextFloat() * 0.4F + 0.8F));

        ContractTriggers.fire(ContractTriggers.OATH_DRUNK, server, 1);

        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        ItemStack held = server.getItemInHand(event.getHand());
        held.shrink(1);
        if (held.isEmpty()) {
            server.setItemInHand(event.getHand(), bottle);
        } else if (!server.getInventory().add(bottle)) {
            server.drop(bottle, false);
        }

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    public IraPotionDrink() {
    }
}
