package com.xeno.goo.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.StateContainer;
import net.minecraftforge.fluids.IFluidBlock;
import javax.annotation.Nullable;
import java.util.function.Supplier;

public abstract class GooBlockBase extends FlowingFluidBlock implements IFluidBlock
{
    public GooBlockBase(Supplier<? extends FlowingFluid> supplier, Properties properties)
    {
        super(supplier, properties);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState().with(FlowingFluidBlock.LEVEL, 0);
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FlowingFluidBlock.LEVEL);
    }
}
