package com.tenfold;

import com.tenfold.entity.MatthiasEntity;
import com.tenfold.entity.MinionEntity;
import com.tenfold.entity.LeiHengEntity;
import com.tenfold.entity.LienEntity;
import com.tenfold.entity.PoeeEntity;
import com.tenfold.entity.PoeyEntity;
import com.tenfold.entity.BloodAncestorEntity;
import com.tenfold.entity.DullahanEntity;
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

    /** 雷横: zombie model & texture, 8000 HP, wields the 天退星刀. */
    public static final RegistryObject<EntityType<LeiHengEntity>> LEIHENG =
            ENTITIES.register("leiheng", () -> EntityType.Builder.of(LeiHengEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .build("leiheng"));

    /** 雷横的小弟: baby zombie wearing an ammo box, 3-second timer. */
    public static final RegistryObject<EntityType<MinionEntity>> MINION =
            ENTITIES.register("leiheng_minion", () -> EntityType.Builder.of(MinionEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build("leiheng_minion"));

    /** 里恩: zombie boss wearing the mask, wields the 双蛇杖. */
    public static final RegistryObject<EntityType<LienEntity>> LIEN =
            ENTITIES.register("lien", () -> EntityType.Builder.of(LienEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .build("lien"));

    /** Poey: 友方支援单位，跟随玩家、治疗濒死玩家、给敌人挂「卑贱之物」。 */
    public static final RegistryObject<EntityType<PoeyEntity>> POEY =
            ENTITIES.register("poey", () -> EntityType.Builder.of(PoeyEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("poey"));

    /** 血魔老祖: 一代血魔，自然生成于主世界各处，免疫阳光。 */
    public static final RegistryObject<EntityType<BloodAncestorEntity>> BLOOD_ANCESTOR =
            ENTITIES.register("blood_ancestor", () -> EntityType.Builder.of(BloodAncestorEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .build("blood_ancestor"));

    /** 杜拉奇: 无头坐骑，由狂猎大剑召唤。 */
    public static final RegistryObject<EntityType<DullahanEntity>> DULLAHAN =
            ENTITIES.register("dullahan", () -> EntityType.Builder.of(DullahanEntity::new, MobCategory.CREATURE)
                    .sized(1.3964844F, 1.6F)
                    .clientTrackingRange(10)
                    .build("dullahan"));

    /** Poee: 带着「抛弃」死去的 Poey 变成的敌对 BOSS（5000 血）。 */
    public static final RegistryObject<EntityType<PoeeEntity>> POEE =
            ENTITIES.register("poee", () -> EntityType.Builder.of(PoeeEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.9F)
                    .clientTrackingRange(12)
                    .build("poee"));

    @Mod.EventBusSubscriber(modid = Tenfold.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    private static class AttributeHandler {
        @SubscribeEvent
        public static void onAttributes(EntityAttributeCreationEvent event) {
            event.put(MATTHIAS.get(), MatthiasEntity.createAttributes().build());
            event.put(LEIHENG.get(), LeiHengEntity.createAttributes().build());
            event.put(MINION.get(), MinionEntity.createAttributes().build());
            event.put(LIEN.get(), LienEntity.createAttributes().build());
            event.put(POEY.get(), PoeyEntity.createAttributes().build());
            event.put(BLOOD_ANCESTOR.get(), BloodAncestorEntity.createAttributes().build());
            event.put(DULLAHAN.get(), DullahanEntity.createAttributes().build());
            event.put(POEE.get(), PoeeEntity.createAttributes().build());
        }
    }

    private ModEntities() {
    }
}
