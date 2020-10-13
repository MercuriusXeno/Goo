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

public class GooPumpTile extends TileEntity implements ITickableTileEntity, GooFlowPacket.IGooFlowReceiver, ChangeItemTargetPacket.IChangeItemTargetReceiver
{
    public static final int DEFAULT_ANIMATION_FRAMES = 20;
    public static final float PROGRESS_PER_FRAME = (float)Math.PI / DEFAULT_ANIMATION_FRAMES;
    private Fluid pumpFluid;
    private float flowIntensity;
    private int animationFrames;
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

    // default timer span of 5 seconds should be plenty of time to swap an input?
    private static final int CHANGE_TARGET_TIMER_DURATION = 100;

    public GooPumpTile()
    {
        super(Registry.GOO_PUMP_TILE.get());
        this.pumpFluid = Fluids.EMPTY;
        target = Items.AIR;
        targetStack = EMPTY;
        newTarget = Items.AIR;
        newTargetStack = EMPTY;
        changeTargetTimer = 0;
    }

    @Override
    public void updateVerticalFill(Fluid f, float intensity)
    {
        this.pumpFluid = f;
        this.flowIntensity = intensity;
        if (this.animationFrames == 0) {
            this.animationFrames = DEFAULT_ANIMATION_FRAMES;
        }
    }

    public int animationFrames() {
        return this.animationFrames;
    }

    public float verticalFillIntensity()
    {
        return this.flowIntensity;
    }

    public FluidStack verticalFillFluid()
    {
        return new FluidStack(pumpFluid, 1);
    }

    private float verticalFillDecay() {
        // throttle the intensity decay so it doesn't look so jittery. This will cause the first few frames to be slow
        // changing, but later frames will be proportionately somewhat faster.
        if (flowIntensity > 0.9f) {
            return 0.01f;
        }
        float decayRate = 0.2f;
        return Math.min(flowIntensity * decayRate, 0.125f);
    }

    public void decayVerticalFillVisuals() {
        if (this.animationFrames > 0) {
            this.animationFrames--;
        }
        if (!isVerticallyFilled()) {
            return;
        }
        flowIntensity -= verticalFillDecay(); // flow reduces each frame work tick until there's nothing left.
        float cutoffThreshold = 0.05f;
        if (flowIntensity <= cutoffThreshold) {
            disableVerticalFillVisuals();
        }
    }

    public void disableVerticalFillVisuals() {
        pumpFluid = Fluids.EMPTY;
        flowIntensity = 0f;
    }

    public boolean isVerticallyFilled() {
        return !pumpFluid.equals(Fluids.EMPTY) && flowIntensity > 0f;
    }

    public void toggleVerticalFillVisuals(Fluid f)
    {
        pumpFluid = f;
        flowIntensity = 1f; // default fill intensity is just "on", essentially
        if (world == null) {
            return;
        }
        if (this.animationFrames == 0) {
            animationFrames = DEFAULT_ANIMATION_FRAMES;
        }
        Networking.sendToClientsAround(new GooFlowPacket(world.func_234923_W_(), pos, pumpFluid, flowIntensity), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.func_234923_W_())), pos);
    }

    @Override
    public void tick()
    {
        handleTargetChangingCountdown();
        if (world == null) {
            return;
        }

        if (world.isRemote) {
            // vertical fill visuals are client-sided, for a reason. We get sent activity from server but
            // the decay is local because that's needless packets otherwise. It's deterministic.
            decayVerticalFillVisuals();
            return;
        }

        resolveTargetChangingCountdown();

        tryPushingFluid();
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

    private void tryPushingFluid()
    {
        TileEntity source = tileAtSource();
        TileEntity target = tileAtTarget();

        if (source == null || target == null) {
            return;
        }

        LazyOptional<IFluidHandler> sourceHandler = FluidHandlerHelper.capabilityOfNeighbor(this, sourceDirection());
        LazyOptional<IFluidHandler> targetHandler = FluidHandlerHelper.capabilityOfNeighbor(this, targetDirection());

        sourceHandler.ifPresent((s) -> targetHandler.ifPresent((t) -> pushFluid(s, t)));
    }

    private void pushFluid(IFluidHandler sourceHandler, IFluidHandler targetHandler)
    {
        int maxDrain = getMaxDrain();
        FluidStack simulatedDrain = FluidStack.EMPTY;
        // iterate over all tanks and try a simulated drain until something sticks.
        for (int i = 0; i < sourceHandler.getTanks(); i++) {
            FluidStack s = sourceHandler.getFluidInTank(i).copy();
            if (s.isEmpty()) {
                continue;
            }

            if (s.getAmount() > maxDrain) {
                s.setAmount(maxDrain);
            }

            // skip if we're empty
            simulatedDrain = sourceHandler.drain(s, IFluidHandler.FluidAction.SIMULATE);
            if (simulatedDrain.isEmpty()) {
                continue;
            }

            // if we're targeting an item, skip this fluid if it's not in the target entry
            if (!this.targetStack.isEmpty()) {
                GooEntry entry = Equivalencies.getEntry(this.world, this.target);
                if (entry.values().stream().noneMatch(v -> v.getFluidResourceLocation().equals(Objects.requireNonNull(s.getFluid().getRegistryName()).toString()))) {
                    continue;
                }
            }

            int filled = targetHandler.fill(simulatedDrain, IFluidHandler.FluidAction.SIMULATE);
            if (filled == 0) {
                continue;
            }

            if (filled < simulatedDrain.getAmount()) {
                simulatedDrain.setAmount(filled);
            }

            FluidStack result = sourceHandler.drain(simulatedDrain, IFluidHandler.FluidAction.EXECUTE);
            toggleVerticalFillVisuals(result.getFluid());

            // pump for real though
            targetHandler.fill(result, IFluidHandler.FluidAction.EXECUTE);

            // this is purely visual and not vital to the fill operation
            if (targetHandler instanceof BulbFluidHandler && targetDirection() == Direction.DOWN) {
                ((BulbFluidHandler)targetHandler).sendVerticalFillSignalForVisuals(s.getFluid());
            }
            // don't continue if we hit this break, we managed to push some fluid.
            break;
        }
    }

    private int getMaxDrain()
    {
        return GooMod.config.pumpAmountPerCycle();
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

    private TileEntity tileAtTarget()
    {
        return FluidHandlerHelper.tileAtDirection(this, targetDirection());
    }

    private TileEntity tileAtSource()
    {
        return FluidHandlerHelper.tileAtDirection(this, sourceDirection());
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
            changeTarget(item);
        } else if (!item.equals(target)) {
            enterTargetSwapMode(item);
        }
    }

    private GooEntry getItemEntry(Item item)
    {
        if (world == null) {
            return GooEntry.UNKNOWN;
        }
        return Equivalencies.getEntry(world, item);
    }

    private boolean isValidTarget(Item item)
    {
        return !getItemEntry(item).isUnusable();
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

        Networking.sendToClientsAround(new ChangeItemTargetPacket(world.func_234923_W_(), pos, targetStack, newTargetStack, changeTargetTimer), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.func_234923_W_())), pos);
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
        tag.put("items", serializeItems());
        return super.write(tag);
    }

    @Override
    public void read(BlockState state, CompoundNBT tag)
    {
        super.read(state, tag);
        deserializeItems(tag);
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

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
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
}
