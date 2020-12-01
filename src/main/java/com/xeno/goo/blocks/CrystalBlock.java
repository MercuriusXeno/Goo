package com.xeno.goo.blocks;

import com.xeno.goo.fluids.GooFluid;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;

import java.util.function.Supplier;

public class CrystalBlock extends Block {
    private final Supplier<GooFluid> gooFluidSupplier;
    public CrystalBlock(Supplier<GooFluid> f) {
        super(
                Properties.create(Material.ROCK)
                .hardnessAndResistance(2.0f)
                .sound(SoundType.STONE)
        );
        this.gooFluidSupplier = f;
    }

    @Override
    public int getLightValue(BlockState state, IBlockReader world, BlockPos pos) {
        return gooFluidSupplier.get().getLightLevel();
    }
}
