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

    /** Applies the selection on the server thread. */
    public static void handle(GloveSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack glove = findGlove(player);
            if (glove == null) return;

            if (payload.gooTypeId().isEmpty()) {
                GooGloveItem.setSelectedType(glove, null);
            } else {
                GooType type = GooType.fromId(payload.gooTypeId());
                if (type != null) {
                    GooGloveItem.setSelectedType(glove, type);
                }
            }
        });
    }

    /** Finds a glove in main hand or offhand. */
    private static ItemStack findGlove(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GooGloveItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof GooGloveItem) return off;
        return null;
    }
}
