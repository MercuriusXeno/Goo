package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.network.BlobThrowPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Player;

/**
 * Client-only helper that resolves the player's aim target and sends
 * a {@link BlobThrowPayload} to the server. Lives in the client package
 * to keep server-safe code in {@link com.mercuriusxeno.goo.item.GooGloveItem}
 * free of client-only imports.
 */
public final class GloveThrowSender {

    private GloveThrowSender() {}

    /**
     * Resolves the current aim target and sends the throw packet.
     * Must only be called on the client side.
     *
     * @param player the local player
     * @param gooType the selected goo type to throw
     */
    public static void sendThrow(Player player, GooType gooType) {
        // Don't send a throw the server will reject - no goo of this type in inventory
        if (!GloveUseTracker.isSelectedTypeAvailable()) return;

        float partialTick = Minecraft.getInstance()
                .getDeltaTracker().getGameTimeDeltaPartialTick(false);
        TargetResult target = GooTargetHighlighter.resolveTarget(player, partialTick);

        BlobThrowPayload payload = switch (target) {
            case TargetResult.EntityTarget et ->
                    new BlobThrowPayload(gooType.getId(), et.entity().getId(),
                            BlockPos.ZERO, -1);
            case TargetResult.BlockTarget bt ->
                    new BlobThrowPayload(gooType.getId(), -1,
                            bt.pos(), bt.face().ordinal());
            case TargetResult.None ignored -> null;
        };

        if (payload == null) return; // nothing targeted

        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }
}
