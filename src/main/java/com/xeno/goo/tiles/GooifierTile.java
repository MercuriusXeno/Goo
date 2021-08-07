package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.*;

public class GooifierTile extends FluidHandlerInteractionAbstraction implements ITickableTileEntity, ISidedInventory
{
    private List<FluidStack> progressFromItem = new ArrayList<>();
    private ItemStack slot = ItemStack.EMPTY;
    private boolean isDoingStuff;
    public GooifierTile() {
        super(Registry.GOOIFIER_TILE.get());
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

        if (getBlockState().get(BlockStateProperties.POWERED)) {
            return;
        }

        // buffered output means we have work left from our last item destruction where a fluidstack can be generated (>= 1f of any fluid)
        if (hasBufferedOutput()) {
            tryDistributingFluid();
        }

        // to make production seamless, return only if we are still pumping out goo. If we ran out of work, resume melting items.
        if (!hasBufferedOutput()) {
            if (!slot.isEmpty()) {
                GooEntry mapping = getEntryForItem(slot);
                if (mapping != null) {
                    bufferOutput(mapping);
                    slot.shrink(1);
                }
            }
        }
    }

    private GooEntry getEntryForItem(ItemStack e)
    {
        // String key = Objects.requireNonNull(s.getItem().getRegistryName()).toString();
        if (world == null) {
            return GooEntry.UNKNOWN;
        }

        GooEntry mapping = Equivalencies.getEntry(world, e.getItem());

        if (mapping.isUnusable()) {
            return GooEntry.UNKNOWN;
        }

        List<FluidStack> itemHandlerContents = FluidHandlerHelper.contentsOfItemStack(e);
        List<FluidStack> tileHandlerContents = FluidHandlerHelper.contentsOfTileStack(e);
        if (!itemHandlerContents.isEmpty()) {
            return mapping.addGooContentsToMapping(itemHandlerContents);
        } else if (!tileHandlerContents.isEmpty()) {
            return mapping.addGooContentsToMapping(tileHandlerContents);
        } else if (GooMod.config.canDamagedItemsBeGooified()) { // this is here to prevent "damage" containers from falsely reporting their damage values as durability
            // you may not melt down items that are damageable *and damaged*. Sorry not sorry
            if (e.isDamageable() && e.isDamaged()) {
                return mapping.scale((e.getMaxDamage() * 1d - e.getDamage()) / e.getMaxDamage());
            }
        } else {
            return GooEntry.UNKNOWN;
        }

        return mapping;
    }

    private class DistributionState {
        boolean isFirstPass;
        int workRemaining;
        int workLastCycle;

        private DistributionState (boolean isFirstPass, int workRemaining, int workLastCycle) {
            this.isFirstPass = isFirstPass;
            this.workRemaining = workRemaining;
            this.workLastCycle = workLastCycle;
        }

        private void setNextPass() {
            this.isFirstPass = false;
        }

        private void addWork(int work) {
            workRemaining -= work;
            workLastCycle += work;
        }
    }
    private void tryDistributingFluid()
    {
        for(Direction d : getValidDirections()) {
            DistributionState[] state = {new DistributionState(true, GooMod.config.gooProcessingRate(), 0)};
            LazyOptional<IFluidHandler> cap = fluidHandlerInDirection(d);
            cap.ifPresent((c) -> state[0] = doDistribution(c, state[0]));
        }
    }

    private DistributionState doDistribution(IFluidHandler cap, DistributionState state)
    {
        while(state.workRemaining > 0 && (state.workLastCycle > 0 || state.isFirstPass)) {
            state.setNextPass();
            state.workLastCycle = 0;
            for (FluidStack fluidInBuffer : progressFromItem) {
                if (fluidInBuffer.getAmount() <= 0) {
                    continue;
                }

                int fillResult = cap.fill(fluidInBuffer, IFluidHandler.FluidAction.SIMULATE);
                if (fillResult > 0) {
                    fillResult = cap.fill(fluidInBuffer, IFluidHandler.FluidAction.EXECUTE);
                } else {
                    continue;
                }
                state.addWork(fillResult);
                fluidInBuffer.setAmount(fluidInBuffer.getAmount() - fillResult);

                if (state.workRemaining <= 0) break;
            }
        }
        return state;
    }

