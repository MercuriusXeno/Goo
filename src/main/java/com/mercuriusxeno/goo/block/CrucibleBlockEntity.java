package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.item.DepletedBlazeRodItem;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRegionResolver;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core crucible logic: melts items into goo via a per-tick drain pipeline.
 * Multiple items merge into a shared PMI pool. Extraction rate follows a
 * power-law curve: max(1, floor(remaining ^ exponent)), where the exponent
 * is raised by rune ink matrices. Redstone signal disables the crucible.
 *
 * <h3>Platform Design (invariant - do not change)</h3>
 * <ul>
 *   <li>{@code platformY} is server-side physical state, serialized and synced.
 *       The BER only lerps for sub-tick smoothness - it does NOT compute targets.</li>
 *   <li>Platform lifts the rod to the basin. Target: BASIN_Y - PLAT_THICKNESS - rodHeight.
 *       Moves toward target at PLATFORM_SPEED per tick (1px/tick).</li>
 *   <li>Melting requires rod contact: only starts when rod top reaches the basin
 *       ({@code isRodContactingBasin}). Platform travel time IS the startup delay.</li>
 *   <li>As fuel depletes, rod shrinks and platform RISES to maintain contact.
 *       Full fuel = platform at floor, depleted = platform at basin.</li>
 *   <li>Depleted rods stay in the slot. They persist until replaced or removed.</li>
 * </ul>
 */
public class CrucibleBlockEntity extends BlockEntity implements IGasketHolder, IGooReservoir {

    /** Float tolerance for rod-to-basin contact check (sub-pixel). */
    private static final float ROD_CONTACT_TOLERANCE = 0.001f;
    /** Base ignition spray duration in ticks. */
    private static final int IGNITION_BASE_TICKS = 4;
    /** Random variance added to ignition spray duration (exclusive bound). */
    private static final int IGNITION_RANDOM_TICKS = 2;
    /** Minimum ticks between sizzle sounds (debounce). */
    private static final int SIZZLE_DEBOUNCE_TICKS = 20;

    /** Basin bottom Y in block coords (platform + rod must reach here to melt). */
    static final float BASIN_Y = 9f / 16f;
    /** Platform thickness in block coords (1 pixel). */
    static final float PLAT_THICKNESS = 1f / 16f;
    /** Floor position for the platform. */
    static final float PLAT_FLOOR = 0f;
    /** Full rod height: gap between platform top at floor and basin bottom. */
    static final float ROD_FULL_HEIGHT = BASIN_Y - (PLAT_FLOOR + PLAT_THICKNESS);
    /** Platform travel speed in block coords per tick (1 pixel/tick). */
    private static final float PLATFORM_SPEED = 1f / 16f;

    private ItemStack meltingItem = ItemStack.EMPTY;
    private ItemStack fuelRod = ItemStack.EMPTY;

    /** Multi-type goo reservoir backed by the Transfer API. */
    private final GooFluidHandler reservoir = new GooFluidHandler(
        Integer.MAX_VALUE, this::syncToClients);

    /** Physical platform Y position (bottom edge, block coords). Serialized. */
    private float platformY = PLAT_FLOOR;
    /** Platform Y at the start of the current tick (for client interpolation). */
    private float prevPlatformY = PLAT_FLOOR;

    /** UUID for the bottom gasket, used by the tuner link network. */
    private @Nullable UUID gasketId;
    /** Denormalized partner reference for HUD display. */
    private @Nullable GasketPartner gasketPartner;
    /** Game time of the last sizzle sound play (debounce, not serialized). */
    private long lastSizzleTick;

    /** Decoupled registry access, captured in setLevel to avoid direct GasketRegistry.get calls. */
    private @Nullable IGasketRegistryAccess gasketRegistryAccess;

    /** Client-side debounce + crossfade for the dominant goo type display. */
    private final DominantTypeFader dominantTypeFader = new DominantTypeFader();

