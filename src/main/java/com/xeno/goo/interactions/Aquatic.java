package com.xeno.goo.interactions;

import com.xeno.goo.setup.Registry;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.state.properties.BlockStateProperties;

public class Aquatic
{
    public static boolean hydrateFarmland(InteractionContext context)
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

    public static boolean waterCoolLava(InteractionContext context)
    {
        // cool lava
        if (context.fluidState().getFluid().isEquivalentTo(Fluids.LAVA)) {
            // spawn some sizzly smoke and sounds
            if (!context.isRemote()) {
                if (context.fluidState().isSource()) {
                    context.setBlockState(net.minecraftforge.event.ForgeEventFactory
                            .fireFluidPlaceBlockEvent(context.world(), context.blockPos(), context.blockPos(), Blocks.OBSIDIAN.getDefaultState()));
                } else {
                    context.setBlockState(net.minecraftforge.event.ForgeEventFactory
                            .fireFluidPlaceBlockEvent(context.world(), context.blockPos(), context.blockPos(), Blocks.COBBLESTONE.getDefaultState()));
                }
            }
            context.world().playEvent(1501, context.blockPos(), 0); // sizzly bits
            return true;
        }
        return false;
    }

    public static boolean edifyNonSourceWater(InteractionContext context)
    {
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

    public static boolean extinguishFire(InteractionContext context)
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

    public static void registerInteractions()
    {
        GooInteractions.register(Registry.AQUATIC_GOO.get(), "hydrate_farmland", 0, Aquatic::hydrateFarmland);
        GooInteractions.register(Registry.AQUATIC_GOO.get(), "edify_flowing_water", 1, Aquatic::edifyNonSourceWater);
        GooInteractions.register(Registry.AQUATIC_GOO.get(), "cool_lava", 2, Aquatic::waterCoolLava);
        GooInteractions.register(Registry.AQUATIC_GOO.get(), "extinguish_fire", 3, Aquatic::extinguishFire);
    }
}
