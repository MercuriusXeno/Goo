package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.BulbFluidHandler;
import com.xeno.goo.library.Compare;
import com.xeno.goo.network.BulbVerticalFillPacket;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.setup.Registry;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.NumberFormat;
import java.util.*;

public class GooBulbTile extends TileEntity implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver, BulbVerticalFillPacket.IVerticalFillReceiver {
    private BulbFluidHandler fluidHandler = createHandler();
    private LazyOptional<BulbFluidHandler> handler = LazyOptional.of(() -> fluidHandler);
    private List<FluidStack> goo = new ArrayList<>();
    private float verticalFillIntensity = 0f;
    private Fluid verticalFillFluid = Fluids.EMPTY;

    public GooBulbTile() {
        super(Registry.GOO_BULB_TILE.get());
    }

    @Override
    public void tick() {
        if (world == null) {
            return;
        }

        if (world.isRemote) {
            // vertical fill visuals are client-sided, for a reason. We get sent activity from server but
            // the decay is local because that's needless packets otherwise. It's deterministic.
            decayVerticalFillVisuals();
            return;
        }

        doVerticalDrain();
        doLateralShare();
    }

    public List<FluidStack> goo()
    {
        return this.goo;
    }

    @Override
    public void updateVerticalFill(Fluid f, float intensity)
    {
        this.verticalFillIntensity = intensity;
        this.verticalFillFluid = f;
    }

