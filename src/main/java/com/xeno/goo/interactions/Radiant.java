package com.xeno.goo.interactions;

import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.math.BlockPos;

public class Radiant
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.RADIANT_GOO.get(), "radiant_light", Radiant::radiantLight, Radiant::isValidForLightLocation);
    }

    private static boolean radiantLight(SplatContext context) {
        BlockPos blockPos = context.blockPos().offset(context.sideHit());
        return context.world().setBlockState(blockPos, BlocksRegistry.RadiantLight.get().getDefaultState()
            .with(BlockStateProperties.FACING, context.sideHit()));
    }

    private static boolean isValidForLightLocation(SplatContext context) {
        BlockPos blockPos = context.blockPos().offset(context.sideHit());
        BlockState state = context.world().getBlockState(blockPos);
        return state.isAir(context.world(), blockPos) && context.blockState().isSolidSide(context.world(), context.blockPos(), context.sideHit());
    }

}
