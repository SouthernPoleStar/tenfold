package com.tenfold.client;

import com.tenfold.entity.PoeeEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Poee 渲染：复用玩家人形模型，贴图是用户提供的 64×64 皮肤。
 */
public class PoeeRenderer extends MobRenderer<PoeeEntity, HumanoidModel<PoeeEntity>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tenfold", "textures/entity/poee.png");

    public PoeeRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(PoeeEntity entity) {
        return TEXTURE;
    }
}
