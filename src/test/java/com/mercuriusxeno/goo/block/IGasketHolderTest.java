package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRegionResolver;
import com.mercuriusxeno.goo.item.GasketRole;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IGasketHolder default methods and concrete dispatch contracts.
 * Uses lightweight stubs to replicate each machine's dispatch logic,
 * avoiding Minecraft class initialization.
 */
class IGasketHolderTest {

    /** Minimal stub that inherits all defaults. */
    private static class StubHolder implements IGasketHolder {
        @Override public @Nullable UUID getGasketId(GasketRole role) { return null; }
        @Override public @Nullable UUID ensureGasketId(GasketRole role) { return null; }
        @Override public @Nullable GasketPartner getPartner(GasketRole role) { return null; }
        @Override public void setPartner(GasketRole role, @Nullable GasketPartner partner) {}
    }

    // --- resolveSlot default ---

    @Test
    void resolveSlot_default_returnsNoSlot() {
        assertEquals(GasketPartner.NO_SLOT, new StubHolder().resolveSlot(null));
    }

    // --- supportsRole default ---

    @Test
    void supportsRole_default_trueForBothRoles() {
        StubHolder holder = new StubHolder();
        assertTrue(holder.supportsRole(GasketRole.RECEIVER));
        assertTrue(holder.supportsRole(GasketRole.TRANSMITTER));
    }

    // --- getFaceLabel default ---

    @Test
    void getFaceLabel_default_isNull() {
        StubHolder holder = new StubHolder();
        assertNull(holder.getFaceLabel(GasketRole.RECEIVER));
        assertNull(holder.getFaceLabel(GasketRole.TRANSMITTER));
    }

    // --- getMachineLabel default ---

    @Test
    void getMachineLabel_default_negativeSlot_isNull() {
        assertNull(new StubHolder().getMachineLabel(GasketPartner.NO_SLOT));
    }

    // --- allowsTuning default ---

    @Test
    void allowsTuning_default_alwaysTrue() {
        StubHolder holder = new StubHolder();
        assertTrue(holder.allowsTuning(null));
        assertTrue(holder.allowsTuning(UUID.randomUUID()));
    }

    // --- hasIntake default ---

    @Test
    void hasIntake_default_isFalse() {
        assertFalse(new StubHolder().hasIntake());
    }

    // --- SLOT_MISS constant ---

    @Test
    void slotMiss_isMinValue() {
        assertEquals(Integer.MIN_VALUE, IGasketHolder.SLOT_MISS);
    }

    @Test
    void slotMiss_distinctFromNoSlot() {
        assertNotEquals(GasketPartner.NO_SLOT, IGasketHolder.SLOT_MISS);
    }

    // --- Canister allowsTuning contract ---
    // CanisterBlockEntity.allowsTuning: null owner -> true, null tuner -> true,
    // matching owner -> true, mismatched -> false.

    @Nested
    class CanisterAllowsTuningContract {

        /** Stub replicating CanisterBlockEntity.allowsTuning logic. */
        private static class CanisterStub extends StubHolder {
            private final @Nullable UUID ownerUuid;
            CanisterStub(@Nullable UUID ownerUuid) { this.ownerUuid = ownerUuid; }

            @Override
            public boolean allowsTuning(@Nullable UUID tunerOwner) {
                if (ownerUuid == null) return true;
                if (tunerOwner == null) return true;
                return tunerOwner.equals(ownerUuid);
            }
        }

        @Test
        void nullOwner_anyTunerAllowed() {
            CanisterStub canister = new CanisterStub(null);
            assertTrue(canister.allowsTuning(null));
            assertTrue(canister.allowsTuning(UUID.randomUUID()));
        }

        @Test
        void ownedCanister_nullTunerAllowed() {
            CanisterStub canister = new CanisterStub(UUID.randomUUID());
            assertTrue(canister.allowsTuning(null));
        }

