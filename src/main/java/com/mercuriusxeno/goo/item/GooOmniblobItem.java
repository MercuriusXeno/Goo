package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Omniblob: a single-type, uncapped-capacity goo container for volumes that
 * do not fit in a regular blob stack (sub-blob remainders or amounts exceeding
 * 64,000 mB). One registration per goo type (15 total).
 *
 * <p>Inventory cursor interactions allow inserting and extracting blobs
 * via click mechanics.</p>
 */
public class GooOmniblobItem extends Item implements IGooItemInteraction {

    /** Separator between type name and tier in display name. */
    private static final String NAME_SEPARATOR = " ";
    /** Divisor for splitting omniblob volume in half. */
    private static final long HALF_DIVISOR = 2;
    /** Same-type neighbors within this radius gravitate toward each other. */
    private static final double GRAVITATE_RADIUS = 3.0;
    /** Near-collision radius: once the anchor is this close to a neighbor, it absorbs. */
    private static final double MERGE_RADIUS = 0.3;
    /** {@link #MERGE_RADIUS} squared, for distanceToSqr comparisons. */
    private static final double MERGE_RADIUS_SQ = MERGE_RADIUS * MERGE_RADIUS;
    /** Peak per-tick velocity in the pull direction. Also the max target when far from the stop point. */
    private static final double PULL_SPEED_CAP = 0.15;
    /** Target-velocity slope vs distance-to-stop-point. Target = min(distCent * APPROACH_SLOPE, CAP). */
    private static final double APPROACH_SLOPE = 0.4;
    /** Per-tick velocity increment when current speed is below target. */
    private static final double ACCELERATION = 0.05;
    /** Per-tick velocity decrement when current speed exceeds target (braking on approach). */
    private static final double DECELERATION = 0.05;
    /** Distance-squared floor below which the gravitation direction is ill-defined (avoid divide-by-zero). */
    private static final double MIN_GRAV_DIST_SQ = 1e-6;
    /** Below this |speedDelta| we skip the setDeltaMovement/needsSync churn. */
    private static final double NEGLIGIBLE_SPEED_DELTA = 1e-6;

    private final GooType gooType;

    /**
     * Cached state for one tick's gravitation pass. Computed once from the
     * neighbor snapshot, then consumed by the velocity-ramp step.
     *
     * @param dirX      normalized pull direction X
     * @param dirY      normalized pull direction Y
     * @param dirZ      normalized pull direction Z
     * @param asymmetry |sum of unit vectors to neighbors| / count, in [0, 1].
     *                  1 means all neighbors are on one side (edge of cluster);
     *                  0 means they cancel (center of cluster, no net force).
     *                  Preserves cluster contraction: edge items pull harder
     *                  than center items even though the absolute cap is shared.
     * @param distCent  distance from self to the centroid of all cluster
     *                  members (including self). The "stop point" — target
     *                  velocity shrinks as this approaches zero.
     */
    private record PullState(double dirX, double dirY, double dirZ, double asymmetry, double distCent) {}

    /**
     * Raw accumulator for the neighbor-scan loop: sum of unit vectors toward
     * each neighbor (for direction + asymmetry factor) and sum of relative
     * positions (for centroid distance), plus the count of contributing
     * neighbors. Extracted so {@link #computePullState} stays under the
     * method-length threshold.
     *
     * @param sumUX sum of unit-vector X components
     * @param sumUY sum of unit-vector Y components
     * @param sumUZ sum of unit-vector Z components
     * @param centX sum of relative X positions
     * @param centY sum of relative Y positions
     * @param centZ sum of relative Z positions
     * @param count number of neighbors that contributed (outside the MIN_GRAV_DIST_SQ floor)
     */
    private record NeighborSums(double sumUX, double sumUY, double sumUZ,
            double centX, double centY, double centZ, int count) {}

    /**
     * Creates a new omniblob item for the given goo type.
     *
     * @param gooType the goo type this omniblob carries
     * @param properties item properties (should include stacksTo(1))
     */
    public GooOmniblobItem(GooType gooType, Properties properties) {
        super(properties);
        this.gooType = gooType;
    }

    /**
     * Returns the goo type this omniblob carries.
     *
     * @return the goo type
     */
    public GooType getGooType() {
        return gooType;
    }

    /**
     * Returns the volume stored in the given omniblob stack, in microblobs.
     *
     * @param stack the omniblob item stack
     * @return volume in microblobs, or 0 if unset
     */
    public static long getVolume(ItemStack stack) {
        Long vol = stack.get(GooDataComponents.BLOB_VOLUME.get());
        return vol != null ? vol : 0L;
    }

