package com.xeno.goop.tiles;

import com.xeno.goop.GoopMod;
import com.xeno.goop.fluids.BulbFluidHandler;
import com.xeno.goop.library.Compare;
import com.xeno.goop.network.BulbVerticalFillPacket;
import com.xeno.goop.network.FluidUpdatePacket;
import com.xeno.goop.network.Networking;
import com.xeno.goop.setup.Registry;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
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

public class GoopBulbTile extends TileEntity implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver, BulbVerticalFillPacket.IVerticalFillReceiver {
    private BulbFluidHandler fluidHandler = createHandler();

    private LazyOptional<BulbFluidHandler> handler = LazyOptional.of(() -> fluidHandler);
    private List<FluidStack> goop = new ArrayList<>();
    private float verticalFillIntensity = 0f;
    private Fluid verticalFillFluid = Fluids.EMPTY;

    public GoopBulbTile() {
        super(Registry.GOOP_BULB_TILE.get());
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

    public List<FluidStack> goop()
    {
        return this.goop;
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
        Networking.sendToClientsAround(new BulbVerticalFillPacket(world.dimension.getType(), pos, verticalFillFluid, verticalFillIntensity), Objects.requireNonNull(world.getServer()).getWorld(world.dimension.getType()), pos);
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

    public void pruneEmptyGoop()
    {
        goop.removeIf(FluidStack::isEmpty);
    }

    public void addGoop(FluidStack fluidStack)
    {
        goop.add(fluidStack);
    }

    // if placed above another bulb, the bulb above will drain everything downward.
    private void doVerticalDrain() {
        if (this.goop.size() == 0) {
            return;
        }
        // check the tile below us, if it's not a bulb, bail.
        GoopBulbTile bulb = getBulbInDirection(Direction.DOWN);
        if (bulb == null) {
            return;
        }

        // try fetching the bulb capabilities (upward) and throw an exception if it fails. return if null.
        IFluidHandler cap = tryGettingBulbCapabilities(bulb, Direction.UP);
        if (cap == null) {
            return;
        }

        // the maximum amount you can drain in a tick is here.
        int simulatedDrainLeft = GoopMod.config.goopTransferRate();

        // iterate over the stacks and ensure
        for(FluidStack s : goop) {
            if (simulatedDrainLeft <= 0) {
                break;
            }
            simulatedDrainLeft -= trySendingFluidToBulb(simulatedDrainLeft, s, cap, bulb, true);
        }

        // avoid concurrent modifications to the indices of the array until all work is final.
        pruneEmptyGoop();
    }

    // bulbs adjacent to one another laterally "equalize" their contents to allow some hotswapping behaviors.
    private void doLateralShare() {
        if (this.goop.size() == 0) {
            return;
        }
        for(Direction d : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST })
        {
            // check the tile in this direction, if it's not another bulb, pass;
            GoopBulbTile bulb = getBulbInDirection(d);
            if (bulb == null) {
                continue;
            }

            // try fetching the bulb capabilities in the opposing direction and throw an exception if it fails. return if null.
            IFluidHandler cap = tryGettingBulbCapabilities(bulb, d.getOpposite());
            if (cap == null) {
                return;
            }

            // the maximum amount you can drain in a tick is here.
            int simulatedDrainLeft =  GoopMod.config.goopTransferRate();

            // iterate over the stacks and ensure
            for(FluidStack s : goop) {
                if (simulatedDrainLeft <= 0) {
                    break;
                }
                // only "distribute" to the bulb adjacent if it has less than this one of whatever type (equalizing)
                int bulbContains = bulb.getSpecificGoopType(s.getFluid()).getAmount();
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
            pruneEmptyGoop();
        }
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

    private int trySendingFluidToBulb(int simulatedDrainLeft, FluidStack s, IFluidHandler cap, GoopBulbTile bulb, boolean isVerticalDrain) {
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

    public boolean hasFluid(Fluid fluid) {
        return !getSpecificGoopType(fluid).equals(FluidStack.EMPTY);
    }

    public boolean fluidNamesAreEqual(FluidStack fluidStack, String goopType) {
        return Objects.requireNonNull(fluidStack.getFluid().getRegistryName()).getPath().equals(goopType);
    }

    @Nonnull
    public FluidStack getLeastQuantityGoop() {
        return goop.stream().min(Comparator.comparingInt(FluidStack::getAmount)).orElse(FluidStack.EMPTY);
    }

    @Nonnull
    public FluidStack getSpecificGoopType(Fluid fluid) {
        return goop.stream().filter(f -> fluidNamesAreEqual(f, fluid.getRegistryName().getPath())).findFirst().orElse(FluidStack.EMPTY);
    }

    public int getTotalGoop() {
        return goop.stream().mapToInt(FluidStack::getAmount).sum();
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
            Networking.sendToClientsAround(new FluidUpdatePacket(world.dimension.getType(), pos, goop), world.getServer().getWorld(world.dimension.getType()), pos);
        }
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    private CompoundNBT serializeGoop()  {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("count", goop.size());
        int index = 0;
        for(FluidStack s : goop) {
            CompoundNBT goopTag = new CompoundNBT();
            s.writeToNBT(goopTag);
            tag.put("goop" + index, goopTag);
            index++;
        }
        return tag;
    }

    private void deserializeGoop(CompoundNBT tag) {
        List<FluidStack> tagGoopList = new ArrayList<>();
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT goopTag = tag.getCompound("goop" + i);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(goopTag);
            if (stack.isEmpty()) {
                continue;
            }
            tagGoopList.add(stack);
        }

        goop = tagGoopList;
    }


    @Override
    public CompoundNBT write(CompoundNBT tag) {
        tag.put("goop", serializeGoop());
        return super.write(tag);
    }

    @Override
    public void read(CompoundNBT compound) {
        CompoundNBT goopTag = compound.getCompound("goop");
        deserializeGoop(goopTag);
        super.read(compound);
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
            goop = fluids;
        }
    }

    private boolean hasChanges(List<FluidStack> fluids) {
        boolean hasChanges = false;
        for(FluidStack f : fluids) {
            if (goop.stream().noneMatch(g -> g.isFluidStackIdentical(f))) {
                hasChanges = true;
            }
        }
        return hasChanges;
    }

    private BulbFluidHandler createHandler() {
        return new BulbFluidHandler(this);
    }

    public ItemStack getBulbStack() {
        ItemStack stack = new ItemStack(Registry.GOOP_BULB.get());

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

        if (!bulbTag.contains("goop")) {
            return;
        }

        CompoundNBT goopTag = bulbTag.getCompound("goop");
        List<FluidStack> fluidsDeserialized = deserializeGoopForDisplay(goopTag);
        int index = 0;
        int displayIndex = 0;
        ITextComponent fluidAmount = null;
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
                fluidAmount = new TranslationTextComponent(fluidTranslationKey).appendText(decimalValue);
            } else {
                if (fluidAmount != null) {
                    fluidAmount = fluidAmount.appendText(", ").appendSibling(new TranslationTextComponent(fluidTranslationKey).appendText(decimalValue));
                }
            }
            if (displayIndex % 2 == 0 || index == sortedValues.size()) {
                tooltip.add(fluidAmount);
            }
        }
    }

    private static List<FluidStack> deserializeGoopForDisplay(CompoundNBT tag) {
        List<FluidStack> tagGoopList = new ArrayList<>();
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT goopTag = tag.getCompound("goop" + i);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(goopTag);
            tagGoopList.add(stack);
        }

        return tagGoopList;
    }

    public boolean hasSpace() {
        return getSpaceRemaining() > 0;
    }

    public int getSpaceRemaining()
    {
        return GoopMod.config.bulbGoopCapacity() - getTotalGoop();
    }
}
