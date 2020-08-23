package com.xeno.goo.fluids;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.CrystalEntity;
import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.setup.Resources;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public abstract class CrystalGoo extends GooBase
{
    private static final ForgeFlowingFluid.Properties PROPERTIES = new ForgeFlowingFluid.Properties(
            Registry.CRYSTAL_GOO, Registry.CRYSTAL_GOO_FLOWING,
            FluidAttributes.builder(
                    Resources.GooTextures.Still.CRYSTAL_GOO,
                    Resources.GooTextures.Flowing.CRYSTAL_GOO)
                    .translationKey("fluid.goo.crystal_goo"))
            .bucket(() -> Items.AIR)
            .block(Registry.CRYSTAL_GOO_BLOCK)
            .slopeFindDistance(3)
            .levelDecreasePerBlock(1)
            .explosionResistance(100f);

    public CrystalGoo() {
        super(PROPERTIES);
    }

    @Override
    public void doEffect(ServerWorld world, ServerPlayerEntity player, GooEntity goo, Entity entityHit, BlockPos pos) { }

    @Override
    public GooEntity createEntity(World world, LivingEntity sender, FluidStack goo, Hand handIn)
    {
        if (world.isRemote()) {
            return null;
        }
        GooEntity e = new CrystalEntity(Registry.CRYSTAL.get(), world, sender, goo);
        world.addEntity(e);
        return e;
    }

    @Override
    public int decayRate()
    {
        return 0;
    }

    @Override
    public Fluid getFluid()
    {
        return Registry.CRYSTAL_GOO.get();
    }

    public boolean isEquivalentTo(Fluid fluidIn) {
        return fluidIn == Registry.CRYSTAL_GOO.get() || fluidIn == Registry.CRYSTAL_GOO_FLOWING.get();
    }

    @Override
    protected boolean canSourcesMultiply()
    {
        return false;
    }

    @Override
    protected int getSlopeFindDistance(IWorldReader worldIn)
    {
        return 4;
    }

    @Override
    protected int getLevelDecreasePerBlock(IWorldReader worldIn)
    {
        return 1;
    }

    @Override
    public int getTickRate(IWorldReader p_205569_1_)
    {
        return 20;
    }

    @Override
    public Fluid getStillFluid() { return Registry.CRYSTAL_GOO.get(); }

    @Override
    public Fluid getFlowingFluid() { return Registry.CRYSTAL_GOO_FLOWING.get(); }

    @Override
    protected BlockState getBlockState(FluidState state)
    {
        return Registry.CRYSTAL_GOO_BLOCK.get().getDefaultState().with(FlowingFluidBlock.LEVEL, 0);
    }

    public static class Flowing extends CrystalGoo {

        public Flowing()
        {
            super();
        }

        @Override
        protected void fillStateContainer(StateContainer.Builder<Fluid, FluidState> builder)
        {
            super.fillStateContainer(builder);
            builder.add(GooBase.LEVEL_1_8);
        }

        @Override
        public boolean isSource(FluidState state) { return false; }

        @Override
        public int getLevel(FluidState state) { return state.get(GooBase.LEVEL_1_8); }
    }

    public static class Source extends CrystalGoo {
        public Source() { super(); }

        @Override
        protected void fillStateContainer(StateContainer.Builder<Fluid, FluidState> builder)
        {
            super.fillStateContainer(builder);
            builder.add(GooBase.LEVEL_1_8);
        }

        @Override
        public boolean isSource(FluidState state) { return true; }

        @Override
        public int getLevel(FluidState state) { return state.get(GooBase.LEVEL_1_8); }
    }
}