    public static final Map<Direction, Direction[]> CACHED_DIRECTIONS = new HashMap<>();
    private Direction[] getValidDirections()
    {
        if (!CACHED_DIRECTIONS.containsKey(facing())) {
            CACHED_DIRECTIONS.put(facing(), new Direction[]{ Direction.UP,
                    (this.facing().getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH),
                    (this.facing().getAxis() == Direction.Axis.Z ? Direction.WEST : Direction.NORTH)
            });
        }
        return CACHED_DIRECTIONS.get(facing());
    }

    private Direction facing() {
        return getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
    }

    private void bufferOutput(GooEntry mapping)
    {
        for(GooValue v : mapping.values()) {
            Fluid f = Registry.getFluid(v.getFluidResourceLocation());
            FluidStack fluidBuffer = fluidInBuffer(f);
            if (!fluidBuffer.isEmpty()) {
                fluidBuffer.setAmount(fluidBuffer.getAmount() + v.amount());
            } else {
                progressFromItem.add(new FluidStack(f, v.amount()));
            }
        }
    }

    private FluidStack fluidInBuffer(Fluid fluid) {
        Optional<FluidStack> existingStack = progressFromItem.stream().filter(f -> f.getFluid().equals(fluid)).findFirst();

        return existingStack.orElse(FluidStack.EMPTY);
    }

    private boolean hasBufferedOutput()
    {
        return !progressFromItem.isEmpty() && progressFromItem.stream().anyMatch(f -> !f.isEmpty());
    }

    @Override
    public int[] getSlotsForFace(Direction side)
    {
        Direction facing = getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
        if (side == facing.getOpposite()) {
            return new int[]{0, 1, 2, 3, 4};
        }
        return new int[0];
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn, Direction direction)
    {
        Direction facing = getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
        if (direction != facing.getOpposite()) {
            return false;
        }

        if (itemStackIn.isDamageable() && itemStackIn.isDamaged()) {
            return false;
        }

        if (getEntryForItem(itemStackIn) == null) {
            return false;
        }

        if (slot.isEmpty()) {
            return true;
        }
        return false;
    }

    @Override
    public boolean canExtractItem(int i, ItemStack stack, Direction direction)
    {
        return !isEmpty();
    }

    @Override
    public int getSizeInventory()
    {
        return 1;
    }

    @Override
    public boolean isEmpty()
    {
        return slot.isEmpty();
    }

    @Override
    public ItemStack getStackInSlot(int index)
    {
        if (index > 0) {
            return ItemStack.EMPTY;
        }
        return slot;
    }

    @Override
    public ItemStack decrStackSize(int index, int count)
    {
        if (isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (slot.getCount() <= count) {
            return removeStackFromSlot(0);
        }
        ItemStack result = slot.copy();
        result.setCount(count);
        slot.shrink(count);
        return result;

    }

    @Override
    public ItemStack removeStackFromSlot(int index)
    {
        if (index > 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = slot.copy();
        slot = ItemStack.EMPTY;
        return result;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack stack)
    {
        if (i > 0) {
            return;
        }
        slot = stack.copy();
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

    private CompoundNBT serializeGoo()  {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("count", progressFromItem.size());
        int index = 0;
        for(FluidStack f : progressFromItem) {
            CompoundNBT gooTag = f.writeToNBT(new CompoundNBT());
            tag.put("goo_" + index, gooTag);
            index++;
        }
        return tag;
    }

    private CompoundNBT serializeItem()
    {
        return slot.write(new CompoundNBT());
    }

    private void deserializeGoo(CompoundNBT tag) {
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            if (tag.contains("goo_" + i)) {
                CompoundNBT gooTag = tag.getCompound("goo_" + i);
                progressFromItem.add(FluidStack.loadFluidStackFromNBT(gooTag));
            }
        }
    }

    private void deserializeItems(CompoundNBT tag)
    {
        if (tag.contains("item")) {
            this.slot = ItemStack.read(tag.getCompound("item"));
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT tag)
    {
        tag.put("item", serializeItem());
        tag.put("goo", serializeGoo());
        tag.putBoolean("is_doing_stuff", isDoingStuff);
        return super.write(tag);
    }

    @Override
    public void read(BlockState state, CompoundNBT tag)
    {
        super.read(state, tag);
        deserializeItems(tag);
        deserializeGoo(tag);
        isDoingStuff = tag.getBoolean("is_doing_stuff");
    }

    public void spewItems()
    {
        ItemEntity itemEntity = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), slot);
        itemEntity.setDefaultPickupDelay();
        world.addEntity(itemEntity);
    }
}
