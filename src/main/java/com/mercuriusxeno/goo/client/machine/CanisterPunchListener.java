package com.mercuriusxeno.goo.client.machine;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.CanisterBlock;
import com.mercuriusxeno.goo.network.CanisterPunchPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Client-side punch handler for canister blocks. A single left-click
 * on a canister slot immediately sends a punch packet to the server,
 * which pops that individual canister out of the block.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CanisterPunchListener {

    private CanisterPunchListener() {}

    /**
     * Intercepts left-click on canister blocks to instantly pop the targeted slot.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) { return; }
        if (!isCanisterAt(event)) { return; }

        int slot = resolveSlot(event.getPos());
        if (slot < 0) { return; }

        sendPunchPacket(event.getPos(), slot);
        event.setCanceled(true);
    }

    /**
     * Returns true if the event targets a canister block.
     *
     * @param event the left-click block interaction event
     * @return true if the targeted block is a CanisterBlock
     */
    private static boolean isCanisterAt(PlayerInteractEvent.LeftClickBlock event) {
        return event.getLevel().getBlockState(event.getPos())
                .getBlock() instanceof CanisterBlock;
    }

    /**
     * Resolves the targeted slot using the client's precise hit result.
     *
     * @param pos the block position
     * @return the slot index, or -1 if the hit result is invalid
     */
    private static int resolveSlot(BlockPos pos) {
        HitResult hitResult = Minecraft.getInstance().hitResult;
        if (!(hitResult instanceof BlockHitResult blockHit)) { return NO_SLOT; }
        return CanisterBlock.hitSlot(blockHit, pos);
    }

    /**
     * Sends the punch payload to the server.
     *
     * @param pos  the block position
     * @param slot the slot index
     */
    private static void sendPunchPacket(BlockPos pos, int slot) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(
                new CanisterPunchPayload(pos, slot)));
        }
    }
}
