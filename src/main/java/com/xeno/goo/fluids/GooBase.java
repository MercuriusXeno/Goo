package com.xeno.goo.fluids;

import com.xeno.goo.entities.GooEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public abstract class GooBase extends Fluid implements IGooBase {

    private final Supplier<? extends Item> bucket;
    private final FluidAttributes.Builder builder;
    private final float rigidity;

    public GooBase(Supplier<? extends Item> bucket, FluidAttributes.Builder builder, float rigidity) {
        this.bucket = bucket;
        this.builder = builder;
        this.rigidity = rigidity;
    }

    @Nonnull
    @Override
    public Item getFilledBucket() {
        return bucket.get();
    }

    @Override
    protected boolean canDisplace(FluidState fluidState, IBlockReader blockReader, BlockPos pos, Fluid fluid, Direction direction)
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
    protected Vector3d getFlow(IBlockReader reader, BlockPos pos, FluidState fluidState)
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

    @Override
    public abstract void doEffect(ServerWorld world, ServerPlayerEntity player, GooEntity goo, Entity entityHit, BlockPos pos);

    public abstract void createEntity(World world, LivingEntity sender, FluidStack goo, Hand isHeld);

    public int decayRate() {
        return 1;
    }

    public abstract ResourceLocation getEntityTexture();
}
