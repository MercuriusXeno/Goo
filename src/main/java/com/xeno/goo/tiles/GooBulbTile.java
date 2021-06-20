package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.enchantments.Containment;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.CrystallizedGooAbstract;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.Compare;
import com.xeno.goo.network.*;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.util.FluidHandlerWrapper;
import com.xeno.goo.util.MultiGooTank;
import com.xeno.goo.util.IGooTank;
import it.unimi.dsi.fastutil.ints.Int2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Supplier;

public class GooBulbTile extends GooContainerAbstraction implements ITickableTileEntity,
        FluidUpdatePacket.IFluidPacketReceiver, GooFlowPacket.IGooFlowReceiver
{
    // it only takes 9 ticks to tier up because of the goo already in the object.
    // at level 1 this is also negligible
    public static final int PROGRESS_TICKS_PER_TIER_UP = 9;
    public static final int TICKS_PER_PROGRESS_TICK = 5;
    private static final int RADIUS_BEFORE_WHO_CARES_HOW_STUTTERY_IT_LOOKS = 12;
    private float verticalFillIntensity = 0f;
    private Fluid verticalFillFluid = Fluids.EMPTY;
    private int verticalFillDelay = 0;
    private int crystalProgressTicks = 0;
    private int lastIncrement = 0;
    private ItemStack crystal = ItemStack.EMPTY;
    private Fluid crystalFluid = Fluids.EMPTY;
    private FluidStack crystalProgress = FluidStack.EMPTY;

    private final LazyOptional<IFluidHandler> topHandler = LazyOptional.of(() -> new FluidHandlerWrapper(goo) {

        @Override
        public int fill(FluidStack resource, FluidAction action) {

            int r = super.fill(resource, action);
            if (r > 0) {
                float fillVisual = Math.max(1f, r / (float) transferRate());
                toggleVerticalFillVisuals(resource.getFluid(), fillVisual);
            }
            return r;
        }
    });

    public GooBulbTile() {
        super(Registry.GOO_BULB_TILE.get());
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
    }

    public int progress() { return this.crystalProgressTicks; }

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

        boolean isAnyCrystalProgress = false;

        // moving this to after the client side return so that clients can't get ahead of the server ticking for
        // visuals. This is experimental.
        if (!crystal.isEmpty()) {
            crystalProgressTicks++;
            if (!crystalProgress.isEmpty()) {
                spawnParticles(crystalProgress, 1);
            }
            if (crystalProgressTicks >= TICKS_PER_PROGRESS_TICK) {
                crystalProgressTicks = 0;
                isAnyCrystalProgress = tryCrystalProgress();
            }
            Networking.sendToClientsNearTarget(new CrystalProgressTickPacket(world.getDimensionKey(),
                    this.pos, crystalProgressTicks), (ServerWorld)world, pos, RADIUS_BEFORE_WHO_CARES_HOW_STUTTERY_IT_LOOKS);
        } else {
            crystalProgressTicks = 0;
        }

        boolean verticalDrained = tryVerticalDrain();
        boolean lateralShared = tryLateralShare();
        boolean didStuff = isAnyCrystalProgress || verticalDrained || lateralShared;

        if (didStuff) {
            onContentsChanged();
        }
    }

    private void spawnParticles(FluidStack crystalProgress, int particles) {
        if (world instanceof ServerWorld) {
            BasicParticleType type = Registry.vaporParticleFromFluid(crystalProgress.getFluid());
            if (type == null) {
                return;
            }
            AxisAlignedBB box = getSpaceInBox();
            for (int i = 0; i < particles; i++) {
                float scale = world.rand.nextFloat() / 6f + 0.25f;
                Vector3d lowerBounds = new Vector3d(box.minX, box.minY, box.minZ);
                Vector3d upperBounds = new Vector3d(box.maxX, box.maxY, box.maxZ);
                Vector3d threshHoldMax = upperBounds.subtract(lowerBounds);
                Vector3d centeredBounds = threshHoldMax.scale(0.5f);
                Vector3d center = lowerBounds.add(centeredBounds);
                Vector3d randomOffset = threshHoldMax.mul(world.rand.nextFloat() - 0.5f,
                        world.rand.nextFloat() - 0.5f,
                        world.rand.nextFloat() - 0.5f).scale(scale * 2f);
                Vector3d spawnVec = center.add(randomOffset);
                // make sure the spawn area is offset in a way that puts the particle outside of the block side we live on
                // Vector3d offsetVec = Vector3d.copy(sideWeLiveOn().getDirectionVec()).mul(threshHoldMax.x, threshHoldMax.y, threshHoldMax.z);

                ((ServerWorld) world).spawnParticle(type, spawnVec.x, spawnVec.y, spawnVec.z,
                        1, 0d, 0d, 0d, scale);
            }
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

            // interrupt our assumption above, if we have a crystal type, insist on that type of goo strictly.
            if (crystal.getItem() instanceof CrystallizedGooAbstract) {
                crystalFluid = ((CrystallizedGooAbstract)crystal.getItem()).gooType();
            }

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
            if (goo.drain(target, IFluidHandler.FluidAction.SIMULATE).getAmount() < (target.getAmount() - lastIncrement)) {
                reverseAnyUnfinishedCrystalProgress(true);
                return false;
            }

            if (world instanceof ServerWorld) {
                Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.getDimensionKey(), this.pos,
                        crystal, crystalFluid, crystalProgress, lastIncrement), (ServerWorld)world, pos);
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
            if (goo.drain(target, IFluidHandler.FluidAction.SIMULATE).getAmount() < (target.getAmount() - lastIncrement)) {
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
                spawnParticles(crystalProgress, 4);
                // reduce the target by the 10th we don't need or we'll decrease the fluid amount by more than we intended.
                target.setAmount(target.getAmount() - lastIncrement);
                goo.drain(target, IFluidHandler.FluidAction.EXECUTE);
                crystal = new ItemStack(nextStepInCrystallization(target.getFluid()));
                crystalProgress = FluidStack.EMPTY;
                crystalFluid = Fluids.EMPTY;
                lastIncrement = 0;
                if (world instanceof ServerWorld) {
                    Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.getDimensionKey(),
                            this.pos, crystal, crystalFluid, crystalProgress, lastIncrement), (ServerWorld)world, pos);
                    AudioHelper.headlessAudioEvent(world, pos, Registry.CRYSTALLIZE_SOUND.get(), SoundCategory.BLOCKS, 1f, AudioHelper.PitchFormulas.HalfToOne);
                }
            } else {
                if (world instanceof ServerWorld) {
                    Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.getDimensionKey(),
                            this.pos, crystal, crystalFluid, crystalProgress, lastIncrement), (ServerWorld)world, pos);
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

        // if the crystal exists, we want to use its fluid type instead of the type the tank has the most of.
        if (crystal.getItem() instanceof CrystallizedGooAbstract) {
            fluid = ((CrystallizedGooAbstract) crystal.getItem()).gooType();
        }
        return crystalTransformations.get(fluid).get(crystal.getItem());
    }

    private static void initializeTransformations() {
        Registry.FluidSuppliers.forEach(GooBulbTile::buildAndPushTransformationMapping);
    }

    private static void buildAndPushTransformationMapping(ResourceLocation k, Supplier<GooFluid> v) {
        Fluid f = v.get();
        Map<Item, CrystallizedGooAbstract> result = new HashMap<>();
        ItemsRegistry.CRYSTALLIZED_GOO.values().stream()
                .filter((crystal) -> crystal.get().gooType().equals(f))
                .forEach((crystal) -> result.put(crystal.get().source(), crystal.get()));


        crystalTransformations.put(f, result);
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
        Networking.sendToClientsAround(new GooFlowPacket(world.getDimensionKey(), pos, verticalFillFluid, verticalFillIntensity), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.getDimensionKey())), pos);
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

    // if placed above another bulb, the bulb above will drain everything downward.
    private boolean tryVerticalDrain() {
        if (this.goo.isEmpty()) {
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
        for (int i = 0, e = goo.getTanks(); i < e; ++i) {
            FluidStack s = goo.getFluidInTankInternal(i);
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
        return GooMod.config.gooTransferRate() * getStorageMultiplier();
    }

    // bulbs adjacent to one another laterally "equalize" their contents to allow some hotswapping behaviors.
    private boolean tryLateralShare() {
        if (this.goo.isEmpty()) {
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
        for (int i = 0, e = goo.getTanks(); i < e; ++i) {
            FluidStack s = goo.getFluidInTankInternal(i);
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

        // now call our drain, we're the sender.
        goo.drain(stackBeingSwapped, IFluidHandler.FluidAction.EXECUTE);

        // we can only handle so much work in a tick. Decrement the work limit. If it's zero, this loop breaks.
        // but if it was less than we're allowed to send, we can do more work in this tick, so it will continue.
        simulatedDrainLeft -= amountLeft;

        return simulatedDrainLeft;
    }

    public boolean fluidNamesAreEqual(FluidStack fluidStack, String gooType) {
        return Objects.requireNonNull(fluidStack.getFluid().getRegistryName()).getPath().equals(gooType);
    }

    public FluidStack getMostQuantityGoo() {

        FluidStack ret = FluidStack.EMPTY;
        for (int i = 0, e = goo.getTanks(); i < e; ++i) {
            FluidStack tank = goo.getFluidInTankInternal(i);
            if (tank.getAmount() > ret.getAmount())
                ret = tank;
        }
        return ret;
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

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        CompoundNBT crystalTag = crystal.write(new CompoundNBT());
        CompoundNBT crystalProgressTag = crystalProgress.writeToNBT(new CompoundNBT());
        tag.put("crystal_tag", crystalTag);
        tag.put("crystal_progress", crystalProgressTag);
        return super.write(tag);
    }

    public void read(BlockState state, CompoundNBT tag)
    {
        super.read(state, tag);
        // old holding data fixer
        if (tag.contains("holding")) {
            setContainmentLevel(tag.getInt("holding"));
        } else if (tag.contains(Containment.id())) {
            setContainmentLevel(tag.getInt(Containment.id()));
        }
        if (tag.contains("crystal_tag")) {
            crystal = ItemStack.read(tag.getCompound("crystal_tag"));
        }
        if (tag.contains("crystal_progress")) {
            crystalProgress = FluidStack.loadFluidStackFromNBT(tag.getCompound("crystal_progress"));
        }
        onContentsChanged();
    }

    @Override
    public void updateFluidsTo(PacketBuffer fluids) {

        this.goo.readFromPacket(fluids);
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

    // moved this from renderer to here so that both can utilize the same
    // offset logic (and also renderer is client code, not the same in reverse)
    public static final float FLUID_VERTICAL_OFFSET = 0.01626f; // this offset puts it slightly below/above the 1px line to seal up an ugly seam
    public static final float FLUID_VERTICAL_MAX = 0.01626f;
    public static final float HEIGHT_SCALE = (1f - FLUID_VERTICAL_MAX) - FLUID_VERTICAL_OFFSET;
    public static final float ARBITRARY_GOO_STACK_HEIGHT_MINIMUM = 1f / Registry.FluidSuppliers.size(); // percentile is a representation of all the fluid types in existence.

    public static final Int2DoubleLinkedOpenHashMap CAPACITY_LOGS = new Int2DoubleLinkedOpenHashMap();

    public static Object2FloatMap<Fluid> calculateFluidHeights(int capacity, IGooTank unsortedGoo, FluidStack crystalProgress, int lastAmount, int progressTicks, float partialTicks) {
        if (!CAPACITY_LOGS.containsKey(capacity)) {
            CAPACITY_LOGS.put(capacity, calculateCapacityLog(capacity));
        }
        // start with the smallest stacks because those will influence the "minimum heights" first
        // and result in the larger stacks being diminished to compensate for their relative smallness.
        // shallow copy to avoid concurrent mods
        List<FluidStack> gooStacks = new ArrayList<>();
        for (int i = 0, e = unsortedGoo.getTanks(); i < e; ++i)
            // quick hacks
            if (unsortedGoo.getFluidInTankInternal(i).getAmount() > 0)
                gooStacks.add(unsortedGoo.getFluidInTankInternal(i));

        gooStacks.sort(Compare.fluidAmountComparator);
        float total = gooStacks.stream().mapToInt(FluidStack::getAmount).sum();
        total -= crystalProgress.getAmount();
        if (crystalProgress.getAmount() > 0 && progressTicks < TICKS_PER_PROGRESS_TICK) {
            total -= (int)Math.floor(lastAmount * ((progressTicks + partialTicks) / (float) GooBulbTile.TICKS_PER_PROGRESS_TICK));
        }
        if (total < 0) {
            return new Object2FloatOpenHashMap<>();
        }

        // how high the fluid is in the bulb, as a fraction of 1d
        // 1d is never really achievable since the bulb viewport is technically smaller than that
        float totalFluidHeight = (float)Math.pow(total, 1f / CAPACITY_LOGS.get(capacity)) / 16f; // scaled

        // when something achieves a mandatory minimum in this way, we have to deflate the value
        // proportionally to compensate, and this is also logarithmic.
        Object2FloatMap<Fluid> results = new Object2FloatOpenHashMap<>();
        for (FluidStack g : gooStacks) {
            int totalGooInThisStack = g.getAmount();
            totalGooInThisStack -= crystalProgress.isFluidEqual(g) ? crystalProgress.getAmount() : 0;
            if (g.isFluidEqual(crystalProgress) && progressTicks < GooBulbTile.TICKS_PER_PROGRESS_TICK) {
                int increment = (int)Math.floor(lastAmount * ((progressTicks + partialTicks) / (float) GooBulbTile.TICKS_PER_PROGRESS_TICK));
                totalGooInThisStack -= increment;
                if (totalGooInThisStack < 0) {
                    totalGooInThisStack = 0;
                }
            }
            results.put(g.getFluid(), totalGooInThisStack * totalFluidHeight / total);
        }

        // The purpose here is to scale the contents of the bulb so that amounts
        // less than a threshold are made larger than they should be, so you can see them and target
        // them easily even though there's not a lot.
        float scale = 1f;
        float remainder = 1f;
        float diminished = 0f;
        for (FluidStack g : gooStacks) {
            float v = results.getFloat(g.getFluid());
            if (v < ARBITRARY_GOO_STACK_HEIGHT_MINIMUM) {
                remainder -= ARBITRARY_GOO_STACK_HEIGHT_MINIMUM;
                diminished += v;
                scale = remainder / (1f - diminished);
                v = ARBITRARY_GOO_STACK_HEIGHT_MINIMUM;
            } else {
                v *= scale;
            }
            results.put(g.getFluid(), v);
        }
        return results;
    }

    private static double calculateCapacityLog(int capacity) {
        return Math.log(capacity) / Math.log(16d);
    }

    public Object2FloatMap<Fluid> calculateFluidHeights(float partialTicks) {
        return calculateFluidHeights(getStorageCapacity(), goo, crystalProgress, lastIncrement, crystalProgressTicks, partialTicks);
    }

    public Object2FloatMap<Fluid> calculateFluidHeights() {
        return calculateFluidHeights(0f);
    }

    private AxisAlignedBB getSpaceInBox() {
        float fluidLevels = calculateFluidHeight();
        return new AxisAlignedBB(this.pos.getX(), this.pos.getY() + fluidLevels, this.pos.getZ(),
                this.pos.getX() + 1d, this.pos.getY() + 1d, this.pos.getZ() + 1d);
    }

    @Override
    public FluidStack getGooFromTargetRayTraceResult(Vector3d hitVec, Direction side, RayTraceTargetSource targetSource)
    {
        if (goo.isEmpty()) {
            return FluidStack.EMPTY;
        }

        FluidStack last = FluidStack.EMPTY;
        Object2FloatMap<Fluid> fluidStacksHeights = calculateFluidHeights();
        float height = this.pos.getY() + FLUID_VERTICAL_OFFSET;
        float hitY = (float)hitVec.y;

        for (int i = 0, e = goo.getTanks(); i < e; ++i) {
            FluidStack g = goo.getFluidInTankInternal(i);
            if (g.getAmount() <= 0 || !fluidStacksHeights.containsKey(g.getFluid()))
                continue;

            last = g;

            float gooHeight = fluidStacksHeights.getFloat(g.getFluid()) * HEIGHT_SCALE;
            if (hitY >= height && hitY < height + gooHeight)
                return g;

            height += gooHeight;
        }

        // we couldn't find the stack which means we're *above* any stack. Return the last one.
        return last;
    }

    @Override
    public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource)
    {
        return goo;
    }

    @Override
    protected IGooTank createGooTank() {

        return new MultiGooTank(this::getStorageCapacity)
                .setFilter(GooFluid.IS_GOO_FLUID)
                .setChangeCallback(this::onContentsChanged);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) {

        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && side == Direction.UP) {
            return topHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public int getBaseCapacity() {

        return GooMod.config.bulbCapacity();
    }

    public int getStorageMultiplier()
    {
        return storageMultiplier(getContainmentLevel());
    }

    public static int storageMultiplier(int enchantContainment)
    {
        return (int)Math.pow(GooMod.config.bulbContainmentMultiplier(), enchantContainment);
    }

    public static int storageForDisplay(int containmentLevel)
    {
        return storageMultiplier(containmentLevel) * GooMod.config.bulbCapacity();
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
            Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.getDimensionKey(), this.pos,
                    crystal, crystalFluid, crystalProgress, lastIncrement), (ServerWorld)world, pos);
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
            float gooHeight = calculateFluidHeight();
            ((ServerWorld)world).spawnParticle(ParticleTypes.SMOKE, pos.getX() + 0.5d, pos.getY() + gooHeight, pos.getZ() + 0.5d, 1, 0d, 0d, 0d, 0d);
            Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.getDimensionKey(), this.pos,
                    crystal, crystalFluid, crystalProgress, lastIncrement), (ServerWorld)world, pos);
        }
    }

    public float calculateFluidHeight() {
        Map<Fluid, Float> fluidStackHeights = calculateFluidHeights();
        return (float)fluidStackHeights.values().stream().mapToDouble(v -> v).sum() * HEIGHT_SCALE;
    }

    public void addCrystal(Item item) {
        crystal = new ItemStack(item);
        if (world instanceof ServerWorld) {
            Networking.sendToClientsAround(new UpdateBulbCrystalProgressPacket(world.getDimensionKey(), this.pos,
                    crystal, crystalFluid, crystalProgress, lastIncrement), (ServerWorld)world, pos);
        }
    }

    public ItemStack crystal() {
        return crystal;
    }

    public void updateCrystalProgress(ItemStack crystal, int lastIncrement, ResourceLocation crystalFluid, FluidStack crystalProgress) {
        this.crystal = crystal;
        this.lastIncrement = lastIncrement;
        Fluid f = Registry.getFluid(crystalFluid.toString());
        this.crystalFluid = f == null ? Fluids.EMPTY : f;
        this.crystalProgress = crystalProgress;
    }

    public void updateCrystalTicks(int progressTicks) {
        this.crystalProgressTicks = progressTicks;
    }
}
