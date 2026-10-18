package com.tenfold.common;

import com.tenfold.ModItems;
import com.tenfold.ModPotions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Brewing: Awkward Potion + Oath Shard → IRA Full Release, Redstone extends it.
 * <p>
 * 1.20.1 keeps brewing recipes in a private static list inside
 * {@code PotionBrewing}; there is no registry and no recipe JSON type, so the
 * private {@code addMix(Potion, Item, Potion)} is invoked once after vanilla
 * {@code bootStrap()}.
 */
public final class IraBrewing {

    private static final Logger LOG = LoggerFactory.getLogger("Tenfold");

    public static void register() {
        try {
            Method addMix = findAddMix();
            if (addMix == null) {
                LOG.error("PotionBrewing#addMix not found; IRA potion will not be brewable");
                return;
            }
            addMix.setAccessible(true);
            addMix.invoke(null, Potions.AWKWARD, ModItems.OATH_SHARD.get(),
                    ModPotions.IRA_RELEASE.get());
            addMix.invoke(null, ModPotions.IRA_RELEASE.get(), Items.REDSTONE,
                    ModPotions.LONG_IRA_RELEASE.get());
            // 不再读取 PotionBrewing 的私有字段来打日志：正式包里的字段名是 SRG
            // （f_43494_），按 "POTION_MIXES" 取会抛 NoSuchFieldException，
            // 把已经成功的注册误报成失败（玩家日志里的那个 ERROR 就是它）。
            LOG.info("IRA brewing registered: awkward + oath_shard -> ira_release");
        } catch (Exception e) {
            LOG.error("Failed to register the IRA brewing recipe", e);
        }
    }

    private static Method findAddMix() {
        for (Method method : PotionBrewing.class.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 3 && params[0] == Potion.class && params[1] == Item.class
                    && params[2] == Potion.class) {
                return method;
            }
        }
        return null;
    }

    private IraBrewing() {
    }
}
