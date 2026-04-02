package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Scans a player's inventory for all goo sources and aggregates
 * available volume per goo type. Handles depletion in priority order:
 * loose blobs → omniblobs → canisters → vats, bottom-up slot index.
 *
 * <p>Slot coverage: main inventory (0-35) plus offhand (40). Armor slots
 * are excluded - you can't throw goo from your chestplate.</p>
 */
public final class GooSourceScanner {

    private GooSourceScanner() {}

    /** Main inventory: slots 0-35. */
    private static final int MAIN_START = 0;
    private static final int MAIN_END = 36;
    /** Offhand slot index in Inventory. */
    private static final int OFFHAND_SLOT = Inventory.SLOT_OFFHAND;

    /**
     * Aggregates available mB per goo type across all inventory sources.
     * Used by radial menu to show throwable quantities.
     *
     * @param player the player whose inventory to scan
     * @return map of goo type to total available mB
     */
    public static EnumMap<GooType, Long> aggregateAvailable(Player player) {
        EnumMap<GooType, Long> totals = new EnumMap<>(GooType.class);
        Inventory inv = player.getInventory();

        for (int i = MAIN_START; i < MAIN_END; i++) {
            scanStack(inv.getItem(i), totals);
        }
        scanStack(inv.getItem(OFFHAND_SLOT), totals);

        return totals;
    }

    /**
     * Depletes the specified amount of goo from the player's inventory.
     * Follows priority: blobs → omniblobs → canisters → vats, bottom-up slots.
     * Returns the amount actually depleted (may be less than requested if insufficient).
     *
     * @param player the player whose inventory to deplete from
     * @param type   the goo type to deplete
     * @param amount the amount in mB to deplete
     * @return actual mB depleted
     */
    public static long deplete(Player player, GooType type, long amount) {
        if (amount <= 0) return 0;

        long remaining = amount;
        Inventory inv = player.getInventory();

        // Pass 1: GooBlobItem matching type - consume whole blobs, bottom-up
        remaining = depletePass(inv, type, remaining, GooBlobItem.class);

        // Pass 2: GooOmniblobItem matching type - partial depletion, bottom-up
        if (remaining > 0) remaining = depletePass(inv, type, remaining, GooOmniblobItem.class);

        // Pass 3: CanisterItem with matching type
        if (remaining > 0) remaining = depletePass(inv, type, remaining, CanisterItem.class);

        // Pass 4: VatBlockItem with matching type
        if (remaining > 0) remaining = depletePass(inv, type, remaining, VatBlockItem.class);

        return amount - remaining;
    }

    /**
     * Checks if the player has at least the specified amount of a goo type.
     * Early-exit version of aggregate - stops scanning when threshold is met.
     *
     * @param player the player to check
     * @param type   the goo type
     * @param amount minimum mB required
     * @return true if sufficient goo is available
     */
    public static boolean hasEnough(Player player, GooType type, long amount) {
        if (amount <= 0) return true;

        long found = 0;
        Inventory inv = player.getInventory();

        for (int i = MAIN_START; i < MAIN_END && found < amount; i++) {
            found += volumeOfType(inv.getItem(i), type);
        }
        if (found < amount) {
            found += volumeOfType(inv.getItem(OFFHAND_SLOT), type);
        }
        return found >= amount;
    }

    // --- Private scanning helpers ---

    /** Aggregates all goo from a single stack into the totals map. */
    private static void scanStack(ItemStack stack, EnumMap<GooType, Long> totals) {
        if (stack.isEmpty()) return;

        if (stack.getItem() instanceof GooBlobItem blob) {
            addToMap(totals, blob.getGooType(),
                    (long) stack.getCount() * BlobStacks.MB_PER_BLOB);
        } else if (stack.getItem() instanceof GooOmniblobItem omni) {
            addToMap(totals, omni.getGooType(), GooOmniblobItem.getVolume(stack));
        } else if (stack.getItem() instanceof CanisterItem) {
            for (Map.Entry<GooType, Long> e : CanisterItem.getGooContents(stack).getAll().entrySet()) {
                addToMap(totals, e.getKey(), e.getValue());
            }
        } else if (stack.getItem() instanceof VatBlockItem) {
            for (Map.Entry<GooType, Long> e : VatBlockItem.getGooContents(stack).getAll().entrySet()) {
                addToMap(totals, e.getKey(), e.getValue());
            }
        }
    }

    /** Returns the volume of the given type in a single stack. */
    private static long volumeOfType(ItemStack stack, GooType type) {
        if (stack.isEmpty()) return 0;

        if (stack.getItem() instanceof GooBlobItem blob && blob.getGooType() == type) {
            return (long) stack.getCount() * BlobStacks.MB_PER_BLOB;
        }
        if (stack.getItem() instanceof GooOmniblobItem omni && omni.getGooType() == type) {
            return GooOmniblobItem.getVolume(stack);
        }
        if (stack.getItem() instanceof CanisterItem) {
            return CanisterItem.getGooContents(stack).getVolume(type);
        }
        if (stack.getItem() instanceof VatBlockItem) {
            return VatBlockItem.getGooContents(stack).getVolume(type);
        }
        return 0;
    }

    // --- Depletion dispatch ---

    /**
     * Runs one depletion pass across main inventory + offhand for a specific
     * source class. Bottom-up slot order (slot 0 first).
     */
    private static long depletePass(Inventory inv, GooType type, long remaining, Class<?> sourceClass) {
        for (int i = MAIN_START; i < MAIN_END && remaining > 0; i++) {
            remaining = depleteStack(inv.getItem(i), type, remaining, sourceClass);
        }
        if (remaining > 0) {
            remaining = depleteStack(inv.getItem(OFFHAND_SLOT), type, remaining, sourceClass);
        }
        return remaining;
    }

    /** Depletes from a single stack if it matches the source class and type. */
    private static long depleteStack(ItemStack stack, GooType type, long remaining, Class<?> sourceClass) {
        if (stack.isEmpty()) return remaining;

        if (sourceClass == GooBlobItem.class && stack.getItem() instanceof GooBlobItem blob
                && blob.getGooType() == type) {
            int blobsNeeded = (int) Math.min(
                    ceilDiv(remaining, BlobStacks.MB_PER_BLOB), stack.getCount());
            long depleted = (long) blobsNeeded * BlobStacks.MB_PER_BLOB;
            stack.shrink(blobsNeeded);
            return remaining - depleted;
        }

        if (sourceClass == GooOmniblobItem.class && stack.getItem() instanceof GooOmniblobItem omni
                && omni.getGooType() == type) {
            long volume = GooOmniblobItem.getVolume(stack);
            long take = Math.min(remaining, volume);
            long left = volume - take;
            if (left <= 0) {
                stack.setCount(0);
            } else {
                GooOmniblobItem.setVolume(stack, left);
            }
            return remaining - take;
        }

        if (sourceClass == CanisterItem.class && stack.getItem() instanceof CanisterItem) {
            long removed = CanisterItem.removeGoo(stack, type, remaining);
            return remaining - removed;
        }

        if (sourceClass == VatBlockItem.class && stack.getItem() instanceof VatBlockItem) {
            long removed = VatBlockItem.removeGoo(stack, type, remaining);
            return remaining - removed;
        }

        return remaining;
    }

    // --- Util ---

    private static void addToMap(EnumMap<GooType, Long> map, GooType type, long amount) {
        map.merge(type, amount, Long::sum);
    }

    /** Ceiling division for positive values. */
    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }
}
