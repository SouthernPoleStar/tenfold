package com.tenfold.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.tenfold.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 莱万丁 —— 一把被"包装纸"层层裹住的剑。
 * <p>
 * 四个阶段：铁皮+铁链(10) -> 铁皮(15) -> 露出发光刀刃(20) -> 全剑发亮(25)。
 * 每个阶段需要先攻击若干次，随后潜行+右键"拆开包装纸"进入下一阶段。
 * 第三阶段起攻击附带灼烧，第四阶段有 15% 几率让自己也着火。
 * <p>
 * 阶段与攻击计数都存在物品 NBT 上；外观通过 CustomModelData 1~4 切换贴图。
 */
public class LevandinItem extends SwordItem {

    private static final String ROOT = "levandin";
    private static final UUID ATTACK_DAMAGE_UUID =
            UUID.fromString("D3A17C50-91B2-4C6E-8F04-5A2B7E10D001");
    private static final UUID ATTACK_SPEED_UUID =
            UUID.fromString("D3A17C50-91B2-4C6E-8F04-5A2B7E10D002");

    public LevandinItem() {
        super(Tiers.DIAMOND, 3, -2.4F, new Item.Properties().durability(2500));
    }

    /* ------------------------------------------------------------------ */
    /* 阶段定义                                                             */
    /* ------------------------------------------------------------------ */

    public enum Stage {
        /** 缠着铁皮和铁链的长剑。 */
        WRAPPED(0, 10.0F, 1, false, false, ChatFormatting.GRAY),
        /** 拆掉铁链、缠着铁皮的长剑。 */
        UNCHAINED(1, 15.0F, 2, false, false, ChatFormatting.WHITE),
        /** 铁皮下露出橙色发亮刀刃。 */
        GLOWING_EDGE(2, 20.0F, 3, true, false, ChatFormatting.GOLD),
        /** 橙色发亮的长剑。 */
        BLAZING(3, 25.0F, 0, true, true, ChatFormatting.RED);

        public final int index;
        public final float damage;
        /** 进入下一阶段前需要累计的攻击次数。0 表示已是最终形态。 */
        public final int hitsToUnlock;
        public final boolean burnsTargets;
        public final boolean mayBurnSelf;
        public final ChatFormatting color;

        Stage(int index, float damage, int hitsToUnlock,
              boolean burnsTargets, boolean mayBurnSelf, ChatFormatting color) {
            this.index = index;
            this.damage = damage;
            this.hitsToUnlock = hitsToUnlock;
            this.burnsTargets = burnsTargets;
            this.mayBurnSelf = mayBurnSelf;
            this.color = color;
        }

        public boolean isFinal() {
            return hitsToUnlock <= 0;
        }

        public Stage next() {
            return index >= BLAZING.index ? BLAZING : VALUES[index + 1];
        }

        public static final Stage[] VALUES = values();

        public static Stage of(int index) {
            if (index < 0 || index >= VALUES.length) {
                return WRAPPED;
            }
            return VALUES[index];
        }
    }

    /* ------------------------------------------------------------------ */
    /* NBT 读写                                                             */
    /* ------------------------------------------------------------------ */

