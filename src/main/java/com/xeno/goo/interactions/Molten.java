package com.xeno.goo.interactions;

import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.AudioHelper.PitchFormulas;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

import java.util.Optional;
import java.util.function.Supplier;

public class Molten
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.MOLTEN_GOO;
    public static void registerInteractions()
    {
        GooInteractions.registerBlobHit(fluidSupplier.get(), "melt_obsidian",  Molten::meltObsidian, Molten::isObsidian);
        GooInteractions.registerBlobHit(fluidSupplier.get(), "cook_block", Molten::cookBlock, Molten::isCookable);

        GooInteractions.registerBlobHit(fluidSupplier.get(), "molten_hit", Molten::hitEntity);
    }

    private static boolean hitEntity(BlobHitContext c) {
        c.victim().setFire(60);
        c.damageVictim(3f);
        c.knockback(1f);
        for(int i = 0; i < 4; i++) {
            c.world().addParticle(ParticleTypes.SMOKE, c.blob().getPosX(), c.blob().getPosY(), c.blob().getPosZ(), 0d, 0.1d, 0d);
        }
        AudioHelper.entityAudioEvent(c.blob(), Registry.GOO_SIZZLE_SOUND.get(), SoundCategory.NEUTRAL, 1.0f, PitchFormulas.HalfToOne);
        return true;
    }

    private static boolean isCookable(BlobHitContext context) {
        Optional<IRecipe<?>> matchRecipe = Equivalencies.furnaceRecipes(context.world()).stream()
                .filter((r) -> soleIngredient(r, context.block()))
                .findFirst();
        return matchRecipe.isPresent();
    }

    private static boolean cookBlock(BlobHitContext context)
    {
        Optional<IRecipe<?>> matchRecipe = Equivalencies.furnaceRecipes(context.world()).stream()
                .filter((r) -> soleIngredient(r, context.block()))
                .findFirst();
        boolean[] result = {false};
        matchRecipe.ifPresent((c) -> result[0] = cookBlock(c, context));
        return result[0];
    }

    private static boolean cookBlock(IRecipe<?> c, BlobHitContext context)
    {
        if (!context.isRemote()) {
            Item output = c.getRecipeOutput().getItem();
            doEffects(context);
            if (output instanceof BlockItem) {
                Block block = ((BlockItem) output).getBlock();
                boolean hasChanges = context.setBlockState(block.getDefaultState());
                if (!hasChanges) {
                    return false;
                }
            } else {
                ItemStack result = c.getRecipeOutput().copy();
                Vector3d spawnLoc = Vector3d.copy(context.blockPos()).add(0.5d, 0.5d, 0.5d);
                boolean hasChanges = context.world().removeBlock(context.blockPos(), false);
                if (!hasChanges) {
                    return false;
                }
                context.world().addEntity(new ItemEntity(context.world(), spawnLoc.x,
                        spawnLoc.y, spawnLoc.z, result));
            }
        }
        return true;
    }

    private static void doEffects(BlobHitContext context) {
        if (context.world() instanceof ServerWorld) {
            Vector3d particlePos = context.blob().getPositionVec();
            AxisAlignedBB bounds = context.blob().getBoundingBox();
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
            AudioHelper.entityAudioEvent(context.blob(), Registry.GOO_SIZZLE_SOUND.get(), SoundCategory.BLOCKS,
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

    private static boolean isObsidian(BlobHitContext context) {
        return context.block().equals(Blocks.OBSIDIAN);
    }

    private static boolean meltObsidian(BlobHitContext context)
    {
        if (!context.isRemote()) {
            return context.setBlockState(Blocks.LAVA.getDefaultState());
        }
        return true;
    }
}