    /**
     * Sets the volume on the given omniblob stack.
     *
     * @param stack  the omniblob item stack
     * @param volume volume in microblobs
     */
    public static void setVolume(ItemStack stack, long volume) {
        stack.set(GooDataComponents.BLOB_VOLUME.get(), volume);
    }

    /**
     * Creates an omniblob ItemStack with the given goo type and volume.
     *
     * @param type   the goo type
     * @param volume volume in microblobs
     * @return a new omniblob item stack
     */
    public static ItemStack createWithVolume(GooType type, long volume) {
        ItemStack stack = new ItemStack(GooItems.OMNIBLOBS.get(type).get());
        setVolume(stack, volume);
        return stack;
    }

    /**
     * Returns the display name as "[Type] [Tier]" based on stored volume.
     *
     * @param stack the item stack
     * @return the display name component
     */
    @Override
    public @NonNull Component getName(@NonNull ItemStack stack) {
        long volume = getVolume(stack);
        String tierName = BlobTiers.computeTierName(volume);
        String typeName = gooType.getId().substring(0, 1).toUpperCase(Locale.ROOT)
            + gooType.getId().substring(1);
        return Component.literal(typeName + NAME_SEPARATOR + tierName);
    }

    // -- Ground auto-merge --

    /**
     * Per-tick hook patched into the head of {@link ItemEntity#tick()} by NeoForge.
     * Runs gravitation/absorb dispatch as a side-effect on the server every tick,
     * then returns false so vanilla tick (gravity, despawn, pickup, pickupDelay)
     * continues normally.
     *
     * @param stack the item stack on the entity
     * @param self  the item entity being ticked
     * @return always false - we never replace vanilla tick
     */
    @Override
    public boolean onEntityItemUpdate(@NonNull ItemStack stack, @NonNull ItemEntity self) {
        if (self.level().isClientSide()) { return false; }
        if (self.isRemoved())            { return false; }
        driveMerge(self, stack);
        return false;
    }

    /**
     * Applies symmetric gravitation every tick, then runs absorb if self is
     * the cluster anchor (lowest-ID same-type member in range) AND a neighbor
     * has drifted within {@link #MERGE_RADIUS}. Gravitation is mutual so
     * items converge on their midpoint rather than one chasing the other,
     * doubling the closing rate vs. the asymmetric version - fast enough that
     * the near-collision gate reliably fires while still leaving a visible
     * drift window before the merge happens.
     *
     * @param self      the item entity being ticked
     * @param selfStack the absorber's item stack (mutated if an absorb fires)
     */
    private void driveMerge(ItemEntity self, ItemStack selfStack) {
        List<ItemEntity> nearby = findNearbyOmniblobs(self);
        if (nearby.isEmpty()) { return; }
        gravitateTowardCentroid(self, nearby);
        if (OmniblobAbsorb.findAttractorId(self.getId(), toCandidates(nearby)) != OmniblobAbsorb.NO_ATTRACTOR) { return; }
        tryAbsorbAsAnchor(self, selfStack, nearby);
    }

    /**
     * Runs the near-collision absorb as the cluster anchor. Invoked only
     * when self has no lower-ID same-type neighbor in range. Filters
     * {@code nearby} down to the subset within {@link #MERGE_RADIUS} before
     * delegating to the pure absorb computation.
     *
     * @param self      the anchor item entity
     * @param selfStack the absorber's item stack (mutated if an absorb fires)
     * @param nearby    full gravitation-range neighbor snapshot
     */
    private void tryAbsorbAsAnchor(ItemEntity self, ItemStack selfStack, List<ItemEntity> nearby) {
        List<ItemEntity> touching = filterByMergeRange(self, nearby);
        if (touching.isEmpty()) { return; }
        OmniblobAbsorb.Result result = OmniblobAbsorb.compute(
            self.getId(), getVolume(selfStack), self.getAge(), toCandidates(touching));
        if (result.discardIds().isEmpty()) { return; }
        applyAbsorb(self, selfStack, touching, result);
    }

