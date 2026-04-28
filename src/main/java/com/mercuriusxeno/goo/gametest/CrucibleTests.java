package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for crucible absorption and insertion paths.
 * Exercises CrucibleAbsorption (item entity intake) and
 * CrucibleInsertion (blob right-click insertion).
 */
public final class CrucibleTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final int ABSORB_DELAY = 5;
    /** X/Z center of the crucible basin in test-relative coords. */
    private static final float BASIN_CENTER_XZ = 1.5f;
    /** Y position just above the crucible body surface (13/16 + block y=1). */
    private static final float BASIN_SURFACE_Y = 1.85f;
    private static final String SHOULD_HAVE_GOO = "Crucible reservoir should contain goo after blob insert";
    private static final String SHOULD_ABSORB = "Crucible should absorb the item entity";

    private CrucibleTests() {}

    /**
     * Right-click a crucible with a rock blob inserts goo into the reservoir.
     * Exercises CrucibleInteraction.tryInsertBlob -> CrucibleInsertion.insertGoo.
     *
     * @param helper the gametest helper
     */
    public static void blobInsertViaInteraction(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(GooItems.BLOBS.get(GooType.ROCK).get()));

        BlockPos abs = helper.absolutePos(BE_POS);
        BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(abs), Direction.UP, abs, false);
        helper.useBlock(BE_POS, player, hit);

        helper.assertFalse(crucible.reservoirHandler().isEmpty(), SHOULD_HAVE_GOO);
        helper.succeed();
    }

    /**
     * Dropping an item into a fueled crucible absorbs it via entityInside.
     * Exercises CrucibleAbsorption.tryAbsorbItem -> CrucibleInsertion.insertItem.
     * Requires fuel and the crucible must not be redstone-powered.
     *
     * @param helper the gametest helper
     */
    public static void itemEntityAbsorption(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        crucible.addFuel(new ItemStack(Items.BLAZE_ROD));

        // Spawn inside the basin (center of block, just above the body surface)
        helper.spawnItem(Items.COBBLESTONE, BASIN_CENTER_XZ, BASIN_SURFACE_Y, BASIN_CENTER_XZ);

        helper.runAfterDelay(ABSORB_DELAY, () -> {
            helper.assertFalse(crucible.reservoirHandler().isEmpty(), SHOULD_ABSORB);
            helper.succeed();
        });
    }
}
