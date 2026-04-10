package com.mercuriusxeno.goo.item.gasket;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GasketPartner record: field access, coordinate formatting,
 * and NO_SLOT sentinel.
 */
class GasketPartnerTest {

    @Test
    void constructorStoresFields() {
        BlockPos pos = new BlockPos(10, 64, -5);
        GasketPartner partner = new GasketPartner(pos, 3);
        assertEquals(pos, partner.pos());
        assertEquals(3, partner.slot());
    }

    @Test
    void noSlotSentinel_isNegativeOne() {
        assertEquals(-1, GasketPartner.NO_SLOT);
    }

    @Test
    void noSlot_forSingleGasketMachines() {
        GasketPartner partner = new GasketPartner(BlockPos.ZERO, GasketPartner.NO_SLOT);
        assertEquals(-1, partner.slot());
    }

    @Test
    void equality_sameFields() {
        BlockPos pos = new BlockPos(1, 2, 3);
        GasketPartner a = new GasketPartner(pos, 5);
        GasketPartner b = new GasketPartner(pos, 5);
        assertEquals(a, b);
    }

    @Test
    void equality_differentSlot() {
        BlockPos pos = new BlockPos(1, 2, 3);
        GasketPartner a = new GasketPartner(pos, 0);
        GasketPartner b = new GasketPartner(pos, 1);
        assertNotEquals(a, b);
    }

    @Test
    void equality_differentPos() {
        GasketPartner a = new GasketPartner(new BlockPos(1, 2, 3), 0);
        GasketPartner b = new GasketPartner(new BlockPos(4, 5, 6), 0);
        assertNotEquals(a, b);
    }
}
