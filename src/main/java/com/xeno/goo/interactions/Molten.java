package com.xeno.goo.interactions;

import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

import java.util.Optional;

public class Molten
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.MOLTEN_GOO.get(), "melt_obsidian",  Molten::meltObsidian);
        GooInteractions.registerSplat(Registry.MOLTEN_GOO.get(), "cook_block", Molten::cookBlock);
        GooInteractions.registerSplat(Registry.MOLTEN_GOO.get(), "ignite_block", Molten::igniteBlock);
    }

    private static boolean igniteBlock(SplatContext context) {
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

    private static boolean cookBlock(SplatContext context)
    {
        Optional<IRecipe<?>> matchRecipe = Equivalencies.furnaceRecipes(context.world()).stream()
                .filter((r) -> soleIngredient(r, context.block()))
                .findFirst();
        boolean[] result = {false};
        matchRecipe.ifPresent((c) -> result[0] = cookBlock(c, context));
        return result[0];
    }

    private static boolean cookBlock(IRecipe<?> c, SplatContext context)
    {
        if (!context.isRemote()) {
            Item output = c.getRecipeOutput().getItem();
            doEffects(context);
            if (output instanceof BlockItem) {
                Block block = ((BlockItem) output).getBlock();
                context.setBlockState(block.getDefaultState());
            } else {
                ItemStack result = c.getRecipeOutput().copy();
                Vector3d spawnLoc = Vector3d.copy(context.blockPos()).add(0.5d, 0.5d, 0.5d);
                context.world().removeBlock(context.blockPos(), false);
                context.world().addEntity(new ItemEntity(context.world(), spawnLoc.x,
                        spawnLoc.y, spawnLoc.z, result));
            }
        }
        return true;
    }

    private static void doEffects(SplatContext context) {
        if (context.world() instanceof ServerWorld) {
            Vector3d particlePos = context.splat().getPositionVec();
            AxisAlignedBB bounds = context.splat().getBoundingBox();
            // vec representing the "domain" of the bounding box.
            Vector3d rangeVec = new Vector3d(
                    bounds.maxX - bounds.minX,
                    bounds.maxY - bounds.minY,
                    bounds.maxZ - bounds.minZ);
            for (int i = 0; i < 5; i++) {
                Vector3d finalPos = particlePos.add(
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.x,
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.y,
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.z
                );
                Vector3d finalPos2 = particlePos.add(
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.x,
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.y,
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.z
                );
                ((ServerWorld) context.world()).spawnParticle(ParticleTypes.FLAME,
                        finalPos.x, finalPos.y, finalPos.z, 1, 0d, 0d, 0d, 0d);
                ((ServerWorld) context.world()).spawnParticle(ParticleTypes.SMOKE,
                        finalPos2.x, finalPos2.y, finalPos2.z, 1, 0d, 0d, 0d, 0d);
            }
            AudioHelper.entityAudioEvent(context.splat(), Registry.MOLTEN_SIZZLE_SOUND.get(), SoundCategory.BLOCKS,
                    1f, AudioHelper.PitchFormulas.HalfToOne);
        }
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

    private static boolean meltObsidian(SplatContext context)
    {
        if (context.block().equals(Blocks.OBSIDIAN)) {
            if (!context.isRemote()) {
                context.setBlockState(Blocks.LAVA.getDefaultState());
            }
            return true;
        }

        return false;
    }
}
