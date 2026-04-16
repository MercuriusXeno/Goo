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

    /** Main inventory: slots 0-35. */
    private static final int MAIN_START = 0;
    private static final int MAIN_END = 36;
    /** Offhand slot index in Inventory. */
    private static final int OFFHAND_SLOT = Inventory.SLOT_OFFHAND;

    private GooSourceScanner() {}

    /**
     * Aggregates available mB per goo type across all inventory sources.
     * Used by radial menu to show throwable quantities.
     *
     * @param player the player whose inventory to scan
     * @return map of goo type to total available mB
     */
    public static Map<GooType, Integer> aggregateAvailable(Player player) {
        Map<GooType, Integer> totals = new EnumMap<>(GooType.class);
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
    public static int deplete(Player player, GooType type, int amount) {
        if (amount <= 0) { return 0; }

        int remaining = depleteAllPasses(player.getInventory(), type, amount);
        return amount - remaining;
    }

    /**
     * Runs all four priority passes in order: blobs, omniblobs, canisters, vats.
     *
     * @param inv       the player inventory
     * @param type      the goo type to deplete
     * @param remaining the amount still to deplete
     * @return the amount remaining after all passes
     */
    private static int depleteAllPasses(Inventory inv, GooType type, int remaining) {
        int left = depletePass(inv, type, remaining, GooBlobItem.class);
        if (left > 0) { left = depletePass(inv, type, left, GooOmniblobItem.class); }
        if (left > 0) { left = depletePass(inv, type, left, CanisterItem.class); }
        if (left > 0) { left = depletePass(inv, type, left, VatBlockItem.class); }
        return left;
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
    public static boolean hasEnough(Player player, GooType type, int amount) {
        return amount <= 0 || scanForThreshold(player.getInventory(), type, amount) >= amount;
    }

    /**
     * Scans inventory for volume of a specific type, stopping early once threshold is met.
     *
     * @param inv    the player inventory
     * @param type   the goo type
     * @param threshold minimum mB to find before stopping
     * @return total mB found (may be less than threshold if insufficient)
     */
    private static int scanForThreshold(Inventory inv, GooType type, int threshold) {
        int found = 0;
        for (int i = MAIN_START; i < MAIN_END && found < threshold; i++) {
            found += volumeOfType(inv.getItem(i), type);
        }
        if (found < threshold) {
            found += volumeOfType(inv.getItem(OFFHAND_SLOT), type);
        }
        return found;
    }

    // --- Private scanning helpers ---

    /**
     * Aggregates all goo from a single stack into the totals map.
     *
     * @param stack  the item stack to scan
     * @param totals the running totals map
     */
    private static void scanStack(ItemStack stack, Map<GooType, Integer> totals) {
        if (stack.isEmpty()) { return; }

        if (stack.getItem() instanceof GooBlobItem blob) {
            addToMap(totals, blob.getGooType(), stack.getCount() * BlobStacks.MB_PER_BLOB);
        } else if (stack.getItem() instanceof GooOmniblobItem omni) {
            addToMap(totals, omni.getGooType(), GooOmniblobItem.getVolume(stack));
        } else {
            scanContainerStack(stack, totals);
        }
    }

    /**
     * Scans canister or vat block items for all contained goo types.
     *
     * @param stack  the item stack to scan
     * @param totals the running totals map
     */
    private static void scanContainerStack(ItemStack stack, Map<GooType, Integer> totals) {
        if (stack.getItem() instanceof CanisterItem) {
            CanisterFluidContent content = CanisterItem.getFluidContent(stack);
            GooType type = content.getGooType();
            if (type != null && content.amount() > 0) {
                addToMap(totals, type, content.amount());
            }
        } else if (stack.getItem() instanceof VatBlockItem) {
            addAllEntries(totals, VatBlockItem.getGooContents(stack).getAll());
        }
    }

    /**
     * Merges all entries from a goo contents map into the running totals.
     *
     * @param totals  the running totals map
     * @param entries the entries to merge
     */
    private static void addAllEntries(Map<GooType, Integer> totals, Map<GooType, Integer> entries) {
        for (Map.Entry<GooType, Integer> e : entries.entrySet()) {
            addToMap(totals, e.getKey(), e.getValue());
        }
    }

    /**
     * Returns the volume of the given type in a single stack.
     *
     * @param stack the item stack to inspect
     * @param type  the goo type to look for
     * @return volume in microblobs
     */
    private static int volumeOfType(ItemStack stack, GooType type) {
        if (stack.isEmpty()) { return 0; }
        int loose = looseGooVolume(stack, type);
        return loose > 0 ? loose : containerVolumeOfType(stack, type);
    }

    /**
     * Returns volume from loose goo items (blobs or omniblobs) of the given type.
     *
     * @param stack the item stack to inspect
     * @param type  the goo type to match
     * @return volume in microblobs, or 0 if not a matching loose goo
     */
    private static int looseGooVolume(ItemStack stack, GooType type) {
        if (stack.getItem() instanceof GooBlobItem blob && blob.getGooType() == type) {
            return stack.getCount() * BlobStacks.MB_PER_BLOB;
        }
        if (stack.getItem() instanceof GooOmniblobItem omni && omni.getGooType() == type) {
            return GooOmniblobItem.getVolume(stack);
        }
        return 0;
    }

    /**
     * Returns volume of a specific type from canister or vat block items.
     *
     * @param stack the item stack to inspect
     * @param type  the goo type to look for
     * @return volume in microblobs, or 0 if not a container
     */
    private static int containerVolumeOfType(ItemStack stack, GooType type) {
        if (stack.getItem() instanceof CanisterItem) {
            CanisterFluidContent content = CanisterItem.getFluidContent(stack);
            return (content.getGooType() == type) ? content.amount() : 0;
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
     *
     * @param inv         the player inventory
     * @param type        the goo type to deplete
     * @param remaining   the remaining amount to deplete
     * @param sourceClass the item class to target in this pass
     * @return the remaining amount after this pass
     */
    private static int depletePass(Inventory inv, GooType type, int remaining, Class<?> sourceClass) {
        int left = remaining;
        for (int i = MAIN_START; i < MAIN_END && left > 0; i++) {
            left = depleteStack(inv.getItem(i), type, left, sourceClass);
        }
        if (left > 0) {
            left = depleteStack(inv.getItem(OFFHAND_SLOT), type, left, sourceClass);
        }
        return left;
    }

    /**
     * Depletes from a single stack if it matches the source class and type.
     *
     * @param stack       the item stack to deplete from
     * @param type        the goo type to deplete
     * @param remaining   the remaining amount to deplete
     * @param sourceClass the item class to match
     * @return the remaining amount after depletion
     */
    private static int depleteStack(ItemStack stack, GooType type, int remaining, Class<?> sourceClass) {
        if (stack.isEmpty()) { return remaining; }

        if (sourceClass == GooBlobItem.class) {
            return depleteBlobStack(stack, type, remaining);
        }
        if (sourceClass == GooOmniblobItem.class) {
            return depleteOmniblobStack(stack, type, remaining);
        }
        return depleteContainerStack(stack, type, remaining, sourceClass);
    }

    /**
     * Depletes whole blobs from a matching blob stack.
     *
     * @param stack     the item stack to deplete from
     * @param type      the goo type to match
     * @param remaining the amount still to deplete
     * @return the remaining amount after depletion
     */
    private static int depleteBlobStack(ItemStack stack, GooType type, int remaining) {
        if (!(stack.getItem() instanceof GooBlobItem blob) || blob.getGooType() != type) {
            return remaining;
        }
        int blobsNeeded = (int) Math.min(ceilDiv(remaining, BlobStacks.MB_PER_BLOB), stack.getCount());
        stack.shrink(blobsNeeded);
        return remaining - blobsNeeded * BlobStacks.MB_PER_BLOB;
    }

    /**
     * Depletes partial volume from a matching omniblob stack.
     *
     * @param stack     the item stack to deplete from
     * @param type      the goo type to match
     * @param remaining the amount still to deplete
     * @return the remaining amount after depletion
     */
    private static int depleteOmniblobStack(ItemStack stack, GooType type, int remaining) {
        if (!(stack.getItem() instanceof GooOmniblobItem omni) || omni.getGooType() != type) {
            return remaining;
        }
        int volume = GooOmniblobItem.getVolume(stack);
        int take = Math.min(remaining, volume);
        reduceOmniblobVolume(stack, volume - take);
        return remaining - take;
    }

    /**
     * Sets omniblob volume to the new value, or destroys the stack if depleted.
     *
     * @param stack      the omniblob item stack
     * @param newVolume  the volume to set (destroyed if <= 0)
     */
    private static void reduceOmniblobVolume(ItemStack stack, int newVolume) {
        if (newVolume <= 0) {
            stack.setCount(0);
        } else {
            GooOmniblobItem.setVolume(stack, newVolume);
        }
    }

    /**
     * Depletes from canister or vat block items via their removeGoo API.
     *
     * @param stack       the item stack to deplete from
     * @param type        the goo type to deplete
     * @param remaining   the amount still to deplete
     * @param sourceClass the item class to match
     * @return the remaining amount after depletion
     */
    private static int depleteContainerStack(ItemStack stack, GooType type, int remaining, Class<?> sourceClass) {
        if (sourceClass == CanisterItem.class && stack.getItem() instanceof CanisterItem) {
            return remaining - CanisterItem.removeGoo(stack, type, remaining);
        }
        if (sourceClass == VatBlockItem.class && stack.getItem() instanceof VatBlockItem) {
            return remaining - VatBlockItem.removeGoo(stack, type, remaining);
        }
        return remaining;
    }

    // --- Util ---

    /**
     * Adds the given amount to the running total for the given type.
     *
     * @param map    the running totals map
     * @param type   the goo type
     * @param amount the amount to add
     */
    private static void addToMap(Map<GooType, Integer> map, GooType type, int amount) {
        map.merge(type, amount, Integer::sum);
    }

    /**
     * Ceiling division for positive values.
     *
     * @param a the dividend
     * @param b the divisor
     * @return the ceiling of a/b
     */
    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
