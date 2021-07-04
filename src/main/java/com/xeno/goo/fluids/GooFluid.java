package com.xeno.goo.fluids;

import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.Predicate;

public class GooFluid extends Fluid
{
    public static final Predicate<FluidStack> IS_GOO_FLUID = GooFluid::isGooFluid;
	public static final int FULL_OPACITY = 0xffffffff;

	public static boolean isGooFluid(FluidStack fluid) {

        return fluid == null || fluid.getRawFluid() instanceof GooFluid;
    }

    private final int lightLevel;
    public static final int UNCOLORED_WITH_PARTIAL_TRANSPARENCY =
            // alpha
            224 << 24 |
            255 << 16 |
            255 << 8 |
            255;
    private final ResourceLocation icon;
    private final ResourceLocation shortIcon;
    private final FluidAttributes.Builder builder;
    private final float overrideIndex;
    public GooFluid(ResourceLocation still, ResourceLocation flowing, ResourceLocation icon, float overrideIndex, int lightLevel)
    {
        super();
        this.builder = FluidAttributes
                .builder(still, flowing)
                .temperature(293)
                .color(UNCOLORED_WITH_PARTIAL_TRANSPARENCY);
        this.icon = icon;
        String subpathWithoutExtension = icon.getPath().substring(0, icon.getPath().lastIndexOf(".png"));
        this.shortIcon = new ResourceLocation(icon.getNamespace(), subpathWithoutExtension + "_short.png");
        this.overrideIndex = overrideIndex;
        this.lightLevel = lightLevel;
    }

    public float overrideIndex() {
        return this.overrideIndex;
    }

    @Override
    protected FluidAttributes createAttributes()
    {
        return builder.build(this);
    }

    @Override
    public Item getFilledBucket() {
        return Registry.getBucket(this);
    }

    @Override
    protected boolean canDisplace(FluidState fluidState, IBlockReader blockReader, BlockPos pos, Fluid fluid, Direction direction)
    {
        return false;
    }

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
    public float getHeight(FluidState p_223407_1_)
    {
        return 0;
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
    public int getLevel(FluidState p_207192_1_)
    {
        return 0;
    }

    @Override
    public VoxelShape func_215664_b(FluidState p_215664_1_, IBlockReader p_215664_2_, BlockPos p_215664_3_) {
        return VoxelShapes.fullCube();
    }

    public ResourceLocation icon()
    {
        return this.icon;
    }

    public ResourceLocation shortIcon() { return this.shortIcon; }

    public int getLightLevel() {
        return this.lightLevel;
    }
}