    private static CompoundTag root(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(ROOT, CompoundTag.TAG_COMPOUND)) {
            tag.put(ROOT, new CompoundTag());
        }
        return tag.getCompound(ROOT);
    }

    public static Stage stage(ItemStack stack) {
        return Stage.of(root(stack).getInt("stage"));
    }

    public static int hits(ItemStack stack) {
        return root(stack).getInt("hits");
    }

    private static void setHits(ItemStack stack, int value) {
        root(stack).putInt("hits", Math.max(0, value));
    }

    /** 把当前阶段同步进 CustomModelData，客户端据此换贴图。 */
    private static void syncModel(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        int want = stage(stack).index + 1;
        int current = tag.contains("CustomModelData", CompoundTag.TAG_INT) ? tag.getInt("CustomModelData") : -1;
        if (current != want) {
            tag.putInt("CustomModelData", want);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        syncModel(stack);
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    /* ------------------------------------------------------------------ */
    /* 属性                                                                 */
    /* ------------------------------------------------------------------ */

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.MAINHAND) {
            return base;
        }
        Stage stage = stage(stack);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        base.forEach((attribute, modifier) -> {
            if (attribute != Attributes.ATTACK_DAMAGE && attribute != Attributes.ATTACK_SPEED) {
                builder.put(attribute, modifier);
            }
        });
        // ATTACK_DAMAGE 显示值 = 1(徒手) + 修正值，所以减 1 才能正好落在 stage.damage 上。
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                ATTACK_DAMAGE_UUID, "Levandin modifier",
                stage.damage - 1.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_UUID, "Levandin modifier",
                -2.4F, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    /* ------------------------------------------------------------------ */
    /* 交互：潜行+右键 = 拆开包装纸                                          */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            tryUnwrap(level, player, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown() && context.getHand() == InteractionHand.MAIN_HAND) {
            if (!context.getLevel().isClientSide) {
                tryUnwrap(context.getLevel(), player, player.getItemInHand(context.getHand()));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void tryUnwrap(Level level, Player player, ItemStack stack) {
        Stage stage = stage(stack);
        if (stage.isFinal()) {
            player.displayClientMessage(Component.translatable("levandin.unwrap.final")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int hits = hits(stack);
        if (hits < stage.hitsToUnlock) {
            player.displayClientMessage(Component.translatable(
                            "levandin.unwrap.need", stage.hitsToUnlock - hits)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        Stage next = stage.next();
        root(stack).putInt("stage", next.index);
        setHits(stack, 0);
        syncModel(stack);
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        player.displayClientMessage(Component.translatable(
                        "levandin.unwrap.done", stage.index + 1, next.index + 1)
                .withStyle(next.color), false);
    }

    /* ------------------------------------------------------------------ */
    /* 战斗                                                                 */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (attacker.level().isClientSide) {
            return result;
        }
        Stage stage = stage(stack);

        // 计数：只有尚未解锁下一阶段的阶段才累计攻击次数
        if (!stage.isFinal()) {
            setHits(stack, hits(stack) + 1);
        }

        // 第三阶段起：灼烧敌人
        if (stage.burnsTargets) {
            target.setSecondsOnFire(4);
        }

        // 第四阶段：15% 几率引火烧身
        if (stage.mayBurnSelf && attacker.getRandom().nextFloat() < 0.15F) {
            attacker.setSecondsOnFire(4);
            if (attacker instanceof Player player) {
                player.displayClientMessage(Component.translatable("levandin.self_burn")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
        return result;
    }

    /* ------------------------------------------------------------------ */
    /* 提示                                                                 */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Stage stage = stage(stack);
        tooltip.add(Component.literal("\u25C6 ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("levandin.stage." + stage.index).withStyle(stage.color)));
        tooltip.add(Component.translatable("levandin.stage." + stage.index + ".desc")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.tenfold.damage",
                        String.format(Locale.ROOT, "%.1f", stage.damage))
                .withStyle(ChatFormatting.RED));
        if (stage.burnsTargets) {
            tooltip.add(Component.translatable("levandin.tooltip.burn")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (stage.mayBurnSelf) {
            tooltip.add(Component.translatable("levandin.tooltip.self_burn")
                    .withStyle(ChatFormatting.DARK_RED));
        }
        if (!stage.isFinal()) {
            int hits = hits(stack);
            tooltip.add(Component.translatable("levandin.tooltip.progress",
                            hits, stage.hitsToUnlock)
                    .withStyle(hits >= stage.hitsToUnlock ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("levandin.tooltip.help")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(net.minecraft.world.item.Items.IRON_INGOT);
    }

    public static boolean isLevandin(ItemStack stack) {
        return stack.is(ModItems.LEVANDIN.get());
    }

    @Override
    public @Nullable String getCreatorModId(ItemStack itemStack) {
        return com.tenfold.Tenfold.MOD_ID;
    }
}
