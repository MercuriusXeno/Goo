package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.effect.ChainProfiles;
import com.mercuriusxeno.goo.effect.ChainProfiles.ChainProfile;
import com.mercuriusxeno.goo.effect.EffectMath;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
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

    private GooType gooType = GooType.ROCK;
    private int stackCount = 1;
    private int maxStacks = 1;
    private int fuseRemaining = 0;
    private Direction placedFace = Direction.UP;

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
     */
    public boolean tryStack() {
        if (!EffectMath.canStack(stackCount, maxStacks)) return false;
        ChainProfile profile = ChainProfile.forType(gooType);
        stackCount++;
        fuseRemaining = profile.fuseTicks();
        setChanged();
        syncToClient();
        return true;
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    /** How often to sync fuse to client (every N ticks). */
    private static final int SYNC_INTERVAL = 5;

    /** Ticks before detonation where we sync every tick for smooth implosion. */
    private static final int IMPLOSION_SYNC_THRESHOLD = 8;

    /** Server tick: count down fuse, fire executor on expiry. */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ChainMarkerBlockEntity be) {
        be.fuseRemaining--;
        if (!EffectMath.isFuseLive(be.fuseRemaining)) {
            be.detonate((ServerLevel) level, pos);
            return;
        }
        // Sync to client for BER animation
        boolean implosionZone = be.fuseRemaining <= IMPLOSION_SYNC_THRESHOLD;
        if (implosionZone || be.fuseRemaining % SYNC_INTERVAL == 0) {
            be.syncToClient();
        }
    }

    /** Fires the chain executor and removes the block. */
    private void detonate(ServerLevel level, BlockPos pos) {
        ChainProfile profile = ChainProfile.forType(gooType);
        if (profile != null) {
            int range = profile.rangeFormula().applyAsInt(stackCount);
            profile.executor().execute(level, pos, range, stackCount, placedFace);
        }
        level.removeBlock(pos, false);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public GooType getGooType() {
        return gooType;
    }

    public int getStackCount() {
        return stackCount;
    }

    public int getMaxStacks() {
        return maxStacks;
    }

    public int getFuseRemaining() {
        return fuseRemaining;
    }

    public Direction getPlacedFace() {
        return placedFace;
    }

    // ── Persistence ───────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        GooType loaded = GooType.fromId(input.getStringOr(TAG_GOO_TYPE, "rock"));
        gooType = loaded != null ? loaded : GooType.ROCK;
        stackCount = input.getIntOr(TAG_STACK_COUNT, 1);
        maxStacks = input.getIntOr(TAG_MAX_STACKS, 1);
        fuseRemaining = input.getIntOr(TAG_FUSE_REMAINING, 0);
        String faceName = input.getStringOr(TAG_PLACED_FACE, "up");
        placedFace = Direction.byName(faceName) != null
                ? Direction.byName(faceName) : Direction.UP;
    }

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

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveCustomOnly(registries);
    }

    /** Sends a block update to tracking clients so the BER can render. */
    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }
}
