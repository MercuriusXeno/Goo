package com.xeno.goo.fluids;

import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.setup.Registry;
import javafx.beans.property.adapter.JavaBeanBooleanPropertyBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.loot.conditions.BlockStateProperty;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.ForgeFlowingFluid;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public abstract class GooBase extends ForgeFlowingFluid implements IGooBase {
    protected GooBase(Properties properties)
    {
        super(properties);
    }


    @Nonnull
    @Override
    public Item getFilledBucket() {
        return Items.AIR;
    }

    @Override
    protected boolean canDisplace(FluidState fluidState, IBlockReader blockReader, BlockPos pos, Fluid fluid, Direction direction)
    {
        return false;
    }

    @Override
    protected boolean canSourcesMultiply() { return false; }

    @Override
    protected int getSlopeFindDistance(IWorldReader worldIn) { return 2; }

    @Override
    protected int getLevelDecreasePerBlock(IWorldReader worldIn) { return 0; }

    @Override
    public Vector3d getFlow(IBlockReader reader, BlockPos pos, FluidState fluidState)
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
    public float getHeight(FluidState state)
    {
        return getLevelFromState(state) * 2F;
    }

    @Override
    protected void beforeReplacingBlock(IWorld worldIn, BlockPos pos, BlockState state)
    {
        TileEntity tileentity = state.hasTileEntity() ? worldIn.getTileEntity(pos) : null;
        Block.spawnDrops(state, worldIn.getWorld(), pos, tileentity);
    }

    @Nonnull
    @Override
    public VoxelShape func_215664_b(FluidState p_215664_1_, IBlockReader p_215664_2_, BlockPos p_215664_3_) {
        return VoxelShapes.fullCube();
    }

    @Override
    public abstract void doEffect(ServerWorld world, ServerPlayerEntity player, GooEntity goo, Entity entityHit, BlockPos pos);

    public abstract GooEntity createEntity(World world, LivingEntity sender, FluidStack goo, Hand isHeld);

    public int decayRate() { return 1; }
}
