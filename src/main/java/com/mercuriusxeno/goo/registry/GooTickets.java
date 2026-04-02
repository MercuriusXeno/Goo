package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * Registers chunk-loading ticket controllers for gasket links.
 * Forces the target chunk so that distant gasket partners remain accessible.
 */
public final class GooTickets {

    /** Ticket controller for gasket-linked chunk loading. */
    public static TicketController GASKET_CHUNKS;

    private GooTickets() {}

    /**
     * Registers the gasket chunk ticket controller on the mod event bus.
     *
     * @param event the ticket controller registration event
     */
    public static void register(RegisterTicketControllersEvent event) {
        GASKET_CHUNKS = new TicketController(
            Identifier.fromNamespaceAndPath(Goo.MODID, "gasket_chunks"),
            (level, ticketHelper) -> {
                for (var entry : ticketHelper.getBlockTickets().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (level.getBlockEntity(pos) == null) {
                        ticketHelper.removeAllTickets(pos);
                    }
                }
            }
        );
        event.register(GASKET_CHUNKS);
    }
}