    // --- NBT tag keys ---
    /** NBT key for face label. */
    private static final String TAG_CRUCIBLE = "crucible";
    /** NBT key for platform Y position. */
    private static final String TAG_PLATFORM_Y = "PlatformY";
    /** NBT key for goo reservoir contents. */
    private static final String TAG_RESERVOIR = "Reservoir";
    /** NBT key for the melting item stack. */
    private static final String TAG_MELTING_ITEM = "MeltingItem";
    /** NBT key for the fuel rod stack. */
    private static final String TAG_FUEL_ROD = "FuelRod";
    /** NBT key for gasket UUID. */
    private static final String TAG_GASKET_ID = "GasketId";
    /** NBT key for gasket partner. */
    private static final String TAG_GASKET_PARTNER = "GasketPartner";

    /** Evaluates container items (shulker boxes, bundles) for goo content. */
    private final IContainerEvaluator containerEvaluator = new ContainerEvaluator();

    /** Number of remaining ticks to spray ignition sparks. */
    private int ignitionSprayTicks;

    /** Pushes reservoir goo to gasket partners on a timed interval. */
    private final IGasketPusher gasketPusher = new GasketPusher(
        reservoir, () -> gasketId, () -> gasketPartner, this::getLevel, this::getBlockPos,
        this::syncToClients, gasketRegistryAccess::get);

    /** Returns the container evaluator for use by callers (e.g. CrucibleBlock).
     *
     * @return the container evaluator
     */
    public IContainerEvaluator getContainerEvaluator() { return containerEvaluator; }

    /**
     * Creates a crucible block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CRUCIBLE.get(), pos, state);
    }

    /** Instance-level server tick dispatcher.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     */
    private void serverTick(Level level, BlockPos pos, BlockState state) {
        tickPlatform();
        tickIgnitionSpray();
        handleMeltingTick(level, pos, state);
        handleBoilingEffects(level, pos);
        gasketPusher.tick();
    }

    /** Updates the stabilized dominant type with debounce. Called client-side, gated to once per game tick. */
    public void tickDominantType() {
        if (level == null) { return; }
        dominantTypeFader.tick(reservoir.largestType(), level.getGameTime());
    }

    /** Returns the debounce-stabilized dominant goo type for rendering.
     *
     * @return the shown dominant type
     */
    public @Nullable GooType getShownDominantType() {
        return dominantTypeFader.getShownType();
    }

    /** Returns the outgoing type during a crossfade, or null if not crossfading.
     *
     * @return the outgoing dominant type
     */
    public @Nullable GooType getOutgoingDominantType() {
        return dominantTypeFader.getOutgoingType();
    }

    /** Returns the crossfade alpha [0, 1]: 0 = fully outgoing, 1 = fully shown.
     *
     * @return the crossfade alpha
     */
    public float getCrossfadeAlpha() {
        return dominantTypeFader.getCrossfadeAlpha();
    }

    /**
     * Spawns boiling bubbles and embers whenever the rod is heated and goo is present,
     * regardless of whether there is an item being melted. This lets players enable
     * boiling at will by inserting a fuel rod into goo-filled basins.
     *
     * @param level the current level
     * @param pos   the block position
     */
    private void handleBoilingEffects(Level level, BlockPos pos) {
        if (!isEnabled()) { return; }
        if (!hasFuel()) { return; }
        if (!isRodContactingBasin()) { return; }
        if (hasMeltableItem()) { return; } // already handled by spawnActiveEffects in handleMeltingTick
        spawnActiveEffects(level, pos, false);
    }

    /**
     * Moves the platform toward its target Y at a fixed speed each tick.
     * Saves prev position for client-side partialTick interpolation.
     * Spawns ignition sparks when the rod first makes contact with the basin.
     */
    private void tickPlatform() {
        prevPlatformY = platformY;
        float target = computeTargetPlatformY();
        if (platformY == target) { return; }
        boolean wasTouching = isRodContactingBasin();
        platformY = CrucibleMath.moveToward(platformY, target, PLATFORM_SPEED);
        boolean justIgnited = !wasTouching && isRodContactingBasin();
        if (justIgnited) {
            beginIgnitionSpray();
        }
        syncToClients();
    }

