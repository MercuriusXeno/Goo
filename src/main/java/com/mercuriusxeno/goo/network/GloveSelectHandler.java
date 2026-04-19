package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.item.GooGloveItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handler for glove goo type selection. Finds the glove in
 * the player's hands and updates its data component so the selection
 * persists across saves.
 */
public final class GloveSelectHandler {

    private GloveSelectHandler() {}

    /**
     * Applies the selection on the server thread.
     *
     * @param payload the selection payload data
     * @param context the network context
     */
    public static void handle(GloveSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> applySelection(payload, context));
    }

    /** Resolves the glove and applies the type selection.
     *
     * @param payload the selection payload data
     * @param context the network context
     */
    private static void applySelection(GloveSelectPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) { return; }
        ItemStack glove = findGlove(player);
        if (glove == null) { return; }
        resolveAndApply(glove, payload);
    }

    /** Resolves the selection and applies it to the glove.
     *
     * @param glove   the glove item stack
     * @param payload the selection payload
     */
    private static void resolveAndApply(ItemStack glove, GloveSelectPayload payload) {
        if (payload.gooTypeId().isEmpty()) {
            GooGloveItem.setSelection(glove, GloveSelection.EMPTY);
            return;
        }
        GooType type = GooType.fromId(payload.gooTypeId());
        if (type == null) { return; }
        if (payload.abilityId().isEmpty()) {
            GooGloveItem.setSelection(glove, GloveSelection.ofType(type));
        } else {
            GloveSelection selection = new GloveSelection(payload.gooTypeId(), payload.abilityId());
            GooGloveItem.setSelection(glove, selection);
        }
    }

    /**
     * Finds a glove in main hand or offhand.
     *
     * @param player the interacting player
     * @return the glove item stack, or null if not holding one
     */
    private static ItemStack findGlove(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GooGloveItem) { return main; }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof GooGloveItem) { return off; }
        return null;
    }
}
