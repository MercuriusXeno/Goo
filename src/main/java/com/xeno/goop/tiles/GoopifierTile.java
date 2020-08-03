package com.xeno.goop.tiles;

import com.xeno.goop.GoopMod;
import com.xeno.goop.library.GoopMapping;
import com.xeno.goop.library.GoopValue;
import com.xeno.goop.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class GoopifierTile extends TileEntity implements ITickableTileEntity, ISidedInventory
{
    private Map<String, Double> fluidBuffer;
    private NonNullList<ItemStack> slots = NonNullList.withSize(5, ItemStack.EMPTY);
    private boolean isDoingStuff;
    private int hasNotDoneStuff = 0;
    public GoopifierTile() {
        super(Registry.GOOPIFIER_TILE.get());
        fluidBuffer = new TreeMap<>();
        isDoingStuff = false;
    }

    @Override
    public void tick() {
        if (world == null) {
            return;
        }

        if (world.isRemote()) {
            return;
        }

        // buffered output means we have work left from our last item destruction where a fluidstack can be generated (>= 1f of any fluid)
        if (hasBufferedOutput()) {
            if (tryDistributingFluid()) {
                markDirty();
            }
        }

        // to make production seamless, return only if we are still pumping out goop. If we ran out of work, resume melting items.
        if (!hasBufferedOutput()) {
            for (ItemStack s : slots) {
                if (s.isEmpty()) {
                    continue;
                }
                GoopMapping mapping = getMappingForItem(s);
                if (mapping == null) {
                    continue;
                }
                bufferOutput(mapping);
                s.setCount(s.getCount() - 1);
                markDirty();
                break;
            }
        }

        // latency on when the block goes to a powered off state is to avoid jitter caused by
        // natural hopper timing.
        if (!hasBufferedOutput()) {
            if (hasNotDoneStuff < 10)
                hasNotDoneStuff++;
            if (hasNotDoneStuff >= 10) {
                isDoingStuff = false;
                updateBlockState();
            }
        } else {
            updateBlockState();
            isDoingStuff = true;
            hasNotDoneStuff = 0;
        }
    }

    private GoopMapping getMappingForItem(ItemStack s)
    {
        String key = Objects.requireNonNull(s.getItem().getRegistryName()).toString();
        GoopMapping mapping = GoopMod.mappingHandler.get(key);
        if (mapping.isUnusable()) {
            return null;
        }
        return mapping;
    }

    private void updateBlockState()
    {
        if (world == null) {
            return;
        }

        BlockState state = world.getBlockState(pos);
        if (state.get(BlockStateProperties.POWERED) != isDoingStuff) {
            world.setBlockState(pos, state.with(BlockStateProperties.POWERED, isDoingStuff), Constants.BlockFlags.NOTIFY_NEIGHBORS + Constants.BlockFlags.BLOCK_UPDATE);
        }
    }

    private boolean tryDistributingFluid()
    {
        boolean isAnyWorkDone = false;
        int maxPerTickPerGasket = GoopMod.config.goopTransferRate();
        for(Direction d : getValidGasketDirections()) {
            GoopBulbTile bulb = getBulbInDirection(d);
            if (bulb == null) {
                continue;
            }
            if (!bulb.hasSpace()) {
                continue;
            }

            int workRemaining = maxPerTickPerGasket;
            int workLastCycle = 0;
            boolean isFirstPass = true;
            IFluidHandler cap = tryGettingBulbCapabilities(bulb, d.getOpposite());
            while(workRemaining > 0 && (workLastCycle > 0 || isFirstPass)) {
                isFirstPass = false;
                workLastCycle = 0;
                for (Map.Entry<String, Double> fluidInBuffer : fluidBuffer.entrySet()) {
                    if (fluidInBuffer.getValue() < 1f) {
                        continue;
                    }

                    Fluid f = Registry.getFluid(fluidInBuffer.getKey());
                    if (f == null) {
                        continue;
                    }
                    FluidStack s = new FluidStack(f, Math.min(workRemaining, (int) Math.floor(fluidInBuffer.getValue())));
                    int fillResult = cap.fill(s, IFluidHandler.FluidAction.SIMULATE);
                    if (fillResult > 0) {
                        fillResult = cap.fill(s, IFluidHandler.FluidAction.EXECUTE);
                    } else {
                        continue;
                    }
                    isAnyWorkDone = true;
                    workRemaining -= fillResult;
                    workLastCycle += fillResult;
                    fluidBuffer.put(fluidInBuffer.getKey(), fluidInBuffer.getValue() - fillResult);
                }
            }
        }

        return isAnyWorkDone;
    }

    private IFluidHandler tryGettingBulbCapabilities(GoopBulbTile bulb, Direction dir)
    {
        LazyOptional<IFluidHandler> lazyCap = bulb.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, dir);
        IFluidHandler cap = null;
        try {
            cap = lazyCap.orElseThrow(() -> new Exception("Fluid handler expected from a tile entity that didn't contain one!"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cap;
    }

    private GoopBulbTile getBulbInDirection(Direction dir) {
        if (world == null) {
            return null;
        }
        BlockPos posInDirection = this.pos.offset(dir);
        TileEntity tile = world.getTileEntity(posInDirection);
        if (tile == null) {
            return null;
        }
        if (!(tile instanceof GoopBulbTile)) {
            return null;
        }
        return (GoopBulbTile)tile;
    }

    private final Direction[] VALID_GASKET_DIRECTIONS = new Direction[] { Direction.EAST, Direction.WEST, Direction.UP };
    private Direction[] getValidGasketDirections()
    {
        return VALID_GASKET_DIRECTIONS;
    }

    private void bufferOutput(GoopMapping mapping)
    {
        for(GoopValue v : mapping.values()) {
            String key = v.getFluidResourceLocation();
            if (fluidBuffer.containsKey(key)) {
                fluidBuffer.put(key, fluidBuffer.get(key) + v.getAmount());
            } else {
                fluidBuffer.put(key, v.getAmount());
            }
        }
    }

    private boolean hasBufferedOutput()
    {
        return fluidBuffer.size() > 0 && fluidBuffer.entrySet().stream().anyMatch(b -> b.getValue() >= 1d);
    }

    @Override
    public int[] getSlotsForFace(Direction side)
    {
        if (side == Direction.SOUTH) {
            return new int[]{0, 1, 2, 3, 4};
        }
        return new int[0];
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn, @Nullable Direction direction)
    {
        if (direction != Direction.SOUTH) {
            return false;
        }

        if (getMappingForItem(itemStackIn) == null) {
            return false;
        }

        for(ItemStack s : slots) {
            if (s.isEmpty()) {
                return true;
            }
            if (!s.equals(itemStackIn, false)) {
                continue;
            }
            if (s.getMaxStackSize() > s.getCount()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canExtractItem(int i, ItemStack stack, Direction direction)
    {
        if (direction == Direction.DOWN) {
            return slots.get(i).equals(stack, false);
        }

        return false;
    }

    @Override
    public int getSizeInventory()
    {
        return 5;
    }

    @Override
    public boolean isEmpty()
    {
        for(ItemStack s : slots) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index)
    {
        return slots.get(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count)
    {
        if (slots.get(index).isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (slots.get(index).getCount() <= count) {
            return removeStackFromSlot(index);
        }

        slots.get(index).setCount(slots.get(index).getCount() - count);
        ItemStack result = slots.get(index).copy();
        result.setCount(count);
        return result;

    }

    @Override
    public ItemStack removeStackFromSlot(int index)
    {
        ItemStack result = slots.get(index).copy();
        slots.set(index, ItemStack.EMPTY);
        return result;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack stack)
    {
        slots.set(i, stack);
    }

    @Override
    public boolean isUsableByPlayer(PlayerEntity player)
    {
        return false;
    }

    @Override
    public void clear()
    {
        // ???
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    private CompoundNBT serializeGoop()  {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("count", fluidBuffer.size());
        int index = 0;
        for(Map.Entry<String, Double> e : fluidBuffer.entrySet()) {
            CompoundNBT goopTag = new CompoundNBT();
            goopTag.putString("key", e.getKey());
            goopTag.putDouble("value", e.getValue());
            tag.put("goop" + index, goopTag);
            index++;
        }
        return tag;
    }

    private CompoundNBT serializeItems()
    {
        CompoundNBT itemTag = new CompoundNBT();
        ItemStackHelper.saveAllItems(itemTag, slots);
        return itemTag;
    }

    private void deserializeGoop(CompoundNBT tag) {
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT goopTag = tag.getCompound("goop" + i);
            String key = goopTag.getString("key");
            double value = goopTag.getDouble("value");
            fluidBuffer.put(key, value);
        }
    }

    private void deserializeItems(CompoundNBT tag)
    {
        CompoundNBT itemTag = tag.getCompound("items");
        this.slots = NonNullList.withSize(this.getSizeInventory(), ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(itemTag, this.slots);
    }

    @Override
    public CompoundNBT write(CompoundNBT tag)
    {
        tag.put("goop", serializeGoop());
        tag.put("items", serializeItems());
        tag.putBoolean("is_doing_stuff", isDoingStuff);
        return super.write(tag);
    }

    @Override
    public void read(CompoundNBT tag)
    {
        super.read(tag);
        deserializeGoop(tag);
        deserializeItems(tag);
        isDoingStuff = tag.getBoolean("is_doing_stuff");
    }

    public void spewItems()
    {
        for (ItemStack s : slots) {
            ItemEntity itemEntity = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), s);
            itemEntity.setDefaultPickupDelay();
            world.addEntity(itemEntity);
        }
    }
}
