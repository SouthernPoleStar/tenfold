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
 * 血魔的饱食度条替换为「吸血条」：取消原版饥饿条渲染，在原位画一条血红色的槽。
 * <p>
 * 客户端用「恐水症」效果判断玩家是不是血魔（该效果永远与血魔同在）。
 * <p>
 * 匹配 overlay 时用 {@code id()} 而不是 {@code VanillaGuiOverlay.type()}：
 * 后者由 {@code GuiOverlayManager.assignVanillaOverlayTypes()} 延迟赋值，
 * 直接拿它做相等比较可能永远是 false。
 * 绘制时除了贴图图标，还先铺一层纯色格子（不依赖任何贴图资源），
 * 保证即使贴图出问题也能看到血条。
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
        return player != null && player.hasEffect(ModEffects.HYDROPHOBIA.get());
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (isFoodOverlay(event) && isBloodDemon(Minecraft.getInstance().player)) {
            // 原版饥饿条整体不画，位置留给吸血条。
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (!isFoodOverlay(event)) {
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
            // 兜底：纯色格子（不依赖贴图），先画暗底再画填充。
            graphics.fill(x, top, x + 8, top + 9, 0x60200000);
            if (i * 2 < blood) {
                graphics.fill(x + 1, top + 1, x + 8, top + 8, 0xE0C01C1C);
            }
            // 贴图图标（存在就叠加在上层，更接近原版观感）。
            graphics.blit(i * 2 < blood ? FULL : EMPTY, x, top, 0, 0, 9, 9, 9, 9);
        }
    }

    private ClientBloodBar() {
    }
}
