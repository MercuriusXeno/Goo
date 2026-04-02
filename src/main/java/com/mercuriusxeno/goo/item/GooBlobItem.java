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

    /** Returns the goo type this blob carries. */
    public GooType getGooType() {
        return gooType;
    }

    @Override
    public @NonNull Component getName(@NonNull ItemStack stack) {
        String typeName = gooType.getId().substring(0, 1).toUpperCase()
            + gooType.getId().substring(1);
        return Component.literal(typeName + " Blob");
    }

    /**
     * Migration: converts old volumetric blobs (with BLOB_VOLUME component) to
     * the new stackable format. Creates an omniblob for any remainder.
     */
    @Override
    public void inventoryTick(@NonNull ItemStack stack, @NonNull ServerLevel level, @NonNull Entity entity,
                              EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;
        Long oldVolume = stack.get(GooDataComponents.BLOB_VOLUME.get());
        if (oldVolume == null) return;

        migrateOldBlob(stack, oldVolume, player);
    }

    /** Converts a legacy volumetric blob to stackable format plus omniblob remainder. */
    private void migrateOldBlob(ItemStack stack, long oldVolume, Player player) {
        stack.remove(GooDataComponents.BLOB_VOLUME.get());

        long wholeBlobs = BlobStacks.wholeBlobs(oldVolume);
        long remainder = BlobStacks.remainder(oldVolume);

        int newCount = (int) Math.min(wholeBlobs, BlobStacks.MAX_STACK);
        stack.setCount(newCount);

        long overflowVolume = (wholeBlobs > BlobStacks.MAX_STACK)
            ? (wholeBlobs - BlobStacks.MAX_STACK) * BlobStacks.MB_PER_BLOB + remainder
            : remainder;

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
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack thisStack, @NonNull ItemStack cursor,
            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
            @NonNull SlotAccess cursorAccess) {
        if (!(cursor.getItem() instanceof GooBlobItem otherBlob)) return false;
        if (otherBlob.gooType != this.gooType) return false;
        if (action != ClickAction.PRIMARY) return false;

        int totalCount = thisStack.getCount() + cursor.getCount();
        if (totalCount <= thisStack.getMaxStackSize()) return false;

        long totalVolume = (long) totalCount * BlobStacks.MB_PER_BLOB;
        ItemStack omniblob = GooOmniblobItem.createWithVolume(gooType, totalVolume);
        thisStack.setCount(0);
        slot.set(omniblob);
        cursorAccess.set(ItemStack.EMPTY);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.BLOB_INSERT;
    }
}
