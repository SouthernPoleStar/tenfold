package com.tenfold;

import com.tenfold.common.ContractTriggers;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Tenfold.MOD_ID)
public class Tenfold {
    public static final String MOD_ID = "tenfold";
    public static final Logger LOGGER = LoggerFactory.getLogger("Tenfold");

    public Tenfold() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(bus);
        ModEffects.EFFECTS.register(bus);
        ModPotions.POTIONS.register(bus);
        ModTabs.TABS.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModEntities.ENTITIES.register(bus);

        bus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(new com.tenfold.common.CommonEvents());
        MinecraftForge.EVENT_BUS.register(new com.tenfold.common.OathDropEvent());
        MinecraftForge.EVENT_BUS.register(new com.tenfold.common.IraPotionDrink());

        // Client-only handlers are registered by their @EventBusSubscriber annotations.
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Custom advancement triggers must be registered before datapacks load.
        event.enqueueWork(() -> {
            // 原版 max_health 上限 1024，不放开的话 8000/7500/2000 血的 Boss 会被压成 1024。
            com.tenfold.common.AttributeLimits.raiseMaxHealthLimit();
            CriteriaTriggers.register(ContractTriggers.CONTRACT_ISSUED);
            CriteriaTriggers.register(ContractTriggers.CONTRACT_DONE);
            CriteriaTriggers.register(ContractTriggers.CONTRACT_BROKEN);
            CriteriaTriggers.register(ContractTriggers.OATH_DRUNK);
            com.tenfold.common.IraBrewing.register();
        });
        LOGGER.info("Caduceus loaded: ten forms, one contract.");
    }

}
