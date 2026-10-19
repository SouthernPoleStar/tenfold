package com.tenfold.item;

import com.tenfold.ModItems;
import com.tenfold.common.ContractManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The blade. Base attributes are iron sword level; the per-form damage comes
 * from {@link FormAbilities}. Form switching repaints custom model data so the
 * client swaps textures without a custom renderer.
 */
public class TenfoldBladeItem extends SwordItem {

    public static final int MODEL_DATA_OFFSET = 0;
    private static final java.util.UUID BASE_ATTACK_DAMAGE_UUID =
            java.util.UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final java.util.UUID BASE_ATTACK_SPEED_UUID =
            java.util.UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");
    private static final java.util.UUID BASE_ENTITY_REACH_UUID =
            java.util.UUID.fromString("7C0E6E3A-5B1D-4C8F-9A2E-3D4B5C6D7E8F");

    public TenfoldBladeItem() {
        super(Tiers.IRON, 3, -2.4F, new Item.Properties().durability(2031));
    }

    /* ------------------------------------------------------------------ */
    /* Interaction                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Repaints the model each tick so the ten forms render differently.
     * CustomModelData is client-visible data carried in the stack tag.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        syncModel(stack);
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    /** Pushes the current form into CustomModelData so the right texture is drawn. */
    private static void syncModel(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!WeaponData.isFormed(stack)) {
            // 未成形：用默认模型（毛笔 + 锁链的「双蛇杖」主体贴图），没有任何攻击力。
            if (tag.contains("CustomModelData", CompoundTag.TAG_INT)) {
                tag.remove("CustomModelData");
            }
            return;
        }
        int form = WeaponData.form(stack).index;
        int current = tag.contains("CustomModelData", CompoundTag.TAG_INT) ? tag.getInt("CustomModelData") : -1;
        if (current != form) {
            tag.putInt("CustomModelData", form);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Forming（成形）                                                      */
    /* ------------------------------------------------------------------ */

    /** 未成形 → 成形：正式变成双蛇杖，同时发放根进度。 */
    public static boolean formBlade(Player player, ItemStack stack) {
        if (WeaponData.isFormed(stack)) {
            return false;
        }
        WeaponData.setFormed(stack, true);
        WeaponData.setForm(stack, WeaponForm.GREATSWORD);
        syncModel(stack);
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.ANVIL_USE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
        player.displayClientMessage(Component.translatable("tenfold.blade.formed")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            com.tenfold.common.ModAdvancements.award(server, "root");
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Attributes                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Replaces the static iron-sword attributes with the current form's values.
     * <p>
     * Without this the item pane and the tooltip keep showing the base 6 damage
     * forever, because attributes are baked into the item, not the stack. The
     * modifier is rebuilt whenever the form changes, and the UUID is fixed so we
     * never stack duplicates.
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.MAINHAND) {
            return base;
        }
        if (!WeaponData.isFormed(stack)) {
            // 未成形：单纯一件物品，不加攻击力、攻速、触及。
            return ImmutableMultimap.of();
        }

        WeaponForm form = WeaponData.form(stack);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        // Drop the iron-sword defaults baked in by SwordItem; only the form counts.
        base.forEach((attribute, modifier) -> {
            if (attribute != Attributes.ATTACK_DAMAGE && attribute != Attributes.ATTACK_SPEED) {
                builder.put(attribute, modifier);
            }
        });
        // ATTACK_DAMAGE = 1 (hand) + modifier, so subtract that 1 to land exactly on form.damage.
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                form.damage - 1.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                form.attackSpeed, AttributeModifier.Operation.ADDITION));
        // 形态触及距离（鞭子 5.5、长枪 4.5、锥子 2.4…）。玩家默认 ENTITY_REACH 是 3.0，
        // 所以写入 reach - 3.0；Forge 的 Player#getEntityReach 会读这个属性来判断攻击距离。
        builder.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                BASE_ENTITY_REACH_UUID, "Weapon modifier",
                form.reach - 3.0D, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    /**
     * Right click. Sneak state is decided here and only here, so the two entry
     * points (use and useOn) can never both act on one click.
     * <p>
     * Sneak + right click -> next form. Plain right click -> issue a contract.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        boolean sneaking = player.isShiftKeyDown();

        // 半手剑：右键举剑格挡，而不是下达指令。减伤与反弹在
        // CommonEvents#onShieldBlock 里结算（举剑 = vanilla 的 isBlocking 状态）。
        // 潜行+右键仍然切形态，否则玩家会被困在半手剑里出不来。
        if (!WeaponData.isFormed(stack)) {
            // 未成形：不进入格挡，也不下达指令（下面的成形分支处理）
        } else if (!sneaking && WeaponData.form(stack) == WeaponForm.HALFSWORD) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        if (!level.isClientSide) {
            if (!WeaponData.isFormed(stack)) {
                // 未成形：潜行+右键 = 成形；普通右键提示
                if (sneaking) {
                    formBlade(player, stack);
                } else {
                    player.displayClientMessage(Component.translatable("tenfold.blade.unformed")
                            .withStyle(ChatFormatting.GRAY), true);
                }
            } else if (sneaking) {
                ContractManager.cycleForm(player, stack, true);
            } else {
                ContractManager.onBladeUse(player, stack);
            }
        }

        // SUCCESS stops vanilla from invoking use() a second time for this click.
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Right clicking a block never runs the block's own action with this blade,
     * and delegates to {@link #use} exactly once. Returning CONSUME is what
     * prevents the vanilla "use item then use on block" double dispatch.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        use(context.getLevel(), player, context.getHand());
        return InteractionResult.CONSUME;
    }

    /* ------------------------------------------------------------------ */
    /* Half-sword guard                                                    */
    /* ------------------------------------------------------------------ */

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return WeaponData.isFormed(stack) && WeaponData.form(stack) == WeaponForm.HALFSWORD
                ? UseAnim.BLOCK : UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return WeaponData.isFormed(stack) && WeaponData.form(stack) == WeaponForm.HALFSWORD ? 72000 : 0;
    }

    /**
     * 让半手剑进入 vanilla 的格挡状态。
     * <p>
     * 1.20.1 的 {@code LivingEntity#isBlocking()} 要求 useItem 的
     * {@code canPerformAction(SHIELD_BLOCK)} 为 true；默认实现恒为 false，
     * 所以不重写这里的话，"举剑格挡"永远不会生效。
     */
    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return WeaponData.isFormed(stack)
                && toolAction == ToolActions.SHIELD_BLOCK
                && WeaponData.form(stack) == WeaponForm.HALFSWORD;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!WeaponData.isFormed(stack)) {
            if (player.isShiftKeyDown() && !player.level().isClientSide) {
                formBlade(player, stack);
            }
            return true;
        }
        if (player.isShiftKeyDown()) {
            if (!player.level().isClientSide) {
                // Cycle backwards and keep the click from landing a hit.
                ContractManager.cycleForm(player, stack, false);
            }
            return true;
        }
        return super.onLeftClickEntity(stack, player, entity);
    }

    /* ------------------------------------------------------------------ */
    /* Combat                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Damage is resolved by the normal attack path: {@link #getAttributeModifiers}
     * feeds the current form's value into ATTACK_DAMAGE, so vanilla applies it.
     * <p>
     * This override only adds the per-form on-hit effects. Contract bookkeeping
     * lives in {@code CommonEvents#onLivingDeath}, because vanilla runs
     * {@code target.hurt(...)} before {@code hurtEnemy}, so the creature is
     * already dead by the time this is called.
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide && attacker instanceof Player player
                && WeaponData.isFormed(stack)) {
            FormAbilities.onHit(player, stack, target);
        }
        return result;
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        return WeaponData.form(stack) == WeaponForm.AXE;
    }

    /* ------------------------------------------------------------------ */
    /* Digging (only meaningful in shovel form)                            */
    /* ------------------------------------------------------------------ */

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        // 只有叉勺形态、且只对"铲类方块"给铲子速度；其余情况仍是剑的 1.0。
        WeaponForm form = WeaponData.form(stack);
        if (form.digSpeed > 0.0F && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return form.digSpeed;
        }
        return super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(BlockState state) {
        return false;
    }

    /**
     * 带 stack 的重载：1.20.1 的 {@code ItemStack#isCorrectToolForDrops} 会走这里，
     * 叉勺形态下才对铲类方块算"正确工具"（否则挖雪一类方块不掉落）。
     */
    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return WeaponData.form(stack) == WeaponForm.SHOVEL && state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    /* ------------------------------------------------------------------ */
    /* Tooltip                                                             */
    /* ------------------------------------------------------------------ */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!WeaponData.isFormed(stack)) {
            tooltip.add(Component.translatable("tooltip.tenfold.unformed").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.tenfold.unformed_help").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        WeaponForm form = WeaponData.form(stack);
        tooltip.add(Component.literal("\u25C6 ").withStyle(ChatFormatting.DARK_GRAY).append(form.display()));
        tooltip.add(form.describe());
        tooltip.add(Component.translatable("tooltip.tenfold.damage",
                        String.format("%.1f", form.damage))
                .withStyle(ChatFormatting.RED));

        if (form == WeaponForm.HALFSWORD) {
            tooltip.add(Component.translatable("tooltip.tenfold.halfsword")
                    .withStyle(ChatFormatting.AQUA));
        }

        int streak = WeaponData.streak(stack);
        tooltip.add(Component.translatable("tooltip.tenfold.streak", streak, WeaponData.STREAK_GOAL)
                .withStyle(streak >= WeaponData.STREAK_GOAL ? ChatFormatting.GOLD : ChatFormatting.GRAY));

        int breach = WeaponData.breach(stack);
        tooltip.add(Component.translatable("tooltip.tenfold.breach", breach, WeaponData.BREACH_LIMIT)
                .withStyle(breach > 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));

        long now = level != null ? level.getGameTime() : 0L;
        if (WeaponData.isLocked(stack, now)) {
            tooltip.add(Component.translatable("tooltip.tenfold.locked",
                    WeaponData.lockSecondsLeft(stack, now)).withStyle(ChatFormatting.DARK_RED));
        }
        if (WeaponData.target(stack) != null) {
            tooltip.add(Component.translatable("tooltip.tenfold.target").withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("tooltip.tenfold.help").withStyle(ChatFormatting.DARK_GRAY));
    }

    /* ------------------------------------------------------------------ */
    /* Misc                                                                */
    /* ------------------------------------------------------------------ */

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
        return repair.is(net.minecraft.world.item.Items.NETHERITE_INGOT);
    }

    public static boolean isBlade(ItemStack stack) {
        return stack.is(ModItems.TENFOLD_BLADE.get());
    }

    public static BlockPos frontPos(Player player) {
        return BlockPos.containing(player.getLookAngle().scale(2.0).add(player.position()));
    }

    @Override
    public @Nullable String getCreatorModId(ItemStack itemStack) {
        return com.tenfold.Tenfold.MOD_ID;
    }
}