    /**
     * Collects alive, same-type omniblob item entities within the gravitation
     * radius of {@code self}, excluding {@code self} itself.
     *
     * @param self the querying item entity
     * @return list of candidate neighbors (may be empty)
     */
    private List<ItemEntity> findNearbyOmniblobs(ItemEntity self) {
        AABB box = self.getBoundingBox()
            .inflate(GRAVITATE_RADIUS, GRAVITATE_RADIUS, GRAVITATE_RADIUS);
        return self.level().getEntitiesOfClass(
            ItemEntity.class, box,
            other -> other != self
                  && other.isAlive()
                  && isMatchingOmniblob(other.getItem()));
    }

    /**
     * Accelerates {@code self} toward a distance-capped target velocity in
     * the cluster-aware pull direction. Two-phase:
     * <ol>
     *   <li>{@link #computePullState} builds direction + asymmetry factor +
     *       distance to stop point from one loop over the neighbor snapshot.</li>
     *   <li>{@link #applyPullVelocity} ramps current velocity toward a triple-
     *       capped target ({@code asymmetry * CAP}, {@code distCent * SLOPE},
     *       {@code CAP}) using {@link #ACCELERATION} / {@link #DECELERATION}.</li>
     * </ol>
     * The distance cap is what fixes the overshoot: as items approach the
     * centroid, the target velocity shrinks to zero, so they naturally brake
     * instead of blowing past each other.
     *
     * @param self   the entity being pulled
     * @param nearby same-type neighbor snapshot from the gravitation query
     */
    private static void gravitateTowardCentroid(ItemEntity self, List<ItemEntity> nearby) {
        PullState state = computePullState(self, nearby);
        if (state == null) { return; }
        applyPullVelocity(self, state);
    }

    /**
     * Builds the per-tick pull state from the neighbor snapshot. Single pass
     * that computes both the sum of unit vectors (direction + asymmetry
     * factor) and the sum of relative positions (centroid distance).
     *
     * @param self   the querying item entity
     * @param nearby same-type neighbor snapshot
     * @return the pull state, or null if there is no valid pull direction
     *         (all neighbors are effectively at the same spot, or their unit
     *         vectors sum to near-zero)
     */
    private static @Nullable PullState computePullState(ItemEntity self, List<ItemEntity> nearby) {
        NeighborSums sums = accumulateNeighborSums(self, nearby);
        double sumUMagSq = sums.sumUX() * sums.sumUX() + sums.sumUY() * sums.sumUY() + sums.sumUZ() * sums.sumUZ();
        if (sumUMagSq < MIN_GRAV_DIST_SQ) { return null; }
        double sumUMag = Math.sqrt(sumUMagSq);
        double invSumU = 1.0 / sumUMag;
        int totalWithSelf = sums.count() + 1;
        double distCent = Math.sqrt(
            sums.centX() * sums.centX() + sums.centY() * sums.centY() + sums.centZ() * sums.centZ())
            / totalWithSelf;
        return new PullState(
            sums.sumUX() * invSumU, sums.sumUY() * invSumU, sums.sumUZ() * invSumU,
            sumUMag / sums.count(), distCent);
    }

