package com.mercuriusxeno.goo.item.gasket;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GasketRegionResolver: pure utility, no Minecraft dependencies.
 */
class GasketRegionResolverTest {

    @Test
    void vatRole_upFace_isReceiver() {
        assertEquals(GasketRole.RECEIVER,
            GasketRegionResolver.resolveVatRole(Direction.UP, 0.5));
    }

    @Test
    void vatRole_downFace_isTransmitter() {
        assertEquals(GasketRole.TRANSMITTER,
            GasketRegionResolver.resolveVatRole(Direction.DOWN, 0.5));
    }

    @Test
    void vatRole_sideHighY_isReceiver() {
        assertEquals(GasketRole.RECEIVER,
            GasketRegionResolver.resolveVatRole(Direction.NORTH, 0.75));
    }

    @Test
    void vatRole_sideLowY_isTransmitter() {
        assertEquals(GasketRole.TRANSMITTER,
            GasketRegionResolver.resolveVatRole(Direction.NORTH, 0.25));
    }

    @Test
    void vatRole_sideMidY_isReceiver() {
        assertEquals(GasketRole.RECEIVER,
            GasketRegionResolver.resolveVatRole(Direction.EAST, 0.5));
    }

    @Test
    void vatRole_sideJustBelowMid_isTransmitter() {
        assertEquals(GasketRole.TRANSMITTER,
            GasketRegionResolver.resolveVatRole(Direction.WEST, 0.49));
    }

    @Test
    void canisterSlotRole_topHalf_isReceiver() {
        assertEquals(GasketRole.RECEIVER,
            GasketRegionResolver.resolveCanisterSlotRole(0.75, 0.0, 1.0));
    }

    @Test
    void canisterSlotRole_bottomHalf_isTransmitter() {
        assertEquals(GasketRole.TRANSMITTER,
            GasketRegionResolver.resolveCanisterSlotRole(0.25, 0.0, 1.0));
    }

    @Test
    void canisterSlotRole_exactMid_isReceiver() {
        assertEquals(GasketRole.RECEIVER,
            GasketRegionResolver.resolveCanisterSlotRole(0.5, 0.0, 1.0));
    }

    @Test
    void canisterSlotRole_customRange() {
        assertEquals(GasketRole.TRANSMITTER,
            GasketRegionResolver.resolveCanisterSlotRole(0.3, 0.25, 0.75));
        assertEquals(GasketRole.RECEIVER,
            GasketRegionResolver.resolveCanisterSlotRole(0.6, 0.25, 0.75));
    }

    @Test
    void crucibleRole_alwaysTransmitter() {
        assertEquals(GasketRole.TRANSMITTER,
            GasketRegionResolver.resolveCrucibleRole());
    }
}