    public void toggleVerticalFillVisuals(Fluid f)
    {
        verticalFillFluid = f;
        verticalFillIntensity = 1f; // default fill intensity is just "on", essentially
        if (world == null) {
            return;
        }
        Networking.sendToClientsAround(new BulbVerticalFillPacket(world.func_234923_W_(), pos, verticalFillFluid, verticalFillIntensity), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.func_234923_W_())), pos);
    }

    public float verticalFillIntensity()
    {
        return this.verticalFillIntensity;
    }

    public FluidStack verticalFillFluid()
    {
        return new FluidStack(verticalFillFluid, 1);
    }

    private float VERTICAL_FILL_DECAY_RATE = 0.5f;
    private float VERTICAL_FILL_CUTOFF_THRESHOLD = 0.05f;
    private float verticalFillDecay() {
        // throttle the intensity decay so it doesn't look so jittery. This will cause the first few frames to be slow
        // changing, but later frames will be proportionately somewhat faster.
        return 1f - Math.min(verticalFillIntensity * VERTICAL_FILL_DECAY_RATE, 0.03f);
    }

    public void decayVerticalFillVisuals() {
        if (!isVerticallyFilled()) {
            return;
        }
        verticalFillIntensity -= verticalFillDecay(); // flow reduces each frame work tick until there's nothing left.
        if (verticalFillIntensity <= VERTICAL_FILL_CUTOFF_THRESHOLD) {
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

    public void pruneEmptyGoo()
    {
        goo.removeIf(FluidStack::isEmpty);
    }

    public void addGoo(FluidStack fluidStack)
    {
        goo.add(fluidStack);
    }

    // if placed above another bulb, the bulb above will drain everything downward.
    private void doVerticalDrain() {
        if (this.goo.size() == 0) {
            return;
        }
        // check the tile below us, if it's not a bulb, bail.
        GooBulbTile bulb = getBulbInDirection(Direction.DOWN);
        if (bulb == null) {
            return;
        }

        // try fetching the bulb capabilities (upward) and throw an exception if it fails. return if null.
        IFluidHandler cap = tryGettingBulbCapabilities(bulb, Direction.UP);
        if (cap == null) {
            return;
        }

        // the maximum amount you can drain in a tick is here.
        int simulatedDrainLeft = GooMod.mainConfig.gooTransferRate();

        // iterate over the stacks and ensure
        for(FluidStack s : goo) {
            if (simulatedDrainLeft <= 0) {
                break;
            }
            simulatedDrainLeft -= trySendingFluidToBulb(simulatedDrainLeft, s, cap, bulb, true);
        }

        // avoid concurrent modifications to the indices of the array until all work is final.
        pruneEmptyGoo();
    }

    // bulbs adjacent to one another laterally "equalize" their contents to allow some hotswapping behaviors.
    private void doLateralShare() {
        if (this.goo.size() == 0) {
            return;
        }
        for(Direction d : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST })
        {
            // check the tile in this direction, if it's not another bulb, pass;
            GooBulbTile bulb = getBulbInDirection(d);
            if (bulb == null) {
                continue;
            }

            // try fetching the bulb capabilities in the opposing direction and throw an exception if it fails. return if null.
            IFluidHandler cap = tryGettingBulbCapabilities(bulb, d.getOpposite());
            if (cap == null) {
                return;
            }

            // the maximum amount you can drain in a tick is here.
            int simulatedDrainLeft =  GooMod.mainConfig.gooTransferRate();

            // iterate over the stacks and ensure
            for(FluidStack s : goo) {
                if (simulatedDrainLeft <= 0) {
                    break;
                }
                // only "distribute" to the bulb adjacent if it has less than this one of whatever type (equalizing)
                int bulbContains = bulb.getSpecificGooType(s.getFluid()).getAmount();
                int delta = s.getAmount() - bulbContains;
                // don't send it anything to avoid passing back 1 mB repeatedly.
                if (delta <= 1) {
                    continue;
                }
                int splitDelta = (int)Math.floor(delta / 2d);
                int amountToSend = Math.min(splitDelta, simulatedDrainLeft);
                simulatedDrainLeft = trySendingFluidToBulb(amountToSend, s, cap, bulb, false);
            }

            // avoid concurrent modifications to the indices of the array until all work is final.
            pruneEmptyGoo();
        }
    }

    private IFluidHandler tryGettingBulbCapabilities(GooBulbTile bulb, Direction dir)
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

    private int trySendingFluidToBulb(int simulatedDrainLeft, FluidStack s, IFluidHandler cap, GooBulbTile bulb, boolean isVerticalDrain) {
        // simulated drain left represents how much "suction" is left in the interaction
        // s is the maximum amount in the stack. the lesser of these is how much you can drain in one tick.
        int amountLeft = Math.min(simulatedDrainLeft, s.getAmount());

        // do it again, only this time, testing the amount the receptacle can tolerate.
        amountLeft = Math.min(amountLeft, cap.fill(s, IFluidHandler.FluidAction.SIMULATE));

        // now here, the number can be zero. If it is, it means we don't have space left in the receptacle. Break.
        if (amountLeft == 0) {
            return 0;
        }

        // at this point we know we're able to move a nonzero amount of fluid. Prep a new stack
        FluidStack stackBeingSwapped = new FluidStack(s.getFluid(), amountLeft);

        // fill the receptacle.
        cap.fill(stackBeingSwapped, IFluidHandler.FluidAction.EXECUTE);
        if (cap instanceof BulbFluidHandler && isVerticalDrain) {
            ((BulbFluidHandler)cap).sendVerticalFillSignalForVisuals(s.getFluid());
        }


        // now call our drain, we're the sender.
        fluidHandler.drain(stackBeingSwapped, IFluidHandler.FluidAction.EXECUTE);

        // we can only handle so much work in a tick. Decrement the work limit. If it's zero, this loop breaks.
        // but if it was less than we're allowed to send, we can do more work in this tick, so it will continue.
        simulatedDrainLeft -= amountLeft;

        return simulatedDrainLeft;
    }

    private GooBulbTile getBulbInDirection(Direction dir) {
        if (world == null) {
            return null;
        }
        BlockPos posInDirection = this.pos.offset(dir);
        TileEntity tile = world.getTileEntity(posInDirection);
        if (tile == null) {
            return null;
        }
        if (!(tile instanceof GooBulbTile)) {
            return null;
        }
        return (GooBulbTile)tile;
    }

    public boolean hasFluid(Fluid fluid) {
        return !getSpecificGooType(fluid).equals(FluidStack.EMPTY);
    }

    public boolean fluidNamesAreEqual(FluidStack fluidStack, String gooType) {
        return Objects.requireNonNull(fluidStack.getFluid().getRegistryName()).getPath().equals(gooType);
    }

    @Nonnull
    public FluidStack getLeastQuantityGoo() {
        return goo.stream().min(Comparator.comparingInt(FluidStack::getAmount)).orElse(FluidStack.EMPTY);
    }

    @Nonnull
    public FluidStack getSpecificGooType(Fluid fluid) {
        return goo.stream().filter(f -> fluidNamesAreEqual(f, fluid.getRegistryName().getPath())).findFirst().orElse(FluidStack.EMPTY);
    }

    public int getTotalGoo() {
        return goo.stream().mapToInt(FluidStack::getAmount).sum();
    }

    @Override
    public void markDirty() {
        super.markDirty();
    }

    public void onContentsChanged() {
         if (world == null) {
            return;
        }
        if (!world.isRemote) {
            if (world.getServer() == null) {
                return;
            }
            Networking.sendToClientsAround(new FluidUpdatePacket(world.func_234923_W_(), pos, goo), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.func_234923_W_())), pos);
        }
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    private CompoundNBT serializeGoo()  {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("count", goo.size());
        int index = 0;
        for(FluidStack s : goo) {
            CompoundNBT gooTag = new CompoundNBT();
            s.writeToNBT(gooTag);
            tag.put("goo" + index, gooTag);
            index++;
        }
        return tag;
    }

    private void deserializeGoo(CompoundNBT tag) {
        List<FluidStack> tagGooList = new ArrayList<>();
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT gooTag = tag.getCompound("goo" + i);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(gooTag);
            if (stack.isEmpty()) {
                continue;
            }
            tagGooList.add(stack);
        }

        goo = tagGooList;
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

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        // tanks have omnidirectional gaskets so side is irrelevant.
        if (cap.equals(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)) {
            return handler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void updateFluidsTo(List<FluidStack> fluids) {
        if (hasChanges(fluids)) {
            goo = fluids;
        }
    }

    private boolean hasChanges(List<FluidStack> fluids) {
        boolean hasChanges = false;
        for(FluidStack f : fluids) {
            if (goo.stream().noneMatch(g -> g.isFluidStackIdentical(f))) {
                hasChanges = true;
            }
        }
        return hasChanges;
    }

    private BulbFluidHandler createHandler() {
        return new BulbFluidHandler(this);
    }

    public ItemStack getBulbStack() {
        ItemStack stack = new ItemStack(Registry.GOO_BULB.get());

        CompoundNBT bulbTag = new CompoundNBT();
        write(bulbTag);
        bulbTag.remove("x");
        bulbTag.remove("y");
        bulbTag.remove("z");

        CompoundNBT stackTag = new CompoundNBT();
        stackTag.put("BlockEntityTag", bulbTag);
        stack.setTag(stackTag);

        return stack;
    }

    public static void addInformation(ItemStack stack, List<ITextComponent> tooltip)
    {
        CompoundNBT stackTag = stack.getTag();
        if (stackTag == null) {
            return;
        }

        if (!stackTag.contains("BlockEntityTag")) {
            return;
        }

        CompoundNBT bulbTag = stackTag.getCompound("BlockEntityTag");

        if (!bulbTag.contains("goo")) {
            return;
        }

        CompoundNBT gooTag = bulbTag.getCompound("goo");
        List<FluidStack> fluidsDeserialized = deserializeGooForDisplay(gooTag);
        int index = 0;
        int displayIndex = 0;
        IFormattableTextComponent fluidAmount = null;
        // struggling with values sorting stupidly. Trying to do fix sort by doing this:
        List<FluidStack> sortedValues = new SortedList<>(FXCollections.observableArrayList(fluidsDeserialized), Compare.fluidAmountComparator.thenComparing(Compare.fluidNameComparator));
        for(FluidStack v : sortedValues) {
            index++;
            if (v.isEmpty()) {
                continue;
            }
            String decimalValue = " " + NumberFormat.getNumberInstance(Locale.ROOT).format(v.getAmount()) + " mB";
            String fluidTranslationKey = v.getTranslationKey();
            if (fluidTranslationKey == null) {
                continue;
            }
            displayIndex++;
            if (displayIndex % 2 == 1) {
                fluidAmount = new TranslationTextComponent(fluidTranslationKey).appendString(decimalValue);
            } else {
                if (fluidAmount != null) {
                    fluidAmount = fluidAmount.appendString(", ").append(new TranslationTextComponent(fluidTranslationKey).appendString(decimalValue));
                }
            }
            if (displayIndex % 2 == 0 || index == sortedValues.size()) {
                tooltip.add(fluidAmount);
            }
        }
    }

    private static List<FluidStack> deserializeGooForDisplay(CompoundNBT tag) {
        List<FluidStack> tagGooList = new ArrayList<>();
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT gooTag = tag.getCompound("goo" + i);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(gooTag);
            tagGooList.add(stack);
        }

        return tagGooList;
    }

    public boolean hasSpace() {
        return getSpaceRemaining() > 0;
    }

    public int getSpaceRemaining()
    {
        return GooMod.mainConfig.bulbCapacity() - getTotalGoo();
    }
}
