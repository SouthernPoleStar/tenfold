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
 * 血魔的饱食度条替换为「吸血条」：取消原版饥饿条渲染，并在同一位置自己画一条血红色槽。
 * <p>
 * <b>必须在 Pre 事件里直接画</b>：Forge 的 {@code ForgeGui.render} 在 Pre 被取消后
 * 会立刻 {@code return}，Post 事件根本不会触发，所以"Pre 取消 + Post 绘制"永远画不出东西。
 * <p>
 * 客户端用「恐水症」效果或任意一代「x代血魔」效果判断玩家是不是血魔（两者都会随效果同步）。
 */
@Mod.EventBusSubscriber(modid = Tenfold.MOD_ID, value = Dist.CLIENT)
public final class ClientBloodBar {

    private static final ResourceLocation FOOD_OVERLAY_ID = VanillaGuiOverlay.FOOD_LEVEL.id();
    private static final ResourceLocation FULL =
            new ResourceLocation("tenfold", "textures/gui/blood_icon_full.png");
    private static final ResourceLocation EMPTY =
            new ResourceLocation("tenfold", "textures/gui/blood_icon_empty.png");

    private static boolean isFoodOverlay(RenderGuiOverlayEvent event) {
        return event.getOverlay() != null
                && FOOD_OVERLAY_ID.equals(event.getOverlay().id());
    }

    private static boolean isBloodDemon(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.hasEffect(ModEffects.HYDROPHOBIA.get())) {
            return true;
        }
        for (int generation = 1; generation <= 10; generation++) {
            if (player.hasEffect(ModEffects.bloodGenEffect(generation))) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!isFoodOverlay(event)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (!isBloodDemon(player)) {
            return;
        }
        // 先画我们的吸血条，再取消原版饥饿条（取消后本 overlay 不会继续渲染，也不会触发 Post）。
        drawBloodBar(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(), player.getFoodData().getFoodLevel());
        event.setCanceled(true);
    }

    private static void drawBloodBar(GuiGraphics graphics, int screenWidth, int screenHeight, int blood) {
        int right = screenWidth / 2 + 91;
        int top = screenHeight - 39;
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            // 纯色格子兜底：即使贴图出问题也能看到血条。
            graphics.fill(x, top, x + 8, top + 9, 0x60200000);
            if (i * 2 < blood) {
                graphics.fill(x + 1, top + 1, x + 8, top + 8, 0xE0C01C1C);
            }
            graphics.blit(i * 2 < blood ? FULL : EMPTY, x, top, 0, 0, 9, 9, 9, 9);
        }
    }

    private ClientBloodBar() {
    }
}
