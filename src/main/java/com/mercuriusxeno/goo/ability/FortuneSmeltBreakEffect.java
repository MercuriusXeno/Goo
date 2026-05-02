package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link BlockEffect} that breaks any destructible block with a
 * fortune-3 loot context, then auto-smelts each drop via vanilla
 * smelting recipes before popping the resulting items at the target
 * position.
 */
public final class FortuneSmeltBreakEffect implements BlockEffect {

    /** Singleton instance. */
    public static final FortuneSmeltBreakEffect INSTANCE = new FortuneSmeltBreakEffect();

    /** Block break level event ID (sends break particles to clients). */
    private static final int BREAK_EFFECT_EVENT = 2001;
    /** Fortune level applied to ore drops. */
    private static final int FORTUNE_LEVEL = 3;

    private FortuneSmeltBreakEffect() {
    }

    @Override
    public boolean apply(ServerLevel level, BlockPos pos) {
        if (!canBreak(level, pos)) {
            return false;
        }
        ItemStack tool = buildFortuneTool(level);
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
     * and destructible (destroySpeed >= 0).
     *
     * @param level the server level
     * @param pos   the block position to check
     * @return true if the block can be broken
     */
    private static boolean canBreak(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0;
    }

    /**
     * Creates a diamond pickaxe with fortune 3 for loot context.
     *
     * @param level the server level (provides registry access)
     * @return a fortune-3 diamond pickaxe
     */
    private static ItemStack buildFortuneTool(ServerLevel level) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        Holder<Enchantment> fortune = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE);
        tool.enchant(fortune, FORTUNE_LEVEL);
        return tool;
    }

    /**
     * Mines the block at {@code pos}, smelting each drop via vanilla
     * recipes and accumulating them without popping yet.
     *
     * @param level the server level
     * @param pos   the position to mine
     * @param tool  the fortune tool for loot context
     * @param drops accumulator for smelted block drops
     * @return true if the block was successfully mined
     */
    private static boolean mineBlock(ServerLevel level, BlockPos pos,
                                     ItemStack tool, List<ItemStack> drops) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        List<ItemStack> rawDrops = Block.getDrops(state, level, pos, blockEntity, null, tool);
        for (ItemStack drop : rawDrops) {
            mergeDrop(drops, trySmelting(level, drop));
        }
        state.spawnAfterBreak(level, pos, tool, true);
        level.levelEvent(BREAK_EFFECT_EVENT, pos, Block.getId(state));
        level.removeBlock(pos, false);
        return true;
    }

    /**
     * Attempts to smelt an item via furnace recipe. Returns the
     * smelted result at the same stack count, or the original if no
     * recipe exists.
     *
     * @param level the server level
     * @param drop  the item to try smelting
     * @return smelted result or the original drop
     */
    private static ItemStack trySmelting(ServerLevel level, ItemStack drop) {
        Optional<RecipeHolder<SmeltingRecipe>> recipe = level.recipeAccess()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(drop), level);
        if (recipe.isEmpty()) {
            return drop;
        }
        ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(drop));
        result.setCount(drop.getCount());
        return result;
    }

    /**
     * Merges a drop into the accumulator, stacking with existing
     * entries where capacity allows so the final pop produces fewer
     * item entities.
     *
     * @param accumulator the running drop list
     * @param drop        the drop to merge
     */
    private static void mergeDrop(List<ItemStack> accumulator, ItemStack drop) {
        for (ItemStack existing : accumulator) {
            if (ItemStack.isSameItemSameComponents(existing, drop)
                    && existing.getCount() + drop.getCount() <= existing.getMaxStackSize()) {
                existing.grow(drop.getCount());
                return;
            }
        }
        accumulator.add(drop.copy());
    }
}
