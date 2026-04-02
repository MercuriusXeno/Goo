package com.mercuriusxeno.goo.item;

/**
 * Implemented by goo items that have a specific interaction with canister blocks.
 * Lets the canister dispatch on the item's self-declared interaction type
 * instead of interrogating via instanceof chains.
 */
public interface IGooItemInteraction {

    /**
     * Returns the interaction type this item performs when used on a canister block.
     */
    GooInteractionType canisterInteraction();
}
