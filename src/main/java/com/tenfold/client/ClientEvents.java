package com.tenfold.client;

import com.tenfold.ModItems;
import com.tenfold.item.WeaponData;
import com.tenfold.item.WeaponForm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side input handling for the blade.
 * <p>
 * Sneak + right click switches the form locally for instant feedback, then the
 * same click travels to the server, which performs the authoritative switch and
 * syncs the stack back. Plain right click is left untouched so the server can
 * issue the contract.
 */
@Mod.EventBusSubscriber(modid = "tenfold", value = Dist.CLIENT)
public final class ClientEvents {

    /**
     * Never cancel the interaction: the server needs the packet to run the
     * contract / form-switch logic. This hook only provides immediate visual
     * feedback (swing animation) so sneaking right click does not look dead
     * while waiting for the server round trip.
     */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.TENFOLD_BLADE.get())) {
            return;
        }
        if (player.isShiftKeyDown()) {
            // Optimistic local switch so the texture changes on the same frame.
            // The server repeats it and overwrites the tag, so both sides agree
            // even if this prediction was wrong (e.g. blade is locked).
            cycleLocal(player, stack);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    /**
     * Mirrors {@code ContractManager.cycleForm} on the client stack.
     * Only touches NBT; no world access, so it is safe before the packet lands.
     */
    private static void cycleLocal(LocalPlayer player, ItemStack stack) {
        long now = player.level().getGameTime();
        if (WeaponData.isLocked(stack, now)) {
            return;
        }
        // 未成形的双蛇杖不参与形态循环：等服务器那边潜行+右键成形即可。
        if (!WeaponData.isFormed(stack)) {
            return;
        }
        WeaponForm next = WeaponData.form(stack).next();
        WeaponData.setForm(stack, next);
    }

    private ClientEvents() {
    }
}