    /** Returns true when the platform should drop to the floor (disabled or no fuel).
     *
     * @return true if platform return to floor
     */
    private boolean shouldPlatformReturnToFloor() {
        return !isEnabled() || !hasFuel();
    }

    /**
     * Computes the target platform Y based on enabled state and rod size.
     * The platform lifts the rod so its top contacts the basin.
     * No rod or disabled: platform drops to the floor.
     *
     * @return the computed target platform y
     */
    float computeTargetPlatformY() {
        if (shouldPlatformReturnToFloor()) { return PLAT_FLOOR; }
        float rodHeight = fuelFraction() * ROD_FULL_HEIGHT;
        return Math.max(PLAT_FLOOR, BASIN_Y - PLAT_THICKNESS - rodHeight);
    }

    /** Returns true if the fuel rod's top has reached the basin bottom.
     *
     * @return true if rod contacting basin
     */
    private boolean isRodContactingBasin() {
        float rodHeight = fuelFraction() * ROD_FULL_HEIGHT;
        float rodTop = platformY + PLAT_THICKNESS + rodHeight;
        return rodTop >= BASIN_Y - ROD_CONTACT_TOLERANCE;
    }

    /**
     * Per-tick melting: drains goo from the PMI pool into the reservoir.
     * Skips if disabled, no fuel, no meltable item, or the rod hasn't
     * reached the basin yet (platform still travelling).
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     */
    private void handleMeltingTick(Level level, BlockPos pos, BlockState state) {
        if (!isEnabled()) { return; }
        if (!hasMeltableItem()) { return; }
        if (!hasFuel()) { return; }
        if (!isRodContactingBasin()) { return; }

        convertFreshRodToDepleted();
        drainFromPool();
        spawnActiveEffects(level, pos, true);
        consumeFuelTick();
        clearFinishedMeltingItem();

        syncToClients();
    }

    /** Converts a vanilla blaze rod to a depleted blaze rod on its first burn tick. */
    private void convertFreshRodToDepleted() {
        if (fuelRod.is(Items.BLAZE_ROD)) {
            fuelRod = DepletedBlazeRodItem.createFresh();
            beginIgnitionSpray();
        }
    }

    /** Consumes one fuel tick, destroying the rod when fully exhausted. */
    private void consumeFuelTick() {
        if (!DepletedBlazeRodItem.consumeTick(fuelRod)) {
            fuelRod = ItemStack.EMPTY;
        }
    }

    /** Begins a sustained single-spark spray when the blaze rod first contacts the basin. */
    private void beginIgnitionSpray() {
        ignitionSprayTicks = IGNITION_BASE_TICKS
            + (level != null ? level.getRandom().nextInt(IGNITION_RANDOM_TICKS) : 0);
    }

    /** Spawns four cardinal sparks per tick while the ignition spray is active. */
    private void tickIgnitionSpray() {
        if (ignitionSprayTicks <= 0) { return; }
        ignitionSprayTicks--;
        if (level instanceof ServerLevel serverLevel) {
            CrucibleParticleHelper.spawnIgnitionSparks(serverLevel, worldPosition);
        }
    }

