package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.effect.EffectMath;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Ticking block entity for the frost melt-resist field. Counts down a
 * duration; on expiry converts packed ice back to regular ice within
 * its radius and removes itself. Stackable up to 4 by additional
 * frost blobs, which recompute radius and reset duration.
 */
public class FrostFieldBlockEntity extends BlockEntity {

    private static final String TAG_RADIUS = "Radius";
    private static final String TAG_STACKS = "Stacks";
    private static final String TAG_DURATION = "Duration";

    private static final int MAX_STACKS = 4;

    private int radius = 3;
    private int stacks = 1;
    private int durationRemaining = 0;

    public FrostFieldBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.FROST_FIELD.get(), pos, state);
    }

    // -- Initialization ---------------------------------------------------

    /**
     * Configures the field after initial placement. Computes radius from
     * stack count and duration from radius.
     *
     * @param initialStacks starting stack count (normally 1)
     */
    public void initField(int initialStacks) {
        this.stacks = initialStacks;
        this.radius = EffectMath.computeFreezeRadius(stacks);
        this.durationRemaining = EffectMath.computeFrostDuration(radius);
        setChanged();
        syncToClient();
    }

    // -- Stacking ---------------------------------------------------------

    /**
     * Attempts to add a stack. Returns true if successful. Recomputes
     * radius and resets duration on success.
     */
    public boolean tryStack() {
        if (!EffectMath.canStack(stacks, MAX_STACKS)) return false;
        stacks++;
        radius = EffectMath.computeFreezeRadius(stacks);
        durationRemaining = EffectMath.computeFrostDuration(radius);
        setChanged();
        syncToClient();
        return true;
    }

    // -- Tick -------------------------------------------------------------

    /** How often to sync duration to client (every N ticks). */
    private static final int SYNC_INTERVAL = 20;

    /** Ticks before expiry where we sync every tick for smooth fade. */
    private static final int FADE_SYNC_THRESHOLD = 40;

    /** Server tick: count down duration, thaw on expiry. */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  FrostFieldBlockEntity be) {
        be.durationRemaining--;
        if (be.durationRemaining <= 0) {
            be.thaw((ServerLevel) level, pos);
            return;
        }
        boolean fadeZone = be.durationRemaining <= FADE_SYNC_THRESHOLD;
        if (fadeZone || be.durationRemaining % SYNC_INTERVAL == 0) {
            be.syncToClient();
        }
    }

    /**
     * Converts packed ice back to regular ice within the field radius,
     * then removes the field block.
     */
    private void thaw(ServerLevel level, BlockPos center) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    BlockPos target = center.offset(dx, dy, dz);
                    if (level.getBlockState(target).is(Blocks.PACKED_ICE)) {
                        level.setBlock(target, Blocks.ICE.defaultBlockState(),
                                Block.UPDATE_ALL);
                    }
                }
            }
        }
        level.removeBlock(center, false);
    }

    // -- Accessors --------------------------------------------------------

    public int getRadius() {
        return radius;
    }

    public int getStacks() {
        return stacks;
    }

    public int getDurationRemaining() {
        return durationRemaining;
    }

    // -- Persistence ------------------------------------------------------

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        radius = input.getIntOr(TAG_RADIUS, 3);
        stacks = input.getIntOr(TAG_STACKS, 1);
        durationRemaining = input.getIntOr(TAG_DURATION, 0);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(TAG_RADIUS, radius);
        output.putInt(TAG_STACKS, stacks);
        output.putInt(TAG_DURATION, durationRemaining);
    }

    // -- Client sync ------------------------------------------------------

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveCustomOnly(registries);
    }

    /** Sends a block update to tracking clients for animateTick. */
    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }
}
