package com.mercuriusxeno.goo.block.hub;

import com.mercuriusxeno.goo.block.canister.CanisterSlot;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Static helpers for hub-specific gasket bookkeeping.
 * (Slot save/load lives on {@link CanisterSlot}; this class only handles
 * cross-slot work like chunk-forcing receiver-paired transmitters.)
 */
final class HubSerialization {

    private HubSerialization() {
    }

    /**
     * Forces the chunk of each receiver gasket's transmitter partner.
     *
     * @param be          the hub block entity
     * @param serverLevel the server level
     * @param pos         the block position
     */
    static void forceAllTransmitterChunks(HubBlockEntity be, ServerLevel serverLevel, BlockPos pos) {
        IGasketRegistryAccess access = () -> GasketRegistry.get(serverLevel);
        GasketPusher.forceTransmitterChunk(
                be.gasketState().getId(GasketRole.RECEIVER), access, serverLevel, pos);
        for (CanisterSlot slot : be.containerState().slots) {
            if (slot.isEmpty()) {
                continue;
            }
            CanisterMetadata meta = CanisterItem.getMetadata(slot.canister());
            GasketPusher.forceTransmitterChunk(meta.topGasketId(), access, serverLevel, pos);
        }
    }
}
