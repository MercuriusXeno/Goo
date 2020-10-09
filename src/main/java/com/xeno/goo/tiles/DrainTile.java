package com.xeno.goo.tiles;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class DrainTile extends TileEntity implements ITickableTileEntity
{
    private static final int DEFAULT_DELAY = 5;
    private FluidStack goo;
    private int lastGooAmount;
    private int delay;
    private Entity proxySender;
    public DrainTile()
    {
        super(Registry.DRAIN_TILE.get());
        goo = FluidStack.EMPTY;
        lastGooAmount = 0;
        delay = DEFAULT_DELAY;
    }

    public boolean canFill(FluidStack s) {
        return goo.isEmpty() || goo.getFluid().equals(s.getFluid());
    }

    public void fill(FluidStack s, Entity proxySender) {
        if (!canFill(s)) {
            return;
        }
        this.proxySender = proxySender;
        if (goo.isEmpty()) {
            goo = s;
        } else {
            goo.setAmount(goo.getAmount() + s.getAmount());
        }
    }

    @Override
    public void tick()
    {
        if (world == null) {
            return;
        }

        if (world.isRemote) {
            return;
        }

        if (lastGooAmount > 0 && lastGooAmount == goo.getAmount() && delay > 0) {
            delay--;
        } else {
            delay = DEFAULT_DELAY;
        }

        if (goo.getAmount() > 0 && delay == 0) {
            tryPushingFluid();
        }

        // track how much goo we had at the end of this tick for next tick
        // if this doesn't change for 5 ticks
        lastGooAmount = goo.getAmount();
    }

    private void tryPushingFluid()
    {
        if (this.world instanceof ServerWorld) {
            GooBlob newBlob = new GooBlob(Registry.GOO_BLOB.get(), this.world, proxySender, goo, this.pos);
            this.world.addEntity(newBlob);
            this.goo = FluidStack.EMPTY;
        }
    }

    public Direction facing()
    {
        return this.getBlockState().get(BlockStateProperties.FACING);
    }

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
