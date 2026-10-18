package com.tenfold.client;

import com.tenfold.entity.PoeyEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Poey 渲染：直接复用玩家的人形模型，贴图是用户给的 64×64 皮肤。
 * <p>
 * 皮肤是带透明通道的标准 PNG，未使用区域是透明的，所以帽子层（头发/外层）可以正常显示。
 */
public class PoeyRenderer extends MobRenderer<PoeyEntity, HumanoidModel<PoeyEntity>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tenfold", "textures/entity/poey.png");

    public PoeyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(PoeyEntity entity) {
        return TEXTURE;
    }
}
