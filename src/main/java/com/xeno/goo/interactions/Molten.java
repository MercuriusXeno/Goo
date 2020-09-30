package com.xeno.goo.interactions;

import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;

public class Molten
{
    private static boolean igniteBlock(InteractionContext context) {
        if (CampfireBlock.canBeLit(context.blockState())) {
            if (!context.isRemote()) {
                context.world().setBlockState(context.blockPos(), context.blockState().with(BlockStateProperties.LIT, Boolean.TRUE), 11);
            }
            return true;
        } else {
            BlockPos offPos = context.blockPos().offset(context.sideHit());
            if (AbstractFireBlock.canLightBlock(context.world(), offPos)) {
                if (!context.isRemote()) {
                    BlockState offState = AbstractFireBlock.getFireForPlacement(context.world(), offPos);
                    context.world().setBlockState(offPos, offState, 11);
                }
                return true;
            }
        }

        return false;
    }

    private static boolean cookBlock(InteractionContext context)
    {
        Optional<IRecipe<?>> matchRecipe = Equivalencies.furnaceRecipes(context.world()).stream()
                .filter((r) -> soleIngredient(r, context.block()))
                .findFirst();
        boolean[] result = {false};
        matchRecipe.ifPresent((c) -> result[0] = cookBlock(c, context));
        return result[0];
    }

    private static boolean cookBlock(IRecipe<?> c, InteractionContext context)
    {
        if (!context.isRemote()) {
            Item output = c.getRecipeOutput().getItem();
            if (output instanceof BlockItem) {
                Block block = ((BlockItem) output).getBlock();
                context.setBlockState(block.getDefaultState());
            }
        }
        return true;
    }

    private static boolean soleIngredient(IRecipe<?> r, Block block)
    {
        return r.getIngredients().stream().anyMatch((i) ->
                blockIngredientMatches(i, block)
        );
    }

    private static boolean blockIngredientMatches(Ingredient i, Block block)
    {
        return i != null && anyIngredientMatchesBlock(i.getMatchingStacks(), block);
    }

    private static boolean anyIngredientMatchesBlock(ItemStack[] matchingStacks, Block block)
    {
        for(ItemStack i : matchingStacks) {
            if (i.getItem() instanceof BlockItem) {
                if (((BlockItem) i.getItem()).getBlock().equals(block)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean meltObsidian(InteractionContext context)
    {
        if (context.block().equals(Blocks.OBSIDIAN)) {
            if (!context.isRemote()) {
                context.setBlockState(Blocks.LAVA.getDefaultState());
            }
            return true;
        }

        return false;
    }

    public static void registerInteractions()
    {
        GooInteractions.register(Registry.MOLTEN_GOO.get(), "melt_obsidian", 0,  Molten::meltObsidian);
        GooInteractions.register(Registry.MOLTEN_GOO.get(), "cook_block", 1, Molten::cookBlock);
        GooInteractions.register(Registry.MOLTEN_GOO.get(), "ignite_block", 2, Molten::igniteBlock);
    }
}