    /**
     * Walks the neighbor snapshot once and accumulates unit-vector sums and
     * relative-position sums. Neighbors effectively at the same point as
     * {@code self} are skipped (the {@link #MIN_GRAV_DIST_SQ} floor).
     *
     * @param self   the querying item entity
     * @param nearby same-type neighbor snapshot
     * @return the raw sums for {@link #computePullState}
     */
    private static NeighborSums accumulateNeighborSums(ItemEntity self, List<ItemEntity> nearby) {
        double sumUX = 0;
        double sumUY = 0;
        double sumUZ = 0;
        double centX = 0;
        double centY = 0;
        double centZ = 0;
        int count = 0;
        for (ItemEntity n : nearby) {
            double dx = n.getX() - self.getX();
            double dy = n.getY() - self.getY();
            double dz = n.getZ() - self.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < MIN_GRAV_DIST_SQ) { continue; }
            double invDist = 1.0 / Math.sqrt(distSq);
            sumUX += dx * invDist;
            sumUY += dy * invDist;
            sumUZ += dz * invDist;
            centX += dx;
            centY += dy;
            centZ += dz;
            count++;
        }
        return new NeighborSums(sumUX, sumUY, sumUZ, centX, centY, centZ, count);
    }

    /**
     * Ramps {@code self}'s velocity in the pull direction toward a triple-
     * capped target. The asymmetry cap preserves cluster contraction (center
     * items move slower than edge items); the distance cap preserves the
     * braking-on-approach behavior (items decelerate as they near the stop
     * point); the absolute cap is the hard ceiling.
     *
     * @param self  the entity being pulled
     * @param state the precomputed pull state for this tick
     */
    private static void applyPullVelocity(ItemEntity self, PullState state) {
        double fromAsymmetry = state.asymmetry() * PULL_SPEED_CAP;
        double fromDistance = state.distCent() * APPROACH_SLOPE;
        double targetSpeed = Math.min(Math.min(fromAsymmetry, fromDistance), PULL_SPEED_CAP);
        Vec3 delta = self.getDeltaMovement();
        double currentSpeed = delta.x * state.dirX() + delta.y * state.dirY() + delta.z * state.dirZ();
        double newSpeed = rampSpeed(currentSpeed, targetSpeed);
        double speedDelta = newSpeed - currentSpeed;
        if (Math.abs(speedDelta) < NEGLIGIBLE_SPEED_DELTA) { return; }
        self.setDeltaMovement(delta.add(
            state.dirX() * speedDelta, state.dirY() * speedDelta, state.dirZ() * speedDelta));
        // Per-tick pull is below ItemEntity's 0.01 delta-change sync threshold,
        // so vanilla falls back to the default tracker cadence and the client
        // sees ~1s position snaps. Force an immediate sync every gravitation tick.
        self.needsSync = true;
    }

    /**
     * Ramps {@code current} toward {@code target}: accelerates by
     * {@link #ACCELERATION} when below, decelerates by {@link #DECELERATION}
     * when above, never overshoots.
     *
     * @param current current velocity in the pull direction
     * @param target  target velocity this tick
     * @return the clamped new velocity
     */
    private static double rampSpeed(double current, double target) {
        if (current < target) { return Math.min(current + ACCELERATION, target); }
        return Math.max(current - DECELERATION, target);
    }

    /**
     * Returns the subset of {@code nearby} within {@link #MERGE_RADIUS} of
     * {@code self}. Only invoked on the cluster anchor, so this is the set
     * of higher-ID neighbors that have drifted into near-collision range.
     *
     * @param self   the anchor item entity
     * @param nearby the gravitation-range snapshot
     * @return neighbors within squared merge range
     */
    private static List<ItemEntity> filterByMergeRange(ItemEntity self, List<ItemEntity> nearby) {
        List<ItemEntity> out = new ArrayList<>();
        for (ItemEntity n : nearby) {
            if (self.distanceToSqr(n) <= MERGE_RADIUS_SQ) { out.add(n); }
        }
        return out;
    }

    /**
     * Projects item entities to pure-data absorb candidates for
     * {@link OmniblobAbsorb#compute} and {@link OmniblobAbsorb#findAttractorId}.
     *
     * @param entities nearby same-type omniblob entities
     * @return candidates in the same order
     */
    private static List<OmniblobAbsorb.Candidate> toCandidates(List<ItemEntity> entities) {
        List<OmniblobAbsorb.Candidate> out = new ArrayList<>(entities.size());
        for (ItemEntity n : entities) {
            out.add(new OmniblobAbsorb.Candidate(n.getId(), getVolume(n.getItem()), n.getAge()));
        }
        return out;
    }

    /**
     * Applies an absorb result: discards each absorbed neighbor, writes the
     * combined volume back to the absorber's stack, and resets the absorber's
     * age to the min across the cluster.
     *
     * @param self      the absorbing item entity
     * @param selfStack the absorber's item stack (mutated in place)
     * @param nearby    the full neighbor list the result was computed from
     * @param result    the combined volume, new age, and ids to discard
     */
    private static void applyAbsorb(ItemEntity self, ItemStack selfStack,
            List<ItemEntity> nearby, OmniblobAbsorb.Result result) {
        for (ItemEntity n : nearby) {
            if (result.discardIds().contains(n.getId())) { n.discard(); }
        }
        setVolume(selfStack, result.volume());
        self.setItem(selfStack);
        self.age = result.age();
    }

    // -- Cursor interactions --

    /**
     * Omniblob in cursor, clicking onto a slot target.
     * Right-click on empty slot: place ONE blob (1,000 mB).
     * Left-click on same-type blob: absorb entire blob stack into omniblob.
     * Right-click on same-type blob: absorb 1 blob into omniblob.
     *
     * @param omniblob the omniblob on the cursor
     * @param slot     the target inventory slot
     * @param action   the click action
     * @param player   the interacting player
     * @return true if the interaction was handled
     */
    @Override
    public boolean overrideStackedOnOther(@NonNull ItemStack omniblob, @NonNull Slot slot,
            @NonNull ClickAction action, @NonNull Player player) {
        ItemStack target = slot.getItem();
        if (isMatchingBlob(target)) {
            return handleAbsorbFromSlot(omniblob, target, slot, action, player);
        }
        return action == ClickAction.SECONDARY
                && target.isEmpty()
                && placeSingleBlobInSlot(omniblob, slot, player);
    }

    /**
     * Dispatches left/right-click when omniblob cursor meets a same-type blob stack.
     *
     * @param omniblob the omniblob on the cursor
     * @param target   the blob stack in the slot
     * @param slot     the target inventory slot
     * @param action   the click action
     * @param player   the interacting player
     * @return true if the interaction was handled
     */
    private boolean handleAbsorbFromSlot(ItemStack omniblob, ItemStack target, Slot slot,
            ClickAction action, Player player) {
        if (action == ClickAction.PRIMARY) {
            long total = getVolume(omniblob) + target.getCount() * BlobStacks.MB_PER_BLOB;
            slot.set(BlobStacks.createForOutput(gooType, total));
            player.containerMenu.setCarried(ItemStack.EMPTY);
            return true;
        }
        return feedOneBlobToStack(omniblob, target, player);
    }

    /**
     * Places one blob from the omniblob into an empty slot, updating cursor remainder.
     *
     * @param omniblob the omniblob on the cursor
     * @param slot     the empty target slot
     * @param player   the interacting player
     * @return true if a blob was placed, false if insufficient volume
     */
    private boolean placeSingleBlobInSlot(ItemStack omniblob, Slot slot, Player player) {
        long volume = getVolume(omniblob);
        if (volume < BlobStacks.MB_PER_BLOB) { return false; }

        long remaining = volume - BlobStacks.MB_PER_BLOB;
        slot.set(BlobStacks.createBlobStack(gooType, 1));
        applyCursorRemainder(omniblob, remaining, player);
        return true;
    }

    /**
     * Updates the cursor after removing volume: shrink, downgrade to blob stack, or keep as omniblob.
     *
     * @param omniblob  the omniblob on the cursor
     * @param remaining volume remaining after extraction
     * @param player    the interacting player
     */
    private void applyCursorRemainder(ItemStack omniblob, long remaining, Player player) {
        if (remaining <= 0) {
            omniblob.shrink(1);
        } else if (BlobStacks.isCleanBlobStack(remaining)) {
            player.containerMenu.setCarried(BlobStacks.createBlobStack(gooType, (int) (remaining / BlobStacks.MB_PER_BLOB)));
        } else {
            setVolume(omniblob, remaining);
        }
    }

    /**
     * Right-click: grow blob stack by 1 and reduce omniblob accordingly.
     *
     * @param omniblob the omniblob on the cursor
     * @param target   the blob stack in the slot
     * @param player   the interacting player
     * @return true if a blob was fed, false if insufficient volume
     */
    private boolean feedOneBlobToStack(ItemStack omniblob, ItemStack target, Player player) {
        long volume = getVolume(omniblob);
        if (volume < BlobStacks.MB_PER_BLOB) { return false; }

        target.grow(1);
        long remaining = volume - BlobStacks.MB_PER_BLOB;
        applyCursorRemainder(omniblob, remaining, player);
        return true;
    }

    /**
     * Something clicking onto omniblob in a slot.
     * Left-click + blob stack: absorb entire stack.
     * Right-click + blob stack: absorb 1 (shift: all).
     * Left/right-click + same-type omniblob: combine into slot omniblob.
     * Right-click + empty cursor: split volume in half.
     *
     * @param omniblob    the omniblob in the slot
     * @param cursor      the item stack on the cursor
     * @param slot        the inventory slot
     * @param action      the click action
     * @param player      the interacting player
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack omniblob, @NonNull ItemStack cursor,
            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
            @NonNull SlotAccess cursorAccess) {
        if (cursor.isEmpty() && action == ClickAction.SECONDARY) {
            return handleEmptyCursorExtract(omniblob, slot, cursorAccess);
        }
        if (isMatchingOmniblob(cursor)) { return handleOmniblobCombine(omniblob, cursor, cursorAccess); }
        return isMatchingBlob(cursor) && handleBlobAbsorb(omniblob, cursor, action, cursorAccess, player);
    }

    /**
     * Tests whether the stack is a same-type omniblob.
     *
     * @param stack the item stack to test
     * @return true if the stack is an omniblob of this goo type
     */
    private boolean isMatchingOmniblob(ItemStack stack) {
        return stack.getItem() instanceof GooOmniblobItem omni && omni.getGooType() == gooType;
    }

    /**
     * Tests whether the stack is a same-type blob.
     *
     * @param stack the item stack to test
     * @return true if the stack is a blob of this goo type
     */
    private boolean isMatchingBlob(ItemStack stack) {
        return stack.getItem() instanceof GooBlobItem blob && blob.getGooType() == gooType;
    }

    /**
     * Splits the omniblob in half. One half goes to the cursor, the other stays
     * in the slot. Each half follows the output rule (blob stack if clean, omniblob otherwise).
     * Sub-blob remainder case (volume < 1000) gives the whole omniblob to the cursor.
     *
     * @param omniblob    the omniblob in the slot
     * @param slot        the inventory slot
     * @param cursorAccess access to set the cursor contents
     * @return true if the extraction was performed
     */
    private boolean handleEmptyCursorExtract(ItemStack omniblob, Slot slot, SlotAccess cursorAccess) {
        long volume = getVolume(omniblob);
        if (volume <= 0) { return false; }
        if (BlobStacks.wholeBlobs(volume) <= 0) {
            cursorAccess.set(omniblob.copy());
            omniblob.shrink(1);
            return true;
        }
        return splitVolumeInHalf(omniblob, volume, slot, cursorAccess);
    }

    /**
     * Splits omniblob volume in half: one half to cursor, the other stays in slot.
     *
     * @param omniblob     the omniblob in the slot
     * @param volume       the current volume to split
     * @param slot         the inventory slot
     * @param cursorAccess access to set the cursor contents
     * @return true always (split performed)
     */
    private boolean splitVolumeInHalf(ItemStack omniblob, long volume, Slot slot, SlotAccess cursorAccess) {
        long half = volume / HALF_DIVISOR;
        long other = volume - half;

        cursorAccess.set(BlobStacks.createForOutput(gooType, half));
        applySlotRemainder(omniblob, other, slot);
        return true;
    }

    /**
     * Updates the slot after splitting: remove, downgrade to blob stack, or keep as omniblob.
     *
     * @param omniblob  the omniblob in the slot
     * @param remaining volume remaining after split
     * @param slot      the inventory slot
     */
    private void applySlotRemainder(ItemStack omniblob, long remaining, Slot slot) {
        if (remaining <= 0) {
            omniblob.shrink(1);
        } else if (BlobStacks.isCleanBlobStack(remaining)) {
            slot.set(BlobStacks.createBlobStack(gooType, (int) (remaining / BlobStacks.MB_PER_BLOB)));
        } else {
            setVolume(omniblob, remaining);
        }
    }

    /**
     * Combines a cursor omniblob of the same type into the slot omniblob.
     * The cursor omniblob's volume is added to the slot omniblob, and the cursor is cleared.
     *
     * @param slotOmniblob   the omniblob in the slot
     * @param cursorOmniblob the omniblob on the cursor
     * @param cursorAccess   access to set the cursor contents
     * @return true always (combination performed)
     */
    private boolean handleOmniblobCombine(ItemStack slotOmniblob, ItemStack cursorOmniblob,
            SlotAccess cursorAccess) {
        long cursorVol = getVolume(cursorOmniblob);
        long slotVol = getVolume(slotOmniblob);
        setVolume(slotOmniblob, slotVol + cursorVol);
        cursorAccess.set(ItemStack.EMPTY);
        return true;
    }

    /**
     * Absorbs blob stack into the omniblob.
     *
     * @param omniblob    the omniblob in the slot
     * @param cursor      the blob stack on the cursor
     * @param action      the click action
     * @param cursorAccess access to set the cursor contents
     * @param player      the interacting player
     * @return true always (absorption performed)
     */
    private boolean handleBlobAbsorb(ItemStack omniblob, ItemStack cursor,
            ClickAction action, SlotAccess cursorAccess, Player player) {
        int count = action == ClickAction.PRIMARY ? cursor.getCount() : player.isShiftKeyDown() ? cursor.getCount() : 1;
        setVolume(omniblob, getVolume(omniblob) + count * BlobStacks.MB_PER_BLOB);
        cursor.shrink(count);
        if (cursor.isEmpty()) {
            cursorAccess.set(ItemStack.EMPTY);
        }
        return true;
    }

    /**
     * Returns BLOB_INSERT so canister blocks route to blob pour logic.
     *
     * @return the blob insert interaction type
     */
    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.BLOB_INSERT;
    }
}
