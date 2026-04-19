package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Fluid handler adapting a canister item in a player's inventory for gasket
 * capability queries. Reads/writes through the player's inventory slot.
 * Presents 15 tanks (one per {@link GooType}).
 */
public final class PlayerInventorySlotHandler implements ResourceHandler<FluidResource> {

    private static final int TANK_COUNT = GooType.values().length;

    private final Player player;
    private final int inventorySlot;

    /**
     * @param player        the player whose inventory contains the canister
     * @param inventorySlot the inventory slot index
     */
    public PlayerInventorySlotHandler(Player player, int inventorySlot) {
        this.player = player;
        this.inventorySlot = inventorySlot;
    }

    /** Returns the canister item stack from the player's inventory.
     *
     * @return the stack
     */
    private ItemStack getStack() {
        return player.getInventory().getItem(inventorySlot);
    }

    /** Returns 15 (one tank per goo type).
     *
     * @return the integer value
     */
    @Override
    public int size() {
        return TANK_COUNT;
    }

    /** Returns the FluidResource for the goo type at this index, or empty if absent.
     *
     * @param index the tank index
     * @return the resource
     */
    @Override
    public FluidResource getResource(int index) {
        GooType type = typeForIndex(index);
        if (type == null) { return FluidResource.EMPTY; }
        CanisterFluidContent content = CanisterItem.getFluidContent(getStack());
        return (content.getGooType() == type)
            ? FluidResource.of(GooFluids.SOURCES.get(type).get())
            : FluidResource.EMPTY;
    }

    /** Returns the volume of the goo type at this index in the canister.
     *
     * @param index the tank index
     * @return the amount as long
     */
    @Override
    public long getAmountAsLong(int index) {
        GooType type = typeForIndex(index);
        if (type == null) { return 0; }
        CanisterFluidContent content = CanisterItem.getFluidContent(getStack());
        return (content.getGooType() == type) ? content.amount() : 0L;
    }

    /** Returns the canister's total capacity based on its compression level.
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @return the capacity as long
     */
    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return ContainerCapacity.canisterCapacity(
            com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(getStack()));
    }

    /** Only the goo fluid matching this tank index is valid.
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @return true if valid
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) { return false; }
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        return type != null && type.ordinal() == index;
    }

    /** Inserts goo into the canister at the matching type index.
     *
     * @param index       the tank index
     * @param resource    the fluid resource
     * @param amount      volume in microblobs
     * @param transaction the transaction context
     * @return the integer value
     */
    @Override
    public int insert(int index, FluidResource resource, int amount,
            TransactionContext transaction) {
        GooType type = validateFluidOp(index, resource, amount);
        if (type == null) { return 0; }
        ItemStack stack = getStack();
        if (!(stack.getItem() instanceof CanisterItem)) { return 0; }
        return Math.min(CanisterItem.addGoo(stack, type, amount), Integer.MAX_VALUE);
    }

    /** Extracts goo from the canister at the matching type index.
     *
     * @param index       the tank index
     * @param resource    the fluid resource
     * @param amount      volume in microblobs
     * @param transaction the transaction context
     * @return the integer value
     */
    @Override
    public int extract(int index, FluidResource resource, int amount,
            TransactionContext transaction) {
        GooType type = validateFluidOp(index, resource, amount);
        if (type == null) { return 0; }
        ItemStack stack = getStack();
        if (!(stack.getItem() instanceof CanisterItem)) { return 0; }
        return Math.min(CanisterItem.removeGoo(stack, type, amount), Integer.MAX_VALUE);
    }

    /** Validates a fluid operation: checks amount, resource, and index match.
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @param amount   the requested volume
     * @return the matching GooType, or null if invalid
     */
    private GooType validateFluidOp(int index, FluidResource resource, int amount) {
        if (amount <= 0 || resource.isEmpty()) { return null; }
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        return type != null && type.ordinal() == index ? type : null;
    }

    /** Returns the GooType for the given tank index, or null if out of range.
     *
     * @param index the tank index
     * @return the goo type, or null
     */
    private static GooType typeForIndex(int index) {
        GooType[] types = GooType.values();
        return index >= 0 && index < types.length ? types[index] : null;
    }
}
