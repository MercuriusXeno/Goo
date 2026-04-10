package com.mercuriusxeno.goo.item;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CanisterMetadata record: label, gasket IDs,
 * partner references, and hasData boundary conditions.
 */
class CanisterMetadataTest {

    // -- label --

    @Test
    void empty_hasNullLabel() {
        assertNull(CanisterMetadata.EMPTY.label());
    }

    @Test
    void withLabel_setsLabel() {
        CanisterMetadata meta = CanisterMetadata.EMPTY.withLabel("My Canister");
        assertEquals("My Canister", meta.label());
    }

    @Test
    void withLabel_null_clearsLabel() {
        CanisterMetadata meta = CanisterMetadata.EMPTY
            .withLabel("Test")
            .withLabel(null);
        assertNull(meta.label());
    }

    // -- gasket IDs --

    @Test
    void withGasketIds_generatesBothIds() {
        CanisterMetadata meta = CanisterMetadata.EMPTY.withGasketIds();
        assertNotNull(meta.topGasketId());
        assertNotNull(meta.bottomGasketId());
    }

    @Test
    void withGasketIds_preservesLabel() {
        CanisterMetadata meta = new CanisterMetadata(
            null, null, "Gasket Test", null, null);
        CanisterMetadata withGaskets = meta.withGasketIds();
        assertEquals("Gasket Test", withGaskets.label());
    }

    @Test
    void withTopGasketId_setsTopOnly() {
        UUID id = UUID.randomUUID();
        CanisterMetadata meta = CanisterMetadata.EMPTY.withTopGasketId(id);
        assertEquals(id, meta.topGasketId());
        assertNull(meta.bottomGasketId());
    }

    @Test
    void withBottomGasketId_setsBottomOnly() {
        UUID id = UUID.randomUUID();
        CanisterMetadata meta = CanisterMetadata.EMPTY.withBottomGasketId(id);
        assertNull(meta.topGasketId());
        assertEquals(id, meta.bottomGasketId());
    }

    // -- partners --

    @Test
    void withTopPartner_setsPartner() {
        GasketPartner partner = new GasketPartner(new BlockPos(1, 2, 3), 0);
        CanisterMetadata meta = CanisterMetadata.EMPTY.withTopPartner(partner);
        assertEquals(partner, meta.topPartner());
        assertNull(meta.bottomPartner());
    }

    @Test
    void withBottomPartner_setsPartner() {
        GasketPartner partner = new GasketPartner(new BlockPos(4, 5, 6), 1);
        CanisterMetadata meta = CanisterMetadata.EMPTY.withBottomPartner(partner);
        assertNull(meta.topPartner());
        assertEquals(partner, meta.bottomPartner());
    }

    @Test
    void withTopPartner_null_clearsPartner() {
        GasketPartner partner = new GasketPartner(new BlockPos(1, 2, 3), 0);
        CanisterMetadata meta = CanisterMetadata.EMPTY
            .withTopPartner(partner)
            .withTopPartner(null);
        assertNull(meta.topPartner());
    }

    @Test
    void withPartners_preserveOtherFields() {
        GasketPartner partner = new GasketPartner(new BlockPos(1, 2, 3), 0);
        CanisterMetadata meta = CanisterMetadata.EMPTY
            .withLabel("Test")
            .withTopPartner(partner);
        assertEquals("Test", meta.label());
        assertEquals(partner, meta.topPartner());
    }

    // -- gasket removal --

    @Test
    void empty_hasNullGasketIds() {
        assertNull(CanisterMetadata.EMPTY.topGasketId());
        assertNull(CanisterMetadata.EMPTY.bottomGasketId());
    }

    @Test
    void withoutTopGasket_clearsTopIdAndPartner() {
        GasketPartner partner = new GasketPartner(new BlockPos(1, 2, 3), 0);
        CanisterMetadata meta = CanisterMetadata.EMPTY
            .withTopGasketId(UUID.randomUUID())
            .withTopPartner(partner)
            .withBottomGasketId(UUID.randomUUID());
        CanisterMetadata cleared = meta.withoutTopGasket();
        assertNull(cleared.topGasketId());
        assertNull(cleared.topPartner());
        assertNotNull(cleared.bottomGasketId());
    }

    @Test
    void withoutBottomGasket_clearsBottomIdAndPartner() {
        GasketPartner partner = new GasketPartner(new BlockPos(4, 5, 6), 1);
        CanisterMetadata meta = CanisterMetadata.EMPTY
            .withTopGasketId(UUID.randomUUID())
            .withBottomGasketId(UUID.randomUUID())
            .withBottomPartner(partner);
        CanisterMetadata cleared = meta.withoutBottomGasket();
        assertNotNull(cleared.topGasketId());
        assertNull(cleared.bottomGasketId());
        assertNull(cleared.bottomPartner());
    }

    @Test
    void withoutTopGasket_preservesOtherFields() {
        CanisterMetadata meta = CanisterMetadata.EMPTY
            .withLabel("Keep")
            .withTopGasketId(UUID.randomUUID());
        CanisterMetadata cleared = meta.withoutTopGasket();
        assertEquals("Keep", cleared.label());
    }

    // -- hasData --

    @Test
    void hasData_empty_returnsFalse() {
        assertFalse(CanisterMetadata.EMPTY.hasData());
    }

    @Test
    void hasData_withLabelOnly_returnsTrue() {
        CanisterMetadata meta = new CanisterMetadata(
            null, null, "Named", null, null);
        assertTrue(meta.hasData());
    }

    @Test
    void hasData_withGasketOnly_returnsTrue() {
        CanisterMetadata meta = new CanisterMetadata(
            UUID.randomUUID(), null, null, null, null);
        assertTrue(meta.hasData());
    }

    @Test
    void hasData_withPartnerOnly_returnsTrue() {
        GasketPartner partner = new GasketPartner(new BlockPos(1, 2, 3), 0);
        CanisterMetadata meta = new CanisterMetadata(
            null, null, null, partner, null);
        assertTrue(meta.hasData());
    }

    @Test
    void hasData_allDefaults_returnsFalse() {
        CanisterMetadata meta = new CanisterMetadata(
            null, null, null, null, null);
        assertFalse(meta.hasData());
    }
}
