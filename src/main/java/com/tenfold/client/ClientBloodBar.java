package com.tenfold.client;

import com.tenfold.ModEffects;
import com.tenfold.Tenfold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 血魔的饱食度条替换为「吸血条」：取消原版饥饿条渲染，
 * 在原位画一条血红色的槽（每格 2 点，即半颗"血食"）。
 * 客户端用「恐水症」效果判断玩家是不是血魔（该效果永远与血魔同在）。
 */
@Mod.EventBusSubscriber(modid = Tenfold.MOD_ID, value = Dist.CLIENT)
public final class ClientBloodBar {

    private static final ResourceLocation FULL =
            new ResourceLocation("tenfold", "textures/gui/blood_icon_full.png");
    private static final ResourceLocation EMPTY =
            new ResourceLocation("tenfold", "textures/gui/blood_icon_empty.png");

    private static boolean isBloodDemon(LocalPlayer player) {
        return player != null && player.hasEffect(ModEffects.HYDROPHOBIA.get());
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()
                && isBloodDemon(Minecraft.getInstance().player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.FOOD_LEVEL.type()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (!isBloodDemon(player)) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        int right = screenWidth / 2 + 91;
        int top = screenHeight - 39;
        int blood = player.getFoodData().getFoodLevel();

        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            graphics.blit(i * 2 < blood ? FULL : EMPTY, x, top, 0, 0, 16, 16, 16, 16);
        }
    }

    private ClientBloodBar() {
    }
}
