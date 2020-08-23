package com.xeno.goo.blocks;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;

public class FungalGooBlock extends GooBlockBase
{
    public FungalGooBlock()
    {
        super(Registry.FUNGAL_GOO,
                Properties
                        .create(Material.WATER)
                        .doesNotBlockMovement()
                        .hardnessAndResistance(100.0F)
                        .setLightLevel((state) -> 4)
                        .noDrops());
    }

    @Override
    public int place(World world, BlockPos pos, @Nonnull FluidStack fluidStack, IFluidHandler.FluidAction action)
    {
        if (world.isAirBlock(pos)) {
            // automatically allowed, basically.
            GooMod.debug("On air, should place");
        } else {
            if (world.getBlockState(pos).isSolid()) {
                // probably don't do anything
                GooMod.debug("Struck solid, can't place");
            }
            // do stuff with block
            if (!world.getBlockState(pos).getFluidState().isSource()) {
                // allowed because not source block
                GooMod.debug("Struck flowing fluid, should place");
            }
        }
        // not sure what's necessary here - do we check the level first? trying that.
        return 0;
    }
}
