package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.GooType;
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
        resolveAndSetType(glove, payload.gooTypeId());
    }

    /** Sets the glove's selected type: null for empty ID, or the resolved GooType.
     *
     * @param glove     the glove item stack
     * @param gooTypeId the goo type ID string (empty to clear)
     */
    private static void resolveAndSetType(ItemStack glove, String gooTypeId) {
        if (gooTypeId.isEmpty()) {
            GooGloveItem.setSelectedType(glove, null);
            return;
        }
        GooType type = GooType.fromId(gooTypeId);
        if (type != null) {
            GooGloveItem.setSelectedType(glove, type);
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
