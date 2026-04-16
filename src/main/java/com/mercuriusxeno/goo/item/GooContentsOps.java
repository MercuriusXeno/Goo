package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Shared add/remove logic for any item stack that carries a
 * {@link GooDataComponents#GOO_CONTENTS} component. Centralizes the
 * cap-and-accept pattern used by both {@link CanisterItem} and
 * {@link VatBlockItem}, so the per-container wrappers only supply the
 * capacity calculation.
 */
public final class GooContentsOps {

    private GooContentsOps() {}

    /**
     * Adds goo to a stack's {@code GOO_CONTENTS}, capped at {@code capacity}.
     * Writes the component back only if any amount was accepted.
     *
     * @param stack    the item stack holding the goo contents
     * @param type     the goo type to add
     * @param amount   desired volume in microblobs
     * @param capacity maximum total volume the stack may hold, in microblobs
     * @return the amount actually accepted (0 if full or non-positive input)
     */
    public static int addGoo(ItemStack stack, GooType type, int amount, int capacity) {
        if (amount <= 0) { return 0; }
        GooContents contents = getContents(stack);
        int accepted = contents.cappedAddAmount(amount, capacity);
        if (accepted <= 0) { return 0; }
        setContents(stack, contents.withAdded(type, accepted));
        return accepted;
    }

    /**
     * Removes goo of the given type from a stack's {@code GOO_CONTENTS},
     * capped at what is currently stored.
     *
     * @param stack  the item stack holding the goo contents
     * @param type   the goo type to remove
     * @param amount desired volume to remove, in microblobs
     * @return the amount actually removed
     */
    public static int removeGoo(ItemStack stack, GooType type, int amount) {
        GooContents contents = getContents(stack);
        if (contents.isEmpty()) { return 0; }
        int available = contents.getVolume(type);
        int toRemove = Math.min(amount, available);
        if (toRemove <= 0) { return 0; }
        setContents(stack, contents.withRemoved(type, toRemove));
        return toRemove;
    }

    /**
     * Reads the goo contents from a stack, returning {@link GooContents#EMPTY}
     * if the component is absent.
     *
     * @param stack the item stack to read
     * @return the goo contents, never null
     */
    private static GooContents getContents(ItemStack stack) {
        GooContents c = stack.get(GooDataComponents.GOO_CONTENTS.get());
        return c != null ? c : GooContents.EMPTY;
    }

    /**
     * Writes goo contents to a stack, removing the component outright when empty
     * so empty stacks do not carry dead data.
     *
     * @param stack    the item stack to write
     * @param contents the goo contents to set
     */
    private static void setContents(ItemStack stack, GooContents contents) {
        if (contents.isEmpty()) {
            stack.remove(GooDataComponents.GOO_CONTENTS.get());
        } else {
            stack.set(GooDataComponents.GOO_CONTENTS.get(), contents);
        }
    }
}
