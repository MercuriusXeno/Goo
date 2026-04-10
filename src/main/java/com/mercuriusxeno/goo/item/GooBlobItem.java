package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import java.util.Locale;

/**
 * A stackable goo blob item. Each blob represents exactly 1,000 mB of goo.
 * Stacks to 64 (vanilla default). For volumes that don't fit in a blob stack,
 * see {@link GooOmniblobItem}.
 *
 * <p>Migration: old volumetric blobs with a BLOB_VOLUME component are converted
 * on inventory tick to the new stackable format.</p>
 */
public class GooBlobItem extends Item implements IGooItemInteraction {

    /** Volume of one blob in microblobs. */
    public static final long VOLUME_PER_BLOB = BlobStacks.MB_PER_BLOB;
    /** Suffix appended to the type name for display. */
    private static final String NAME_SUFFIX = " Blob";

    private final GooType gooType;

    /**
     * Creates a new blob item for the given goo type.
     *
     * @param gooType    the goo type this blob carries
     * @param properties item properties (default stack size 64)
     */
    public GooBlobItem(GooType gooType, Properties properties) {
        super(properties);
        this.gooType = gooType;
    }

    /**
     * Returns the goo type this blob carries.
     *
     * @return the goo type
     */
    public GooType getGooType() {
        return gooType;
    }

    /**
     * Returns the display name as "[Type] Blob".
     *
     * @param stack the item stack
     * @return the display name component
     */
    @Override
    public @NonNull Component getName(@NonNull ItemStack stack) {
        String typeName = gooType.getId().substring(0, 1).toUpperCase(Locale.ROOT)
            + gooType.getId().substring(1);
        return Component.literal(typeName + NAME_SUFFIX);
    }

    /**
     * Migration: converts old volumetric blobs (with BLOB_VOLUME component) to
     * the new stackable format. Creates an omniblob for any remainder.
     *
     * @param stack  the item stack
     * @param level  the server level
     * @param entity the entity holding this item
     * @param slot   the equipment slot
     */
    @Override
    public void inventoryTick(@NonNull ItemStack stack, @NonNull ServerLevel level, @NonNull Entity entity,
                              EquipmentSlot slot) {
        if (!(entity instanceof Player player)) { return; }
        Long oldVolume = stack.get(GooDataComponents.BLOB_VOLUME.get());
        if (oldVolume == null) { return; }

        migrateOldBlob(stack, oldVolume, player);
    }

    /**
     * Converts a legacy volumetric blob to stackable format plus omniblob remainder.
     *
     * @param stack     the item stack to migrate
     * @param oldVolume the legacy volume in microblobs
     * @param player    the player holding the stack
     */
    private void migrateOldBlob(ItemStack stack, long oldVolume, Player player) {
        stack.remove(GooDataComponents.BLOB_VOLUME.get());

        long wholeBlobs = BlobStacks.wholeBlobs(oldVolume);
        long remainder = BlobStacks.remainder(oldVolume);

        int newCount = (int) Math.min(wholeBlobs, BlobStacks.MAX_STACK);
        stack.setCount(newCount);

        long overflowVolume = computeOverflow(wholeBlobs, remainder);
        distributeOverflow(player, overflowVolume, stack, newCount);
    }

    /**
     * Computes the leftover volume that exceeds the max blob stack size.
     * @param wholeBlobs the total number of whole blobs from the legacy volume
     * @param remainder the sub-blob leftover in microblobs
     * @return the overflow volume in microblobs (excess blobs beyond 64 plus remainder)
     */
    private long computeOverflow(long wholeBlobs, long remainder) {
        return (wholeBlobs > BlobStacks.MAX_STACK)
            ? (wholeBlobs - BlobStacks.MAX_STACK) * BlobStacks.MB_PER_BLOB + remainder
            : remainder;
    }

    /**
     * Creates an omniblob for overflow volume, or clears the stack if nothing remains.
     * @param player the player to receive the overflow omniblob
     * @param overflowVolume the excess volume in microblobs to distribute
     * @param stack the original blob stack being migrated
     * @param newCount the stack count after capping at max blob stack size
     */
    private void distributeOverflow(Player player, long overflowVolume, ItemStack stack, int newCount) {
        if (overflowVolume > 0) {
            ItemStack omniblob = GooOmniblobItem.createWithVolume(gooType, overflowVolume);
            PlayerUtils.addOrDrop(player, omniblob);
        }
        if (newCount <= 0 && overflowVolume <= 0) {
            stack.setCount(0);
        }
    }

    /**
     * When a blob stack of the same type is clicked onto a full stack of 64,
     * or when combined count exceeds 64: create an omniblob with total volume.
     *
     * @param thisStack   the blob stack in the slot
     * @param cursor      the item stack on the cursor
     * @param slot        the inventory slot
     * @param action      the click action
     * @param player      the interacting player
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack thisStack, @NonNull ItemStack cursor,
            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
            @NonNull SlotAccess cursorAccess) {
        if (!(cursor.getItem() instanceof GooBlobItem otherBlob)) { return false; }
        if (otherBlob.gooType != this.gooType) { return false; }
        if (action != ClickAction.PRIMARY) { return false; }

        int totalCount = thisStack.getCount() + cursor.getCount();
        if (totalCount <= thisStack.getMaxStackSize()) { return false; }

        long totalVolume = totalCount * BlobStacks.MB_PER_BLOB;
        ItemStack omniblob = GooOmniblobItem.createWithVolume(gooType, totalVolume);
        thisStack.setCount(0);
        slot.set(omniblob);
        cursorAccess.set(ItemStack.EMPTY);
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
