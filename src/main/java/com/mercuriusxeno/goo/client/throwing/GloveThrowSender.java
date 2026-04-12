package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.overlay.GooTargetHighlighter;
import com.mercuriusxeno.goo.network.BlobThrowPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Player;

/**
 * Client-only helper that resolves the player's aim target and sends
 * a {@link BlobThrowPayload} to the server. Lives in the client package
 * to keep server-safe code in {@link com.mercuriusxeno.goo.item.GooGloveItem}
 * free of client-only imports.
 */
public final class GloveThrowSender {

    /** Sentinel value indicating no entity target. */
    private static final int NO_ENTITY = -1;

    private GloveThrowSender() {}

    /**
     * Resolves the current aim target and sends the throw packet.
     * Must only be called on the client side.
     *
     * @param player the local player
     * @param gooType the selected goo type to throw
     */
    public static void sendThrow(Player player, GooType gooType) {
        if (!GloveUseTracker.isSelectedTypeAvailable()) { return; }
        TargetResult target = resolveAimTarget(player);
        BlobThrowPayload payload = targetToPayload(target, gooType);
        if (payload != null) {
            // Arm the freeze optimistically: the half-second aim lock must
            // kick in on send, not on server ack, so the next throw in the
            // window stays glued to the same spot.
            ThrowFreezeState.arm(target);
            sendPayload(payload);
        }
    }

    /** Resolves the player's current aim target at the current partial tick.
     *
     * @param player the local player
     * @return the resolved target result
     */
    private static TargetResult resolveAimTarget(Player player) {
        float partialTick = Minecraft.getInstance()
                .getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return GooTargetHighlighter.resolveTarget(player, partialTick);
    }

    /** Converts a target result into a throw payload, or null if no valid target.
     *
     * @param target  the aim target
     * @param gooType the selected goo type
     * @return the payload, or null for no target
     */
    private static BlobThrowPayload targetToPayload(TargetResult target, GooType gooType) {
        return switch (target) {
            case TargetResult.EntityTarget et -> new BlobThrowPayload(gooType.getId(), et.entity().getId(), BlockPos.ZERO, NO_ENTITY, false);
            case TargetResult.BlockTarget bt -> new BlobThrowPayload(gooType.getId(), NO_ENTITY, bt.pos(), bt.face().ordinal(), bt.grannyArc());
            case TargetResult.ChainMarkerTarget cmt -> new BlobThrowPayload(gooType.getId(), NO_ENTITY, cmt.pos(), resolveChainMarkerFace(cmt.pos()).getOpposite().ordinal(), false);
            case TargetResult.None ignored -> null;
        };
    }

    /**
     * Reads the placed face from the chain marker BE so the flight
     * destination lands at the orb's face boundary position.
     *
     * @param pos the chain marker block position
     * @return the placed face, or UP if the BE is unavailable
     */
    private static Direction resolveChainMarkerFace(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be) {
            return be.getPlacedFace();
        }
        return Direction.UP;
    }

    /** Sends a custom payload packet to the server.
     *
     * @param payload the payload to send
     */
    private static void sendPayload(BlobThrowPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }
}
