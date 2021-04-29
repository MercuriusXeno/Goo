package com.xeno.goo.interactions;

import com.xeno.goo.setup.Registry;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.state.properties.BlockStateProperties;

public class Aquatic
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.AQUATIC_GOO.get(), "cool_lava", Aquatic::waterCoolLava, Aquatic::isHittingSourceLava);

        GooInteractions.registerBlob(Registry.AQUATIC_GOO.get(), "cool_flowing_lava", Aquatic::waterCoolFlowingLava);
        GooInteractions.registerBlob(Registry.AQUATIC_GOO.get(), "edify_flowing_water", Aquatic::edifyNonSourceWater);
        GooInteractions.registerBlob(Registry.AQUATIC_GOO.get(), "hydrate_farmland", Aquatic::hydrateFarmland);
        GooInteractions.registerBlob(Registry.AQUATIC_GOO.get(), "extinguish_fire", Aquatic::extinguishFire);

        GooInteractions.registerBlobHit(Registry.AQUATIC_GOO.get(), "aquatic_hit", Aquatic::hitEntity);
    }

    private static boolean hitEntity(BlobHitContext blobHitContext) {
        GooInteractions.blobHitKnockback(1.0f, blobHitContext);
        blobHitContext.hurt(3f);
        return false;
    }

    public static boolean hydrateFarmland(BlobContext context)
    {
        // hydrate farmland
        if (context.block().equals(Blocks.FARMLAND)) {
            int hydration = context.blockState().get(FarmlandBlock.MOISTURE);
            if (hydration < 7) {
                int newHydration = Math.min(7, hydration + 1);

                if (!context.isRemote()) {
                    context.setBlockState(context.blockState().with(FarmlandBlock.MOISTURE, newHydration));
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isHittingSourceLava(SplatContext context) {
        return context.fluidState().getFluid().isEquivalentTo(Fluids.LAVA) && context.fluidState().isSource();
    }

    public static boolean waterCoolLava(SplatContext context)
    {
        // cool lava
        if (!context.isRemote()) {
            boolean hasChanges = context.setBlockState(net.minecraftforge.event.ForgeEventFactory
                    .fireFluidPlaceBlockEvent(context.world(), context.blockPos(), context.blockPos(), Blocks.OBSIDIAN.getDefaultState()));
            if (!hasChanges) {
                return false;
            }
        }
        // spawn some sizzly smoke and sounds
        context.world().playEvent(1501, context.blockPos(), 0); // sizzly bits
        return true;
    }

    public static boolean waterCoolFlowingLava(BlobContext context) {
        // cool lava
        if (context.fluidState().getFluid().isEquivalentTo(Fluids.LAVA)) {
            // spawn some sizzly smoke and sounds
            if (!context.isRemote()) {
                if (!context.fluidState().isSource()) {
                    context.setBlockState(net.minecraftforge.event.ForgeEventFactory
                            .fireFluidPlaceBlockEvent(context.world(), context.blockPos(), context.blockPos(), Blocks.STONE.getDefaultState()));
                }
            }
            context.world().playEvent(1501, context.blockPos(), 0); // sizzly bits
            return true;
        }
        return false;
    }

    private static boolean edifyNonSourceWater(BlobContext context) {
        // edify non-source water to source water
        if (context.fluidState().getFluid().isEquivalentTo(Fluids.WATER)) {
            if (!context.isRemote()) {
                if (!context.fluidState().isSource()) {
                    context.setBlockState(Blocks.WATER.getDefaultState().with(BlockStateProperties.LEVEL_1_8, 8));
                }
            }
            return true;
        }
        return false;
    }

    public static boolean extinguishFire(BlobContext context)
    {
        // extinguish fires
        if (context.blockState().getBlock().equals(Blocks.FIRE)) {
            context.world().playEvent(null, 1009, context.blockPos(), 0);
            if (!context.isRemote()) {
                context.world().removeBlock(context.blockPos(), false);
            }
            return true;
        }
        return false;
    }
}
