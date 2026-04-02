package com.mercuriusxeno.goo.item;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TunerState record: pure data, no Minecraft dependencies.
 */
class TunerStateTest {

    @Test
    void empty_hasNoOwnerOrSelection() {
        TunerState state = TunerState.EMPTY;
        assertFalse(state.hasOwner());
        assertFalse(state.hasSelection());
        assertNull(state.ownerUuid());
        assertNull(state.selectedGasketId());
        assertNull(state.selectedPos());
        assertNull(state.selectedRole());
        assertEquals(-1, state.selectedSlot());
        assertNull(state.selectedFaceLabel());
        assertEquals(ConfirmAction.NONE, state.pendingConfirm());
    }

    @Test
    void withOwner_setsOwnerUuid() {
        UUID owner = UUID.randomUUID();
        TunerState state = TunerState.EMPTY.withOwner(owner);
        assertTrue(state.hasOwner());
        assertEquals(owner, state.ownerUuid());
        assertFalse(state.hasSelection());
    }

    @Test
    void withRoleSelection_setsAllSelectionFields() {
        UUID gasketId = UUID.randomUUID();
        BlockPos pos = new BlockPos(1, 2, 3);
        TunerState state = TunerState.EMPTY.withRoleSelection(
            gasketId, pos, GasketRole.RECEIVER, -1, "cap");
        assertTrue(state.hasSelection());
        assertEquals(gasketId, state.selectedGasketId());
        assertEquals(pos, state.selectedPos());
        assertEquals(GasketRole.RECEIVER, state.selectedRole());
        assertEquals(-1, state.selectedSlot());
        assertEquals("cap", state.selectedFaceLabel());
    }

    @Test
    void withRoleSelection_preservesOwner() {
        UUID owner = UUID.randomUUID();
        UUID gasketId = UUID.randomUUID();
        BlockPos pos = new BlockPos(4, 5, 6);
        TunerState state = TunerState.EMPTY.withOwner(owner)
            .withRoleSelection(gasketId, pos, GasketRole.TRANSMITTER, 2, null);
        assertTrue(state.hasOwner());
        assertEquals(owner, state.ownerUuid());
        assertTrue(state.hasSelection());
    }

    @Test
    void withRoleSelection_clearsConfirmState() {
        UUID gasketId = UUID.randomUUID();
        BlockPos pos = new BlockPos(1, 2, 3);
        TunerState state = TunerState.EMPTY
            .withPendingConfirm(ConfirmAction.SEVER_LINK, pos, 0)
            .withRoleSelection(gasketId, pos, GasketRole.TRANSMITTER, 0, null);
        assertEquals(ConfirmAction.NONE, state.pendingConfirm());
        assertNull(state.confirmTarget());
    }

    @Test
    void clearSelection_removesSelectionButKeepsOwner() {
        UUID owner = UUID.randomUUID();
        UUID gasketId = UUID.randomUUID();
        BlockPos pos = new BlockPos(7, 8, 9);
        TunerState state = TunerState.EMPTY
            .withOwner(owner)
            .withRoleSelection(gasketId, pos, GasketRole.RECEIVER, -1, "cap")
            .clearSelection();
        assertTrue(state.hasOwner());
        assertEquals(owner, state.ownerUuid());
        assertFalse(state.hasSelection());
        assertNull(state.selectedGasketId());
        assertNull(state.selectedPos());
        assertNull(state.selectedRole());
    }

    @Test
    void withPendingConfirm_storesConfirmState() {
        BlockPos pos = new BlockPos(10, 20, 30);
        TunerState state = TunerState.EMPTY
            .withPendingConfirm(ConfirmAction.REPLACE_LINK, pos, 3);
        assertEquals(ConfirmAction.REPLACE_LINK, state.pendingConfirm());
        assertEquals(pos, state.confirmTarget());
        assertEquals(3, state.confirmSlot());
    }

    @Test
    void clearConfirm_removesConfirmButKeepsSelection() {
        UUID gasketId = UUID.randomUUID();
        BlockPos pos = new BlockPos(1, 2, 3);
        TunerState state = TunerState.EMPTY
            .withRoleSelection(gasketId, pos, GasketRole.TRANSMITTER, 0, null)
            .withPendingConfirm(ConfirmAction.SEVER_LINK, pos, 0)
            .clearConfirm();
        assertEquals(ConfirmAction.NONE, state.pendingConfirm());
        assertNull(state.confirmTarget());
        assertTrue(state.hasSelection());
    }

    @Test
    void hasSelection_requiresBothGasketIdAndPos() {
        TunerState withGasketOnly = new TunerState(null, UUID.randomUUID(),
            null, null, -1, null, ConfirmAction.NONE, null, -1);
        assertFalse(withGasketOnly.hasSelection());

        TunerState withPosOnly = new TunerState(null, null,
            new BlockPos(0, 0, 0), null, -1, null, ConfirmAction.NONE, null, -1);
        assertFalse(withPosOnly.hasSelection());
    }

    @Test
    void clearSelection_resetsSlotAndConfirm() {
        UUID gasketId = UUID.randomUUID();
        BlockPos pos = new BlockPos(1, 2, 3);
        TunerState state = TunerState.EMPTY
            .withRoleSelection(gasketId, pos, GasketRole.RECEIVER, 5, null)
            .withPendingConfirm(ConfirmAction.REPLACE_LINK, pos, 5)
            .clearSelection();
        assertEquals(-1, state.selectedSlot());
        assertEquals(ConfirmAction.NONE, state.pendingConfirm());
    }

    @Test
    void formatSubBlockCoords_centerSlot() {
        BlockPos pos = new BlockPos(10, 20, 30);
        String coords = TunerState.formatSubBlockCoords(pos, 4);
        assertEquals("10.50, 20, 30.50", coords);
    }

    @Test
    void formatSubBlockCoords_topLeftSlot() {
        BlockPos pos = new BlockPos(5, 10, 15);
        String coords = TunerState.formatSubBlockCoords(pos, 0);
        assertEquals("5.19, 10, 15.19", coords);
    }

    @Test
    void formatSubBlockCoords_bottomRightSlot() {
        BlockPos pos = new BlockPos(5, 10, 15);
        String coords = TunerState.formatSubBlockCoords(pos, 8);
        assertEquals("5.81, 10, 15.81", coords);
    }

    @Test
    void formatSubBlockCoords_invalidSlot() {
        BlockPos pos = new BlockPos(5, 10, 15);
        String coords = TunerState.formatSubBlockCoords(pos, -1);
        assertEquals("5, 10, 15", coords);
    }
}
