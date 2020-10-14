package com.xeno.goo.interactions;

import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.SoundCategory;

public class Snow
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.SNOW_GOO.get(), "freeze_water", Snow::freezeWater, Snow::isWaterSource);
        GooInteractions.registerSplat(Registry.SNOW_GOO.get(), "cool_lava", Snow::coolLava, Snow::isLavaSource);

        // outrageously, this is allowed
        GooInteractions.registerBlob(Registry.SNOW_GOO.get(), "extinguish_fire", Aquatic::extinguishFire); // aquatic lolol
        GooInteractions.registerBlob(Registry.SNOW_GOO.get(), "cool_flowing_lava", Aquatic::waterCoolFlowingLava);
    }

    private static boolean isWaterSource(SplatContext context) {
        return context.fluidState().getFluid().isEquivalentTo(Fluids.WATER)
                && context.fluidState().isSource() && context.isBlockAboveAir();
    }

    public static boolean freezeWater(SplatContext context)
    {
        // freeze water
        if (!context.isRemote()) {
            boolean hasChanges = context.setBlockState(Blocks.ICE.getDefaultState());
            if (!hasChanges) {
                return false;
            }
            AudioHelper.headlessAudioEvent(context.world(), context.blockPos(), Registry.FREEZE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, () -> 1.5f);
        }
        return true;
    }

    private static boolean isLavaSource(SplatContext context) {
        return context.fluidState().getFluid().isEquivalentTo(Fluids.LAVA)
                && context.fluidState().isSource();
    }

    public static boolean coolLava(SplatContext context)
    {
        // spawn some sizzly smoke and sounds
        if (!context.isRemote()) {
                boolean hasChanges = context.setBlockState(net.minecraftforge.event.ForgeEventFactory
                        .fireFluidPlaceBlockEvent(context.world(), context.blockPos(), context.blockPos(), Blocks.OBSIDIAN.getDefaultState()));
                if (!hasChanges) {
                    return false;
                }
        }
        context.world().playEvent(1501, context.blockPos(), 0); // sizzly bits
        return true;
    }
}
