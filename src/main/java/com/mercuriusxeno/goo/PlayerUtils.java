package com.mercuriusxeno.goo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Pure utility methods for common player inventory operations.
 */
public final class PlayerUtils {

    private PlayerUtils() {}

    /**
     * Adds an item to the player's inventory, dropping it on the ground
     * if the inventory is full.
     *
     * @param player the player to give the item to
     * @param stack  the item stack to add
     */
    public static void addOrDrop(Player player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }
}
