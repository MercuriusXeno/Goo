package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/**
 * Registers chunk-loading ticket controllers for gasket links.
 * Forces the target chunk so that distant gasket partners remain accessible.
 */
public final class GooTickets {

    /** Identifier path for the gasket chunk ticket controller. */
    private static final String TICKET_ID = "gasket_chunks";

    /** Ticket controller for gasket-linked chunk loading. */
    @SuppressWarnings("PMD.MutableStaticState") // NeoForge registration result, set once during init
    public static TicketController gasketChunks;

    private GooTickets() {}

    /**
     * Registers the gasket chunk ticket controller on the mod event bus.
     *
     * @param event the ticket controller registration event
     */
    public static void register(RegisterTicketControllersEvent event) {
        gasketChunks = new TicketController(
            Identifier.fromNamespaceAndPath(Goo.MODID, TICKET_ID),
            GooTickets::pruneOrphanedTickets);
        event.register(gasketChunks);
    }

    /** Removes chunk tickets for positions whose block entities no longer exist.
     *
     * @param level        the server level
     * @param ticketHelper the ticket helper for this controller
     */
    private static void pruneOrphanedTickets(ServerLevel level, TicketHelper ticketHelper) {
        for (var entry : ticketHelper.getBlockTickets().entrySet()) {
            BlockPos pos = entry.getKey();
            if (level.getBlockEntity(pos) == null) {
                ticketHelper.removeAllTickets(pos);
            }
        }
    }
}
