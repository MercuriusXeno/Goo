package com.xeno.goo.interactions;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.tags.ITag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;

public class Energetic
{
    private static final int WORST_HARVEST_LEVEL = 0;


    public static void registerInteractions()
    {
        GooInteractions.register(Registry.ENERGETIC_GOO.get(), "mining_blast", 0, Energetic::miningBlast);
    }

    private static boolean miningBlast(InteractionContext context)
    {
        if (!(context.world() instanceof ServerWorld)) {
            return false;
        }
        LootContext.Builder lootBuilder = new LootContext.Builder((ServerWorld) context.world());
        // in a radius centered around the block with a spherical distance of [configurable] or less
        // and a harvest level of wood (stone type blocks only) only
        // destroy blocks in the radius and yield full drops.
        double radius = GooMod.config.energeticMiningBlastRadius();
        List<BlockPos> blockPosList = blockPositionsByRadius(context.blockCenterVec(), context.blockPos(), radius);

        blockPosList.forEach((p) -> tryMiningBlast(p, context, lootBuilder));
        return true;
    }

    private static void tryMiningBlast(BlockPos blockPos, InteractionContext context, LootContext.Builder lootBuilder)
    {
        BlockState state = context.world().getBlockState(blockPos);
        Vector3d dropPos = new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ())
                .add(0.5d, 0.5d, 0.5d);
        if (state.getHarvestLevel() == WORST_HARVEST_LEVEL) {
            List<ItemStack> drops = state.getDrops(lootBuilder);
            drops.forEach((d) -> context.world().addEntity(
                    new ItemEntity(context.world(), dropPos.getX(), dropPos.getY(), dropPos.getZ(), d)
            ));
            context.world().removeBlock(blockPos, false);
        }
    }

    private static List<BlockPos> blockPositionsByRadius(Vector3d center, BlockPos blockPos, double radius)
    {
        int ceilingRadius = (int)Math.ceil(radius);
        List<BlockPos> result = new ArrayList<>();
        for(int x = -ceilingRadius; x <= ceilingRadius; x++) {
            for(int y = -ceilingRadius; y <= ceilingRadius; y++) {
                for(int z = -ceilingRadius; z <= ceilingRadius; z++) {
                    BlockPos match = new BlockPos(blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z);
                    Vector3d matchCenter = new Vector3d(match.getX(), match.getY(), match.getZ()).add(0.5d, 0.5d, 0.5d);
                    if (center.distanceTo(matchCenter) <= radius) {
                        result.add(match);
                    }
                }
            }
        }

        result.sort((bp1, bp2) -> vector3dComparator(blockPos, bp1, bp2));
        return result;
    }

    private static int vector3dComparator(BlockPos blockPos, BlockPos bp1, BlockPos bp2)
    {
        return Double.compare(blockPos.distanceSq(bp1), blockPos.distanceSq(bp2));
    }
}
