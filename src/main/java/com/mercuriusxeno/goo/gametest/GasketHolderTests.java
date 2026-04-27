package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Gametests for {@link IGasketHolder} contracts on real block entities.
 * Replaces the stub-based JUnit tests that used StubHolder, VatStub,
 * CrucibleStub, and HubStub to avoid Minecraft class initialization.
 */
public final class GasketHolderTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final String CRUCIBLE_TRANSMITTER = "Crucible resolveRole should always return TRANSMITTER";
    private static final String NO_GASKET_TX = "Crucible without gasket should not support TRANSMITTER";
    private static final String NO_GASKET_RX = "Crucible without gasket should not support RECEIVER";
    private static final String WITH_GASKET_TX = "Crucible with gasket should support TRANSMITTER";
    private static final String VAT_CAP_RX = "Vat with cap should support RECEIVER";
    private static final String VAT_NO_BASE_TX = "Vat without base should not support TRANSMITTER";
    private static final String HUB_INTAKE = "Hub should always have intake";
    private static final String TUNING_NULL = "Default allowsTuning(null) should be true";
    private static final String TUNING_RANDOM = "Default allowsTuning(random) should be true";

    private GasketHolderTests() {
    }

    // --- Crucible ---

    /**
     * Crucible always resolves as TRANSMITTER regardless of hit location.
     *
     * @param helper the gametest helper
     */
    public static void crucibleResolveRoleAlwaysTransmitter(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        helper.assertTrue(be.resolveRole(null) == GasketRole.TRANSMITTER, CRUCIBLE_TRANSMITTER);
        helper.succeed();
    }

    /**
     * Crucible supportsRole returns false when HAS_GASKET blockstate is false.
     *
     * @param helper the gametest helper
     */
    public static void crucibleNoGasketUnsupported(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.HAS_GASKET, false));
        CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        helper.assertFalse(be.supportsRole(GasketRole.TRANSMITTER), NO_GASKET_TX);
        helper.assertFalse(be.supportsRole(GasketRole.RECEIVER), NO_GASKET_RX);
        helper.succeed();
    }

    /**
     * Crucible supportsRole returns true when HAS_GASKET blockstate is true.
     *
     * @param helper the gametest helper
     */
    public static void crucibleWithGasketSupported(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.HAS_GASKET, true));
        CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        helper.assertTrue(be.supportsRole(GasketRole.TRANSMITTER), WITH_GASKET_TX);
        helper.succeed();
    }

    // --- Vat ---

    /**
     * Vat supportsRole reflects GASKET_CAP and GASKET_BASE blockstate properties.
     *
     * @param helper the gametest helper
     */
    public static void vatSupportsRoleMatchesBlockstate(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.VAT.get().defaultBlockState()
                .setValue(VatBlock.GASKET_CAP, true)
                .setValue(VatBlock.GASKET_BASE, false));
        VatBlockEntity be = helper.getBlockEntity(BE_POS, VatBlockEntity.class);
        helper.assertTrue(be.supportsRole(GasketRole.RECEIVER), VAT_CAP_RX);
        helper.assertFalse(be.supportsRole(GasketRole.TRANSMITTER), VAT_NO_BASE_TX);
        helper.succeed();
    }

    // --- Hub ---

    /**
     * Hub always reports hasIntake() = true.
     *
     * @param helper the gametest helper
     */
    public static void hubHasIntake(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity be = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        helper.assertTrue(be.hasIntake(), HUB_INTAKE);
        helper.succeed();
    }

    // --- Default contract ---

    /**
     * IGasketHolder.allowsTuning defaults to true for all machines.
     * Verified on a crucible (no ownership model).
     *
     * @param helper the gametest helper
     */
    public static void defaultAllowsTuningIsTrue(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        helper.assertTrue(be.allowsTuning(null), TUNING_NULL);
        helper.assertTrue(be.allowsTuning(java.util.UUID.randomUUID()), TUNING_RANDOM);
        helper.succeed();
    }
}
