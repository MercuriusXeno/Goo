package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.CrucibleRecipe;
import com.xeno.goo.library.CrucibleRecipes;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.GooFlowPacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TroughTile extends GooContainerAbstraction implements ITickableTileEntity,
        FluidUpdatePacket.IFluidPacketReceiver, GooFlowPacket.IGooFlowReceiver
{
    private final TroughFluidHandler fluidHandler = createHandler();
    private final LazyOptional<TroughFluidHandler> lazyHandler = LazyOptional.of(() -> fluidHandler);
    private float verticalFillIntensity = 0f;
    private Fluid verticalFillFluid = Fluids.EMPTY;
    private int verticalFillDelay = 0;

    public TroughTile()
    {
        super(Registry.TROUGH_TILE.get());
        goo.addAll(Collections.singletonList(FluidStack.EMPTY));
    }

    @Override
    protected void invalidateCaps() {
        super.invalidateCaps();
        lazyHandler.invalidate();
    }

    public FluidStack onlyGoo() {
        if (goo().size() == 0) {
            goo.addAll(Collections.singletonList(FluidStack.EMPTY));
        }

        return goo.get(0);
    }

    public boolean hasFluid(Fluid fluid)
    {
        return onlyGoo() != FluidStack.EMPTY && onlyGoo().getFluid().equals(fluid);
    }

    @Override
    public void updateFluidsTo(List<FluidStack> fluids)
    {
        if (fluids.size() == 0) {
            this.setGoo(FluidStack.EMPTY);
        } else {
            this.setGoo(fluids.get(0));
        }
    }

    @Override
    public void tick()
    {
        if (world == null) {
            return;
        }

        if (world.isRemote) {
            // vertical fill visuals are client-sided, for a reason. We get sent activity from server but
            // the decay is local because that's needless packets otherwise. It's deterministic.
            decayVerticalFillVisuals();
            return;
        }

        // try pulling fluid from adjacent fluid handlers, but only goo
        Direction d = facing();
        TileEntity e = world.getTileEntity(pos.offset(d));
        if (e != null) {
            LazyOptional<IFluidHandler> lazyHandler = e.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
            if (lazyHandler.isPresent() && lazyHandler.resolve().isPresent()) {
                IFluidHandler handler = lazyHandler.resolve().get();
                int amountAllowed = GooMod.config.gooTransferRate();
                FluidStack drained = handler.drain(amountAllowed, IFluidHandler.FluidAction.SIMULATE);
                if (shouldAllowFluid(drained)) {
                    int spaceRemaining = getSpaceRemaining(drained);
                    if (amountAllowed > spaceRemaining) {
                        amountAllowed = spaceRemaining;
                    }
                    if (amountAllowed > 0) {
                        fluidHandler.fill(handler.drain(amountAllowed,
                                IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
                        toggleVerticalFillVisuals(drained.getFluid());
                    }
                }
            }
        }
    }

    public Direction facing() {
        return getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public void updateVerticalFill(Fluid f, float intensity)
    {
        if (intensity > 0) {
            this.verticalFillDelay = 3;
        }
        this.verticalFillIntensity = intensity;
        this.verticalFillFluid = f;
    }

    public void toggleVerticalFillVisuals(Fluid f, float intensity)
    {
        verticalFillFluid = f;
        verticalFillIntensity = intensity; // default fill intensity is just "on", essentially
        if (world == null) {
            return;
        }
        Networking.sendToClientsAround(new GooFlowPacket(world.getDimensionKey(), pos, verticalFillFluid, verticalFillIntensity), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.getDimensionKey())), pos);
    }

    public void toggleVerticalFillVisuals(Fluid f)
    {
        toggleVerticalFillVisuals(f, 1f);
    }

    public float verticalFillIntensity()
    {
        return this.verticalFillIntensity;
    }

    public FluidStack verticalFillFluid()
    {
        return new FluidStack(verticalFillFluid, 1);
    }

    private float verticalFillDecay() {
        // throttle the intensity decay so it doesn't look so jittery. This will cause the first few frames to be slow
        // changing, but later frames will be proportionately somewhat faster.
        float decayRate = 0.2f;
        return Math.min(verticalFillIntensity * decayRate, 0.125f);
    }

    public void decayVerticalFillVisuals() {
        if (!isVerticallyFilled()) {
            return;
        }
        if (verticalFillDelay > 0) {
            verticalFillDelay--;
            return;
        }
        verticalFillIntensity -= verticalFillDecay(); // flow reduces each frame work tick until there's nothing left.
        float cutoffThreshold = 0.05f;
        if (verticalFillIntensity <= cutoffThreshold) {
            disableVerticalFillVisuals();
        }
    }

    public void disableVerticalFillVisuals() {
        verticalFillFluid = Fluids.EMPTY;
        verticalFillIntensity = 0f;
    }

    public boolean isVerticallyFilled() {
        return !verticalFillFluid.equals(Fluids.EMPTY) && verticalFillIntensity > 0f;
    }

    public void setGoo(FluidStack fluidStack)
    {
        if (goo.size() == 0) {
            goo.add(fluidStack);
        } else {
            goo.set(0, fluidStack);
        }
    }

    public void onContentsChanged() {
        if (world == null) {
            return;
        }
        if (!world.isRemote) {
            if (world.getServer() == null) {
                return;
            }
            Networking.sendToClientsAround(new FluidUpdatePacket(world.getDimensionKey(), pos, goo), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.getDimensionKey())), pos);
        }
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        tag.put("goo", serializeGoo());
        return super.write(tag);
    }

    public void read(BlockState state, CompoundNBT tag)
    {
        CompoundNBT gooTag = tag.getCompound("goo");
        deserializeGoo(gooTag);
        super.read(state, tag);
        onContentsChanged();
    }


    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (side == Direction.UP) {
            return LazyOptional.empty();
        }
        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return lazyHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    private TroughFluidHandler createHandler() {
        return new TroughFluidHandler(this);
    }

    public int getSpaceRemaining(FluidStack stack)
    {
        if (!onlyGoo().isEmpty() && !onlyGoo().getFluid().equals(stack.getFluid())) {
            return 0;
        }

        // one last check; we don't allow "inert" fluids or inherently invalid fluids.
        if (!shouldAllowFluid(stack)) {
            return 0;
        }
        return fluidHandler.getTankCapacity(0) - onlyGoo().getAmount();
    }

    private boolean shouldAllowFluid(FluidStack stack)
    {
        if (stack.isEmpty()) {
            return false;
        }

        // if we already contain this fluid we've passed this test already.
        if (onlyGoo().isFluidEqual(stack)) {
            return true;
        }

        if (!onlyGoo().isEmpty()) {
            return false;
        }

        return stack.getFluid() instanceof GooFluid;
    }

    @Override
    public FluidStack getGooFromTargetRayTraceResult(Vector3d hitVector, Direction face, RayTraceTargetSource targetSource)
    {
        return onlyGoo();
    }

    @Override
    public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource)
    {
        return fluidHandler;
    }
}
