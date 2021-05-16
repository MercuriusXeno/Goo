package com.xeno.goo.interactions;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Energetic
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.ENERGETIC_GOO;
    private static final int bedrockHardness = -1;
    private static final int ironHarvestLevel = 2;
    private static final float particleChance = 0.33f;
    private static final ItemStack mockPick = new ItemStack(Items.IRON_PICKAXE, 1);

    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.ENERGETIC_GOO.get(), "mining_blast", Energetic::miningBlast, (context) -> true);

        GooInteractions.registerBlobHit(Registry.ENERGETIC_GOO.get(), "energetic_hit", Energetic::hitEntity);
    }

    private static boolean hitEntity(BlobHitContext c) {
        // note here using victim center as the block center, the origin of the blast, makes a bit of sense when
        // doing entity collisions. We're making the entity we hit explode, in a sense.
        return miningBlast(c.blob().getPosition(), c.world(), c.victimCenterVec(), c.owner());
    }

    private static boolean miningBlast(SplatContext c)
    {
        return miningBlast(c.blockPos(), c.world(), c.blockCenterVec(), c.owner());
    }

    private static DamageSource mobDamage(LivingEntity owner) {
        return DamageSource.causeMobDamage(owner);
    }

    private static DamageSource damageSource(LivingEntity owner) {
        if (owner == null) {
            return DamageSource.GENERIC;
        }
        if (owner instanceof PlayerEntity) {
            return DamageSource.causePlayerDamage((PlayerEntity)owner);
        }
        return mobDamage(owner);
    }


    private static boolean miningBlast(BlockPos blockPos, World world, Vector3d blockCenter, LivingEntity owner) {

        miningBlastOnBlocks(blockPos, world, blockCenter);
        miningBlastOnEntities(blockCenter, world, owner);

        doMinimumParticleAndAudio(blockPos, world, blockCenter);

        return true;
    }

    private static void miningBlastOnEntities(Vector3d gooCenter, World world, LivingEntity owner) {
        int radius = GooMod.config.energeticMiningBlastDistance();
        Vector3d cubeMin = gooCenter.subtract(radius, radius, radius);
        Vector3d cubeMax = gooCenter.add(radius, radius, radius);
        AxisAlignedBB bb = new AxisAlignedBB(cubeMin, cubeMax);
        List<LivingEntity> entitiesInBounds = world.getEntitiesWithinAABB(LivingEntity.class, bb, null);

        entitiesInBounds.forEach(l -> hurtEntity(l, radius, gooCenter, owner));
    }

    public static double getDistanceSq(Vector3d targetCenter, double targetSize, Vector3d vec) {

        double d0 = targetCenter.x - vec.x;
        double d1 = targetCenter.y - vec.y;
        double d2 = targetCenter.z - vec.z;
        return (d0 * d0 + d1 * d1 + d2 * d2) - (targetSize * targetSize);
    }

    private static void hurtEntity(LivingEntity livingEntity, int radius, Vector3d gooCenter, LivingEntity owner) {
        // test distance and abort if distance breaks threshold
        double radiusSq = radius * radius;
        AxisAlignedBB bb = livingEntity.getBoundingBox();
        double volume = bb.getXSize() * bb.getYSize() * bb.getZSize();
        double emulatedEntityRadius = Math.pow(volume, 1d / 3d);
        double distanceSq = getDistanceSq(livingEntity.getBoundingBox().getCenter(), emulatedEntityRadius, gooCenter);
        if (distanceSq > radiusSq) {
            return;
        }
        double damageFactor = Math.min(1d, ((radius - distanceSq) + 1d) / radius);
        float baseDamage = 5f;
        float damage = (float)Math.ceil(damageFactor * baseDamage);

        livingEntity.attackEntityFrom(damageSource(owner), damage);
    }

    private static void doMinimumParticleAndAudio(BlockPos pos, World world, Vector3d vec) {
        // it's possible for the explosion to not *do* anything, and it looks really bizarre when there's no visual.
        // always hit at least one visual here.
        if (world instanceof ServerWorld) {
            ((ServerWorld) world).spawnParticle(ParticleTypes.EXPLOSION, vec.x, vec.y, vec.z,
                    1, 0d, 0d, 0d, 0d);
        }
        AudioHelper.headlessAudioEvent(world, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS,
                3.0f, AudioHelper.PitchFormulas.HalfToOne);
    }

    private static void miningBlastOnBlocks(BlockPos blockPos, World world, Vector3d blockCenterVec ) {

        int radius = GooMod.config.energeticMiningBlastDistance();
        List<BlockPos> blockPosList = blockPositionsByCuboid(blockPos, radius);
        blockPosList.forEach((p) -> tryMiningBlast(p, world, blockCenterVec));
    }

    private static void tryMiningBlast(BlockPos blockPos, World world, Vector3d blockCenterVec)
    {
        BlockState state = world.getBlockState(blockPos);
        // we don't break blocks with tile entities, sorry not sorry
        if (state.getBlock().hasTileEntity(state)) {
            return;
        }
        Vector3d dropPos = Vector3d.copy(blockPos).add(0.5d, 0.5d, 0.5d);

        if (isExplosionOccluded(dropPos, blockPos, state, world, blockCenterVec)) {
            return;
        }

        if (state.getHarvestLevel() <= ironHarvestLevel && state.getBlockHardness(world, blockPos) != bedrockHardness) {
            if ((world instanceof ServerWorld)) {
                // figure out drops for this block
                LootContext.Builder lootBuilder = new LootContext.Builder((ServerWorld)world);
                List<ItemStack> drops = state.getDrops(lootBuilder
                        .withParameter(LootParameters.ORIGIN, blockCenterVec)
                        .withParameter(LootParameters.TOOL, mockPick)
                );
                // throttle particles to look a bit less dense.
                // spawning roughly a 1/3 chance
                if (world.rand.nextFloat() <= particleChance) {
                    ((ServerWorld) world).spawnParticle(ParticleTypes.EXPLOSION, dropPos.x, dropPos.y, dropPos.z, 1, 0d, 0d, 0d, 0d);
                }
                drops.forEach((d) -> world.addEntity(
                        new ItemEntity(world, dropPos.getX(), dropPos.getY(), dropPos.getZ(), d)
                ));
                world.removeBlock(blockPos, false);
            }
        }
    }

    private static boolean isExplosionOccluded(Vector3d dropPos, BlockPos blockPos, BlockState state,
            World world, Vector3d blockCenterVec) {
        // now also draw a line between the context center and the block position center. If it intersects *ANYTHING* abort.
        // context center in this case is the block we're touching, not the block we exist in.
        // if this changes, the block sorting function needs to match whatever this is doing.
        RayTraceContext rtc = new RayTraceContext(blockCenterVec, dropPos, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.ANY, null);
        BlockRayTraceResult result = world.rayTraceBlocks(rtc);
        if (result.getType() != RayTraceResult.Type.MISS) {
            // we intersected with a block that isn't the one we're trying to break
            if (!result.getPos().equals(blockPos)) {
                return state.getMaterial().blocksMovement() && !(state.getBlock() instanceof LeavesBlock);
            }
        }

        return false;
    }

    private static List<BlockPos> blockPositionsByCuboid(BlockPos blockPos, int radius)
    {
        List<BlockPos> result = new ArrayList<>();
        for(int x = -radius; x <= radius; x++) {
            for(int y = -radius; y <= radius; y++) {
                for(int z = -radius; z <= radius; z++) {
                    result.add(blockPos.add(x, y, z));
                }
            }
        }

        // manhattan distance sort to avoid doing a biased center check
        result.sort((bp1, bp2) -> compareManhattanDistance(blockPos, bp1, bp2));
        return result;
    }

    private static int compareManhattanDistance(BlockPos blockPos, BlockPos bp1, BlockPos bp2)
    {
        return Integer.compare(blockPos.manhattanDistance(bp1), blockPos.manhattanDistance(bp2));
    }
}
