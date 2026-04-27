package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Gametests for {@link com.mercuriusxeno.goo.block.gasket.GasketPusher} tick guard
 * and dispose behavior on real block entities. Replaces the supplier-stubbed
 * JUnit tests that needed StubFluidHandler to avoid Minecraft bootstrap.
 */
public final class GasketPusherTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final int IDLE_TICKS = 25;
    private static final int SETTLE_TICKS = 2;
    private static final String EMPTY_AFTER_IDLE = "Crucible reservoir should still be empty after idle ticks";
    private static final String NO_CRASH_NO_PARTNER = "Crucible should not crash or produce goo without a partner";

    private GasketPusherTests() {}

    /**
     * An empty crucible (no items, no goo) should tick its pusher without
     * syncing or crashing. Exercises the tick guard's "source empty" path.
     *
     * @param helper the gametest helper
     */
    public static void emptyReservoirSkipsTick(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
            .setValue(CrucibleBlock.HAS_GASKET, true));
        helper.runAfterDelay(IDLE_TICKS, () -> {
            CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
            helper.assertTrue(be.reservoirHandler().isEmpty(), EMPTY_AFTER_IDLE);
            helper.succeed();
        });
    }

    /**
     * A crucible with a gasket but no partner should tick safely.
     * Exercises the "null partner" guard path.
     *
     * @param helper the gametest helper
     */
    public static void noPartnerSkipsTick(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
            .setValue(CrucibleBlock.HAS_GASKET, true));
        CrucibleBlockEntity be = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        be.gasketState().ensureId(GasketRole.TRANSMITTER, () -> {});
        helper.runAfterDelay(IDLE_TICKS, () -> {
            helper.assertTrue(be.reservoirHandler().isEmpty(), NO_CRASH_NO_PARTNER);
            helper.succeed();
        });
    }

    /**
     * Breaking a crucible that has a gasket and then ticking the world should
     * not throw. Exercises the real-world dispose path (setRemoved lifecycle).
     *
     * @param helper the gametest helper
     */
    public static void disposeAndTickIsSafe(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
            .setValue(CrucibleBlock.HAS_GASKET, true));
        helper.runAfterDelay(1, () -> {
            helper.destroyBlock(BE_POS);
            helper.runAfterDelay(SETTLE_TICKS, helper::succeed);
        });
    }

    /**
     * Breaking a crucible, re-placing it, and breaking it again should not throw.
     * Double-lifecycle on the same position.
     *
     * @param helper the gametest helper
     */
    public static void doubleDisposeIsSafe(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
            .setValue(CrucibleBlock.HAS_GASKET, true));
        helper.runAfterDelay(1, () -> {
            helper.destroyBlock(BE_POS);
            helper.runAfterDelay(1, () -> {
                helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get().defaultBlockState()
                    .setValue(CrucibleBlock.HAS_GASKET, true));
                helper.runAfterDelay(1, () -> {
                    helper.destroyBlock(BE_POS);
                    helper.runAfterDelay(SETTLE_TICKS, helper::succeed);
                });
            });
        });
    }
}
