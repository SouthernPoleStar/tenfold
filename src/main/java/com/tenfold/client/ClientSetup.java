package com.tenfold.client;

import com.tenfold.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only glue: FOV nudge for the greatsword and a small held-item
 * transform so each form reads differently without a custom renderer.
 */
@Mod.EventBusSubscriber(modid = "tenfold", value = Dist.CLIENT)
public final class ClientSetup {

    /** Registers the Matthias boss renderer: reuses the vanilla zombie renderer. */
    @Mod.EventBusSubscriber(modid = "tenfold", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    private static class ModBus {
        @SubscribeEvent
        public static void onRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(com.tenfold.ModEntities.MATTHIAS.get(),
                    ctx -> new BossZombieRenderer(ctx,
                            new net.minecraft.resources.ResourceLocation("tenfold", "textures/entity/matthias.png")));
            event.registerEntityRenderer(com.tenfold.ModEntities.LEIHENG.get(),
                    ctx -> new BossZombieRenderer(ctx,
                            new net.minecraft.resources.ResourceLocation("tenfold", "textures/entity/leiheng.png")));
            event.registerEntityRenderer(com.tenfold.ModEntities.LIEN.get(),
                    ctx -> new BossZombieRenderer(ctx,
                            new net.minecraft.resources.ResourceLocation("tenfold", "textures/entity/lien.png")));
            event.registerEntityRenderer(com.tenfold.ModEntities.MINION.get(),
                    net.minecraft.client.renderer.entity.ZombieRenderer::new);
        }
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        // Intentionally empty: item models are chosen by the item properties,
        // see TenfoldBladeItem#getModelKey. Kept as the FML hook target.
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.is(ModItems.TENFOLD_BLADE.get())) {
            switch (com.tenfold.item.WeaponData.form(stack)) {
                case GREATSWORD, HAMMER -> event.setNewFovModifier(event.getNewFovModifier() * 0.95F);
                case WHIP, SPEAR -> event.setNewFovModifier(event.getNewFovModifier() * 1.03F);
                default -> {
                }
            }
        }
    }

    private ClientSetup() {
    }
}
