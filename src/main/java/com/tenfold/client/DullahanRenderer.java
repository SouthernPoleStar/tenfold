package com.tenfold.client;

import com.tenfold.Tenfold;
import com.tenfold.entity.DullahanEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 杜拉奇渲染：沿用原版马模型与贴图坐标，但用 {@link HeadlessHorseModel} 去掉头颅。
 */
public class DullahanRenderer extends AbstractHorseRenderer<DullahanEntity, HeadlessHorseModel<DullahanEntity>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Tenfold.MOD_ID, "textures/entity/dullahan.png");

    public DullahanRenderer(EntityRendererProvider.Context context) {
        super(context, new HeadlessHorseModel<>(context.bakeLayer(ModelLayers.HORSE)), 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(DullahanEntity entity) {
        return TEXTURE;
    }
}
