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
import org.jspecify.annotations.NonNull;
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
    /** Ground-absorb scan interval in ticks (20t = 1s). */
    private static final int ABSORB_SCAN_INTERVAL = 20;
    /** Horizontal inflation of the absorb search box. Wider than vanilla merge (0.5) to catch bouncing blobs. */
    private static final double ABSORB_INFLATE_XZ = 1.0;
    /** Vertical inflation of the absorb search box. Tighter than XZ to avoid jumping across vertical gaps. */
    private static final double ABSORB_INFLATE_Y = 0.5;

    private final GooType gooType;

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
     * Runs the absorb scan as a side-effect on the server every
     * {@link #ABSORB_SCAN_INTERVAL} ticks, then returns false so vanilla tick
     * (gravity, despawn, pickup, pickupDelay) continues normally.
     *
     * @param stack the item stack on the entity
     * @param self  the item entity being ticked
     * @return always false - we never replace vanilla tick
     */
    @Override
    public boolean onEntityItemUpdate(@NonNull ItemStack stack, @NonNull ItemEntity self) {
        if (self.level().isClientSide())                { return false; }
        if (self.isRemoved())                           { return false; }
        if (self.tickCount % ABSORB_SCAN_INTERVAL != 0) { return false; }
        absorbNeighbors(self, stack);
        return false;
    }

    /**
     * Drives a single absorb pass: find same-type neighbors, run the pure
     * merge computation, apply mutations if anything was absorbed.
     *
     * @param self      the absorbing item entity
     * @param selfStack the absorber's item stack (mutated with the combined volume)
     */
    private void absorbNeighbors(ItemEntity self, ItemStack selfStack) {
        List<ItemEntity> nearby = findNearbyOmniblobs(self);
        if (nearby.isEmpty()) { return; }
        OmniblobAbsorb.Result result = OmniblobAbsorb.compute(
            self.getId(), getVolume(selfStack), self.getAge(), toCandidates(nearby));
        if (result.discardIds().isEmpty()) { return; }
        applyAbsorb(self, selfStack, nearby, result);
    }

    /**
     * Collects alive, same-type omniblob item entities in an inflated AABB
     * around {@code self}, excluding {@code self} itself.
     *
     * @param self the absorbing item entity
     * @return list of candidate neighbors (may be empty)
     */
    private List<ItemEntity> findNearbyOmniblobs(ItemEntity self) {
        AABB box = self.getBoundingBox()
            .inflate(ABSORB_INFLATE_XZ, ABSORB_INFLATE_Y, ABSORB_INFLATE_XZ);
        return self.level().getEntitiesOfClass(
            ItemEntity.class, box,
            other -> other != self
                  && other.isAlive()
                  && isMatchingOmniblob(other.getItem()));
    }

    /**
     * Projects item entities to pure-data absorb candidates for
     * {@link OmniblobAbsorb#compute}.
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
