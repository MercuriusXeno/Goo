package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.block.gasket.GasketState;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.gasket.IGasketPusher;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.DepletedBlazeRodItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRegionResolver;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Core crucible logic: melts items into goo via a per-tick drain pipeline.
 *
 * <p>Internal logic delegated to:
 * {@link CrucibleMelting} (tick pipeline),
 * {@link CrucibleInsertion} (item/goo insertion),
 * {@link CrucibleSerialization} (NBT).</p>
 */
public class CrucibleBlockEntity extends BlockEntity implements IGasketHolder, IGooReservoir {

    /** Base ignition spray duration in ticks. */
    static final int IGNITION_BASE_TICKS = 4;
    /** Random variance added to ignition spray duration (exclusive bound). */
    static final int IGNITION_RANDOM_TICKS = 2;
    /** Minimum ticks between sizzle sounds (debounce). */
    private static final int SIZZLE_DEBOUNCE_TICKS = 20;

    // Package-private fields accessed by CrucibleMelting, CrucibleInsertion, CrucibleSerialization.

    /** NBT key for goo reservoir contents. */
    static final String TAG_RESERVOIR = "Reservoir";
    /** NBT key for the melting item stack. */
    static final String TAG_MELTING_ITEM = "MeltingItem";
    /** NBT key for the fuel rod stack. */
    static final String TAG_FUEL_ROD = "FuelRod";
    /** NBT key for face label. */
    private static final String TAG_CRUCIBLE = "crucible";

    ItemStack meltingItem = ItemStack.EMPTY;
    ItemStack fuelRod = ItemStack.EMPTY;

    /** Multi-type goo reservoir backed by the Transfer API. */
    final GooFluidHandler reservoir = new GooFluidHandler(
        Integer.MAX_VALUE, this::syncToClients);

    /** Composed gasket state for the TRANSMITTER role. */
    private final GasketState gasketState = GasketState.single(GasketRole.TRANSMITTER, TAG_CRUCIBLE);

    /** Game time of the last sizzle sound play (debounce, not serialized). */
    private long lastSizzleTick;

    /** Decoupled registry access, captured in setLevel. */
    @Nullable IGasketRegistryAccess gasketRegistryAccess;

    /** Client-side debounce + crossfade for the dominant goo type display. Public for BER access. */
    public final DominantTypeFader dominantTypeFader = new DominantTypeFader();

    /** Per-instance bubble spawn history for proximity rejection. */
    final CrucibleParticleHelper.BubbleHistory bubbleHistory =
        new CrucibleParticleHelper.BubbleHistory();

    /** Evaluates container items (shulker boxes, bundles) for goo content. */
    final IContainerEvaluator containerEvaluator = new ContainerEvaluator();

    /** Number of remaining ticks to spray ignition sparks. */
    int ignitionSprayTicks;

    /** Pushes reservoir goo to gasket partners on a timed interval. */
    @SuppressWarnings("PMD.LambdaCanBeMethodReference") // field is null at construction; lambda defers read
    final IGasketPusher gasketPusher = new GasketPusher(
        reservoir, () -> gasketState.getId(GasketRole.TRANSMITTER),
        () -> gasketState.getPartner(GasketRole.TRANSMITTER),
        this::getLevel, this::getBlockPos,
        this::syncToClients, () -> gasketRegistryAccess.get());

    /**
     * Creates a crucible block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CRUCIBLE.get(), pos, state);
    }

    // --- Fuel ---

    /** Inserts a fuel rod. Returns ejected rod, or EMPTY if none.
     *
     * @param incoming the incoming item stack
     * @return the ejected rod
     */
    public ItemStack addFuel(ItemStack incoming) {
        ItemStack ejected = fuelRod.isEmpty() ? ItemStack.EMPTY : fuelRod.copy();
        fuelRod = incoming.copyWithCount(1);
        syncToClients();
        return ejected;
    }

    /** Removes and returns the current fuel rod.
     *
     * @return the removed fuel rod, or EMPTY
     */
    public ItemStack removeFuelRod() {
        if (fuelRod.isEmpty()) { return ItemStack.EMPTY; }
        ItemStack removed = fuelRod.copy();
        fuelRod = ItemStack.EMPTY;
        syncToClients();
        return removed;
    }

    /** Returns true if fuel rod has ticks remaining or is a fresh blaze rod.
     *
     * @return true if fuel present
     */
    public boolean hasFuel() {
        return !fuelRod.isEmpty()
                && (fuelRod.is(Items.BLAZE_ROD) || !DepletedBlazeRodItem.isEmpty(fuelRod));
    }

