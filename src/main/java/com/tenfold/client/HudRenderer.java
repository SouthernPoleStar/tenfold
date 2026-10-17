package com.tenfold.client;

import com.tenfold.ModItems;
import com.tenfold.Tenfold;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.tenfold.item.WeaponData;
import com.tenfold.item.WeaponForm;
import net.minecraftforge.fml.common.Mod;

/**
 * Compact contract readout above the hotbar: current form, streak pips,
 * breach pips and the degradation timer.
 */
@Mod.EventBusSubscriber(modid = Tenfold.MOD_ID, value = Dist.CLIENT)
public final class HudRenderer {

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.TENFOLD_BLADE.get())) {
            return;
        }

        GuiGraphics gfx = event.getGuiGraphics();
        PoseStack pose = gfx.pose();
        pose.pushPose();

        int screenW = event.getWindow().getGuiScaledWidth();
        int screenH = event.getWindow().getGuiScaledHeight();
        int baseX = screenW / 2 - 90;
        int baseY = screenH - 46;

        WeaponForm form = WeaponData.form(stack);
        var font = mc.font;
        gfx.drawString(font, form.display(), baseX, baseY, 0xFFFFFF, true);

        long now = player.level().getGameTime();
        if (WeaponData.isLocked(stack, now)) {
            gfx.drawString(font, "\u00A7cLOCKED " + WeaponData.lockSecondsLeft(stack, now) + "s",
                    baseX, baseY + 10, 0xFF5555, true);
        } else {
            drawPips(gfx, baseX, baseY + 11, WeaponData.streak(stack), WeaponData.STREAK_GOAL, 0xFFD24A);
            drawPips(gfx, baseX + 42, baseY + 11, WeaponData.breach(stack), WeaponData.BREACH_LIMIT, 0xFF5555);
            if (WeaponData.target(stack) != null) {
                gfx.drawString(font, "\u00A7c\u25CF", baseX + 78, baseY + 10, 0xFF5555, true);
            }
        }

        pose.popPose();
    }

    private static void drawPips(GuiGraphics gfx, int x, int y, int filled, int total, int color) {
        for (int i = 0; i < total; i++) {
            int c = i < filled ? color : 0x55333333;
            gfx.fill(x + i * 6, y, x + i * 6 + 4, y + 4, c);
        }
    }

    private HudRenderer() {
    }
}
