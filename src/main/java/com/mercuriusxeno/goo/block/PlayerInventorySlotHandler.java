package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
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

    private ItemStack getStack() {
        return player.getInventory().getItem(inventorySlot);
    }

    @Override
    public int size() {
        return TANK_COUNT;
    }

    @Override
    public FluidResource getResource(int index) {
        GooType type = typeForIndex(index);
        if (type == null) return FluidResource.EMPTY;
        long volume = CanisterItem.getGooContents(getStack()).getVolume(type);
        return volume > 0
            ? FluidResource.of(GooFluids.SOURCES.get(type).get())
            : FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        GooType type = typeForIndex(index);
        if (type == null) return 0L;
        return CanisterItem.getGooContents(getStack()).getVolume(type);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return ContainerCapacity.canisterCapacity(
            com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(getStack()));
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) return false;
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        return type != null && type.ordinal() == index;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount,
            TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) return 0;
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        if (type == null || type.ordinal() != index) return 0;
        ItemStack stack = getStack();
        if (!(stack.getItem() instanceof CanisterItem)) return 0;
        return (int) Math.min(CanisterItem.addGoo(stack, type, amount), Integer.MAX_VALUE);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount,
            TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) return 0;
        GooType type = GooFluids.getTypeFromFluid(resource.getFluid());
        if (type == null || type.ordinal() != index) return 0;
        ItemStack stack = getStack();
        if (!(stack.getItem() instanceof CanisterItem)) return 0;
        return (int) Math.min(CanisterItem.removeGoo(stack, type, amount), Integer.MAX_VALUE);
    }

    private static GooType typeForIndex(int index) {
        GooType[] types = GooType.values();
        return index >= 0 && index < types.length ? types[index] : null;
    }
}
