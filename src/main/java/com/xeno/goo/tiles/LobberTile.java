package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Optional;

public class LobberTile extends TileEntity
{
    private int lastFiredDirection = -1;
    public LobberTile()
    {
        super(Registry.LOBBER_TILE.get());
    }

    public boolean tryPushingFluid(Direction d)
    {
        TileEntity source = tileAtSource(d);

        if (source == null) {
            return false;
        }

        Optional<IFluidHandler> sourceHandler = FluidHandlerHelper.capabilityOfNeighbor(this, d).resolve();
        return sourceHandler.filter(this::lobFluid).isPresent();
    }

    private boolean lobFluid(IFluidHandler cap) {
        // iterate over all tanks and try a simulated drain until something sticks.
        for (int i = 0; i < cap.getTanks(); i++) {
            FluidStack simulatedDrain = cap.getFluidInTank(i).copy();
            if (simulatedDrain.isEmpty()) {
                continue;
            }

            if (!(simulatedDrain.getFluid() instanceof GooFluid)) {
                continue;
            }

            // figure out how much a gauntlet would throw in an interaction, that's how much we want to pull.
            // we try to get the full amount of drain but a smaller fluid stack just means a smaller, weaker projectile
            int drainAmountThrown = GooMod.config.thrownGooAmount(cap.getFluidInTank(0).getFluid());

            // -1 is disabled, 0 is just about to crash the server.
            if (drainAmountThrown <= -1) {
                return false;
            }

            if (drainAmountThrown == 0) {
                drainAmountThrown = 1;
            }

            if (simulatedDrain.getAmount() > drainAmountThrown) {
                simulatedDrain.setAmount(drainAmountThrown);
            }

            // skip if we're empty
            simulatedDrain = cap.drain(simulatedDrain, IFluidHandler.FluidAction.SIMULATE);
            if (simulatedDrain.isEmpty()) {
                continue;
            }

            FluidStack result = cap.drain(simulatedDrain, IFluidHandler.FluidAction.EXECUTE);
            // throw that blob
            Vector3d offsetVec = Vector3d.copy(facing().getDirectionVec());
            float cubicSizeHalf = GooBlob.cubicSize(result.getAmount()) / 2f;
            Vector3d sizeOffset = offsetVec.add(offsetVec.scale(cubicSizeHalf));
            Vector3d spawnPos = Vector3d.copy(this.getPos()).add(0.5d, 0.5d, 0.5d) // move to center of block
                    .add(offsetVec.scale(0.52d)) // move to edge of block in facing
                    .add(sizeOffset); // move out based on half the edge length expected so it's pretty much out of the block
            GooBlob blob = GooBlob.createLobbedBlob(world, result, spawnPos);

            blob.setMotion(offsetVec);
            world.addEntity(blob);
            return true;
        }
        return false;
    }

    private TileEntity tileAtSource(Direction d)
    {
        return FluidHandlerHelper.tileAtDirection(this, d);
    }

    public Direction facing() { return this.getBlockState().get(BlockStateProperties.FACING); }

    private boolean triggered() { return this.getBlockState().get(BlockStateProperties.TRIGGERED); }

    @Override
    public CompoundNBT write(CompoundNBT tag)
    {
        return super.write(tag);
    }

    @Override
    public void read(BlockState state, CompoundNBT tag)
    {
        super.read(state, tag);
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }
}
