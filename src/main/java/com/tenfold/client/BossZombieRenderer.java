package com.tenfold.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/** Vanilla zombie model, boss-specific texture. */
public class BossZombieRenderer extends ZombieRenderer {

    private final ResourceLocation texture;

    public BossZombieRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
        super(context);
        this.texture = texture;
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return this.texture;
    }
}
