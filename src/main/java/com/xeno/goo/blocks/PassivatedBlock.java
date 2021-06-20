package com.xeno.goo.blocks;

import com.xeno.goo.fluids.GooFluid;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;

import java.util.function.Supplier;

public class PassivatedBlock extends Block {
    public PassivatedBlock() {
        super(
                Properties.create(Material.IRON)
                .hardnessAndResistance(3.0f)
                .sound(SoundType.METAL)
        );
    }
}
