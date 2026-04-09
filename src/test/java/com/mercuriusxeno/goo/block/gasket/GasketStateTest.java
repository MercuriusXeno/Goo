package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for GasketState sealed hierarchy: Single, Dual, Null variants. */
class GasketStateTest {

    private static final GasketPartner PARTNER =
            new GasketPartner(BlockPos.ZERO, -1, null);

    @Nested
    class NullVariant {

        @Test
        void supportsNoRoles() {
            var state = GasketState.none();
            assertFalse(state.supportsRole(GasketRole.TRANSMITTER));
            assertFalse(state.supportsRole(GasketRole.RECEIVER));
        }

        @Test
        void getIdReturnsNull() {
            var state = GasketState.none();
            assertNull(state.getId(GasketRole.TRANSMITTER));
            assertNull(state.getId(GasketRole.RECEIVER));
        }

        @Test
        void ensureIdReturnsNull() {
            var state = GasketState.none();
            assertNull(state.ensureId(GasketRole.TRANSMITTER, () -> {}));
        }
    }

    @Nested
    class SingleVariant {

        @Test
        void supportsConfiguredRoleOnly() {
            var state = GasketState.single(GasketRole.TRANSMITTER, "crucible");
            assertTrue(state.supportsRole(GasketRole.TRANSMITTER));
            assertFalse(state.supportsRole(GasketRole.RECEIVER));
        }

        @Test
        void ensureIdGeneratesOnce() {
            var state = GasketState.single(GasketRole.TRANSMITTER, "crucible");
            assertNull(state.getId(GasketRole.TRANSMITTER));

            var id = state.ensureId(GasketRole.TRANSMITTER, () -> {});
            assertNotNull(id);
            assertEquals(id, state.ensureId(GasketRole.TRANSMITTER, () -> {}));
        }

        @Test
        void partnerRoundTrips() {
            var state = GasketState.single(GasketRole.TRANSMITTER, "crucible");
            assertNull(state.getPartner(GasketRole.TRANSMITTER));

            state.setPartner(GasketRole.TRANSMITTER, PARTNER, () -> {});
            assertEquals(PARTNER, state.getPartner(GasketRole.TRANSMITTER));
        }

        @Test
        void clearResetsIdAndPartner() {
            var state = GasketState.single(GasketRole.TRANSMITTER, "crucible");
            state.ensureId(GasketRole.TRANSMITTER, () -> {});
            state.setPartner(GasketRole.TRANSMITTER, PARTNER, () -> {});

            state.clear(GasketRole.TRANSMITTER, () -> {});
            assertNull(state.getId(GasketRole.TRANSMITTER));
            assertNull(state.getPartner(GasketRole.TRANSMITTER));
        }

        @Test
        void wrongRoleIsNoOp() {
            var state = GasketState.single(GasketRole.TRANSMITTER, "crucible");
            assertNull(state.ensureId(GasketRole.RECEIVER, () -> {}));
            assertNull(state.getPartner(GasketRole.RECEIVER));
        }

        @Test
        void faceLabelReturnsConfiguredLabel() {
            var state = GasketState.single(GasketRole.TRANSMITTER, "crucible");
            assertEquals("crucible", state.getFaceLabel(GasketRole.TRANSMITTER));
            assertNull(state.getFaceLabel(GasketRole.RECEIVER));
        }
    }

    @Nested
    class DualVariant {

        @Test
        void supportsBothRoles() {
            var state = GasketState.dual("cap", "base");
            assertTrue(state.supportsRole(GasketRole.RECEIVER));
            assertTrue(state.supportsRole(GasketRole.TRANSMITTER));
        }

        @Test
        void rolesAreIndependent() {
            var state = GasketState.dual("cap", "base");
            var recvId = state.ensureId(GasketRole.RECEIVER, () -> {});
            var transId = state.ensureId(GasketRole.TRANSMITTER, () -> {});

            assertNotNull(recvId);
            assertNotNull(transId);
            assertFalse(recvId.equals(transId));
        }

        @Test
        void faceLabelsByRole() {
            var state = GasketState.dual("cap", "base");
            assertEquals("cap", state.getFaceLabel(GasketRole.RECEIVER));
            assertEquals("base", state.getFaceLabel(GasketRole.TRANSMITTER));
        }

        @Test
        void clearOneRoleDoesNotAffectOther() {
            var state = GasketState.dual("cap", "base");
            state.ensureId(GasketRole.RECEIVER, () -> {});
            state.ensureId(GasketRole.TRANSMITTER, () -> {});
            state.setPartner(GasketRole.RECEIVER, PARTNER, () -> {});

            state.clear(GasketRole.TRANSMITTER, () -> {});

            assertNotNull(state.getId(GasketRole.RECEIVER));
            assertEquals(PARTNER, state.getPartner(GasketRole.RECEIVER));
            assertNull(state.getId(GasketRole.TRANSMITTER));
        }
    }
}