    /** Returns true if the crucible is enabled (no redstone signal).
     *
     * @return true if enabled
     */
    public boolean isEnabled() { return !getBlockState().getValue(CrucibleBlock.POWERED); }

    // --- Reservoir access ---

    /** {@inheritDoc} */
    @Override
    public GooFluidHandler reservoirHandler() { return reservoir; }

    /** Empties the entire reservoir. */
    public void drainReservoir() {
        reservoir.loadFrom(GooContents.EMPTY);
        syncToClients();
    }

    /** Returns the fuel rod stack (may be empty).
     *
     * @return the fuel rod
     */
    public ItemStack getFuelRod() { return fuelRod; }

    /** Returns the melting item stack (may be empty).
     *
     * @return the melting item
     */
    public ItemStack getMeltingItem() { return meltingItem; }

    /** Returns the total mB remaining in the PMI pool.
     *
     * @return the pool volume
     */
    public long getPoolVolume() {
        return meltingItem.isEmpty() ? 0L : PartiallyMeltedItem.getContents(meltingItem).totalVolume();
    }

    /** Returns the fuel rod's remaining fraction (0.0 = depleted, 1.0 = fresh).
     *
     * @return the fuel fraction
     */
    public float fuelFraction() {
        if (fuelRod.isEmpty()) { return 0f; }
        if (fuelRod.is(Items.BLAZE_ROD)) { return 1f; }
        return (float) DepletedBlazeRodItem.getTicksRemaining(fuelRod) / DepletedBlazeRodItem.FULL_FUEL_TICKS;
    }

    /** Returns true if enough time has passed since the last sizzle sound.
     *
     * @param gameTime the game time
     * @return true if should play sizzle
     */
    public boolean shouldPlaySizzle(long gameTime) {
        if (gameTime - lastSizzleTick >= SIZZLE_DEBOUNCE_TICKS) {
            lastSizzleTick = gameTime;
            return true;
        }
        return false;
    }

    /** Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity be) {
        CrucibleMelting.serverTick(be, level, pos, state);
    }

    // --- IGasketHolder ---

    /** {@inheritDoc} */
    @Override
    public GasketState gasketState() { return gasketState; }

    /** {@inheritDoc} */
    @Override
    public GasketRole resolveRole(BlockHitResult hit) { return GasketRegionResolver.resolveCrucibleRole(); }

    /** {@inheritDoc} Checks blockstate rather than static role. */
    @Override
    public boolean supportsRole(GasketRole role) { return getBlockState().getValue(CrucibleBlock.HAS_GASKET); }

    /** {@inheritDoc} */
    @Override
    public Runnable gasketSyncCallback() { return this::syncToClients; }

    /** {@inheritDoc} Rebuilds pusher cache after partner change. */
    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner partner) {
        gasketState.setPartner(role, partner, () -> {
            gasketPusher.rebuildCache();
            syncToClients();
        });
    }

    /** {@inheritDoc} Rebuilds pusher cache after clearing. */
    @Override
    public void clearGasket(GasketRole role) {
        gasketState.clear(role, () -> {
            gasketPusher.rebuildCache();
            syncToClients();
        });
    }

    // --- Framework lifecycle ---

    /** {@inheritDoc} */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        CrucibleSerialization.saveMeltingState(this, output);
        GooContents reservoirContents = reservoir.toGooContents();
        if (!reservoirContents.isEmpty()) {
            output.store(TAG_RESERVOIR, GooContents.CODEC, reservoirContents);
        }
        gasketState.save(output);
    }

    /** {@inheritDoc} */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        CrucibleSerialization.loadMeltingState(this, input);
        reservoir.loadFrom(input.read(TAG_RESERVOIR, GooContents.CODEC).orElse(GooContents.EMPTY));
        gasketState.load(input);
    }

    /** {@inheritDoc} */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            gasketRegistryAccess = () -> GasketRegistry.get(serverLevel);
        }
        gasketPusher.rebuildCache();
    }

    /** {@inheritDoc} */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        gasketPusher.rebuildCache();
        GasketPusher.forceTransmitterChunk(
            gasketState.getId(GasketRole.TRANSMITTER),
            () -> GasketRegistry.get(serverLevel), serverLevel, worldPosition);
    }

    /** Marks dirty and sends sync packet to tracking clients. */
    void syncToClients() { BlockEntitySync.markDirtyAndSync(this); }

    /** {@inheritDoc} */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithFullMetadata(registries);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
