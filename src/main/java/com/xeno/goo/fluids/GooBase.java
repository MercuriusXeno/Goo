package com.xeno.goo.fluids;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.throwing.Breakpoint;
import com.xeno.goo.fluids.throwing.ThrownEffect;
import com.xeno.goo.library.Compare;
import com.xeno.goo.library.GooEntry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraftforge.fluids.FluidAttributes;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

public class GooBase extends Fluid {
    private static final Map<GooEntry, ThrownEffect> breakpoints = new TreeMap<>(Compare.entryWeightThrownEffectComparator);

    public GooBase(Supplier<? extends Item> bucket, FluidAttributes.Builder builder) {
        this.bucket = bucket;
        this.builder = builder;
    }

    private final Supplier<? extends Item> bucket;
    private final FluidAttributes.Builder builder;

    @Nonnull
    @Override
    public Item getFilledBucket() {
        return bucket.get();
    }

    @Override
    protected boolean canDisplace(FluidState p_215665_1_, IBlockReader p_215665_2_, BlockPos p_215665_3_, Fluid p_215665_4_, Direction p_215665_5_)
    {
        return false;
    }

    @Nonnull
    @Override
    protected FluidAttributes createAttributes()
    {
        return builder.build(this);
    }

    @Override
    protected Vector3d getFlow(IBlockReader p_215663_1_, BlockPos p_215663_2_, FluidState p_215663_3_)
    {
        return Vector3d.ZERO;
    }

    @Override
    public int getTickRate(IWorldReader p_205569_1_) {
        return 5;
    }

    @Override
    protected float getExplosionResistance() {
        return 100;
    }

    @Override
    public float getActualHeight(FluidState p_215662_1_, IBlockReader p_215662_2_, BlockPos p_215662_3_)
    {
        return 1;
    }

    @Override
    public float getHeight(FluidState p_223407_1_)
    {
        return 1;
    }

    @Override
    protected BlockState getBlockState(FluidState state)
    {
        return Blocks.AIR.getDefaultState();
    }

    @Override
    public boolean isSource(FluidState state)
    {
        return false;
    }

    @Override
    public int getLevel(FluidState p_207192_1_) {
        return 0;
    }

    @Nonnull
    @Override
    public VoxelShape func_215664_b(FluidState p_215664_1_, IBlockReader p_215664_2_, BlockPos p_215664_3_) {
        return VoxelShapes.fullCube();
    }

    public void registerBreakpoint(Breakpoint breakpoint) {
        if (GooBase.breakpoints.containsKey(breakpoint.goo)) {
            if (GooBase.breakpoints.get(breakpoint.goo).equals(breakpoint.effect)) {
                return;
            } else {
                GooMod.warn("There appears to be a conflicting thrown goo effect entry : " + breakpoint.goo.toString());
            }
        }
        GooBase.breakpoints.put(breakpoint.goo, breakpoint.effect);
    }
}
