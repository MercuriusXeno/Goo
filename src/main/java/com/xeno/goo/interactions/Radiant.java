package com.xeno.goo.interactions;

import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.math.BlockPos;

import java.util.function.Supplier;

public class Radiant
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.RADIANT_GOO;
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(fluidSupplier.get(), "radiant_light", Radiant::radiantLight, Radiant::isValidForLightLocation);
    }

    private static boolean radiantLight(SplatContext context) {
        BlockPos blockPos = context.blockPos().offset(context.sideHit());
        return context.world().setBlockState(blockPos, BlocksRegistry.RadiantLight.get().getDefaultState()
            .with(BlockStateProperties.FACING, context.sideHit()));
    }

    private static boolean isValidForLightLocation(SplatContext context) {
        BlockPos blockPos = context.blockPos().offset(context.sideHit());
        BlockState state = context.world().getBlockState(blockPos);
        boolean isAir = state.isAir(context.world(), blockPos);
        boolean isReplaceable = state.getMaterial().isReplaceable();
        return (isAir || isReplaceable) && context.blockState().isSolidSide(context.world(), context.blockPos(), context.sideHit());
    }

}
