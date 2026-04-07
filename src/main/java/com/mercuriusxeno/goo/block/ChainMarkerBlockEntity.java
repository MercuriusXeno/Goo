package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
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
    /** Default goo type id when loading from NBT. */
    private static final String DEFAULT_GOO_TYPE = "rock";
    /** Default face name when loading from NBT. */
    private static final String DEFAULT_FACE = "up";

    private GooType gooType = GooType.ROCK;
    private int stackCount = 1;
    private int maxStacks = 1;
    private int fuseRemaining;
    private Direction placedFace = Direction.UP;

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

    /** Server tick: count down fuse, fire executor on expiry.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ChainMarkerBlockEntity be) {
        be.fuseRemaining--;
        if (!EffectMath.isFuseLive(be.fuseRemaining)) {
            be.detonate((ServerLevel) level, pos);
            return;
        }
        be.syncIfNeeded();
    }

    /** Sends a client sync packet when entering implosion zone or at regular intervals. */
    private void syncIfNeeded() {
        boolean implosionZone = fuseRemaining <= IMPLOSION_SYNC_THRESHOLD;
        if (implosionZone || fuseRemaining % SYNC_INTERVAL == 0) {
            syncToClient();
        }
    }

    /** Fires the chain executor and removes the block.
     *
     * @param level the current level
     * @param pos   the block position
     */
    private void detonate(ServerLevel level, BlockPos pos) {
        ChainProfile profile = ChainProfile.forType(gooType);
        if (profile != null) {
            int range = profile.rangeFormula().applyAsInt(stackCount);
            profile.executor().execute(level, pos, range, stackCount, placedFace);
        }
        level.removeBlock(pos, false);
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
