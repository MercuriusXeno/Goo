package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.effect.ChainProfiles.ChainProfile;
import com.mercuriusxeno.goo.effect.EffectMath;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Ticking block entity for chain effects. Counts down a fuse; additional
 * blobs landing during the window increment stacks. On expiry, computes
 * range from the profile's formula and fires the executor, then removes
 * itself.
 */
public class ChainMarkerBlockEntity extends BlockEntity {

    private static final String TAG_GOO_TYPE = "GooType";
    private static final String TAG_STACK_COUNT = "StackCount";
    private static final String TAG_MAX_STACKS = "MaxStacks";
    private static final String TAG_FUSE_REMAINING = "FuseRemaining";
    private static final String TAG_PLACED_FACE = "PlacedFace";
    private static final String TAG_MINING_STEP = "MiningStep";
    private static final String TAG_MINING_DEPTH = "MiningDepth";
    private static final String TAG_PHASE = "Phase";
    private static final String TAG_IMPLODE_REMAINING = "ImplodeRemaining";
    private static final String TAG_IMPLODE_INITIAL = "ImplodeInitial";
    private static final String TAG_IMPLODE_RADIUS = "ImplodeRadius";
    private static final String TAG_ACCUMULATOR = "Accumulator";
    /** Default goo type id when loading from NBT. */
    private static final String DEFAULT_GOO_TYPE = "rock";
    /** Default face name when loading from NBT. */
    private static final String DEFAULT_FACE = "up";
    /** Sentinel: no progressive mining in progress. */
    private static final int MINING_INACTIVE = -1;
    /** Default implosion duration in ticks (1.25 seconds). */
    public static final int IMPLODE_DURATION = 25;
    /** Base placeholder soul particle count per implosion tick. */
    private static final int IMPLODE_PARTICLE_BASE = 3;
    /** Additional placeholder soul particles per stack. */
    private static final int IMPLODE_PARTICLE_PER_STACK = 2;
    /** Placeholder particle spread as a fraction of the implosion radius. */
    private static final double IMPLODE_PARTICLE_SPREAD_PER_RANGE = 0.6;
    /** Placeholder particle velocity. */
    private static final double IMPLODE_PARTICLE_SPEED = 0.02;
    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;

    /**
     * Lifecycle phase of the chain marker. Most profiles stay in {@link #FUSE}
     * forever and the BE is removed when the fuse expires. Nether additionally
     * transitions through {@link #IMPLODING} (accumulator priming + animation)
     * and {@link #POPPING} (single-tick drop + self-remove).
     */
    public enum Phase { FUSE, IMPLODING, POPPING }

    private GooType gooType = GooType.ROCK;
    private int stackCount = 1;
    private int maxStacks = 1;
    private int fuseRemaining;
    private Direction placedFace = Direction.UP;
    /** Next layer index to mine while progressive mining is active; -1 otherwise. */
    private int miningStep = MINING_INACTIVE;
    /** Total number of layers to mine when progressive mining is active. */
    private int miningDepth;
    /** Lifecycle phase. See {@link Phase}. */
    private Phase phase = Phase.FUSE;
    /** Ticks remaining in the current implosion animation. Only meaningful when phase == IMPLODING. */
    private int implodeTicksRemaining;
    /** Total implosion duration for progress calculation. */
    private int initialImplodeTicks;
    /** Radius of the effect sphere, exposed for goal-013's BER. */
    private int implodeRadius;
    /** Per-type mB totals accumulated during sphere walk, dropped at POPPING. */
    private GooContents accumulator = GooContents.EMPTY;

    /** How often to sync fuse to client (every N ticks). */
    private static final int SYNC_INTERVAL = 5;

    /** Ticks before detonation where we sync every tick for smooth implosion. */
    private static final int IMPLOSION_SYNC_THRESHOLD = 8;

