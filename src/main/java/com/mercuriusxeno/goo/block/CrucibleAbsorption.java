package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooBlobItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.List;

/**
 * Static helpers for item entity absorption into the crucible basin.
 * Handles blobs, PMIs, containers, and regular meltable items that
 * fall or are thrown into the goocible.
 */
final class CrucibleAbsorption {

    private CrucibleAbsorption() { }

    /** Attempts to absorb a single item entity into the crucible's melting pool.
     *
     * @param itemEntity the item entity to absorb
     * @param crucible   the crucible block entity
     */
    static void tryAbsorbItem(ItemEntity itemEntity, CrucibleBlockEntity crucible) {
        ItemStack stack = itemEntity.getItem();
        boolean isGooBlob = stack.getItem() instanceof GooBlobItem
            || stack.getItem() instanceof GooOmniblobItem;
        if (isGooBlob) {
            absorbBlob(itemEntity, stack, crucible);
        } else if (stack.getItem() instanceof PartiallyMeltedItem) {
            absorbPMI(itemEntity, stack, crucible);
        } else if (crucible.containerEvaluator.isContainer(stack)) {
            absorbContainer(itemEntity, stack, crucible);
        } else {
            absorbMeltable(itemEntity, stack, crucible);
        }
    }

    /** Re-inserts a dropped PMI's remaining goo directly into the melt pool.
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbPMI(ItemEntity entity, ItemStack stack,
            CrucibleBlockEntity crucible) {
        GooContents contents = PartiallyMeltedItem.getContents(stack);
        if (contents.isEmpty()) { return; }
        CrucibleInsertion.mergeIntoPool(crucible, contents);
        crucible.syncToClients();
        entity.discard();
        spawnMeltEffects(entity.level(), crucible);
    }

    /** Inserts a blob or omniblob directly into the reservoir, bypassing the melt pipeline.
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbBlob(ItemEntity entity, ItemStack stack,
            CrucibleBlockEntity crucible) {
        GooType type = BlobStacks.gooTypeOf(stack);
        long volume = BlobStacks.volumeOf(stack);
        if (type == null || volume <= 0) { return; }
        crucible.insertGoo(type, volume);
        entity.discard();
        spawnMeltEffects(entity.level(), crucible);
    }

    /** Inserts all items in the stack into the PMI pool as one pooled merge.
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbMeltable(ItemEntity entity, ItemStack stack,
            CrucibleBlockEntity crucible) {
        if (CrucibleInsertion.insertItem(crucible, stack, stack.getCount())) {
            entity.discard();
            spawnMeltEffects(entity.level(), crucible);
        }
    }

    /**
     * Evaluates a container's contents recursively, merges goo values into
     * the melt pool, and ejects items without goo values as item entities.
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbContainer(ItemEntity entity, ItemStack stack,
            CrucibleBlockEntity crucible) {
        List<ItemStack> ejects = CrucibleInsertion.insertContainer(crucible, stack);
        if (ejects == null) { return; }
        entity.discard();
        spawnEjectedItems(entity.level(), crucible.getBlockPos(), ejects);
        spawnMeltEffects(entity.level(), crucible);
    }

    /** Spawns ejected items as item entities above the crucible basin.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param ejects the list of items to eject
     */
    static void spawnEjectedItems(Level level, BlockPos pos,
            List<ItemStack> ejects) {
        for (ItemStack eject : ejects) {
            Block.popResource(level, pos.above(), eject);
        }
    }

    /** Spawns smoke particles and plays a sizzle sound when an item is absorbed.
     *
     * @param level    the current level
     * @param crucible the crucible block entity
     */
    static void spawnMeltEffects(Level level, CrucibleBlockEntity crucible) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        BlockPos pos = crucible.getBlockPos();
        CrucibleParticleHelper.spawnMeltSmoke(serverLevel, pos);
        if (crucible.shouldPlaySizzle(level.getGameTime())) {
            CrucibleParticleHelper.playSizzle(serverLevel, pos);
        }
    }
}
