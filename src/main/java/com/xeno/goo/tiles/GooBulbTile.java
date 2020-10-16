package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.CrystallizedGooAbstract;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.network.*;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.*;
import java.util.function.Supplier;

public class GooBulbTile extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver, GooFlowPacket.IGooFlowReceiver
{
    // it only takes 9 ticks to tier up because of the goo already in the object.
    // at level 1 this is also negligible
    public static final int PROGRESS_TICKS_PER_TIER_UP = 9;
    public static final int TICKS_PER_PROGRESS_TICK = 5;
    private final BulbFluidHandler fluidHandler = createHandler();
    private final LazyOptional<BulbFluidHandler> lazyHandler = LazyOptional.of(() -> fluidHandler);
    private float verticalFillIntensity = 0f;
    private Fluid verticalFillFluid = Fluids.EMPTY;
    private int verticalFillDelay = 0;
    private int enchantHolding = 0;
    private int crystalProgressTicks = 0;
    private int lastIncrement = 0;
    private ItemStack crystal = ItemStack.EMPTY;
    private Fluid crystalFluid = Fluids.EMPTY;
    private FluidStack crystalProgress = FluidStack.EMPTY;

    public GooBulbTile() {
        super(Registry.GOO_BULB_TILE.get());
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

    public int progress() { return this.crystalProgressTicks; }

    @Override
    public void tick() {
        if (world == null) {
            return;
        }
        // let clientside increment progress ticks, this is harmless and for visuals.
        // server occasionally "corrects" it if it's wrong.
        if (!crystal.isEmpty()) {
            crystalProgressTicks++;
        } else {
            crystalProgressTicks = 0;
        }

        if (world.isRemote) {
            // vertical fill visuals are client-sided, for a reason. We get sent activity from server but
            // the decay is local because that's needless packets otherwise. It's deterministic.
            decayVerticalFillVisuals();
            return;
        }

        boolean isAnyCrystalProgress = false;
        if (crystalProgressTicks >= TICKS_PER_PROGRESS_TICK) {
            isAnyCrystalProgress = tryCrystalProgress();
            crystalProgressTicks = 0;
        }
        boolean verticalDrained = tryVerticalDrain();
        boolean lateralShared = tryLateralShare();
        boolean didStuff = isAnyCrystalProgress || verticalDrained || lateralShared;

        pruneEmptyGoo();

        if (didStuff) {
            onContentsChanged();
        }
    }

    private static final Map<Fluid, Map<Item, CrystallizedGooAbstract>> crystalTransformations = new HashMap<>();

    private boolean tryCrystalProgress() {
        // crystal is empty so we're not working and if we WERE working, we undo progress.
        if (crystal.isEmpty()) {
            reverseAnyUnfinishedCrystalProgress(true);
            return false;
        }

        // crystal isn't empty, check if we're making progress already
        if (crystalFluid.equals(Fluids.EMPTY)) {
            // there's no progress so we're about to start some.
            crystalFluid = getMostQuantityGoo().getFluid();

            // there's no progress so we're about to start some.
            CrystallizedGooAbstract crystalTarget = nextStepInCrystallization(crystalFluid);
            if (crystalTarget == null) {
                return false;
            }
            FluidStack target = new FluidStack(crystalFluid, crystalTarget.amount());

            // you can't crystallize zero goo fool.
            if (target.isEmpty() || !(target.getFluid() instanceof GooFluid)) {
                reverseAnyUnfinishedCrystalProgress(true);
                return false;
            }

            // set our increment
            lastIncrement = target.getAmount() / (PROGRESS_TICKS_PER_TIER_UP + 1);

            // not enough, we fail.
            if (this.fluidHandler.drain(target, IFluidHandler.FluidAction.SIMULATE).getAmount() < (target.getAmount() - lastIncrement)) {
                reverseAnyUnfinishedCrystalProgress(true);
                return false;
            }

            if (world instanceof ServerWorld) {
                Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.func_234923_W_(), this.pos, crystal, crystalFluid, crystalProgress, crystalProgressTicks, lastIncrement), (ServerWorld)world, pos);
            }
        } else {
            // there's no progress so we're about to start some.
            CrystallizedGooAbstract crystalTarget = nextStepInCrystallization(crystalFluid);
            if (crystalTarget == null) {
                return false;
            }
            FluidStack target = new FluidStack(crystalFluid, crystalTarget.amount());

            // you can't crystallize zero goo fool.
            if (target.isEmpty() || !(target.getFluid() instanceof GooFluid)) {
                reverseAnyUnfinishedCrystalProgress(true);
                return false;
            }

            // not enough, we fail.
            if (this.fluidHandler.drain(target, IFluidHandler.FluidAction.SIMULATE).getAmount() < (target.getAmount() - lastIncrement)) {
                reverseAnyUnfinishedCrystalProgress(true);
                return false;
            }

            int amountPerProgressTick = target.getAmount() / (PROGRESS_TICKS_PER_TIER_UP + 1);
            lastIncrement = amountPerProgressTick;
            FluidStack actualDrain = target.copy();
            actualDrain.setAmount(amountPerProgressTick);
            // start us off or tick us up
            if (crystalProgress.isEmpty()) {
                crystalProgress = new FluidStack(crystalFluid, actualDrain.getAmount());
            } else {
                crystalProgress.setAmount(crystalProgress.getAmount() + actualDrain.getAmount());
            }

            // note here we only need 9 progress ticks to convert, because the tier below us contained 1/10th of the value
            // this is even true of quartz just because the difference is negligible and quartz is worth way more than 1.
            if (crystalProgress.getAmount() >= target.getAmount() - lastIncrement) {
                this.fluidHandler.drain(target, IFluidHandler.FluidAction.EXECUTE);
                crystal = new ItemStack(nextStepInCrystallization(target.getFluid()));
                crystalProgress = FluidStack.EMPTY;
                crystalFluid = Fluids.EMPTY;
                lastIncrement = 0;
                if (world instanceof ServerWorld) {
                    Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.func_234923_W_(), this.pos, crystal, crystalFluid, crystalProgress, crystalProgressTicks, lastIncrement), (ServerWorld)world, pos);
                    AudioHelper.headlessAudioEvent(world, pos, Registry.CRYSTALLIZE_SOUND.get(), SoundCategory.BLOCKS, 1f, AudioHelper.PitchFormulas.HalfToOne);
                }
            } else {
                if (world instanceof ServerWorld) {
                    Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.func_234923_W_(), this.pos, crystal, crystalFluid, crystalProgress, crystalProgressTicks, lastIncrement), (ServerWorld)world, pos);
                }
            }
        }
        return true;
    }

    private CrystallizedGooAbstract nextStepInCrystallization(Fluid fluid) {
        if (crystalTransformations.size() == 0) {
            initializeTransformations();
        }
        if (fluid.equals(Fluids.EMPTY)) {
            return null;
        }
         return crystalTransformations.get(fluid).get(crystal.getItem());
    }

    private static void initializeTransformations() {
        Registry.FluidSuppliers.forEach(GooBulbTile::buildAndPushTransformationMapping);
    }

    private static void buildAndPushTransformationMapping(ResourceLocation k, Supplier<GooFluid> v) {
        Fluid f = v.get();
        Map<Item, CrystallizedGooAbstract> result = new HashMap<>();
        ItemsRegistry.CrystallizedGoo.values().stream()
                .filter((crystal) -> crystal.get().gooType().equals(f))
                .forEach((crystal) -> result.put(crystal.get().source(), crystal.get()));


        crystalTransformations.put(f, result);
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

    public FluidStack getMostQuantityGoo() {
        return goo.stream().filter(f -> !f.isEmpty() && f.getAmount() > 0 && f.getFluid() instanceof GooFluid).max(Comparator.comparingInt(FluidStack::getAmount)).orElse(FluidStack.EMPTY);
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
        CompoundNBT crystalTag = crystal.write(new CompoundNBT());
        CompoundNBT crystalProgressTag = crystalProgress.writeToNBT(new CompoundNBT());
        tag.put("crystal_tag", crystalTag);
        tag.put("crystal_progress", crystalProgressTag);
        return super.write(tag);
    }

    public void read(BlockState state, CompoundNBT tag)
    {
        CompoundNBT gooTag = tag.getCompound("goo");
        deserializeGoo(gooTag);
        if (tag.contains("holding")) {
            enchantHolding(tag.getInt("holding"));
        }
        if (tag.contains("crystal_tag")) {
            crystal = ItemStack.read(tag.getCompound("crystal_tag"));
        }
        if (tag.contains("crystal_progress")) {
            crystalProgress = FluidStack.loadFluidStackFromNBT(tag.getCompound("crystal_progress"));
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

    public void spewItems()
    {
        if (crystal.isEmpty()) {
            return;
        }
        ItemEntity itemEntity = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), crystal);
        itemEntity.setDefaultPickupDelay();
        world.addEntity(itemEntity);
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

            heightScale = rescaleHeightForMinimumLevels(heightScale, lastIncrement, crystalProgressTicks,
                    0f, crystalFluid, crystalProgress, goo, fluidHandler.getTankCapacity(0));
            float yOffset = 0f;
            // create a small spacer between each goo to stop weird z fighting issues?
            // this may look megadumb.

            for(FluidStack stack : goo) {
                int gooAmount = stack.getAmount();
                if (stack.getFluid().equals(crystalFluid)) {
                    gooAmount -= crystalProgress.getAmount();
                    if (gooAmount < 0) {
                        gooAmount = 0;
                    }
                }
                // this is the total fill of the goo in the tank of this particular goo, as a percentage
                float gooHeight = Math.max(GooBulbTile.ARBITRARY_GOO_STACK_HEIGHT_MINIMUM, gooAmount / (float)fluidHandler.getTankCapacity(0));
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

    public static float rescaleHeightForMinimumLevels(float heightScale, int lastIncrement, int progressTicks, float partialTicks, Fluid crystalFluid, FluidStack crystalProgress, List<FluidStack> gooList, int bulbCapacity)
    {
        // "lost cap" is the amount of space in the bulb lost to the mandatory minimum we
        // render very small amounts of fluid so that we can still target really small amounts
        // the space in the tank has to be recouped by reducing the overall virtual capacity.
        // we measure it as a percentage because it's close enough.
        float lostCap = 0f;
        // first we have to "rescale" the heightscale so that the fluid levels come out looking correct
        for(FluidStack goo : gooList) {
            int gooAmount = goo.getAmount();
            if (goo.getFluid().equals(crystalFluid)) {
                int increment = (int)Math.floor(lastIncrement * ((progressTicks + partialTicks) / (float) GooBulbTile.TICKS_PER_PROGRESS_TICK));
                gooAmount -= (crystalProgress.getAmount() + increment);
                if (gooAmount < 0) {
                    gooAmount = 0;
                }
            }
            // this is the total fill of the goo in the tank of this particular goo, as a percentage
            float gooHeight =
                    // the minimum height the goo has. If it's lower than the minimum, use the minimum, otherwise use the real value.
                    Math.max(GooBulbTile.ARBITRARY_GOO_STACK_HEIGHT_MINIMUM, gooAmount / (float)bulbCapacity);
            lostCap +=
                    // if we're "losing cap" by being at the mandatory minimum, figure out how much space we "lost"
                    // this space gets reserved by the routine so it doesn't allow the rendering to go out of bounds.
                    gooHeight == GooBulbTile.ARBITRARY_GOO_STACK_HEIGHT_MINIMUM ?
                        // the amount of space lost is equal to the minimum height minus the value we would have if we weren't being "padded"
                        (GooBulbTile.ARBITRARY_GOO_STACK_HEIGHT_MINIMUM - (gooAmount / (float)bulbCapacity))
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

    public boolean hasCrystal() {
        return !crystal.isEmpty();
    }

    public void spitOutCrystal(PlayerEntity player, Direction face) {
        reverseAnyUnfinishedCrystalProgress(false);
        ejectCrystal(face, player);
    }

    private void ejectCrystal(Direction face, PlayerEntity player) {
        if (world == null || world.isRemote || crystal.isEmpty()) {
            return;
        }

        Vector3d spawnPos = Vector3d.copy(this.getPos())
                .add(Vector3d.copy(face.getDirectionVec()).scale(0.5d))
                .add(0.5d, 0.5d, 0.5d);

        ItemEntity e = new ItemEntity(world, spawnPos.x, spawnPos.y, spawnPos.z, crystal);
        world.addEntity(e);
        e.setMotion(player.getPositionVec().subtract(spawnPos).normalize().scale(0.3d));
        crystal = ItemStack.EMPTY;
        if (world instanceof ServerWorld) {
            Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.func_234923_W_(), this.pos, crystal, crystalFluid, crystalProgress, crystalProgressTicks, lastIncrement), (ServerWorld)world, pos);
        }
    }

    private void reverseAnyUnfinishedCrystalProgress(boolean sendUpdate) {
        // fluidstack "spent" on the crystal isn't really gone until the item is done.
        // if anything causes the amount in the tank to drop lower than the progress, the progress is reversed.
        // goo in the tank is "reserved" to avoid reversals causing a weird overflow or goo having nowhere to go.
        crystalProgress = FluidStack.EMPTY;
        crystalFluid = Fluids.EMPTY;
        lastIncrement = 0;
        if (sendUpdate && world instanceof ServerWorld) {
            float heightScale = (getPos().getY() + 1f - FLUID_VERTICAL_MAX) - (getPos().getY() + FLUID_VERTICAL_OFFSET);
            float gooHeight = rescaleHeightForMinimumLevels(heightScale, lastIncrement, crystalProgressTicks,
                    0f, crystalFluid, crystalProgress, goo, fluidHandler.getTankCapacity(0));
            ((ServerWorld)world).spawnParticle(ParticleTypes.SMOKE, pos.getX() + 0.5d, pos.getY() + gooHeight, pos.getZ() + 0.5d, 1, 0d, 0d, 0d, 0d);
            Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.func_234923_W_(), this.pos, crystal, crystalFluid, crystalProgress, crystalProgressTicks, lastIncrement), (ServerWorld)world, pos);
        }
    }

    public void addCrystal(Item item) {
        crystal = new ItemStack(item);
        if (world instanceof ServerWorld) {
            Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.func_234923_W_(), this.pos, crystal, crystalFluid, crystalProgress, crystalProgressTicks, lastIncrement), (ServerWorld)world, pos);
        }
    }

    public FluidStack crystalProgress() {
        return crystalProgress;
    }

    public ItemStack crystal() {
        return crystal;
    }

    public void updateCrystalProgress(ItemStack crystal, int lastIncrement, ResourceLocation crystalFluid, FluidStack crystalProgress, int progressTicks) {
        this.crystal = crystal;
        this.lastIncrement = lastIncrement;
        Fluid f = Registry.getFluid(crystalFluid.toString());
        this.crystalFluid = f == null ? Fluids.EMPTY : f;
        this.crystalProgress = crystalProgress;
        this.crystalProgressTicks = progressTicks;

    }

    public int Increment() {
        return lastIncrement;
    }

    public Fluid crystalFluid() {
        return crystalFluid;
    }

}
