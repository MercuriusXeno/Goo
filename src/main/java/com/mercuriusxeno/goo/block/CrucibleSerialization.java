package com.mercuriusxeno.goo.block;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Static helpers for crucible NBT serialization: melting state (PMI + fuel rod).
 * Gasket fields are now handled by GasketState. Keeps framework save/load
 * overrides in CrucibleBlockEntity.
 */
final class CrucibleSerialization {

    private CrucibleSerialization() { }

    /** Saves melting item and fuel rod stacks.
     *
     * @param be     the crucible block entity
     * @param output the value output to write to
     */
    static void saveMeltingState(CrucibleBlockEntity be, ValueOutput output) {
        if (!be.meltingItem.isEmpty()) {
            output.store(CrucibleBlockEntity.TAG_MELTING_ITEM, ItemStack.CODEC, be.meltingItem);
        }
        if (!be.fuelRod.isEmpty()) {
            output.store(CrucibleBlockEntity.TAG_FUEL_ROD, ItemStack.CODEC, be.fuelRod);
        }
    }

    /** Loads melting item and fuel rod stacks.
     *
     * @param be    the crucible block entity
     * @param input the value input to read from
     */
    static void loadMeltingState(CrucibleBlockEntity be, ValueInput input) {
        be.meltingItem = input.read(CrucibleBlockEntity.TAG_MELTING_ITEM, ItemStack.CODEC)
            .orElse(ItemStack.EMPTY);
        be.fuelRod = input.read(CrucibleBlockEntity.TAG_FUEL_ROD, ItemStack.CODEC)
            .orElse(ItemStack.EMPTY);
    }

}
