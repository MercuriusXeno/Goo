package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Per-goo-type behavior for a chain marker block entity after its fuse
 * expires. The chain marker BE owns the shared state (goo type, stack
 * count, fuse countdown, placed face) and delegates all type-specific
 * post-detonation work to an implementation of this interface.
 *
 * <p>Lifecycle on the server:
 * <ol>
 *   <li>The BE ticks its fuse down in {@code tickFuse}.</li>
 *   <li>When the fuse hits zero, the BE instantiates a fresh behavior
 *       from the goo type's registered factory and calls
 *       {@link #onFuseExpired}. Instant behaviors (blaze/frost) do all
 *       their work here and finish by leaving {@link #isActive()} false.</li>
 *   <li>While {@link #isActive()} is true, the BE calls
 *       {@link #serverTick} once per server tick for multi-tick effects
 *       (nether black-hole phase machine, rock progressive mining).</li>
 *   <li>The first tick {@link #isActive()} returns false, the BE removes
 *       itself from the world.</li>
 * </ol>
 *
 * <p>Persistence is owned by the behavior: the BE calls
 * {@link #saveAdditional} and {@link #loadAdditional} during its own
 * save/load. Each behavior reads and writes its own tag keys directly
 * on the shared value stream; the BE only handles tags for the shared
 * fields (goo type, stack count, fuse, face). On load, the BE re-creates
 * the behavior instance via the profile factory before delegating
 * {@link #loadAdditional} to it.
 */
public interface ChainBehavior {

    /**
     * Called the tick the fuse hits zero. The behavior should snapshot
     * anything it needs from the BE (stack count, placed face, etc.) and
     * start its effect. Instant behaviors finish in this call and leave
     * {@link #isActive()} false; multi-tick behaviors seed their internal
     * phase state and leave {@link #isActive()} true so {@link #serverTick}
     * is called on subsequent ticks.
     *
     * @param level the server level
     * @param pos   the chain marker position
     * @param be    the owning block entity (for reading shared fields)
     */
    void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be);

    /**
     * Called once per server tick while {@link #isActive()} is true.
     * Instant behaviors will never receive this call because they set
     * {@link #isActive()} to false during {@link #onFuseExpired}.
     *
     * @param level the server level
     * @param pos   the chain marker position
     * @param be    the owning block entity
     */
    void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be);

    /**
     * Returns true while the behavior still has work to do. The BE
     * removes itself the tick this first returns false.
     *
     * @return true if the behavior should keep ticking
     */
    boolean isActive();

    /**
     * Returns true if this behavior accepts additional blobs after
     * the fuse has expired. Metal and crystal support top-off to
     * replenish charges; tunnelers do not.
     *
     * @return true if post-fuse stacking is allowed
     */
    default boolean allowsTopOff() {
        return false;
    }

    /**
     * Called after a successful top-off stack increment. Behaviors
     * that maintain internal charge counts (crystal) use this to
     * sync charges from the updated stack count.
     *
     * @param be the owning block entity with the updated stack count
     */
    default void onTopOff(ChainMarkerBlockEntity be) {
    }

    /**
     * Returns the number of depth layers already mined. Used by the
     * ghost outline renderer to shrink the preview as the effect
     * progresses. Behaviors that don't mine progressively return 0.
     *
     * @return the count of completed (mined) layers
     */
    default int getMinedLayers() {
        return 0;
    }

    /**
     * Persists this behavior's state onto the BE's shared value stream.
     * Implementations should only write their own tag keys; the BE writes
     * the shared-field tags separately.
     *
     * @param output the value output to write to
     */
    void saveAdditional(ValueOutput output);

    /**
     * Restores this behavior's state from the BE's shared value stream.
     * Called by the BE after it re-creates the behavior instance via the
     * profile factory during {@code loadAdditional}.
     *
     * @param input the value input to read from
     */
    void loadAdditional(ValueInput input);
}
