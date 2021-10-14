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
    private int cooldown;
    private boolean isOn;
    private int flipSwitchDelay;

    // default timer span of 5 seconds should be plenty of time to swap an input?
    private static final int CHANGE_TARGET_TIMER_DURATION = 100;

    private float verticalFillIntensity = 0f;
    private Fluid verticalFillFluid = Fluids.EMPTY;
    private int verticalFillDelay = 0;

    private float itemProgress = 0f;
    private float itemProgressLastTick = 0f;

    // the internal buffer gets filled when the machine is in the process of solidifying an item
    private List<FluidStack> progressToItem = new ArrayList<>();

    public SolidifierTile() {
        super(Registry.SOLIDIFIER_TILE.get());
        target = Items.AIR;
        targetStack = EMPTY;
        newTarget = Items.AIR;
        newTargetStack = EMPTY;
        changeTargetTimer = 0;
        cooldown = 0;
    }

    // frame timings
    public static final int HATCH_CLOSED_UPPER = 13;
    public static final int HATCH_CLOSED_LOWER = 2;
    public static final int HATCH_WAXING_UPPER = 12;
    public static final int HATCH_WAXING_LOWER = 4;
    public static final int HATCH_HALF_UPPER = 11;
    public static final int HATCH_HALF_LOWER = 6;
    public static final int HATCH_WANING_UPPER = 10;
    public static final int HATCH_WANING_LOWER = 8;
    // states
    public static final int HATCH_CLOSED_STATE = 0;
    public static final int HATCH_WAXING_STATE = 1;
    public static final int HATCH_HALF_STATE = 2;
    public static final int HATCH_WANING_STATE = 3;
    public static final int HATCH_OPEN_STATE = 4;

    private int hatchOpeningFrames = 0;
    public void updateHatchState() {
        List<ItemEntity> itemsInBox = world.getEntitiesWithinAABB(ItemEntity.class, getRenderBoundingBox(), null);
        if (itemsInBox.size() > 0 && hatchOpeningFrames == HATCH_OPEN_STATE) {
            return;
        }
        if (hatchOpeningFrames > 0) {
            hatchOpeningFrames--;
        }
        HatchOpeningState.HatchOpeningStates openness = getBlockState().get(HatchOpeningState.OPENING_STATE);
        HatchOpeningState.HatchOpeningStates shouldBe = hatchOpeningFrames > HATCH_WAXING_LOWER
                && hatchOpeningFrames < HATCH_WAXING_UPPER ? HatchOpeningStates.OPENED : HatchOpeningStates.CLOSED;
        if (shouldBe != openness) {
            world.setBlockState(this.pos, getBlockState().with(HatchOpeningState.OPENING_STATE, shouldBe), 2);
        }
    }

    public void flipSwitch() {
        if (this.world.isRemote()) {
            isOn = !isOn;
            flipSwitchDelay = HALF_SECOND_TICKS;
        } else {
            sendFlipSwitchPacket();
        }
    }

    private void sendFlipSwitchPacket() {

    }

    private float visualItemProgress() {
        GooEntry e = getItemEntry(target);
        if (e.isEmpty() || e.isUnusable()) {
            return 0f;
        }

        int sumOfTemplate = e.inputsAsFluidStacks().stream().mapToInt(FluidStack::getAmount).sum();

        if (sumOfTemplate == 0) {
            return 0f;
        }
        int sumOfProgress = progressToItem.stream().mapToInt(FluidStack::getAmount).sum();
        int delta = sumOfTemplate - sumOfProgress;

        return 1f - (delta / sumOfTemplate);
    }

    public float progress() {
        return itemProgress;
    }

    public float previousProgress() {
        return itemProgressLastTick;
    }

    public void startOpeningHatch() {
        if (this.world != null && !this.world.isRemote()) {
            Networking.sendToClientsAround(new SolidifierHatchOpeningPacket(world.getDimensionKey(), this.pos),
                    (ServerWorld)world, this.pos);
        }
        hatchOpeningFrames = 14;
    }

    @Override
    public void tick() {
        handleTargetChangingCountdown();
        if (world == null) {
            return;
        }

        updateProgressVisuals(visualItemProgress());

        if (world.getGameTime() % 60 == 0) {
            // check to see if box contains items
            List<ItemEntity> itemsInBox = world.getEntitiesWithinAABB(ItemEntity.class, getRenderBoundingBox(), null);
            if (itemsInBox.size() > 0) {
                startOpeningHatch();
            }
        }

        updateHatchState();
        
        if (world.isRemote()) {
            decayVerticalFillVisuals();
            return;
        }

        resolveTargetChangingCountdown();

        if (getBlockState().get(BlockStateProperties.POWERED)) {
            return;
        }

        if (hasValidTarget() && cooldown <= 0) {
            handleSolidifying();
        } else {
            cooldown--;
        }
    }

    public void updateProgressVisuals(float f) {
        if (this.world.isRemote()) {
            itemProgressLastTick = itemProgress;
            itemProgress = f;
        } else {
            Networking.sendToClientsAround(new SolidifierProgressPacket(this.world.getDimensionKey(), this.pos, f), (ServerWorld)this.world, this.pos);
        }
    }

    private void logicVaporVisuals() {
        for (int i = 0; i < 2; i++) {
            double dx = world.rand.nextGaussian() * 0.1d;
            double dy = world.rand.nextGaussian() * 0.1d;
            double dz = world.rand.nextGaussian() * 0.1d;
            world.addParticle(Registry.vaporParticleFromFluid(Registry.LOGIC_GOO.get()),
                    vaporPos().x + dx, vaporPos().y + dy, vaporPos().z + dz,
                    0d, 0d, 0.5d);
        }
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
            setCooldown();
        }
    }

    private void setCooldown() {
        cooldown = ONE_SECOND_TICKS;
    }

    private void produceItem()
    {
        if (world == null) {
            return;
        }

        ItemStack stack = targetStack.copy();

        dropStack(world, stack);
    }

    private void dropStack(World world, ItemStack stack)
    {
        Vector3d bellLocation = bellPos();
        ItemEntity itemEntity = new ItemEntity(world, bellLocation.getX(), bellLocation.getY(), bellLocation.getZ(), stack);

        if (world == null) {
            return;
        }
        if (stack.isEmpty()) {
            return;
        }
        itemEntity.setMotion(dropVector.getX(), dropVector.getY(), dropVector.getZ());
        itemEntity.setDefaultPickupDelay();
        world.addEntity(itemEntity);
    }

    private Vector3d bellPos()
    {
        double d0 = pos.getX() + 0.5D;
        double d1 = pos.getY()+ 0.25D;
        double d2 = pos.getZ() + 0.5D;
        return new Vector3d(d0, d1, d2);
    }

    private Vector3d vaporPos()
    {
        double d0 = pos.getX() + 0.5D;
        double d1 = pos.getY()+ 0.28D;
        double d2 = pos.getZ() + 0.5D;
        return new Vector3d(d0, d1, d2);
    }

    private static Vector3d dropVector = new Vector3d(0f, 0f, 0f);

    private boolean hasBufferedEnough(GooEntry mapping)
    {
        int mappingSum = mapping.values().stream().mapToInt(GooValue::amount).sum();
        int bufferSum = progressToItem.stream().mapToInt(FluidStack::getAmount).sum();

        if (bufferSum == mappingSum) {
            return true;
        }
        return false;
    }

    // numbers carefully tuned
    // 0.8335 a second [loss coefficient], over 20 ticks results in .02618~ remaining in the source
    // *on* the twentieth tick, scoop the rest.
    // result: produce one item per second, consistently, regardless of what it is.
    // since this math is wimbly, we have to approximate and *track* the drainage on a given item.
    // since this is counted as progress towards the item, it ultimately cannot exceed the full value of the item.
    private boolean tryDrainingSources(GooEntry mapping)
    {
        if (world == null) {
            return false;
        }

        AtomicBoolean didStuff = new AtomicBoolean(false);
        LazyOptional<IFluidHandler> cap = FluidHandlerHelper.capabilityOfNeighbor(this, Direction.UP);
        cap.ifPresent((c) ->
                {
                    for (GooValue v : mapping.values()) {
                        boolean progressHappened = tryDrainingFluid(c, v);
                        if (progressHappened) {
                            didStuff.set(true);
                        }
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

    private final double DRAIN_CONSTANT = 1d / 6d;
    private boolean tryDrainingFluid(IFluidHandler cap, GooValue v)
    {
        Fluid f = Registry.getFluid(v.getFluidResourceLocation());
        int absentFluid = getAbsentFluid(f, v.amount());

        int maxDrain = (int)Math.ceil(absentFluid * DRAIN_CONSTANT);

        FluidStack drainTarget = getDrainTarget(v, maxDrain);
        if (drainTarget.isEmpty()) {
            return false;
        }

        // simulate
        if (cap.drain(drainTarget, IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
            return false;
        }

        FluidStack result = cap.drain(drainTarget, IFluidHandler.FluidAction.EXECUTE);

        FluidStack existingFluid = fluidInBuffer(result.getFluid());
        if (existingFluid.isEmpty()) {
            progressToItem.add(result.copy());
        } else {
            existingFluid.setAmount(existingFluid.getAmount() + result.getAmount());
        }
        toggleVerticalFillVisuals(result.getFluid());
        return true;
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
        tag.putInt("cooldown", cooldown);
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
        if (tag.contains("cooldown")) {
            cooldown = tag.getInt("cooldown");
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
        if (this.fuelTime > fuelTime && this.world != null && this.world.isRemote()) {
            logicVaporVisuals();
        }
        this.fuelTime = fuelTime;
        if (this.world != null && !this.world.isRemote()) {
            Networking.sendToClientsAround(new SolidifierFueledPacket(world.getDimensionKey(), this.pos, fuelTime),
                    (ServerWorld)world, this.pos);
        }
    }

    public void decrFuelTime() {
        setFuelTime(fuelTime - 1);
    }

    public int hatchOpeningFrames() {
        return hatchOpeningFrames;
    }
}
