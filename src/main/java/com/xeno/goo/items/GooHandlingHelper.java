package com.xeno.goo.items;

import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.events.TargetingHandler;
import com.xeno.goo.network.*;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.Hand;

public class GooHandlingHelper {
    public static void tryUsingGauntletOrBasin(ClientPlayerEntity player, Hand hand) {
        if (player.isSwingInProgress) {
            return;
        }
        if (TargetingHandler.lastTargetedEntity instanceof GooSplat && ((GooSplat) TargetingHandler.lastTargetedEntity).isAtRest()) {
            // refer to the targeting handler to figure out if we are looking at a goo entity
            Networking.sendToServer(new GooGrabPacket(TargetingHandler.lastTargetedEntity, hand), player);
        } else if (TargetingHandler.lastTargetedBlock != null) {
            if (TargetingHandler.lastHitIsGooContainer) {
                if (player.getHeldItem(hand).getItem() instanceof Gauntlet) {
                    // refer to the targeting handler to figure out if we are looking at a goo container
                    Networking.sendToServer(new GooGauntletCollectPacket(TargetingHandler.lastTargetedBlock, TargetingHandler.lastHitVector, TargetingHandler.lastHitSide, hand), player);
                } else if (player.getHeldItem(hand).getItem() instanceof Basin) {
                    // basins instead place as many as 9, utilizing the overlay to indicate where they will be placed.
                    Networking.sendToServer(new GooBasinCollectPacket(TargetingHandler.lastTargetedBlock, TargetingHandler.lastHitVector, TargetingHandler.lastHitSide, hand), player);
                }
            } else {

                // placing a single splat is a gauntlet function
                if (player.getHeldItem(hand).getItem() instanceof Gauntlet) {
                    // try placing a splat at the block if it's a valid location. Let the server handle the check.
                    Networking.sendToServer(new GooPlaceSplatPacket(TargetingHandler.lastTargetedBlock, TargetingHandler.lastHitVector, TargetingHandler.lastHitSide, hand), player);
                } else if (player.getHeldItem(hand).getItem() instanceof Basin) {
                    // basins instead place as many as 9, utilizing the overlay to indicate where they will be placed.
                    Networking.sendToServer(new GooPlaceSplatAreaPacket(TargetingHandler.lastTargetedBlock, TargetingHandler.lastHitVector, TargetingHandler.lastHitSide, hand), player);
                }
            }
        } else {
            // lobbing is something only gauntlets can do
            if (player.getHeldItem(hand).getItem() instanceof Gauntlet) {
                // packet to server to request a throw event in lieu of grabbing anything
                Networking.sendToServer(new GooLobPacket(hand), player);
            }
        }
    }
}
