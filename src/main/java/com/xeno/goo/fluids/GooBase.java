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
    public static final double GRAVITY_DEFAULT = -0.05d;
    public static final double BARELY = 0.01d;
    public static final double SLIGHT = 0.02d;
    public static final double MODERATELY_BUOYANT = 0.03d;
    public static final double MOSTLY_BUOYANT = 0.05d;
    public static final double SUPER_BUOYANT = 0.1d;
    public static final double HEAVIER_THAN_NOT = -0.06d;
    public static final double PRETTY_HEAVY = -0.08d;
    public static final double HEAVY = -0.1d;
    public static final double VERY_HEAVY = -0.15d;
    public static final double EXTREMELY_HEAVY = -0.2d;
    public static final double FRICTIONLESS = 0d;
    public static final double VERY_SLIPPERY = 0.1d;
    public static final double SLIPPERY = 0.25d;
    public static final double BARELY_SLIPPERY = 0.45d;
    public static final double SLIGHTLY_STICKY = 0.55d;
    public static final double STICKY = 0.75d;
    public static final double VERY_STICKY = 0.9d;
    public static final double STUCK = 1.0d;
    public static final double AIR_DRAG_DEFAULT = 0.01d;
    public static final double WATER_DRAG_DEFAULT = 0.4d;
    public static final double LAVA_DRAG_DEFAULT = 0.64d;
    public static final double NO_TRANSFER = 0.0d;
    public static final double VERY_LOSSY = 0.2d;
    public static final double LOSSY = 0.4d;
    public static final double SLIGHTLY_BOUNCY = 0.6d;
    public static final double VERY_BOUNCY = 0.8d;
    public static final double PERFECT_TRANSFER = 1.0d;

    public final Map<LooseMaterialTypes, Double> buoyancy;
    private final Map<LooseMaterialTypes, Double> stickiness;
    private final Map<LooseMaterialTypes, Double> bounciness;

    public GooBase(Supplier<? extends Item> bucket, FluidAttributes.Builder builder, Map<LooseMaterialTypes, Double> buoyancy, Map<LooseMaterialTypes, Double> stickiness, Map<LooseMaterialTypes, Double> bounciness) {
        this.bucket = bucket;
        this.builder = builder;
        this.buoyancy = buoyancy;
        this.stickiness = stickiness;
        this.bounciness = bounciness;
    }

    private final Supplier<? extends Item> bucket;
    private final FluidAttributes.Builder builder;

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

    public static Map<LooseMaterialTypes, Double> createMaterialMappedCoefficient(double air, double water, double lava, double solids, double otherGoo) {
        Map<LooseMaterialTypes, Double> result = new HashMap<>();
        result.put(LooseMaterialTypes.AIR, air);
        result.put(LooseMaterialTypes.WATER, water);
        result.put(LooseMaterialTypes.LAVA, lava);
        result.put(LooseMaterialTypes.ANY, solids);
        result.put(LooseMaterialTypes.GOO, otherGoo);
        return result;
    }

    public double stickiness(LooseMaterialTypes material) {
        return this.stickiness.getOrDefault(material, SLIGHTLY_STICKY);
    }

    public double buoyancy(LooseMaterialTypes material)
    {
        return this.buoyancy.getOrDefault(material, GRAVITY_DEFAULT);
    }
}
