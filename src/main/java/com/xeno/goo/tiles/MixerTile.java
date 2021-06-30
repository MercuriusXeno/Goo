package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.MixerRecipe;
import com.xeno.goo.library.MixerRecipes;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.MixerAnimationPacket;
import com.xeno.goo.network.MixerRecipePacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.util.GooTank;
import com.xeno.goo.util.IGooTank;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static net.minecraft.util.Direction.*;

public class MixerTile extends GooContainerAbstraction implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver
{
    private static final int RECIPE_COOLDOWN = 20;
    private float spinnerDegrees = 0;
    private float spinnerSpeed = 0f;
    private boolean isActive = false;
    private int changeRecipeCooldown = 0;
    private MixerRecipe currentRecipe = null;
    private boolean isFirstInputLeftInput = false;

    @Override
    protected void invalidateCaps() {
        super.invalidateCaps();
    }

    public MixerTile()
    {
        super(Registry.MIXER_TILE.get());
    }

    public Direction facing()
    {
        if (this.world == null) {
            return NORTH;
        }
        return this.getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
    }

    private boolean isValid(LazyOptional<IFluidHandler> lazyOptionalHandler) {
        return lazyOptionalHandler.isPresent() && lazyOptionalHandler.resolve().isPresent();
    }

    private LazyOptional<IFluidHandler> cacheLeft = LazyOptional.empty();
    public LazyOptional<IFluidHandler> leftHandler() {
        if (isValid(cacheLeft)) {
            return cacheLeft;
        }
        cacheLeft = fluidHandlerInDirection(facing().rotateY());

        return cacheLeft;
    }

    private LazyOptional<IFluidHandler> cacheRight = LazyOptional.empty();
    public LazyOptional<IFluidHandler> rightHandler() {
        if (isValid(cacheRight)) {
            return cacheRight;
        }
        cacheRight = fluidHandlerInDirection(facing().rotateYCCW());
        return cacheRight;
    }

    @Override
    public void updateFluidsTo(PacketBuffer fluids)
    {
        this.goo.readFromPacket(fluids);
    }

    @Override
    public void tick()
    {
        handleAnimation();
        if (world == null || world.isRemote) {
            return;
        }

        if (hasRecipe()) {
            if (!tryDoingRecipe()) {
                setRecipe(null);
                setRecipeCooldown();
                stopAnimation();
            }
        } else {
            if (hasRecipeCooldown()) {
                decrementRecipeCooldown();
            } else {
                findRecipe();
            }
        }
        tryVerticalDrain();
    }

    private void handleAnimation() {
        if (this.isActive) {
            accelerateSpinner();
            if (this.currentRecipe != null) {
                    Vector3d center = Vector3d.copyCentered(this.pos);
                    Vector3d leftOffset = Vector3d.copy(this.facing().rotateY().getDirectionVec()).scale(0.4d).add(center);
                    Vector3d rightOffset = Vector3d.copy(this.facing().rotateYCCW().getDirectionVec()).scale(0.4d).add(center);
                    Vector3d leftSpeed = center.subtract(leftOffset).normalize();
                    Vector3d rightSpeed = center.subtract(rightOffset).normalize();
                    world.addParticle(Registry.sprayParticleFromFluid(currentRecipe.inputs().get(isFirstInputLeftInput ? 0 : 1).getFluid()),
                            leftOffset.x, leftOffset.y, leftOffset.z,
                            leftSpeed.x, leftSpeed.y, leftSpeed.z);
                    world.addParticle(Registry.sprayParticleFromFluid(currentRecipe.inputs().get(isFirstInputLeftInput ? 1 : 0).getFluid()),
                            rightOffset.x, rightOffset.y, rightOffset.z,
                            rightSpeed.x, rightSpeed.y, rightSpeed.z);
            }
        } else {
            decelerateSpinner();
        }
        this.spinnerDegrees = (spinnerDegrees + spinnerSpeed) % 360;
    }

