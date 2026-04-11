package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.effect.ChainBehavior;
import com.mercuriusxeno.goo.effect.ChainProfiles.ChainProfile;
import com.mercuriusxeno.goo.effect.EffectMath;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
 * Ticking block entity for chain effects. Owns only the shared state:
 * goo type, stack count, fuse countdown, placed face. The type-specific
 * post-fuse behavior is delegated to a {@link ChainBehavior} instance
 * created from the goo type's profile at fuse expiry.
 *
 * <p>Rock progressive mining is currently still inline here under the
 * legacy {@code layerExecutor} path; it will be extracted into a
 * RockBehavior in a follow-up pass.
 */
public class ChainMarkerBlockEntity extends BlockEntity {

    private static final String TAG_GOO_TYPE = "GooType";
    private static final String TAG_STACK_COUNT = "StackCount";
    private static final String TAG_MAX_STACKS = "MaxStacks";
    private static final String TAG_FUSE_REMAINING = "FuseRemaining";
    private static final String TAG_PLACED_FACE = "PlacedFace";
    private static final String TAG_MINING_STEP = "MiningStep";
    private static final String TAG_MINING_DEPTH = "MiningDepth";
    /** Default goo type id when loading from NBT. */
    private static final String DEFAULT_GOO_TYPE = "rock";
    /** Default face name when loading from NBT. */
    private static final String DEFAULT_FACE = "up";
    /** Sentinel: no progressive mining in progress. */
    private static final int MINING_INACTIVE = -1;

    /** How often to sync fuse to client (every N ticks). */
    private static final int SYNC_INTERVAL = 5;
    /** Ticks before detonation where we sync every tick for smooth implosion. */
    private static final int IMPLOSION_SYNC_THRESHOLD = 8;

    private GooType gooType = GooType.ROCK;
    private int stackCount = 1;
    private int maxStacks = 1;
    private int fuseRemaining;
    private Direction placedFace = Direction.UP;
    /** Next layer index to mine while progressive mining is active; -1 otherwise. */
    private int miningStep = MINING_INACTIVE;
    /** Total number of layers to mine when progressive mining is active. */
    private int miningDepth;
    /** Active post-fuse behavior; null during FUSE phase. Set at fuse
     * expiry when the profile has a behavior factory, and nulled out
     * implicitly when the BE removes itself. */
    @Nullable
    private ChainBehavior behavior;

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
     * @return true if the stack count was incremented
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

    /** Server tick: either a post-fuse behavior is active (delegate) or
     * the fuse is still counting down (or rock progressive mining is
     * running under the legacy path).
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ChainMarkerBlockEntity be) {
        ServerLevel server = (ServerLevel) level;
        if (be.behavior != null) {
            be.behavior.serverTick(server, pos, be);
            if (!be.behavior.isActive()) {
                server.removeBlock(pos, false);
                return;
            }
            be.setChanged();
            be.syncToClient();
            return;
        }
        be.tickFuse(server, pos);
    }

    /** FUSE-phase tick: progressive mining passthrough (legacy rock path)
     * or fuse countdown + detonate on expiry.
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

    /** Sends a client sync packet when entering implosion zone or at regular intervals. */
    private void syncIfNeeded() {
        boolean implosionZone = fuseRemaining <= IMPLOSION_SYNC_THRESHOLD;
        if (implosionZone || fuseRemaining % SYNC_INTERVAL == 0) {
            syncToClient();
        }
    }

    /** Fires the chain effect for this goo type. Prefers the profile's
     * {@code behaviorFactory} (new-style {@link ChainBehavior}) if one is
     * registered; otherwise falls back to the legacy {@code executor} /
     * {@code layerExecutor} one-shot paths.
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
        if (profile.behaviorFactory() != null) {
            detonateBehavior(level, pos, profile);
            return;
        }
        detonateLegacy(level, pos, profile);
    }

    /** Behavior path: creates the behavior, invokes {@code onFuseExpired},
     * and removes the BE immediately if the behavior already finished
     * (instant one-shots via the behavior API).
     *
     * @param level   the current level
     * @param pos     the block position
     * @param profile the chain profile for the current goo type
     */
    private void detonateBehavior(ServerLevel level, BlockPos pos, ChainProfile profile) {
        behavior = profile.behaviorFactory().get();
        behavior.onFuseExpired(level, pos, this);
        if (!behavior.isActive()) {
            level.removeBlock(pos, false);
            return;
        }
        setChanged();
        syncToClient();
    }

    /** Legacy path: routes to the old {@code executor} or
     * {@code layerExecutor} depending on which the profile provides.
     *
     * @param level   the current level
     * @param pos     the block position
     * @param profile the chain profile for the current goo type
     */
    private void detonateLegacy(ServerLevel level, BlockPos pos, ChainProfile profile) {
        int range = profile.rangeFormula().applyAsInt(stackCount);
        if (profile.layerExecutor() != null) {
            beginProgressiveMining(range);
            return;
        }
        if (profile.executor() != null) {
            profile.executor().execute(level, pos, range, stackCount, placedFace);
        }
        level.removeBlock(pos, false);
    }

    /** Transitions this marker into progressive mining mode (legacy rock
     * path). The BE remains in place, ticking one layer per server tick
     * until {@code depth} layers have been processed.
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

    /** Returns the active post-fuse behavior, or null if still in FUSE.
     * The BER calls this to {@code instanceof}-check for type-specific
     * render paths (e.g. nether black-hole sphere).
     *
     * @return the active chain behavior, or null
     */
    @Nullable
    public ChainBehavior getBehavior() {
        return behavior;
    }

    // ── Persistence ───────────────────────────────────────────────────────

    /** Restores chain state from persistent storage.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        loadSharedFields(input);
        placedFace = loadFace(input);
        reconstituteBehaviorIfNeeded(input);
    }

    /** Restores goo type, stack count, max stacks, fuse, and mining state
     * from persistent data.
     *
     * @param input the value input to read from
     */
    private void loadSharedFields(ValueInput input) {
        GooType loaded = GooType.fromId(input.getStringOr(TAG_GOO_TYPE, DEFAULT_GOO_TYPE));
        gooType = loaded != null ? loaded : GooType.ROCK;
        stackCount = input.getIntOr(TAG_STACK_COUNT, 1);
        maxStacks = input.getIntOr(TAG_MAX_STACKS, 1);
        fuseRemaining = input.getIntOr(TAG_FUSE_REMAINING, 0);
        miningStep = input.getIntOr(TAG_MINING_STEP, MINING_INACTIVE);
        miningDepth = input.getIntOr(TAG_MINING_DEPTH, 0);
    }

    /** If the fuse has already expired, re-creates the behavior instance
     * via the profile factory and lets it reload its own state from the
     * same value stream. Called after the shared fields have been loaded.
     *
     * @param input the value input to read from
     */
    private void reconstituteBehaviorIfNeeded(ValueInput input) {
        if (fuseRemaining > 0) { return; }
        ChainProfile profile = ChainProfile.forType(gooType);
        if (profile == null || profile.behaviorFactory() == null) { return; }
        behavior = profile.behaviorFactory().get();
        behavior.loadAdditional(input);
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
        if (behavior != null) {
            behavior.saveAdditional(output);
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
