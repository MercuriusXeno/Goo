package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.GooPad;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.util.GooTank;
import com.xeno.goo.util.IGooTank;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import java.util.List;

public class PadTile extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver
{

    private static final int VAPOR_PARTICLE_DENSITY = 12;
    private int cooldown = 0;
    private int sprayTime = 0;

    public PadTile()
    {
        super(Registry.PAD_TILE.get());
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
            trySprayingContentsAsParticles();
            return;
        }

        if (isOnCooldown()) {
            cooldown--;
        }

        specialSlimePadLogic();

        trySprayingContentsAsEffect();

        // try pulling fluid from adjacent fluid handlers, but only goo
        Direction d = Direction.DOWN;
        TileEntity e = world.getTileEntity(pos.offset(d));
        if (e != null) {
            LazyOptional<IFluidHandler> lazyHandler = e.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
            if (lazyHandler.isPresent() && lazyHandler.resolve().isPresent()) {
                IFluidHandler handler = lazyHandler.resolve().get();
                int amountAllowed = GooMod.config.gooTransferRate();
                FluidStack drained = handler.drain(amountAllowed, FluidAction.SIMULATE);
                if (goo.fill(drained, FluidAction.SIMULATE) > 0) {
                    int spaceRemaining = goo.getRemainingCapacity();
                    if (amountAllowed > spaceRemaining) {
                        amountAllowed = spaceRemaining;
                    }
                    if (amountAllowed > 0) {
                        goo.fill(handler.drain(amountAllowed, FluidAction.EXECUTE), FluidAction.EXECUTE);
                    }
                }
            }
        }
    }

    // if the pad contains slime goo it has a special AABB projection routine
    // to reset the fall distance of any incoming entities.
    private void specialSlimePadLogic() {
        if (!goo.getFluidInTank(0).getFluid().equals(Registry.SLIME_GOO.get())) {
            return;
        }
        AxisAlignedBB pressurePadBox = GooPad.PRESSURE_AABB.offset(this.getPos());
        AxisAlignedBB detectionField = pressurePadBox.grow(3d, 0d, 3d).expand(0d, 3d, 0d);
        // get entities in a healthy range (3 blocks)
        // project their motion and see if anything is coming at us.
        List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(null, detectionField);
        if (entities.isEmpty()) {
            return;
        }

        for(Entity e : entities) {
            if (e.getBoundingBox().expand(e.getMotion()).intersects(pressurePadBox)) {
                if (e.fallDistance > 0f) {
                    e.fallDistance = 0f;
                }
            }
        }
    }

    private void trySprayingContentsAsEffect() {
        if (!shouldTrigger()) {
            return;
        }

        // cooldown doesn't stop draining goo, it just prevents entity effects for a brief time so these aren't remarkably overpowered.


        // TODO do effect
    }

    private boolean isOnCooldown() {
        return cooldown > 0;
    }

    private void trySprayingContentsAsParticles() {
        if (!shouldTrigger()) {
            return;
        }

        BasicParticleType type = Registry.sprayParticleFromFluid(goo.getFluidInTankInternal(0).getRawFluid());
        for (int i = 0; i < VAPOR_PARTICLE_DENSITY; i++) {
            Vector3d origin = new Vector3d(this.getPos().getX() + 0.5d, this.getPos().getY() + 0.0625d, this.getPos().getZ() + 0.5d);
            double dx = (this.world.rand.nextDouble() - 0.5d) / 8d; // roughly the width of the nozzle
            double dz = (this.world.rand.nextDouble() - 0.5d) / 8d; // same for z

            Vector3d pos = origin.add(dx, 0d, dz);

            // now the motion
            double mx = (this.world.rand.nextDouble() - 0.5d) / 12d;
            double my = (this.world.rand.nextDouble()) / 10d;
            double mz = (this.world.rand.nextDouble() - 0.5d) / 12d;

            this.world.addParticle(type, pos.x, pos.y, pos.z, mx, my, mz);
        }

    }

    private boolean shouldTrigger() {
        boolean isTriggered = this.getBlockState().get(BlockStateProperties.TRIGGERED);

        if (!isTriggered || goo.isEmpty() || !(goo.getFluidInTank(0).getFluid() instanceof GooFluid)) {
            return false;
        }

        return true;
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

        return new GooTank(this::getStorageCapacity).setUniversalFilter(stack -> stack.getFluid() instanceof GooFluid).setChangeCallback(this::onContentsChanged);
    }

    @Override
    public int getBaseCapacity() {

        return GooMod.config.padCapacity();
    }

    @Override
    public int getStorageMultiplier() {

        return 1;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        // fixes an issue where trying to get the tile entity during a render state for *breaking it* (and it being an item)
        // causes the facing() call to crash with an NRE.
        if (side == null) {
            return super.getCapability(cap, null);
        }
        if (side != Direction.DOWN) {
            return LazyOptional.empty();
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
