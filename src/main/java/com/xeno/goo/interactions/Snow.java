package com.xeno.goo.interactions;

import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.SoundCategory;

public class Snow
{
    public static boolean freezeWater(InteractionContext context)
    {
        // freeze water
        if (context.fluidState().getFluid().isEquivalentTo(Fluids.WATER)) {
            if (!context.isRemote()) {
                if (context.fluidState().isSource()) {
                    context.setBlockState(Blocks.ICE.getDefaultState());
                }
                AudioHelper.headlessAudioEvent(context.world(), context.blockPos(), Registry.FREEZE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, () -> 1.5f);
            }
            return true;
        }
        return false;
    }

    public static boolean iceCoolLava(InteractionContext context)
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
        GooInteractions.register(Registry.SNOW_GOO.get(), "freeze_water", 0, Snow::freezeWater);
        GooInteractions.register(Registry.SNOW_GOO.get(), "cool_lava", 1, Snow::iceCoolLava);
        GooInteractions.register(Registry.SNOW_GOO.get(), "extinguish_fire", 2, Snow::extinguishFire);
    }
}
