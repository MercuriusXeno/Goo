package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.EntryHelper;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import com.xeno.goo.client.render.block.HatchOpeningState;
import com.xeno.goo.client.render.block.HatchOpeningState.HatchOpeningStates;
import com.xeno.goo.network.*;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static net.minecraft.item.ItemStack.EMPTY;

public class SolidifierTile extends TileEntity implements ITickableTileEntity,
                                                          ChangeItemTargetPacket.IChangeItemTargetReceiver,
                                                          GooFlowPacket.IGooFlowReceiver
{
    private static final int HALF_SECOND_TICKS = 10;
    private static final int ONE_SECOND_TICKS = 20;
    // the item the solidifier currently "targets" is what it tries to make more of
    private Item target;
    private ItemStack targetStack;

    // when switching targets, a safety mechanism is designed to prevent accidental swaps
    // this is the stack the machine will transition to if the player confirms their input.
    private Item newTarget;
    private ItemStack newTargetStack;

    // timer that counts down after a change of target request. Failing to confirm the change reverts the selection.
    private int changeTargetTimer;
    private int fuelTime;
    private int previousFuelTime;

    // default timer span of 5 seconds should be plenty of time to swap an input?
    private static final int CHANGE_TARGET_TIMER_DURATION = 100;

    private float verticalFillIntensity = 0f;
    private Fluid verticalFillFluid = Fluids.EMPTY;
    private int verticalFillDelay = 0;

    // the internal buffer gets filled when the machine is in the process of solidifying an item
    private List<FluidStack> progressToItem = new ArrayList<>();
    private ItemEntity lastItem;

    public SolidifierTile() {
        super(Registry.SOLIDIFIER_TILE.get());
        target = Items.AIR;
        targetStack = EMPTY;
        newTarget = Items.AIR;
        newTargetStack = EMPTY;
        changeTargetTimer = 0;
        lastItem = null;
    }

    // 13, 12 - CLOSED
    // 11, 10 - WAXING
    // 9, 8 - WANING
    // 7, 6 - OPEN
    // 5, 4 - WANING
    // 3, 2 - WAXING
    // 1, 0 - CLOSED
    private static int HATCH_OPENING_FULL_CYCLE = 13;
    private static int HATCH_FRAME_TIMING = 2;
    private int hatchOpeningFrames = 0;
    public void updateHatchState() {
        BlockState state = getBlockState();
        HatchOpeningStates openness = state.get(HatchOpeningState.OPENING_STATE);
        if (hatchOpeningFrames > 0) {
            hatchOpeningFrames--;
        }

        HatchOpeningStates shouldBe;
        if (hatchOpeningFrames > HATCH_OPENING_FULL_CYCLE - HATCH_FRAME_TIMING
                || hatchOpeningFrames < HATCH_FRAME_TIMING) {
            shouldBe = HatchOpeningStates.CLOSED;
        } else if (hatchOpeningFrames > HATCH_OPENING_FULL_CYCLE - 2 * HATCH_FRAME_TIMING
                || hatchOpeningFrames < 2 * HATCH_FRAME_TIMING) {
            shouldBe = HatchOpeningStates.WAXING;
        } else if (hatchOpeningFrames > HATCH_OPENING_FULL_CYCLE - 3 * HATCH_FRAME_TIMING
                || hatchOpeningFrames < 3 * HATCH_FRAME_TIMING) {
            shouldBe = HatchOpeningStates.WANING;
        } else {
            shouldBe = HatchOpeningStates.OPEN;
        }
        if (shouldBe != openness) {
            world.setBlockState(this.pos, state.with(HatchOpeningState.OPENING_STATE, shouldBe), 2);
        }
    }
    public void startOpeningHatch() {
        hatchOpeningFrames = 14;
    }

    @Override
    public void tick() {
        handleTargetChangingCountdown();
        if (world == null) {
            return;
        }
        updateHatchState();
        
        if (world.isRemote()) {
            logicVaporVisuals();
            decayVerticalFillVisuals();
            return;
        }

        resolveTargetChangingCountdown();

        if (getBlockState().get(BlockStateProperties.POWERED)) {
            return;
        }

        // go on a brief cooldown while the hatch is open
        if (hatchOpeningFrames > 0) {
            return;
        }

        if (hasValidTarget()) {
            handleSolidifying();
        }
    }

    private void logicVaporVisuals() {
        if (fuelTime > 0 && fuelTime != previousFuelTime) {
            for (int i = 0; i < 4; i++) {
                double dx = world.rand.nextGaussian() * 0.1d;
                double dy = world.rand.nextGaussian() * 0.1d;
                double dz = world.rand.nextGaussian() * 0.1d;
                world.addParticle(Registry.vaporParticleFromFluid(Registry.LOGIC_GOO.get()),
                        bellPos().x + dx, bellPos().y + dy, bellPos().z + dz,
                        0d, 0.02d, 0d);
            }
        }
        previousFuelTime = fuelTime;
    }

    public void toggleVerticalFillVisuals(Fluid f)
    {
        toggleVerticalFillVisuals(f, 1f);
    }

    public void toggleVerticalFillVisuals(Fluid f, float intensity)
    {
        verticalFillFluid = f;
        verticalFillIntensity = intensity; // default fill intensity is just "on", essentially
        if (world == null) {
            return;
        }
        Networking.sendToClientsAround(new GooFlowPacket(world.getDimensionKey(), pos, verticalFillFluid, verticalFillIntensity), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.getDimensionKey())), pos);
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

    private void handleTargetChangingCountdown()
    {
        if (changeTargetTimer > 0) {
            changeTargetTimer--;
        }
    }

    private void resolveTargetChangingCountdown()
    {
        if (changeTargetTimer <= 0) {
            newTarget = Items.AIR;
            newTargetStack = EMPTY;
            sendTargetUpdate();
        }
    }

    private boolean hasValidTarget()
    {
        if (targetStack.isEmpty()) {
            return false;
        }
        return isValidTarget(target);
    }

    private void handleSolidifying()
    {
        GooEntry mapping = getItemEntry(target);
        if (mapping == null || mapping.isUnusable()) {
            return;
        }

        if (!hasBufferedEnough(mapping)) {
            if (fuelTime <= 0) {
                tryDrainingFuel();
            }

            if (fuelTime <= 0) {
                return;
            }
            // only drain fuel if we're actively coalescing an item
            if (tryDrainingSources(mapping)) {
                decrFuelTime();
            }
        }

        if (hasBufferedEnough(mapping)) {
            progressToItem.clear();
            produceItem();
        }
    }

    private void produceItem()
    {
        if (world == null) {
            return;
        }

        ItemStack stack = targetStack.copy();

        dropStack(world, stack);
    }

    private ItemStack dropStack(World world, ItemStack stack)
    {
        Vector3d bellLocation = bellPos();
        ItemEntity itemEntity = new ItemEntity(world, bellLocation.getX(), bellLocation.getY(), bellLocation.getZ(), stack);

        if (world == null) {
            return stack;
        }
        if (stack.isEmpty()) {
            return stack;
        }
        itemEntity.setMotion(dropVector.getX(), dropVector.getY(), dropVector.getZ());
        itemEntity.setDefaultPickupDelay();
        world.addEntity(itemEntity);
        startOpeningHatch();
        return EMPTY;
    }

    private Vector3d bellPos()
    {
        double d0 = pos.getX() + 0.5D;
        double d1 = pos.getY()+ 0.28D;
        double d2 = pos.getZ() + 0.5D;
        return new Vector3d(d0, d1, d2);
    }

    private static Vector3d dropVector = new Vector3d(0f, -0.08f, 0f);

    private boolean hasBufferedEnough(GooEntry mapping)
    {
        int mappingSum = mapping.values().stream().mapToInt(GooValue::amount).sum();
        int bufferSum = progressToItem.stream().mapToInt(FluidStack::getAmount).sum();

        if (bufferSum == mappingSum) {
            return true;
        }
        return false;
    }

    private boolean tryDrainingSources(GooEntry mapping)
    {
        if (world == null) {
            return false;
        }

        AtomicInteger potentialWork = new AtomicInteger(GooMod.config.gooProcessingRate());
        AtomicBoolean didStuff = new AtomicBoolean(false);
        LazyOptional<IFluidHandler> cap = FluidHandlerHelper.capabilityOfNeighbor(this, Direction.UP);
        cap.ifPresent((c) ->
                {
                    for (GooValue v : mapping.values()) {
                        int suctionLeft = tryDrainingFluid(potentialWork.get(), c, v);
                        if (suctionLeft != potentialWork.get()) {
                            didStuff.set(true);
                        }
                        potentialWork.set(suctionLeft);
                    }
                }
        );
        return didStuff.get();
    }

    private void tryDrainingFuel() {
        LazyOptional<IFluidHandler> cap = FluidHandlerHelper.capabilityOfNeighbor(this, facing().getOpposite());
        if (cap.isPresent() && cap.resolve().isPresent()) {
            if (tryDrainingFuel(cap.resolve().get())) {
                setFuelTime(GooMod.config.logicPowersSolidifierTicks());
            }
        }
    }

    private final Supplier<FluidStack> fuelSupplier = () -> new FluidStack(Registry.LOGIC_GOO.get(), 1);
    private boolean tryDrainingFuel(IFluidHandler cap)
    {
        // simulate
        if (cap.drain(fuelSupplier.get(), IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
            return false;
        }

        cap.drain(fuelSupplier.get(), IFluidHandler.FluidAction.EXECUTE);

        return true;
    }

    private int tryDrainingFluid(int workLeftThisGasket, IFluidHandler cap, GooValue v)
    {
        if (workLeftThisGasket == 0) {
            return 0;
        }

        Fluid f = Registry.getFluid(v.getFluidResourceLocation());
        int absentFluid = getAbsentFluid(f, v.amount());
        int maxDrain = (int)Math.min(Math.ceil(absentFluid), workLeftThisGasket);

        FluidStack drainTarget = getDrainTarget(v, maxDrain);
        if (drainTarget.isEmpty()) {
            return workLeftThisGasket;
        }

        // simulate
        if (cap.drain(drainTarget, IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
            return workLeftThisGasket;
        }

        FluidStack result = cap.drain(drainTarget, IFluidHandler.FluidAction.EXECUTE);
        workLeftThisGasket -= result.getAmount();

        FluidStack existingFluid = fluidInBuffer(result.getFluid());
        if (existingFluid.isEmpty()) {
            progressToItem.add(result.copy());
        } else {
            existingFluid.setAmount(existingFluid.getAmount() + result.getAmount());
        }
        toggleVerticalFillVisuals(result.getFluid());
        return workLeftThisGasket;
    }

    private FluidStack getDrainTarget(GooValue v, int maxDrain)
    {
        Fluid f = Registry.getFluid(v.getFluidResourceLocation());
        if (f == null) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(f, Math.min(maxDrain, (int)Math.ceil(v.amount())));
    }

    private FluidStack fluidInBuffer(Fluid fluid) {
        Optional<FluidStack> existingStack = progressToItem.stream().filter(f -> f.getFluid().equals(fluid)).findFirst();

        return existingStack.orElse(FluidStack.EMPTY);
    }

    private int getAbsentFluid(Fluid fluid, int fluidAmount)
    {
        FluidStack fluidInBuffer = fluidInBuffer(fluid);
        if (fluidInBuffer.isEmpty()) {
            return fluidAmount;
        }

        return fluidAmount - fluidInBuffer.getAmount();
    }

    private Direction facing() {
        return getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
    }

    private GooEntry getItemEntry(Item item)
    {
        if (world == null) {
            return GooEntry.UNKNOWN;
        }
        return Equivalencies.getEntry(world, item);
    }

    public ItemStack getDisplayedItem()
    {
        return targetStack;
    }

    public void changeTargetItem(Item item) {
        // air is special, it means we're disabling the machine, essentially.
        // skip our returns if we're setting the target to nothing.
        if (!item.equals(Items.AIR)) {
            if (!isValidTarget(item)) {
                return;
            }
        }
        if (isEmptyTarget() || isChangingTargetValid(item)) {
            if (progressToItem.isEmpty()) {
                changeTarget(item);
            } else {
                progressToItem.clear();
                playFizzleOfLostProgress();
                changeTarget(item);
            }
        } else if (!item.equals(target)) {
            enterTargetSwapMode(item);
        }
    }

    private static int SMOKE_WHEN_FIZZLING_COUNT = 4;
    public void playFizzleOfLostProgress() {
        if (world.isRemote()) {
            Vector3d smokePos = Vector3d.copyCentered(pos).subtract(0d, 0.1875d, 0d);
            for (int i = 0; i < SMOKE_WHEN_FIZZLING_COUNT; i++) {
                double dx = world.rand.nextGaussian() * 0.1d;
                double dy = world.rand.nextGaussian() * 0.1d;
                double dz = world.rand.nextGaussian() * 0.1d;
                world.addParticle(ParticleTypes.SMOKE, smokePos.x + dx, smokePos.y + dy, smokePos.z + dz, 0d, 0.06d, 0d);
            }
        } else {
            Networking.sendToClientsAround(new SolidifierFizzlePacket(this.world.getDimensionKey(), this.pos), (ServerWorld)world, this.pos);
        }
    }

    private boolean isValidTarget(Item item)
    {
        return !getItemEntry(item).deniesSolidification();
    }

    private void enterTargetSwapMode(Item item)
    {
        changeTargetTimer = CHANGE_TARGET_TIMER_DURATION;
        newTarget = item;
        newTargetStack = EntryHelper.getSingleton(item);

        sendTargetUpdate();
    }

    private void sendTargetUpdate()
    {
        if (world == null || world.isRemote()) {
            return;
        }

        Networking.sendToClientsAround(new ChangeItemTargetPacket(world.getDimensionKey(), pos, targetStack, newTargetStack, changeTargetTimer), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.getDimensionKey())), pos);
    }

    private void changeTarget(Item item)
    {
        changeTargetTimer = 0;
        target = item;
        targetStack = EntryHelper.getSingleton(item);
        newTarget = Items.AIR;
        newTargetStack = EMPTY;

        sendTargetUpdate();
    }

    private boolean isChangingTargetValid(Item item)
    {
        return changeTargetTimer > 0 && item.equals(newTarget);
    }

    private boolean isEmptyTarget()
    {
        return target.equals(Items.AIR);
    }

    @Override
    public CompoundNBT write(CompoundNBT tag)
    {
        tag.put("goo", serializeGoo());
        tag.put("items", serializeItems());
        tag.putInt("fuelTime", fuelTime);
        tag.putInt("hatchFrames", hatchOpeningFrames);
        return super.write(tag);
    }

    @Override
    public void read(BlockState state, CompoundNBT tag)
    {
        super.read(state, tag);
        deserializeGoo(tag);
        deserializeItems(tag);
        if (tag.contains("fuelTime")) {
            setFuelTime(tag.getInt("fuelTime"));
        }
        if (tag.contains("hatchFrames")) {
            hatchOpeningFrames = tag.getInt("hatchFrames");
        }
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    private CompoundNBT serializeGoo()  {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("count", progressToItem.size());
        int index = 0;
        for(FluidStack e : progressToItem) {
            CompoundNBT gooTag = e.writeToNBT(new CompoundNBT());
            tag.put("goo_" + index, gooTag);
            index++;
        }
        return tag;
    }

    private void deserializeGoo(CompoundNBT tag) {
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            if (tag.contains("goo_" + i)) {
                CompoundNBT gooTag = tag.getCompound("goo_" + i);
                FluidStack f = FluidStack.loadFluidStackFromNBT(gooTag);
                progressToItem.add(f);
            }
        }
    }

    private CompoundNBT serializeItems()
    {
        CompoundNBT itemTag = new CompoundNBT();
        NonNullList<ItemStack> targetStackList = NonNullList.withSize(2, EMPTY);
        targetStackList.set(0, targetStack);
        targetStackList.set(1, newTargetStack);
        ItemStackHelper.saveAllItems(itemTag, targetStackList);
        itemTag.putInt("change_target_timer", this.changeTargetTimer);
        return itemTag;
    }

    private void deserializeItems(CompoundNBT tag)
    {
        CompoundNBT itemTag = tag.getCompound("items");
        NonNullList<ItemStack> targetStackList = NonNullList.withSize(2, EMPTY);
        ItemStackHelper.loadAllItems(itemTag, targetStackList);
        this.targetStack = targetStackList.get(0);
        this.target = this.targetStack.getItem();
        this.newTargetStack = targetStackList.get(1);
        this.newTarget = this.newTargetStack.getItem();
        this.changeTargetTimer = itemTag.getInt("change_target_timer");
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

        if (bulbTag.contains("items")) {
            CompoundNBT gooTag = bulbTag.getCompound("items");
            NonNullList<ItemStack> targetStacks = NonNullList.withSize(2, EMPTY);
            ItemStackHelper.loadAllItems(gooTag, targetStacks);
            ItemStack tagTargetStack = targetStacks.get(0);

            if (!tagTargetStack.isEmpty()) {
                tooltip.add(new TranslationTextComponent("tooltip.goo.solidifying_target_preface").appendSibling(new TranslationTextComponent(tagTargetStack.getTranslationKey())));
            }
        }
    }

    @Override
    public void updateItemTarget(ItemStack target, ItemStack newTarget, int changeTargetTimer)
    {
        this.target = target.getItem();
        this.targetStack = target;
        this.newTarget = newTarget.getItem();
        this.newTargetStack = newTarget;
        this.changeTargetTimer = changeTargetTimer;
    }

    public boolean shouldFlashTargetItem()
    {
        // we may as well send the renderer a signal that it shouldn't render the item targeted, because there's nothing
        if (targetStack.isEmpty()) {
            return true;
        }

        if (world == null) {
            return false;
        }

        // half second intervals
        return changeTargetTimer > 0 && changeTargetTimer % ONE_SECOND_TICKS > HALF_SECOND_TICKS;
    }

    @Override
    public void updateVerticalFill(Fluid f, float intensity) {
        if (intensity > 0) {
            this.verticalFillDelay = 3;
        }
        this.verticalFillIntensity = intensity;
        this.verticalFillFluid = f;
    }

    public void setFuelTime(int fuelTime) {
        this.fuelTime = fuelTime;
        if (this.world != null && !this.world.isRemote()) {
            Networking.sendToClientsAround(new SolidifierFueledPacket(world.getDimensionKey(), this.pos, fuelTime),
                    (ServerWorld)world, this.pos);
        }
    }

    public void decrFuelTime() {
        setFuelTime(fuelTime - 1);
    }
}
