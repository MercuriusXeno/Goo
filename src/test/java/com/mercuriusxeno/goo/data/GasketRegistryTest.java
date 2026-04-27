package com.mercuriusxeno.goo.data;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for GasketRegistry pairing, inverse index, and location tracking.
 */
class GasketRegistryTest {

    @Nested
    class Link {

        /**
         * Linking two gaskets makes each findable from the other direction.
         */
        @Test
        void linkCreatesBidirectionalLookup() {
            GasketRegistry reg = new GasketRegistry();
            UUID out = UUID.randomUUID();
            UUID in = UUID.randomUUID();
            reg.link(out, in);
            assertEquals(in, reg.getTarget(out));
            assertEquals(out, reg.getSource(in));
        }

        /**
         * Linking replaces any prior pairing on either gasket.
         */
        @Test
        void linkReplacesExistingPairing() {
            GasketRegistry reg = new GasketRegistry();
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            reg.link(a, b);
            reg.link(a, c);
            assertEquals(c, reg.getTarget(a));
            assertNull(reg.getSource(b));
            assertEquals(a, reg.getSource(c));
        }

        /**
         * Linking an input that was already paired unlinks its old output.
         */
        @Test
        void linkEvictsOldOutputWhenInputReused() {
            GasketRegistry reg = new GasketRegistry();
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            reg.link(a, b);
            reg.link(c, b);
            assertNull(reg.getTarget(a));
            assertEquals(b, reg.getTarget(c));
            assertEquals(c, reg.getSource(b));
        }
    }

    @Nested
    class Unlink {

        /**
         * Unlinking by output removes both forward and reverse entries.
         */
        @Test
        void unlinkByOutputClearsBothMaps() {
            GasketRegistry reg = new GasketRegistry();
            UUID out = UUID.randomUUID();
            UUID in = UUID.randomUUID();
            reg.link(out, in);
            reg.unlink(out);
            assertNull(reg.getTarget(out));
            assertNull(reg.getSource(in));
        }

        /**
         * Unlinking by input removes both forward and reverse entries.
         */
        @Test
        void unlinkByInputClearsBothMaps() {
            GasketRegistry reg = new GasketRegistry();
            UUID out = UUID.randomUUID();
            UUID in = UUID.randomUUID();
            reg.link(out, in);
            reg.unlink(in);
            assertNull(reg.getTarget(out));
            assertNull(reg.getSource(in));
        }

        /**
         * Unlinking a UUID not in any pairing is a no-op.
         */
        @Test
        void unlinkUnknownIdIsNoOp() {
            GasketRegistry reg = new GasketRegistry();
            assertDoesNotThrow(() -> reg.unlink(UUID.randomUUID()));
        }
    }

    @Nested
    class InverseConsistency {

        /**
         * Forward and reverse maps stay consistent across multiple link/unlink cycles.
         */
        @Test
        void multipleOperationsKeepMapsConsistent() {
            GasketRegistry reg = new GasketRegistry();
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID c = UUID.randomUUID();
            UUID d = UUID.randomUUID();

            reg.link(a, b);
            reg.link(c, d);
            assertEquals(2, reg.getPairings().size());
            assertEquals(2, reg.getReversePairings().size());

            reg.unlink(a);
            assertEquals(1, reg.getPairings().size());
            assertEquals(1, reg.getReversePairings().size());
            assertEquals(d, reg.getTarget(c));
            assertEquals(c, reg.getSource(d));
        }

        /**
         * Deserialization constructor rebuilds the inverse index from forward pairings.
         */
        @Test
        void constructorRebuildsReverseFromForward() {
            UUID out = UUID.randomUUID();
            UUID in = UUID.randomUUID();
            Map<UUID, UUID> pairings = Map.of(out, in);
            GasketRegistry reg = new GasketRegistry(pairings, Map.of());
            assertEquals(out, reg.getSource(in));
            assertEquals(in, reg.getTarget(out));
        }
    }

    @Nested
    class Location {

        /**
         * Storing a location makes it retrievable by gasket ID.
         */
        @Test
        void updateLocationStoresAndRetrieves() {
            GasketRegistry reg = new GasketRegistry();
            UUID id = UUID.randomUUID();
            GasketLocation loc = mock(GasketLocation.class);
            reg.updateLocation(id, loc);
            assertSame(loc, reg.getLocation(id));
        }

        /**
         * Passing null removes the stored location.
         */
        @Test
        void updateLocationWithNullRemoves() {
            GasketRegistry reg = new GasketRegistry();
            UUID id = UUID.randomUUID();
            reg.updateLocation(id, mock(GasketLocation.class));
            reg.updateLocation(id, null);
            assertNull(reg.getLocation(id));
        }

        /**
         * getLocation returns null for unknown IDs.
         */
        @Test
        void getLocationReturnsNullForUnknown() {
            GasketRegistry reg = new GasketRegistry();
            assertNull(reg.getLocation(UUID.randomUUID()));
        }

        /**
         * Locations survive the deserialization constructor round-trip.
         */
        @Test
        void constructorPreservesLocations() {
            UUID id = UUID.randomUUID();
            GasketLocation loc = mock(GasketLocation.class);
            GasketRegistry reg = new GasketRegistry(Map.of(), Map.of(id, loc));
            assertSame(loc, reg.getLocation(id));
        }

        /**
         * getSource + getLocation together resolve a transmitter's position from the receiver side.
         */
        @Test
        void sourceLocationResolvableFromReceiver() {
            GasketRegistry reg = new GasketRegistry();
            UUID transmitter = UUID.randomUUID();
            UUID receiver = UUID.randomUUID();
            GasketLocation txLoc = mock(GasketLocation.class);
            reg.link(transmitter, receiver);
            reg.updateLocation(transmitter, txLoc);

            UUID sourceId = reg.getSource(receiver);
            assertNotNull(sourceId);
            assertSame(txLoc, reg.getLocation(sourceId));
        }
    }
}
