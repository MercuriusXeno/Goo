package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.GooFlowPacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.util.GooTank;
import com.xeno.goo.util.IGooTank;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import java.util.Objects;

public class TroughTile extends GooContainerAbstraction implements ITickableTileEntity,
        FluidUpdatePacket.IFluidPacketReceiver, GooFlowPacket.IGooFlowReceiver
{
    private float verticalFillIntensity = 0f;
    private Fluid verticalFillFluid = Fluids.EMPTY;
    private int verticalFillDelay = 0;

    public TroughTile()
    {
        super(Registry.TROUGH_TILE.get());
    }

    @Override
    public void updateFluidsTo(PacketBuffer fluids) {

        this.goo.readFromPacket(fluids);
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
                if (goo.fill(drained, FluidAction.SIMULATE) > 0) {
                    int spaceRemaining = goo.getRemainingCapacity();
                    if (amountAllowed > spaceRemaining) {
                        amountAllowed = spaceRemaining;
                    }
                    if (amountAllowed > 0) {
                        goo.fill(handler.drain(amountAllowed,
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

    public void onContentsChanged() {
        if (world == null || world.isRemote) {
            return;
        }
        Networking.sendToClientsAround(new FluidUpdatePacket(world.getDimensionKey(), pos, goo), (ServerWorld) world, pos);
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    @Override
    protected IGooTank createGooTank() {

        return new GooTank(this::getStorageCapacity).setFilter(stack -> stack.getFluid() instanceof GooFluid).setChangeCallback(this::onContentsChanged);
    }

    @Override
    public int getBaseCapacity() {

        return GooMod.config.troughCapacity();
    }

    @Override
    public int getStorageMultiplier() {

        return 1;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (side == Direction.UP) {
            return LazyOptional.empty();
        }
        //noinspection StatementWithEmptyBody
        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && side == facing()) {
            // maybe?
        }
        return super.getCapability(cap, side);
    }

    @Override
    public FluidStack getGooFromTargetRayTraceResult(Vector3d hitVector, Direction face, RayTraceTargetSource targetSource)
    {
        return goo.getFluidInTankInternal(0);
    }

    @Override
    public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource)
    {
        return goo;
    }
}
