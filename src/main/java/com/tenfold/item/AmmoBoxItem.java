package com.tenfold.item;

import com.tenfold.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 肩挂式弹药箱 — a shoulder-slung ammo box worn in the chest slot.
 * <p>
 * Worn as a chestplate it grants 10 armor points. Holding it in hand and
 * right-clicking looks for another player within five blocks who is holding an
 * empty 句点事务所·狙击枪 and tops both of its magazines back up. You can
 * never refill your own gun with it — the box is for covering your teammates.
 */
public class AmmoBoxItem extends Item {

    public static final double REACH = 5.0D;
    public static final double ARMOR_BONUS = 10.0D;
    private static final UUID ARMOR_UUID = UUID.fromString("7c3d41e8-95b2-4f0a-8d16-2e9a55c0b7a1");

    public AmmoBoxItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /* ------------------------------------------------------------------ */
    /* Chestplate slot: Forge lets plain items occupy equipment slots      */
    /* ------------------------------------------------------------------ */

    @Override
    public net.minecraft.world.entity.EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return net.minecraft.world.entity.EquipmentSlot.CHEST;
    }

    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>
    getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
        com.google.common.collect.ImmutableMultimap.Builder<
                net.minecraft.world.entity.ai.attributes.Attribute,
                net.minecraft.world.entity.ai.attributes.AttributeModifier> builder =
                com.google.common.collect.ImmutableMultimap.builder();
        if (slot == net.minecraft.world.entity.EquipmentSlot.CHEST) {
            builder.put(Attributes.ARMOR, new AttributeModifier(ARMOR_UUID, "tenfold_ammo_box",
                    ARMOR_BONUS, AttributeModifier.Operation.ADDITION));
        }
        return builder.build();
    }

    /* ------------------------------------------------------------------ */
    /* Right-click: refill a teammate's empty sniper                       */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack box = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(box, level.isClientSide());
        }
        ServerPlayer server = (ServerPlayer) player;

        RefillTarget target = findTarget(server);
        if (target == null) {
            server.displayClientMessage(Component.translatable("tenfold.ammobox.no_target")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.consume(box);
        }

        if (target.stack().getItem() instanceof SniperItem) {
            SniperItem.setNormal(target.stack(), SniperItem.NORMAL_MAG);
            SniperItem.setSpecial(target.stack(), SniperItem.SPECIAL_MAG);
        } else {
            SkyBladeItem.refill(target.stack());
        }
        level.playSound(null, target.player().blockPosition(), ModSounds.FORM_SHIFT.get(),
                SoundSource.PLAYERS, 0.9F, 0.8F);
        target.player().displayClientMessage(Component.translatable("tenfold.ammobox.refilled",
                player.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
        server.displayClientMessage(Component.translatable("tenfold.ammobox.gave",
                target.player().getDisplayName()).withStyle(ChatFormatting.GREEN), true);
        return InteractionResultHolder.consume(box);
    }

    /** One refillable weapon held by a nearby player. */
    private record RefillTarget(Player player, ItemStack stack) {
    }

    /** Nearest other player within reach holding an empty sniper / sky blade. */
    @Nullable
    private static RefillTarget findTarget(ServerPlayer user) {
        return user.level().getEntitiesOfClass(Player.class,
                        user.getBoundingBox().inflate(REACH),
                        p -> p != user && p.isAlive() && !p.isSpectator())
                .stream()
                // 先筛出"手上真的有空枪"的人，再按距离取最近 —— 之前只看最近的那个
                // 玩家，最近的人没空枪就直接失败，旁边有空枪的队友反而补给不到。
                .map(AmmoBoxItem::emptyWeaponOf)
                .filter(t -> t != null)
                .min(Comparator.comparingDouble(t -> t.player().distanceToSqr(user)))
                .orElse(null);
    }

    /** 该玩家手上第一把空弹的枪 / 刀，没有则返回 null。 */
    @Nullable
    private static RefillTarget emptyWeaponOf(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SniperItem && SniperItem.isEmpty(stack)) {
                return new RefillTarget(player, stack);
            }
            if (stack.getItem() instanceof SkyBladeItem && SkyBladeItem.isEmpty(stack)) {
                return new RefillTarget(player, stack);
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ */
    /* Tooltip                                                             */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tenfold.ammobox.armor",
                (int) ARMOR_BONUS).withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tenfold.ammobox.desc",
                (int) REACH).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tenfold.ammobox.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