    private void decelerateSpinner() {
        this.spinnerSpeed = Math.min(0.0f, spinnerSpeed * .94f);
    }

    private void accelerateSpinner() {
        this.spinnerSpeed = Math.max(0.1f, Math.min(30.0f, spinnerSpeed * 1.06f));
    }

    private void decrementRecipeCooldown() {
        changeRecipeCooldown--;
    }

    private boolean hasRecipeCooldown() {
        return changeRecipeCooldown > 0;
    }

    private void setRecipeCooldown() {
        changeRecipeCooldown = RECIPE_COOLDOWN;

    }

    public void setRecipe(MixerRecipe r) {
        currentRecipe = r;
    }

    private boolean hasRecipe() {
        return currentRecipe != null;
    }

    // if placed above another bulb, the bulb above will drain everything downward.
    private boolean tryVerticalDrain() {
        if (this.goo.getFluidInTankInternal(0).isEmpty()) {
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

        if (simulatedDrainLeft <= 0) {
            return false;
        }

        FluidStack s = goo.getFluidInTankInternal(2);
        if (s.isEmpty()) {
            return false;
        }
        int simulatedDrain = trySendingFluid(simulatedDrainLeft, s, c, true);
        if (simulatedDrain != simulatedDrainLeft) {
            return true;
        }

        return false;
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

        // now call our drain, we're the sender.
        goo.drain(stackBeingSwapped, IFluidHandler.FluidAction.EXECUTE);

        // we can only handle so much work in a tick. Decrement the work limit. If it's zero, this loop breaks.
        // but if it was less than we're allowed to send, we can do more work in this tick, so it will continue.
        simulatedDrainLeft -= amountLeft;

        return simulatedDrainLeft;
    }

    private int transferRate()
    {
        return GooMod.config.gooTransferRate() * getStorageMultiplier();
    }

    private void findRecipe() {
        leftHandler().ifPresent((l) -> {
            rightHandler().ifPresent((r) -> {
                MixerRecipes.recipes().forEach((m) -> checkRecipeForValidity(l, r, m));
            });
        });
    }

    private void checkRecipeForValidity(IFluidHandler l, IFluidHandler r, MixerRecipe m) {
        if (hasRecipe()) {
            return;
        }
        // make sure we have room in the tank before we try anything
        if (!hasRoomForResult(m)) {
            return;
        }
        if (tryDrainingRecipe(l, r, m, FluidAction.SIMULATE)) {
            setRecipeInverted(false);
        } else if(tryDrainingRecipe(r, l, m, FluidAction.SIMULATE)) {
            setRecipeInverted(true);
        } else {
            return;
        }
        currentRecipe = m;
        sendRecipePacket();
    }

    private void setRecipeInverted(boolean b) {
        this.isFirstInputLeftInput = b;
    }

    private boolean tryDoingRecipe() {
        if (currentRecipe == null) {
            return false;
        }
        // if we can't hold what the output produces, abort.
        int simulatedFill = goo.fill(currentRecipe.output(), FluidAction.SIMULATE);
        if (simulatedFill != currentRecipe.output().getAmount()) {
            return false;
        }
        AtomicReference<Boolean> isSuccessful = new AtomicReference<>();
        isSuccessful.set(false);
        leftHandler().ifPresent(l -> {
            rightHandler().ifPresent(r -> {
                if (tryDrainingRecipe(l, r, currentRecipe, FluidAction.SIMULATE)) {
                    tryDrainingRecipe(l, r, currentRecipe, FluidAction.EXECUTE);
                    isSuccessful.set(true);
                } else if (tryDrainingRecipe(r, l, currentRecipe, FluidAction.SIMULATE)) {
                    tryDrainingRecipe(r, l, currentRecipe, FluidAction.EXECUTE);
                    isSuccessful.set(true);
                }
                if (isSuccessful.get()) {
                    goo.fill(currentRecipe.output(), FluidAction.EXECUTE);
                }
            });
        });
        startAnimation();
        return isSuccessful.get();
    }

    public void startAnimation() {
        if (world.isRemote()) {
            this.isActive = true;
        } else {
            Networking.sendToClientsAround(new MixerAnimationPacket(this.pos, this.world.getDimensionKey(), true), (ServerWorld) this.world, this.pos);
        }
    }

    public void stopAnimation() {
        if (world.isRemote()) {
            this.isActive = false;
        } else {
            Networking.sendToClientsAround(new MixerAnimationPacket(this.pos, this.world.getDimensionKey(), false), (ServerWorld) this.world, this.pos);
        }
    }

    private void sendRecipePacket() {
        Networking.sendToClientsAround(new MixerRecipePacket(this.pos, this.world.getDimensionKey(), currentRecipe), (ServerWorld)this.world, this.pos);
    }

    private boolean tryDrainingRecipe(IFluidHandler i1, IFluidHandler i2, MixerRecipe m, FluidAction simulate) {
        FluidStack r1 = m.inputs().get(0);
        if (!r1.isFluidStackIdentical(i1.drain(r1, simulate))) {
            return false;
        }
        FluidStack r2 = m.inputs().get(1);
        return r2.isFluidStackIdentical(i2.drain(r2, simulate));
    }

    private boolean hasRoomForResult(MixerRecipe m) {
        // temporarily sets the current recipe so that the fill check "works"
        // the fill won't accept any fluid unless it's the output of the current recipe.
        // at the time this fires, the recipe isn't determined. We set it, try it, and nullify it immediately.
        currentRecipe = m;
        int sentResult = goo.fill(m.output(), IFluidHandler.FluidAction.SIMULATE);
        currentRecipe = null;
        if (sentResult < m.output().getAmount()) {
            return false;
        }
        return true;
    }

    public void onContentsChanged() {
        if (world == null || world.isRemote) {
            return;
        }
        Networking.sendToClientsAround(new FluidUpdatePacket(world.getDimensionKey(), pos, goo), (ServerWorld) world, pos);
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    @Nonnull
    @Override
    public CompoundNBT write(CompoundNBT tag) {
        super.write(tag);
        tag.putBoolean("is_active", isActive);
        tag.putInt("recipe_cooldown", changeRecipeCooldown);
        if (currentRecipe != null) {
            tag.put("recipe", currentRecipe.serializeNbt(new CompoundNBT()));
        }
        return tag;
    }

    @Override
    public void read(BlockState state, CompoundNBT tag) {
        super.read(state, tag);
        if (tag.contains("is_active")) {
            isActive = tag.getBoolean("is_active");
        }
        if (tag.contains("recipe_cooldown")) {
            changeRecipeCooldown = tag.getInt("recipe_cooldown");
        }
        if (tag.contains("recipe")) {
            currentRecipe = MixerRecipe.deserializeNbt(tag.getCompound("recipe"));
        }
    }

    @Override
    protected IGooTank createGooTank() {
        return new GooTank(this::getStorageCapacity)
                .setFilter(this::isRecipeOutput)
                .setChangeCallback(this::onContentsChanged);
    }

    private boolean isRecipeOutput(FluidStack fluidStack) {
        return currentRecipe != null && currentRecipe.output().isFluidEqual(fluidStack);
    }

    @Override
    public int getBaseCapacity() {

        return GooMod.config.mixerInputCapacity();
    }

    @Override
    public int getStorageMultiplier() {

        return 1;
    }

    public ItemStack mixerStack(Block block) {
        ItemStack stack = new ItemStack(block);

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

    @Override
    public FluidStack getGooFromTargetRayTraceResult(Vector3d hitVector, Direction face, RayTraceTargetSource targetSource)
    {
        if (goo.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return goo.getFluidInTankInternal(0);
    }

    @Override
    public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource)
    {
        return goo;
    }

    public float spinnerDegrees() {
        return spinnerDegrees;
    }
}