    /**
     * Spawns embers and goo bubbles while the crucible is actively processing.
     * Ember frequency scales with whether an item is being melted.
     *
     * @param level   the current level
     * @param pos     the block position
     * @param melting true if actively melting an item
     */
    private void spawnActiveEffects(Level level, BlockPos pos, boolean melting) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        CrucibleParticleHelper.spawnEmbers(serverLevel, pos, level.getRandom(), melting);
        spawnBubblesIfGooPresent(serverLevel, pos);
    }

    /** Spawns goo-colored bubbles if there is goo in the reservoir or pool.
     *
     * @param serverLevel the server level
     * @param pos         the block position
     */
    private void spawnBubblesIfGooPresent(ServerLevel serverLevel, BlockPos pos) {
        long totalGoo = reservoir.totalVolume() + getPoolVolume();
        if (totalGoo <= 0) { return; }
        GooType dominant = reservoir.largestType();
        if (dominant == null) { dominant = dominantPoolType(); }
        if (dominant == null) { return; }
        float surfaceY = CrucibleParticleHelper.computeSurfaceY(totalGoo);
        CrucibleParticleHelper.spawnGooBubbles(
            serverLevel, pos, surfaceY, dominant.getColor(), serverLevel.getRandom());
    }

    /** Returns the largest goo type in the PMI pool, or null if empty.
     *
     * @return the goo type, or null
     */
    private @Nullable GooType dominantPoolType() {
        if (meltingItem.isEmpty()) { return null; }
        return PartiallyMeltedItem.getContents(meltingItem).largestType();
    }

    /**
     * Returns true if enough time has passed since the last sizzle sound.
     * Updates the timestamp when returning true. Debounce interval: 20 ticks (1 sec).
     *
     * @param gameTime the game time
     * @return true if play sizzle
     */
    public boolean shouldPlaySizzle(long gameTime) {
        if (gameTime - lastSizzleTick >= SIZZLE_DEBOUNCE_TICKS) {
            lastSizzleTick = gameTime;
            return true;
        }
        return false;
    }

    /**
     * Returns true if the crucible is enabled (no redstone signal).
     * Reads from the POWERED blockstate property set by neighborChanged.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return !getBlockState().getValue(CrucibleBlock.POWERED);
    }

    /** Returns true if a PMI with remaining goo is loaded.
     *
     * @return true if meltable item
     */
    private boolean hasMeltableItem() {
        return !meltingItem.isEmpty() && !PartiallyMeltedItem.isFullyMelted(meltingItem);
    }

    /** Returns true if the fuel rod has ticks remaining, or is a fresh vanilla blaze rod.
     *
     * @return true if fuel
     */
    public boolean hasFuel() {
        return !fuelRod.isEmpty()
                && (fuelRod.is(Items.BLAZE_ROD) || !DepletedBlazeRodItem.isEmpty(fuelRod));
    }

    /**
     * Drains extractionRate() mB from the PMI pool, distributed proportionally
     * across all goo types present. Each type receives at least 1 mB per tick
     * (or its remaining volume if less). This ensures multi-type items melt
     * all their types simultaneously rather than one-at-a-time.
     */
    private void drainFromPool() {
        GooContents pmiContents = PartiallyMeltedItem.getContents(meltingItem);
        long totalRemaining = pmiContents.totalVolume();
        if (totalRemaining <= 0) { return; }

        long rate = CrucibleMath.extractionRate(totalRemaining, 0);
        Map<GooType, Long> shares = CrucibleMath.computeDrainShares(pmiContents, rate);

        for (Map.Entry<GooType, Long> entry : shares.entrySet()) {
            long drained = PartiallyMeltedItem.drain(
                meltingItem, entry.getKey(), entry.getValue());
            reservoir.insertGoo(entry.getKey(), (int) drained, false);
        }
    }

    /** Clears the melting item when all goo has been fully drained. */
    private void clearFinishedMeltingItem() {
        if (meltingItem.isEmpty()) { return; }
        if (PartiallyMeltedItem.isFullyMelted(meltingItem)) {
            meltingItem = ItemStack.EMPTY;
        }
    }

    /** Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity be) {
        be.serverTick(level, pos, state);
    }

    // --- Fuel ---

    /**
     * Inserts a fuel rod (vanilla blaze rod or depleted blaze rod).
     * If a rod is already present, it is returned for ejection.
     * Returns the ejected rod, or EMPTY if none.
     *
     * @param incoming the incoming item stack
     * @return the item stack
     */
    public ItemStack addFuel(ItemStack incoming) {
        ItemStack ejected = fuelRod.isEmpty() ? ItemStack.EMPTY : fuelRod.copy();
        fuelRod = incoming.copyWithCount(1);
        syncToClients();
        return ejected;
    }

    /**
     * Removes and returns the current fuel rod. Returns EMPTY if no rod is present.
     *
     * @return the item stack
     */
    public ItemStack removeFuelRod() {
        if (fuelRod.isEmpty()) { return ItemStack.EMPTY; }
        ItemStack removed = fuelRod.copy();
        fuelRod = ItemStack.EMPTY;
        syncToClients();
        return removed;
    }

    // --- IGasketHolder tuner dispatch ---

    /** {@inheritDoc} Crucible is always a transmitter.
     *
     * @param hit the ray trace hit result
     * @return the resolved gasket role
     */
    @Override
    public GasketRole resolveRole(BlockHitResult hit) {
        return GasketRegionResolver.resolveCrucibleRole();
    }

    /** {@inheritDoc} Checks blockstate for HAS_GASKET.
     *
     * @param role the gasket role
     * @return true if the condition is met
     */
    @Override
    public boolean supportsRole(GasketRole role) {
        return getBlockState().getValue(CrucibleBlock.HAS_GASKET);
    }

    /** {@inheritDoc} Always "crucible".
     *
     * @param role the gasket role
     * @return the face label
     */
    @Override
    public @Nullable String getFaceLabel(GasketRole role) {
        return TAG_CRUCIBLE;
    }

    // --- IGasketHolder (TRANSMITTER only) ---

    /** {@inheritDoc}
     *
     * @param role the gasket role
     * @return the gasket id
     */
    @Override
    public @Nullable UUID getGasketId(GasketRole role) {
        return role == GasketRole.TRANSMITTER ? gasketId : null;
    }

    /** {@inheritDoc}
     *
     * @param role the gasket role
     * @return the UUID, or null
     */
    @Override
    public @Nullable UUID ensureGasketId(GasketRole role) {
        if (role != GasketRole.TRANSMITTER) { return null; }
        if (gasketId == null) {
            gasketId = UUID.randomUUID();
            syncToClients();
        }
        return gasketId;
    }

    /** {@inheritDoc}
     *
     * @param role the gasket role
     * @return the partner
     */
    @Override
    public @Nullable GasketPartner getPartner(GasketRole role) {
        return role == GasketRole.TRANSMITTER ? gasketPartner : null;
    }

    /** {@inheritDoc}
     *
     * @param role    the gasket role
     * @param partner the gasket partner, or null to clear
     */
    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner partner) {
        if (role != GasketRole.TRANSMITTER) { return; }
        gasketPartner = partner;
        gasketPusher.rebuildCache();
        syncToClients();
    }

    /** Clears the gasket UUID and partner for the transmitter role.
     *
     * @param role the gasket role
     */
    @Override
    public void clearGasket(GasketRole role) {
        if (role != GasketRole.TRANSMITTER) { return; }
        gasketId = null;
        gasketPartner = null;
        gasketPusher.rebuildCache();
        syncToClients();
    }

    // --- Item insertion ---

    /**
     * Returns true if the crucible can accept a new item for melting.
     * Items can always be added to the shared pool as long as they have goo value.
     *
     * @param stack the item stack to check
     * @return true if the item has a non-empty goo value
     */
    public boolean canInsertItem(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return canInsertItem(id, Goo.GOO_VALUES);
    }

    /** Testable seam: checks goo value via Identifier without registry coupling.
     *
     * @param itemId the item registry ID
     * @param lookup the goo value lookup
     * @return true if insert item
     */
    boolean canInsertItem(Identifier itemId, IGooValueLookup lookup) {
        GooValue value = lookup.lookup(itemId);
        return value != null && !value.isEmpty();
    }

    /**
     * Inserts a stack of items for melting into the shared PMI pool.
     * Goo value is multiplied by count and merged in one operation.
     * If a PMI already exists, the goo is added to it. Otherwise, a new PMI is created.
     *
     * @param stack the item stack to insert
     * @param count the number of items to insert (multiplies goo value)
     * @return true if the item was inserted
     */
    public boolean insertItem(ItemStack stack, int count) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return insertItem(id, count, Goo.GOO_VALUES);
    }

    /** Testable seam: inserts goo by Identifier without registry coupling.
     *
     * @param itemId the item registry ID
     * @param count  the item count
     * @param lookup the goo value lookup
     * @return true if the condition is met
     */
    boolean insertItem(Identifier itemId, int count, IGooValueLookup lookup) {
        GooValue value = lookup.lookup(itemId);
        if (value == null || value.isEmpty()) { return false; }
        GooContents itemContents = value.toGooContents(count);
        mergeIntoPool(itemContents);
        syncToClients();
        return true;
    }

    /** Merges goo contents into the PMI pool, creating a new PMI if needed.
     *
     * @param contents the goo contents
     */
    private void mergeIntoPool(GooContents contents) {
        if (meltingItem.isEmpty()) {
            meltingItem = PartiallyMeltedItem.createWith(contents);
        } else {
            PartiallyMeltedItem.mergeContents(meltingItem, contents);
        }
    }

    /** Re-inserts a PMI's remaining goo contents into the melt pool.
     *
     * @param contents the goo contents
     */
    public void insertPMI(GooContents contents) {
        mergeIntoPool(contents);
        syncToClients();
    }

    /**
     * Merges bucket contents directly into the reservoir, bypassing the melting
     * pipeline. Used when pouring a filled bucket into the basin. No fuel required.
     *
     * @param bucketContents the bucket goo contents
     */
    public void insertGooContents(GooContents bucketContents) {
        if (bucketContents.isEmpty()) { return; }
        for (var entry : bucketContents.getAll().entrySet()) {
            reservoir.insertGoo(entry.getKey(), (int) Math.min(entry.getValue(), Integer.MAX_VALUE), false);
        }
        syncToClients();
    }

    /** {@inheritDoc} Bypasses the melting pipeline. No fuel required.
     *
     * @param type   the goo type
     * @param volume volume in microblobs
     * @return the long value
     */
    @Override
    public long insertGoo(GooType type, long volume) {
        if (volume <= 0) { return 0; }
        int clamped = (int) Math.min(volume, Integer.MAX_VALUE);
        long inserted = reservoir.insertGoo(type, clamped, false);
        syncToClients();
        return inserted;
    }

    /**
     * Inserts a container item's evaluated contents into the melt pool.
     * Goo values are merged into the PMI; returns the eject list for
     * the caller to spawn as item entities.
     *
     * @param container the container item stack
     * @return list of items to eject, or null if the container had no content
     */
    public @Nullable List<ItemStack> insertContainer(ItemStack container) {
        Identifier containerId = BuiltInRegistries.ITEM.getKey(container.getItem());
        return insertContainer(containerId, container, Goo.GOO_VALUES);
    }

    /** Testable seam: evaluates a container via Identifier without registry coupling.
     *
     * @param containerId the container registry ID
     * @param container   the container item stack
     * @param lookup      the goo value lookup
     * @return the list
     */
    @Nullable List<ItemStack> insertContainer(Identifier containerId, ItemStack container, IGooValueLookup lookup) {
        IContainerEvaluator.ContainerEvaluation eval = containerEvaluator.evaluate(containerId, container, lookup);
        if (eval.goo().isEmpty() && eval.ejects().isEmpty()) { return null; }
        if (!eval.goo().isEmpty()) {
            mergeIntoPool(eval.goo());
        }
        syncToClients();
        return eval.ejects();
    }

    // --- Reservoir access ---

    /** {@inheritDoc}
     *
     * @return the reservoir
     */
    @Override
    public GooContents getReservoir() { return reservoir.toGooContents(); }

    /** Empties the entire reservoir and syncs to clients. */
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
        if (meltingItem.isEmpty()) { return 0L; }
        return PartiallyMeltedItem.getContents(meltingItem).totalVolume();
    }

    /** Returns the current physical platform Y position in block coords.
     *
     * @return the platform y
     */
    public float getPlatformY() { return platformY; }

    /** Returns the platform Y at the start of the current tick (for partialTick interpolation).
     *
     * @return the prev platform y
     */
    public float getPrevPlatformY() { return prevPlatformY; }

    /**
     * Returns the fuel rod's remaining fraction (0.0 = depleted, 1.0 = fresh).
     * A vanilla blaze rod (not yet burning) returns 1.0.
     *
     * @return the float value
     */
    public float fuelFraction() {
        if (fuelRod.isEmpty()) { return 0f; }
        if (fuelRod.is(Items.BLAZE_ROD)) { return 1f; }
        int remaining = DepletedBlazeRodItem.getTicksRemaining(fuelRod);
        return (float) remaining / DepletedBlazeRodItem.FULL_FUEL_TICKS;
    }

    /** {@inheritDoc}
     *
     * @param type   the goo type
     * @param amount volume in microblobs
     * @return the long value
     */
    @Override
    public long extractGoo(GooType type, long amount) {
        int clamped = (int) Math.min(amount, Integer.MAX_VALUE);
        return reservoir.extractGoo(type, clamped, false);
    }

    // --- Serialization ---

    /** {@inheritDoc}
     *
     * @param output the value output to write to
     */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat(TAG_PLATFORM_Y, platformY);
        saveMeltingState(output);
        GooContents reservoirContents = reservoir.toGooContents();
        if (!reservoirContents.isEmpty()) {
            output.store(TAG_RESERVOIR, GooContents.CODEC, reservoirContents);
        }
        saveGasketFields(output);
    }

    /** {@inheritDoc}
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        platformY = input.getFloatOr(TAG_PLATFORM_Y, PLAT_FLOOR);
        prevPlatformY = platformY;
        loadMeltingState(input);
        reservoir.loadFrom(input.read(TAG_RESERVOIR, GooContents.CODEC).orElse(GooContents.EMPTY));
        loadGasketFields(input);
    }

    /** Saves melting item and fuel rod stacks.
     *
     * @param output the value output to write to
     */
    private void saveMeltingState(ValueOutput output) {
        if (!meltingItem.isEmpty()) {
            output.store(TAG_MELTING_ITEM, ItemStack.CODEC, meltingItem);
        }
        if (!fuelRod.isEmpty()) {
            output.store(TAG_FUEL_ROD, ItemStack.CODEC, fuelRod);
        }
    }

    /** Loads melting item and fuel rod stacks.
     *
     * @param input the value input to read from
     */
    private void loadMeltingState(ValueInput input) {
        meltingItem = input.read(TAG_MELTING_ITEM, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        fuelRod = input.read(TAG_FUEL_ROD, ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    /** Saves gasket UUID and partner reference.
     *
     * @param output the value output to write to
     */
    private void saveGasketFields(ValueOutput output) {
        if (gasketId != null) {
            output.store(TAG_GASKET_ID, UUIDUtil.STRING_CODEC, gasketId);
        }
        if (gasketPartner != null) {
            output.store(TAG_GASKET_PARTNER, GasketPartner.CODEC, gasketPartner);
        }
    }

    /** Loads gasket UUID and partner reference.
     *
     * @param input the value input to read from
     */
    private void loadGasketFields(ValueInput input) {
        gasketId = input.read(TAG_GASKET_ID, UUIDUtil.STRING_CODEC).orElse(null);
        gasketPartner = input.read(TAG_GASKET_PARTNER, GasketPartner.CODEC).orElse(null);
    }

    /** Captures gasket registry access and rebuilds the push cache when the level is assigned.
     *
     * @param level the current level
     */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            gasketRegistryAccess = () -> GasketRegistry.get(serverLevel);
        }
        gasketPusher.rebuildCache();
    }

    /** Rebuilds pusher cache (cascade) and forces transmitter chunk (bidirectional) on chunk load. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        gasketPusher.rebuildCache();
        GasketPusher.forceTransmitterChunk(gasketId, () -> GasketRegistry.get(serverLevel), serverLevel, worldPosition);
    }

    /** Marks dirty and sends sync packet to tracking clients. */
    private void syncToClients() {
        BlockEntitySync.markDirtyAndSync(this);
    }

    /** Returns full serialized state as the initial sync packet for tracking clients.
     *
     * @param registries the registry provider
     * @return the update tag
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithFullMetadata(registries);
    }

    /** Creates the block entity data packet for incremental client sync.
     *
     * @return the update packet
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
