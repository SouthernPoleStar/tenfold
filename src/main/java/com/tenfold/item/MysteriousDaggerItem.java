package com.tenfold.item;

import com.tenfold.ModEffects;
import com.tenfold.entity.PoeyEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 神秘的匕首：恶魂之泪 + 木棍合成。
 * <p>
 * 平时伤害 4；对 **Poey** 的伤害 +1000%（×11），并在命中时给 Poey 挂上「抛弃」。
 * 带着「抛弃」死去的 Poey 会变成 Poee（转化逻辑在 {@code PoeeEvents}）。
 */
public class MysteriousDaggerItem extends SwordItem {

    public static final float BASE_DAMAGE = 4.0F;
    /** 对 Poey 的伤害倍率：+1000% → ×11。 */
    public static final float POEY_DAMAGE_MULTIPLIER = 11.0F;
    /** 抛弃持续时间：10 分钟，每次命中刷新。 */
    public static final int ABANDONED_TICKS = 20 * 60 * 10;

    public MysteriousDaggerItem() {
        // 最终伤害 = 1 + 修正值，所以填 3 得到 4。
        super(Tiers.IRON, 3, -1.6F, new net.minecraft.world.item.Item.Properties().durability(300));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide && target instanceof PoeyEntity) {
            target.addEffect(new MobEffectInstance(ModEffects.ABANDONED.get(),
                    ABANDONED_TICKS, 0, false, true, true));
            if (attacker instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("tenfold.dagger.abandoned")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tenfold.dagger.damage",
                String.format("%.0f", BASE_DAMAGE)).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.tenfold.dagger.poey",
                String.format("%.0f", (POEY_DAMAGE_MULTIPLIER - 1) * 100)).withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.tenfold.dagger.abandoned")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public @Nullable String getCreatorModId(ItemStack itemStack) {
        return com.tenfold.Tenfold.MOD_ID;
    }
}
