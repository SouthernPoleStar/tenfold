package com.tenfold;

import com.tenfold.entity.MatthiasEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Tenfold.MOD_ID);

    /** 马蒂亚斯: zombie model & texture, 2000 HP, wields the Levandin. */
    public static final RegistryObject<EntityType<MatthiasEntity>> MATTHIAS =
            ENTITIES.register("matthias", () -> EntityType.Builder.of(MatthiasEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("matthias"));

    @Mod.EventBusSubscriber(modid = Tenfold.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    private static class AttributeHandler {
        @SubscribeEvent
        public static void onAttributes(EntityAttributeCreationEvent event) {
            event.put(MATTHIAS.get(), MatthiasEntity.createAttributes().build());
        }
    }

    private ModEntities() {
    }
}
