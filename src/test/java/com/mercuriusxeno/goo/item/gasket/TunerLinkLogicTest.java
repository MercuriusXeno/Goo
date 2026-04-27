package com.mercuriusxeno.goo.item.gasket;

import com.mercuriusxeno.goo.item.ConfirmAction;
import com.mercuriusxeno.goo.item.gasket.TunerLinkLogic.TunerAction;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Unit tests for TunerLinkLogic decision matrix: pure logic, no Minecraft dependencies.
 */
class TunerLinkLogicTest {

    private static final BlockPos POS_A = new BlockPos(1, 2, 3);
    private static final BlockPos POS_B = new BlockPos(4, 5, 6);
    private static final UUID GASKET_A = UUID.randomUUID();
    private static final UUID GASKET_B = UUID.randomUUID();
    private static final UUID PARTNER_C = UUID.randomUUID();

    @Test
    void oppositeRoles_completesLink_receiverClicked() {
        TunerAction action = TunerLinkLogic.resolve(
                new GasketClick(GasketRole.RECEIVER, GASKET_B, POS_B, 0, null),
                null,
                GasketRole.TRANSMITTER, GASKET_A,
                new ConfirmContext(ConfirmAction.NONE, null, -1));
        assertInstanceOf(TunerAction.CompleteLink.class, action);
        TunerAction.CompleteLink link = (TunerAction.CompleteLink) action;
        assertEquals(GASKET_A, link.outputGasket());
        assertEquals(GASKET_B, link.inputGasket());
    }

    @Test
    void oppositeRoles_completesLink_transmitterClicked() {
        TunerAction action = TunerLinkLogic.resolve(
                new GasketClick(GasketRole.TRANSMITTER, GASKET_B, POS_B, 0, null),
                null,
                GasketRole.RECEIVER, GASKET_A,
                new ConfirmContext(ConfirmAction.NONE, null, -1));
        assertInstanceOf(TunerAction.CompleteLink.class, action);
        TunerAction.CompleteLink link = (TunerAction.CompleteLink) action;
        assertEquals(GASKET_B, link.outputGasket());
        assertEquals(GASKET_A, link.inputGasket());
    }

    @Test
    void noCarried_noPartner_startsAwaiting() {
        TunerAction action = TunerLinkLogic.resolve(
                new GasketClick(GasketRole.TRANSMITTER, GASKET_A, POS_A, -1, "base"),
                null,
                null, null,
                new ConfirmContext(ConfirmAction.NONE, null, -1));
        assertInstanceOf(TunerAction.StartAwaiting.class, action);
        TunerAction.StartAwaiting awaiting = (TunerAction.StartAwaiting) action;
        assertEquals(GasketRole.TRANSMITTER, awaiting.role());
        assertEquals(GASKET_A, awaiting.gasketId());
        assertEquals(POS_A, awaiting.pos());
        assertEquals("base", awaiting.faceLabel());
    }

    @Test
    void noCarried_hasPartner_promptsSever() {
        TunerAction action = TunerLinkLogic.resolve(
                new GasketClick(GasketRole.RECEIVER, GASKET_A, POS_A, 0, null),
                PARTNER_C,
                null, null,
                new ConfirmContext(ConfirmAction.NONE, null, -1));
        assertInstanceOf(TunerAction.PromptSever.class, action);
    }

    @Test
    void noCarried_hasPartner_severConfirmed() {
        TunerAction action = TunerLinkLogic.resolve(
                new GasketClick(GasketRole.RECEIVER, GASKET_A, POS_A, 0, null),
                PARTNER_C,
                null, null,
                new ConfirmContext(ConfirmAction.SEVER_LINK, POS_A, 0));
        assertInstanceOf(TunerAction.ConfirmSever.class, action);
        assertEquals(GASKET_A, ((TunerAction.ConfirmSever) action).gasketId());
    }

    @Test
    void noCarried_hasPartner_severWrongTarget() {
        TunerAction action = TunerLinkLogic.resolve(
                new GasketClick(GasketRole.RECEIVER, GASKET_A, POS_A, 0, null),
                PARTNER_C,
                null, null,
                new ConfirmContext(ConfirmAction.SEVER_LINK, POS_B, 0));
        assertInstanceOf(TunerAction.PromptSever.class, action);
    }

    @Test
    void sameRole_promptsReplace() {
        TunerAction action = TunerLinkLogic.resolve(
                new GasketClick(GasketRole.TRANSMITTER, GASKET_B, POS_B, 0, null),
                null,
                GasketRole.TRANSMITTER, GASKET_A,
                new ConfirmContext(ConfirmAction.NONE, null, -1));
        assertInstanceOf(TunerAction.PromptReplace.class, action);
    }

    @Test
    void sameRole_replaceConfirmed() {
        TunerAction action = TunerLinkLogic.resolve(
                new GasketClick(GasketRole.TRANSMITTER, GASKET_B, POS_B, 0, null),
                null,
                GasketRole.TRANSMITTER, GASKET_A,
                new ConfirmContext(ConfirmAction.REPLACE_LINK, POS_B, 0));
        assertInstanceOf(TunerAction.ConfirmReplace.class, action);
        TunerAction.ConfirmReplace confirm = (TunerAction.ConfirmReplace) action;
        assertEquals(GASKET_B, confirm.newGasketId());
        assertEquals(GasketRole.TRANSMITTER, confirm.role());
    }

    @Test
    void sameRole_replaceWrongTarget() {
        TunerAction action = TunerLinkLogic.resolve(
                new GasketClick(GasketRole.TRANSMITTER, GASKET_B, POS_B, 0, null),
                null,
                GasketRole.TRANSMITTER, GASKET_A,
                new ConfirmContext(ConfirmAction.REPLACE_LINK, POS_A, 0));
        assertInstanceOf(TunerAction.PromptReplace.class, action);
    }

    @Test
    void oppositeRoles_completesLink_ignoresExistingPartner() {
        TunerAction action = TunerLinkLogic.resolve(
                new GasketClick(GasketRole.RECEIVER, GASKET_B, POS_B, 0, null),
                PARTNER_C,
                GasketRole.TRANSMITTER, GASKET_A,
                new ConfirmContext(ConfirmAction.NONE, null, -1));
        assertInstanceOf(TunerAction.CompleteLink.class, action);
    }

    @Test
    void noCarried_noPartner_receiver_startsAwaiting() {
        TunerAction action = TunerLinkLogic.resolve(
                new GasketClick(GasketRole.RECEIVER, GASKET_A, POS_A, 3, null),
                null,
                null, null,
                new ConfirmContext(ConfirmAction.NONE, null, -1));
        assertInstanceOf(TunerAction.StartAwaiting.class, action);
        TunerAction.StartAwaiting awaiting = (TunerAction.StartAwaiting) action;
        assertEquals(GasketRole.RECEIVER, awaiting.role());
        assertEquals(3, awaiting.slot());
    }
}
