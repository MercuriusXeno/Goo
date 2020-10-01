package com.xeno.goo.interactions;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.OreBlock;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;

public class Energetic
{
    // private static final int WORST_HARVEST_LEVEL = 0;
    private static final int BEDROCK_HARDNESS = -1;
    private static final int IRON_HARVEST_LEVEL = 2;


    public static void registerInteractions()
    {
        GooInteractions.register(Registry.ENERGETIC_GOO.get(), "mining_blast", 0, Energetic::miningBlast);
    }

    private static boolean miningBlast(InteractionContext context)
    {
        // in a radius centered around the block with a spherical distance of [configurable] or less
        // and a harvest level of wood (stone type blocks only) only
        // destroy blocks in the radius and yield full drops.
        double radius = GooMod.config.energeticMiningBlastRadius();
        List<BlockPos> blockPosList = blockPositionsByRadius(context.blockCenterVec(), context.blockPos(), radius);

        blockPosList.forEach((p) -> tryMiningBlast(p, context));
        AudioHelper.headlessAudioEvent(context.world(), context.blockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS,
                3.0f, AudioHelper.PitchFormulas.HalfToOne);

        return true;
    }

    private static void tryMiningBlast(BlockPos blockPos, InteractionContext context)
    {
        BlockState state = context.world().getBlockState(blockPos);
        // we don't break blocks with tile entities, sorry not sorry
        if (state.getBlock().hasTileEntity(state)) {
            return;
        }
        Vector3d dropPos = Vector3d.copy(blockPos).add(0.5d, 0.5d, 0.5d);
        // now also draw a line between the context center and the block position center. If it intersects *ANYTHING* abort.
        // the force is blocked. True center here is because the block we're attached to is obviously solid.
        Vector3d trueCenter = context.blockCenterVec().add(Vector3d.copy(context.sideHit().getDirectionVec()));
        RayTraceContext rtc = new RayTraceContext(trueCenter, dropPos, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.ANY, null);
        BlockRayTraceResult result = context.world().rayTraceBlocks(rtc);
        if (result.getType() != RayTraceResult.Type.MISS) {
            // we intersected with a block that isn't the one we're trying to break
            if (!result.getPos().equals(blockPos)) {
                return;
            }
        }
        if (state.getHarvestLevel() <= IRON_HARVEST_LEVEL && state.getBlockHardness(context.world(), blockPos) != BEDROCK_HARDNESS) {
            if ((context.world() instanceof ServerWorld)) {
                // figure out drops for this block
                LootContext.Builder lootBuilder = new LootContext.Builder((ServerWorld) context.world());
                List<ItemStack> drops = state.getDrops(lootBuilder
                        .withParameter(LootParameters.POSITION, blockPos)
                        .withParameter(LootParameters.TOOL, ItemStack.EMPTY)
                );
                // if the drops don't resemble the block, we presume there's some fortune potential and refuse to break it
                // the point of this is to make it so that we only break things that return their respective block.
                // Diamonds, coal, lapis, emeralds, glowstone dust and other goodies will be left for fortune.
                if (!validDropsForMiningBlast(drops, state)) {
                    return;
                }
                ((ServerWorld)context.world()).spawnParticle(ParticleTypes.EXPLOSION, dropPos.x, dropPos.y, dropPos.z, 1, 0d, 0d, 0d, 0d);
                drops.forEach((d) -> context.world().addEntity(
                        new ItemEntity(context.world(), dropPos.getX(), dropPos.getY(), dropPos.getZ(), d)
                ));
                context.world().removeBlock(blockPos, false);
            }
        }
    }

    private static boolean validDropsForMiningBlast(List<ItemStack> drops, BlockState state)
    {
        // singleton drops indicate a "normal" block drop. Anything else we reject.
        if (drops.size() != 1) {
            return false;
        }
        // now analyze our one drop. Is it a block? If it's a block, we're cool with it.
        // but we reject "item" drops on the off chance it could benefit from fortune.
        if (drops.get(0).getItem() instanceof BlockItem) {
            return true;
        }

        return false;
    }

    private static List<BlockPos> blockPositionsByRadius(Vector3d center, BlockPos blockPos, double radius)
    {
        int ceilingRadius = (int)Math.ceil(radius);
        List<BlockPos> result = new ArrayList<>();
        for(int x = -ceilingRadius; x <= ceilingRadius; x++) {
            for(int y = -ceilingRadius; y <= ceilingRadius; y++) {
                for(int z = -ceilingRadius; z <= ceilingRadius; z++) {
                    BlockPos match = blockPos.add(x, y, z);
                    Vector3d matchCenter = Vector3d.copy(match).add(0.5d, 0.5d, 0.5d);
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
