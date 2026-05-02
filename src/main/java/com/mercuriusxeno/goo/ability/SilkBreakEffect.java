package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link BlockEffect} that breaks rock-compatible blocks with a
 * silk-touch loot context and pops drops at the target position.
 *
 * <p>"Rock-compatible" is determined by {@link AbilityMath#isRockCompatible}
 * against the goo composition registry: a block whose goo value
 * contains only rock and/or crystal types qualifies. Granite and
 * andesite (rock+crystal) qualify; ores (rock+metal) do not.</p>
 */
public final class SilkBreakEffect implements BlockEffect {

    /** Singleton instance. */
    public static final SilkBreakEffect INSTANCE = new SilkBreakEffect();

    /** Block break level event ID (sends break particles to clients). */
    private static final int BREAK_EFFECT_EVENT = 2001;
    /** Silk-touch enchantment level. */
    private static final int SILK_TOUCH_LEVEL = 1;

    private SilkBreakEffect() {
    }

    @Override
    public boolean apply(ServerLevel level, BlockPos pos) {
        if (!canBreak(level, pos)) {
            return false;
        }
        ItemStack tool = buildSilkTouchTool(level);
        List<ItemStack> drops = new ArrayList<>();
        if (!mineBlock(level, pos, tool, drops)) {
            return false;
        }
        for (ItemStack drop : drops) {
            Block.popResource(level, pos, drop);
        }
        return true;
    }

    /**
     * Returns true if the block at {@code pos} is in-bounds, non-air,
     * and rock-compatible by goo composition.
     *
     * @param level the server level
     * @param pos   the block position to check
     * @return true if the block can be silk-broken
     */
    private static boolean canBreak(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return false;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        return AbilityMath.isRockCompatible(value);
    }

    /**
     * Creates a diamond pickaxe with silk touch for loot context.
     *
     * @param level the server level (provides registry access)
     * @return a silk-touch diamond pickaxe
     */
    private static ItemStack buildSilkTouchTool(ServerLevel level) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        Holder<Enchantment> silkTouch = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);
        tool.enchant(silkTouch, SILK_TOUCH_LEVEL);
        return tool;
    }

    /**
     * Mines the block at {@code pos}, accumulating drops without
     * popping them yet.
     *
     * @param level the server level
     * @param pos   the position to mine
     * @param tool  the silk-touch tool for loot context
     * @param drops accumulator for block drops (caller is responsible for popping)
     * @return true if the block was successfully mined
     */
    private static boolean mineBlock(ServerLevel level, BlockPos pos,
                                     ItemStack tool, List<ItemStack> drops) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        mergeDrops(drops, Block.getDrops(state, level, pos, blockEntity, null, tool));
        state.spawnAfterBreak(level, pos, tool, true);
        level.levelEvent(BREAK_EFFECT_EVENT, pos, Block.getId(state));
        level.removeBlock(pos, false);
        return true;
    }

    /**
     * Merges new drops into an accumulator, stacking with existing
     * entries where capacity allows.
     *
     * @param accumulator the running drop list
     * @param newDrops    the drops to merge
     */
    private static void mergeDrops(List<ItemStack> accumulator, List<ItemStack> newDrops) {
        for (ItemStack drop : newDrops) {
            if (!tryMergeInto(accumulator, drop)) {
                accumulator.add(drop.copy());
            }
        }
    }

    /**
     * Attempts to stack a drop into an existing accumulator entry.
     *
     * @param accumulator the running drop list
     * @param drop        the drop to merge
     * @return true if the drop was merged into an existing entry
     */
    private static boolean tryMergeInto(List<ItemStack> accumulator, ItemStack drop) {
        for (ItemStack existing : accumulator) {
            if (ItemStack.isSameItemSameComponents(existing, drop)
                    && existing.getCount() + drop.getCount() <= existing.getMaxStackSize()) {
                existing.grow(drop.getCount());
                return true;
            }
        }
        return false;
    }
}
