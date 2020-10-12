package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.GooFlowPacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.*;

public class GooBulbTileAbstraction extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver, GooFlowPacket.IGooFlowReceiver
{
    private final BulbFluidHandler fluidHandler = createHandler();
    private final LazyOptional<BulbFluidHandler> lazyHandler = LazyOptional.of(() -> fluidHandler);
    private float verticalFillIntensity = 0f;
    private Fluid verticalFillFluid = Fluids.EMPTY;
    private int verticalFillDelay = 0;
    private int enchantHolding = 0;

    public GooBulbTileAbstraction(TileEntityType t) {
        super(t);
    }

    @Override
    protected void invalidateCaps() {
        super.invalidateCaps();
        lazyHandler.invalidate();
    }

    public void enchantHolding(int holding) {
        this.enchantHolding = holding;
    }

    public int holding() {
        return this.enchantHolding;
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

        boolean didStuff = tryVerticalDrain() || tryLateralShare();

        pruneEmptyGoo();

        if (didStuff) {
            onContentsChanged();
        }
    }

    public List<FluidStack> goo()
    {
        return this.goo;
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
        Networking.sendToClientsAround(new GooFlowPacket(world.func_234923_W_(), pos, verticalFillFluid, verticalFillIntensity), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.func_234923_W_())), pos);
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

    public void pruneEmptyGoo()
    {
        goo.removeIf(FluidStack::isEmpty);
    }

    public void addGoo(FluidStack fluidStack)
    {
        goo.add(fluidStack);
    }

    // if placed above another bulb, the bulb above will drain everything downward.
    private boolean tryVerticalDrain() {
        if (this.goo.size() == 0) {
            return false;
        }

        // try fetching the bulb capabilities (below) and throw an exception if it fails. return if null.
        LazyOptional<IFluidHandler> cap = fluidHandlerInDirection(Direction.DOWN);

        final boolean[] verticalDrained = {false};
        cap.ifPresent((c) -> {
            verticalDrained[0] = doVerticalDrain(c);
        });
        return verticalDrained[0];
    }

    private boolean doVerticalDrain(IFluidHandler c)
    {
        // the maximum amount you can drain in a tick is here.
        int simulatedDrainLeft = transferRate();

        if (simulatedDrainLeft == 0) {
            return false;
        }

        boolean didStuff = false;
        // iterate over the stacks and ensure
        for(FluidStack s : goo) {
            if (simulatedDrainLeft <= 0) {
                break;
            }
            int simulatedDrain = trySendingFluid(simulatedDrainLeft, s, c, true);
            if (simulatedDrain != simulatedDrainLeft) {
                didStuff = true;
            }
            simulatedDrainLeft -= simulatedDrain;
        }

        return didStuff;
    }

    private int transferRate()
    {
        return GooMod.config.gooTransferRate() * storageMultiplier();
    }

    // bulbs adjacent to one another laterally "equalize" their contents to allow some hotswapping behaviors.
    private boolean tryLateralShare() {
        if (this.goo.size() == 0) {
            return false;
        }
        boolean[] didStuff = {false};
        for(Direction d : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST })
        {
            // try fetching the bulb capabilities in the opposing direction and throw an exception if it fails. return if null.
            LazyOptional<IFluidHandler> cap = fluidHandlerInDirection(d);

            cap.ifPresent((c) -> didStuff[0] = doLateralShare(c));
        }
        return didStuff[0];
    }

    private boolean doLateralShare(IFluidHandler destination)
    {
        boolean didStuff = false;
        // the maximum amount you can drain in a tick is here.
        int simulatedDrainLeft =  transferRate();

        // iterate over the stacks and ensure
        for(FluidStack s : goo) {
            if (simulatedDrainLeft <= 0) {
                break;
            }
            // only "distribute" to the bulb adjacent if it has less than this one of whatever type (equalizing)
            // here there be dragons; simulate trying to remove an absurd amount of the fluid from the handler
            // it will return how much it has, if any.
            FluidStack stackInDestination = destination.drain(new FluidStack(s.getFluid(), Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
            int bulbContains = stackInDestination.getAmount();
            int delta = s.getAmount() - bulbContains;
            // don't send it anything to avoid passing back 1 mB repeatedly.
            if (delta <= 1) {
                continue;
            }
            int splitDelta = (int)Math.floor(delta / 2d);
            int amountToSend = Math.min(splitDelta, simulatedDrainLeft);

            int simulatedDrain = trySendingFluid(amountToSend, s, destination, false);
            if (simulatedDrain != simulatedDrainLeft) {
                didStuff = true;
            }
            simulatedDrainLeft -= simulatedDrain;
        }
        return didStuff;
    }

    private int trySendingFluid(int simulatedDrainLeft, FluidStack s, IFluidHandler cap, boolean isVerticalDrain) {
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

        // this is purely visual and not vital to the fill operation
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

    public boolean hasFluid(Fluid fluid) {
        return !getSpecificGooType(fluid).equals(FluidStack.EMPTY);
    }

    public boolean fluidNamesAreEqual(FluidStack fluidStack, String gooType) {
        return Objects.requireNonNull(fluidStack.getFluid().getRegistryName()).getPath().equals(gooType);
    }

    public FluidStack getLeastQuantityGoo() {
        return goo.stream().filter(f -> !f.isEmpty() && f.getAmount() > 0).min(Comparator.comparingInt(FluidStack::getAmount)).orElse(FluidStack.EMPTY);
    }

    public FluidStack getSpecificGooType(Fluid fluid) {
        if (fluid == null) {
            return FluidStack.EMPTY;
        }
        return goo.stream().filter(f -> fluidNamesAreEqual(f, fluid.getRegistryName().getPath())).findFirst().orElse(FluidStack.EMPTY);
    }

    public int getTotalGoo() {
        return goo.stream().mapToInt(FluidStack::getAmount).sum();
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

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        tag.put("goo", serializeGoo());
        tag.putInt("holding", enchantHolding);
        return super.write(tag);
    }

    public void read(BlockState state, CompoundNBT tag)
    {
        CompoundNBT gooTag = tag.getCompound("goo");
        deserializeGoo(gooTag);
        if (tag.contains("holding")) {
            enchantHolding(tag.getInt("holding"));
        }
        super.read(state, tag);
        onContentsChanged();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        // tanks have omnidirectional gaskets so side is irrelevant.
        if (cap.equals(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)) {
            return lazyHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void updateFluidsTo(List<FluidStack> fluids) {
        goo = fluids;
    }

    private BulbFluidHandler createHandler() {
        return new BulbFluidHandler(this);
    }

    private Map<Enchantment, Integer> stackEnchantmentFactory() {
        Map<Enchantment, Integer> result = new HashMap<>();
        if (enchantHolding > 0) {
            result.put(Registry.CONTAINMENT.get(), enchantHolding);
        }
        return result;
    }

    public ItemStack getBulbStack(Block block) {
        pruneEmptyGoo();
        ItemStack stack = new ItemStack(block);
        CompoundNBT bulbTag = new CompoundNBT();
        write(bulbTag);
        bulbTag.remove("x");
        bulbTag.remove("y");
        bulbTag.remove("z");

        CompoundNBT stackTag = new CompoundNBT();
        stackTag.put("BlockEntityTag", bulbTag);
        stack.setTag(stackTag);

        EnchantmentHelper.setEnchantments(stackEnchantmentFactory(), stack);
        return stack;
    }

    public boolean hasSpace() {
        return getSpaceRemaining() > 0;
    }

    public int getSpaceRemaining()
    {
        return fluidHandler.getTankCapacity(0) - getTotalGoo();
    }

    // moved this from renderer to here so that both can utilize the same
    // offset logic (and also renderer is client code, not the same in reverse)
    public static final float FLUID_VERTICAL_OFFSET = 0.0005f; // this offset puts it slightly below/above the 1px line to seal up an ugly seam
    public static final float FLUID_VERTICAL_MAX = 0.0005f;
    public static final float ARBITRARY_GOO_STACK_HEIGHT_MINIMUM = 0.04f; // percentile
    @Override
    public FluidStack getGooFromTargetRayTraceResult(Vector3d hitVec, Direction side, RayTraceTargetSource targetSource)
    {
        pruneEmptyGoo();
        if (goo.size() == 0) {
            return FluidStack.EMPTY;
        }
        if (side == Direction.UP) {
            return goo.get(goo.size() - 1); // return last;
        } else if (side == Direction.DOWN) {
            return goo.get(0);
        } else {
            float minY = getPos().getY() + FLUID_VERTICAL_OFFSET;
            if (hitVec.getY() < minY) {
                return goo.get(0);
            }
            float maxY = getPos().getY() + 1f - FLUID_VERTICAL_MAX;
            float heightScale = maxY - minY;

            heightScale = rescaleHeightForMinimumLevels(heightScale, goo, fluidHandler.getTankCapacity(0));
            float yOffset = 0f;
            // create a small spacer between each goo to stop weird z fighting issues?
            // this may look megadumb.

            for(FluidStack stack : goo) {
                // this is the total fill of the goo in the tank of this particular goo, as a percentage
                float gooHeight = Math.max(GooBulbTile.ARBITRARY_GOO_STACK_HEIGHT_MINIMUM, stack.getAmount() / (float)fluidHandler.getTankCapacity(0));
                float fromY, toY;
                // here is where the spacer height is actually applied, not in the render height, but in the starting vec
                // to render each goo type.
                fromY = minY + yOffset;
                toY = fromY + (gooHeight * heightScale);
                if (hitVec.getY() <= toY && hitVec.getY() >= fromY) {
                    return stack;
                }
                yOffset += (gooHeight * heightScale);
            }
            return goo.get(goo.size() - 1);
        }
    }

    public static float rescaleHeightForMinimumLevels(float heightScale, List<FluidStack> gooList, int bulbCapacity)
    {
        // "lost cap" is the amount of space in the bulb lost to the mandatory minimum we
        // render very small amounts of fluid so that we can still target really small amounts
        // the space in the tank has to be recouped by reducing the overall virtual capacity.
        // we measure it as a percentage because it's close enough.
        float lostCap = 0f;
        // first we have to "rescale" the heightscale so that the fluid levels come out looking correct
        for(FluidStack goo : gooList) {
            // this is the total fill of the goo in the tank of this particular goo, as a percentage
            float gooHeight =
                    // the minimum height the goo has. If it's lower than the minimum, use the minimum, otherwise use the real value.
                    Math.max(GooBulbTile.ARBITRARY_GOO_STACK_HEIGHT_MINIMUM, goo.getAmount() / (float)bulbCapacity);
            lostCap +=
                    // if we're "losing cap" by being at the mandatory minimum, figure out how much space we "lost"
                    // this space gets reserved by the routine so it doesn't allow the rendering to go out of bounds.
                    gooHeight == GooBulbTile.ARBITRARY_GOO_STACK_HEIGHT_MINIMUM ?
                        // the amount of space lost is equal to the minimum height minus the value we would have if we weren't being "padded"
                        (GooBulbTile.ARBITRARY_GOO_STACK_HEIGHT_MINIMUM - (goo.getAmount() / (float)bulbCapacity))
                        : 0f;
        }
        return heightScale - (heightScale * lostCap);
    }

    @Override
    public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource)
    {
        return fluidHandler;
    }

    public int storageMultiplier()
    {
        return storageMultiplier(enchantHolding);
    }

    public static int storageMultiplier(int enchantHolding)
    {
        return (int)Math.pow(GooMod.config.bulbHoldingMultiplier(), enchantHolding);
    }

    public static int storageForDisplay(int enchantHolding)
    {
        return storageMultiplier(enchantHolding) * GooMod.config.bulbCapacity();
    }
}