    /** Creates a chain marker block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public ChainMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CHAIN_MARKER.get(), pos, state);
    }

    // ── Initialization ────────────────────────────────────────────────────

    /**
     * Configures this marker from a chain profile. Call immediately after
     * placement via {@code level.setBlock()}.
     *
     * @param type the goo type (determines chain behavior)
     * @param face the face of the block this marker was placed on
     */
    public void initChain(GooType type, Direction face) {
        ChainProfile profile = ChainProfile.forType(type);
        this.gooType = type;
        this.placedFace = face;
        this.stackCount = 1;
        this.maxStacks = profile.maxStacks();
        this.fuseRemaining = profile.fuseTicks();
        setChanged();
        syncToClient();
    }

    // ── Stacking ──────────────────────────────────────────────────────────

    /**
     * Attempts to increment the stack count. Returns true if successful.
     * Resets the fuse timer on each successful stack.
     *
     * @return the result of stack
     */
    public boolean tryStack() {
        if (!EffectMath.canStack(stackCount, maxStacks)) { return false; }
        ChainProfile profile = ChainProfile.forType(gooType);
        stackCount++;
        fuseRemaining = profile.fuseTicks();
        setChanged();
        syncToClient();
        return true;
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    /** Server tick: dispatches to the current phase's handler.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ChainMarkerBlockEntity be) {
        ServerLevel server = (ServerLevel) level;
        switch (be.phase) {
            case FUSE -> be.tickFuse(server, pos);
            case IMPLODING -> be.tickImplosion(server, pos);
            case POPPING -> be.tickPopping(server, pos);
        }
    }

    /** FUSE-phase tick: progressive mining passthrough or fuse countdown + detonate on expiry.
     *
     * @param level the server level
     * @param pos   the block position
     */
    private void tickFuse(ServerLevel level, BlockPos pos) {
        if (miningStep != MINING_INACTIVE) {
            tickProgressiveMining(level, pos);
            return;
        }
        fuseRemaining--;
        if (!EffectMath.isFuseLive(fuseRemaining)) {
            detonate(level, pos);
            return;
        }
        syncIfNeeded();
    }

    /** IMPLODING-phase tick: countdown, particle burst, transition to POPPING on expiry.
     *
     * @param level the server level
     * @param pos   the marker position
     */
    private void tickImplosion(ServerLevel level, BlockPos pos) {
        implodeTicksRemaining--;
        spawnImplosionParticles(level, pos);
        if (implodeTicksRemaining <= 0) {
            phase = Phase.POPPING;
        }
        setChanged();
        syncToClient();
    }

    /** POPPING-phase tick: single-tick drop of the accumulator + self-removal.
     *
     * @param level the server level
     * @param pos   the marker position
     */
    private void tickPopping(ServerLevel level, BlockPos pos) {
        BlobStacks.dropAll(accumulator, level, pos);
        level.removeBlock(pos, false);
    }

    /** Placeholder visual for the implosion animation. Replaced by a real
     * shader sphere in goal-013 once Phase 2 is stable.
     *
     * @param level the server level
     * @param pos   the marker position
     */
    private void spawnImplosionParticles(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + BLOCK_CENTER_OFFSET;
        double cy = pos.getY() + BLOCK_CENTER_OFFSET;
        double cz = pos.getZ() + BLOCK_CENTER_OFFSET;
        int count = IMPLODE_PARTICLE_BASE + IMPLODE_PARTICLE_PER_STACK * stackCount;
        double spread = implodeRadius * IMPLODE_PARTICLE_SPREAD_PER_RANGE;
        level.sendParticles(ParticleTypes.SOUL,
            cx, cy, cz, count, spread, spread, spread, IMPLODE_PARTICLE_SPEED);
    }

    /**
     * Seeds this BE with the nether accumulator totals and transitions into
     * IMPLODING. Called by {@code NetherExecutor.execute} after it walks the
     * sphere and removes the affected blocks.
     *
     * @param totals per-type mB totals to drop on POPPING
     * @param radius effect radius, exposed to the BER as {@link #getCurrentRadius()}
     */
    public void beginImplosion(@NonNull GooContents totals, int radius) {
        this.accumulator = totals;
        this.implodeRadius = radius;
        this.initialImplodeTicks = IMPLODE_DURATION;
        this.implodeTicksRemaining = IMPLODE_DURATION;
        this.phase = Phase.IMPLODING;
        setChanged();
        syncToClient();
    }

    /** Sends a client sync packet when entering implosion zone or at regular intervals. */
    private void syncIfNeeded() {
        boolean implosionZone = fuseRemaining <= IMPLOSION_SYNC_THRESHOLD;
        if (implosionZone || fuseRemaining % SYNC_INTERVAL == 0) {
            syncToClient();
        }
    }

    /** Fires the chain executor and removes the block, unless the executor
     * transitioned this BE out of {@link Phase#FUSE} (nether's phase machine
     * keeps the marker alive to drive IMPLODING + POPPING on its own).
     *
     * @param level the current level
     * @param pos   the block position
     */
    private void detonate(ServerLevel level, BlockPos pos) {
        ChainProfile profile = ChainProfile.forType(gooType);
        if (profile == null) {
            level.removeBlock(pos, false);
            return;
        }
        int range = profile.rangeFormula().applyAsInt(stackCount);
        if (profile.layerExecutor() != null) {
            beginProgressiveMining(range);
            return;
        }
        if (profile.executor() != null) {
            profile.executor().execute(level, pos, range, stackCount, placedFace);
        }
        if (phase == Phase.FUSE) {
            level.removeBlock(pos, false);
        }
    }

    /** Transitions this marker into progressive mining mode. The BE will
     * remain in place, ticking one layer per server tick until {@code depth}
     * layers have been processed.
     *
     * @param depth total layers to mine
     */
    private void beginProgressiveMining(int depth) {
        miningDepth = depth;
        miningStep = 0;
        setChanged();
    }

    /** Advances progressive mining by one layer. Removes the marker once
     * all layers have been processed.
     *
     * @param level the server level
     * @param pos   the marker position
     */
    private void tickProgressiveMining(ServerLevel level, BlockPos pos) {
        ChainProfile profile = ChainProfile.forType(gooType);
        if (profile == null || profile.layerExecutor() == null) {
            level.removeBlock(pos, false);
            return;
        }
        profile.layerExecutor().tickLayer(level, pos, miningStep, stackCount, placedFace);
        miningStep++;
        if (miningStep >= miningDepth) {
            level.removeBlock(pos, false);
            return;
        }
        setChanged();
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /** Returns the goo type driving this chain effect.
     *
     * @return the goo type
     */
    public GooType getGooType() {
        return gooType;
    }

    /** Returns the current stack count (number of blobs absorbed).
     *
     * @return the stack count
     */
    public int getStackCount() {
        return stackCount;
    }

    /** Returns the maximum stacks allowed by the chain profile.
     *
     * @return the max stacks
     */
    public int getMaxStacks() {
        return maxStacks;
    }

    /** Returns the remaining fuse ticks before detonation.
     *
     * @return the fuse remaining
     */
    public int getFuseRemaining() {
        return fuseRemaining;
    }

    /** Returns the block face this marker was placed on.
     *
     * @return the placed face
     */
    public Direction getPlacedFace() {
        return placedFace;
    }

    /** Returns the current lifecycle phase (FUSE, IMPLODING, POPPING).
     *
     * @return the current phase
     */
    public Phase getPhase() {
        return phase;
    }

    /** Returns the per-type mB accumulator. Empty during FUSE, populated
     * from sphere walk during IMPLODING, dropped at POPPING.
     *
     * @return the accumulator
     */
    public GooContents getAccumulator() {
        return accumulator;
    }

    /** Returns the radius of the in-progress implosion, or 0 if not imploding.
     * Exposed for goal-013's shader BER hand-off contract.
     *
     * @return the current effect radius in blocks
     */
    public int getCurrentRadius() {
        return implodeRadius;
    }

    /** Returns the implosion progress in [0, 1]: 0 at entry, 1 just before POPPING.
     * Exposed for goal-013's shader BER hand-off contract.
     *
     * @return the implosion progress
     */
    public float getProgress() {
        if (initialImplodeTicks <= 0) { return 0f; }
        return 1f - ((float) implodeTicksRemaining / initialImplodeTicks);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    /** Restores chain state from persistent storage.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        loadGooState(input);
        placedFace = loadFace(input);
    }

    /** Restores goo type, stack count, max stacks, and fuse from persistent data.
     *
     * @param input the value input to read from
     */
    private void loadGooState(ValueInput input) {
        GooType loaded = GooType.fromId(input.getStringOr(TAG_GOO_TYPE, DEFAULT_GOO_TYPE));
        gooType = loaded != null ? loaded : GooType.ROCK;
        stackCount = input.getIntOr(TAG_STACK_COUNT, 1);
        maxStacks = input.getIntOr(TAG_MAX_STACKS, 1);
        fuseRemaining = input.getIntOr(TAG_FUSE_REMAINING, 0);
        miningStep = input.getIntOr(TAG_MINING_STEP, MINING_INACTIVE);
        miningDepth = input.getIntOr(TAG_MINING_DEPTH, 0);
        loadPhaseState(input);
    }

    /** Restores the phase state machine fields from persistent data.
     * Non-nether profiles load {@link Phase#FUSE} and empty accumulator.
     *
     * @param input the value input to read from
     */
    private void loadPhaseState(ValueInput input) {
        phase = parsePhase(input.getStringOr(TAG_PHASE, Phase.FUSE.name()));
        implodeTicksRemaining = input.getIntOr(TAG_IMPLODE_REMAINING, 0);
        initialImplodeTicks = input.getIntOr(TAG_IMPLODE_INITIAL, 0);
        implodeRadius = input.getIntOr(TAG_IMPLODE_RADIUS, 0);
        accumulator = input.read(TAG_ACCUMULATOR, GooContents.CODEC).orElse(GooContents.EMPTY);
    }

    /** Parses a phase name, defaulting to FUSE on any unknown/legacy value.
     *
     * @param name stored phase name
     * @return the phase, or FUSE if unrecognized
     */
    private static Phase parsePhase(String name) {
        try {
            return Phase.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Phase.FUSE;
        }
    }

    /** Loads the placed face direction, defaulting to UP if unrecognized.
     *
     * @param input the value input to read from
     * @return the placed face direction
     */
    private Direction loadFace(ValueInput input) {
        String faceName = input.getStringOr(TAG_PLACED_FACE, DEFAULT_FACE);
        Direction dir = Direction.byName(faceName);
        return dir != null ? dir : Direction.UP;
    }

    /** Writes chain state to persistent storage.
     *
     * @param output the value output to write to
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.putString(TAG_GOO_TYPE, gooType.getId());
        output.putInt(TAG_STACK_COUNT, stackCount);
        output.putInt(TAG_MAX_STACKS, maxStacks);
        output.putInt(TAG_FUSE_REMAINING, fuseRemaining);
        output.putString(TAG_PLACED_FACE, placedFace.getName());
        output.putInt(TAG_MINING_STEP, miningStep);
        output.putInt(TAG_MINING_DEPTH, miningDepth);
        output.putString(TAG_PHASE, phase.name());
        output.putInt(TAG_IMPLODE_REMAINING, implodeTicksRemaining);
        output.putInt(TAG_IMPLODE_INITIAL, initialImplodeTicks);
        output.putInt(TAG_IMPLODE_RADIUS, implodeRadius);
        if (!accumulator.isEmpty()) {
            output.store(TAG_ACCUMULATOR, GooContents.CODEC, accumulator);
        }
    }

    // ── Client sync ───────────────────────────────────────────────────────

    /** Returns the sync packet sent when block entity data changes.
     *
     * @return the update packet
     */
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Returns the full NBT for initial chunk sync to clients.
     *
     * @param registries the registry provider
     * @return the update tag
     */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveCustomOnly(registries);
    }

    /** Sends a block update to tracking clients so the BER can render. */
    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(),
                BlockEntitySync.BLOCK_UPDATE_FLAGS);
        }
    }
}
