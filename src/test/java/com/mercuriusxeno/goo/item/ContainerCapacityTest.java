package com.mercuriusxeno.goo.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ContainerCapacity constants and formulas.
 * Verifies capacity at each matrix level for canisters and vats.
 */
class ContainerCapacityTest {

    // -- constants --

    @Test
    void canisterBaseIs1048576() {
        assertEquals(1_048_576L, ContainerCapacity.CANISTER_BASE);
    }

    @Test
    void vatBaseIs33554432() {
        assertEquals(33_554_432L, ContainerCapacity.VAT_BASE);
    }

    @Test
    void bucketCapIs1000() {
        assertEquals(1_000L, ContainerCapacity.BUCKET_CAP);
    }

    @Test
    void blobCapIs64000() {
        assertEquals(64_000L, ContainerCapacity.BLOB_CAP);
    }

    // -- canister capacity at each matrix level --

    @Test
    void canisterCapacity_0matrices() {
        assertEquals(1_048_576L, ContainerCapacity.canisterCapacity(0));
    }

    @Test
    void canisterCapacity_1matrix() {
        assertEquals(2_097_152L, ContainerCapacity.canisterCapacity(1));
    }

    @Test
    void canisterCapacity_2matrices() {
        assertEquals(4_194_304L, ContainerCapacity.canisterCapacity(2));
    }

    @Test
    void canisterCapacity_3matrices() {
        assertEquals(8_388_608L, ContainerCapacity.canisterCapacity(3));
    }

    @Test
    void canisterCapacity_4matrices() {
        assertEquals(16_777_216L, ContainerCapacity.canisterCapacity(4));
    }

    @Test
    void canisterCapacity_5matrices() {
        assertEquals(33_554_432L, ContainerCapacity.canisterCapacity(5));
    }

    @Test
    void canisterCapacity_clampsBeyondMax() {
        assertEquals(ContainerCapacity.canisterCapacity(5),
                     ContainerCapacity.canisterCapacity(10));
    }

    // -- vat capacity at each compression level --

    @Test
    void vatCapacity_0compression() {
        assertEquals(33_554_432L, ContainerCapacity.vatCapacity(0)); // 2^25
    }

    @Test
    void vatCapacity_1compression() {
        assertEquals(67_108_864L, ContainerCapacity.vatCapacity(1)); // 2^26
    }

    @Test
    void vatCapacity_5compression() {
        assertEquals(1_073_741_824L, ContainerCapacity.vatCapacity(5)); // 2^30
    }

    @Test
    void vatCapacity_clampsBeyondMax() {
        assertEquals(ContainerCapacity.vatCapacity(5),
                     ContainerCapacity.vatCapacity(10));
    }

    // -- negative clamping --

    @Test
    void canisterCapacity_negativeClamps() {
        assertEquals(ContainerCapacity.canisterCapacity(0),
                     ContainerCapacity.canisterCapacity(-1));
    }

    @Test
    void vatCapacity_negativeClamps() {
        assertEquals(ContainerCapacity.vatCapacity(0),
                     ContainerCapacity.vatCapacity(-1));
    }
}