        @Test
        void ownedCanister_matchingTunerAllowed() {
            UUID owner = UUID.randomUUID();
            CanisterStub canister = new CanisterStub(owner);
            assertTrue(canister.allowsTuning(owner));
        }

        @Test
        void ownedCanister_mismatchedTunerBlocked() {
            UUID owner = UUID.randomUUID();
            UUID stranger = UUID.randomUUID();
            CanisterStub canister = new CanisterStub(owner);
            assertFalse(canister.allowsTuning(stranger));
        }
    }

    // --- Hub hasIntake contract ---

    @Nested
    class HubHasIntakeContract {

        private static class HubStub extends StubHolder {
            @Override public boolean hasIntake() { return true; }
        }

        @Test
        void hubReturnsTrue() {
            assertTrue(new HubStub().hasIntake());
        }
    }

    // --- Crucible gasket dispatch contract ---

    @Nested
    class CrucibleGasketDispatchContract {

        private static class CrucibleStub extends StubHolder {
            @Override
            public GasketRole resolveRole(BlockHitResult hit) {
                return GasketRegionResolver.resolveCrucibleRole();
            }
            @Override
            public @Nullable String getFaceLabel(GasketRole role) {
                return "crucible";
            }
        }

        @Test
        void resolveRole_alwaysTransmitter() {
            assertEquals(GasketRole.TRANSMITTER, new CrucibleStub().resolveRole(null));
        }

        @Test
        void getFaceLabel_alwaysCrucible() {
            CrucibleStub crucible = new CrucibleStub();
            assertEquals("crucible", crucible.getFaceLabel(GasketRole.RECEIVER));
            assertEquals("crucible", crucible.getFaceLabel(GasketRole.TRANSMITTER));
        }
    }

    // --- Vat gasket dispatch contract ---

    @Nested
    class VatGasketDispatchContract {

        /** Stub replicating VatBlockEntity.getFaceLabel and supportsRole logic. */
        private static class VatStub extends StubHolder {
            private final boolean gasketCap;
            private final boolean gasketBase;
            private final @Nullable String label;

            VatStub(boolean gasketCap, boolean gasketBase, @Nullable String label) {
                this.gasketCap = gasketCap;
                this.gasketBase = gasketBase;
                this.label = label;
            }

            @Override
            public @Nullable String getFaceLabel(GasketRole role) {
                return role == GasketRole.RECEIVER ? "cap" : "base";
            }

            @Override
            public boolean supportsRole(GasketRole role) {
                return role == GasketRole.RECEIVER ? gasketCap : gasketBase;
            }

            @Override
            public @Nullable String getMachineLabel(int slot) {
                return label;
            }
        }

        @Test
        void getFaceLabel_capForReceiver() {
            assertEquals("cap", new VatStub(true, true, null).getFaceLabel(GasketRole.RECEIVER));
        }

        @Test
        void getFaceLabel_baseForTransmitter() {
            assertEquals("base", new VatStub(true, true, null).getFaceLabel(GasketRole.TRANSMITTER));
        }

        @Test
        void supportsRole_capPresent_receiverTrue() {
            assertTrue(new VatStub(true, false, null).supportsRole(GasketRole.RECEIVER));
        }

        @Test
        void supportsRole_capAbsent_receiverFalse() {
            assertFalse(new VatStub(false, true, null).supportsRole(GasketRole.RECEIVER));
        }

        @Test
        void supportsRole_basePresent_transmitterTrue() {
            assertTrue(new VatStub(false, true, null).supportsRole(GasketRole.TRANSMITTER));
        }

        @Test
        void supportsRole_baseAbsent_transmitterFalse() {
            assertFalse(new VatStub(true, false, null).supportsRole(GasketRole.TRANSMITTER));
        }

        @Test
        void getMachineLabel_returnsLabel() {
            assertEquals("my-vat", new VatStub(true, true, "my-vat").getMachineLabel(0));
        }

        @Test
        void getMachineLabel_nullWhenUnnamed() {
            assertNull(new VatStub(true, true, null).getMachineLabel(0));
        }
    }
}
