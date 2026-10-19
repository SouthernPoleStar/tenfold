package com.tenfold.item;

import com.tenfold.common.EgoManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 关刀：常态攻击力 **15**，命中使目标**灼烧 4 秒**，并给自己叠 1 层「情感」（最高 10 层）。
 * <p>
 * 情感满 10 层且自身血量低于一半时 **Shift+右键** 进入「EGO 绽放：亢龙有悔」，
 * 期间攻击力 **+40**、攻击不再叠情感（状态由 {@link EgoManager} 维护）。
 */
public class GuanDaoItem extends SwordItem {

    public static final float BASE_DAMAGE = 15.0F;
    public static final int BURN_TICKS = 20 * 4;

    public GuanDaoItem() {
        // SwordItem 的最终伤害 = 1 + 修正值，所以填 14 得到 15。
        super(Tiers.NETHERITE, 14, -2.7F, new net.minecraft.world.item.Item.Properties().durability(2800));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide && attacker instanceof ServerPlayer player) {
            target.setSecondsOnFire(4);
            EgoManager.addGlaiveEmotion(player);
        }
        return result;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && player.isShiftKeyDown()) {
            EgoManager.tryEnter(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        use(context.getLevel(), player, context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tenfold.guan_dao.damage",
                String.format("%.0f", BASE_DAMAGE)).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.tenfold.guan_dao.burn")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.tenfold.guan_dao.emotion")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.tenfold.guan_dao.ego",
                String.format("%.0f", EgoManager.EGO_BLADE_BONUS)).withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public @Nullable String getCreatorModId(ItemStack itemStack) {
        return com.tenfold.Tenfold.MOD_ID;
    }
}
