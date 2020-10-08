package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.EntryHelper;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.network.ChangeItemTargetPacket;
import com.xeno.goo.network.GooFlowPacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Objects;

import static net.minecraft.item.ItemStack.EMPTY;

public class LobberTile extends TileEntity implements ITickableTileEntity
{
    private static final int HALF_SECOND_TICKS = 10;
    private static final int ONE_SECOND_TICKS = 20;


    public LobberTile()
    {
        super(Registry.LOBBER_TILE.get());
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

        tryPushingFluid();
    }

    private void tryPushingFluid()
    {
        // TODO
    }

    public Direction facing()
    {
        return this.getBlockState().get(BlockStateProperties.FACING);
    }

    private Direction sourceDirection() {
        return this.facing().getOpposite();
    }

    private Direction targetDirection() {
        return this.facing();
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
