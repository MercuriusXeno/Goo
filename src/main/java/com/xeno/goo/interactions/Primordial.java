package com.xeno.goo.interactions;

import com.xeno.goo.GooMod;
import com.xeno.goo.datagen.GooTags.Entities;
import com.xeno.goo.effects.EggedEffect;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Primordial
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.PRIMORDIAL_GOO;
    private static final int bedrockHardness = -1;
    private static final int diamondHarvestLevel = 3;
    private static final float particleChance = 0.33f;
    private static final ItemStack mockPick = new ItemStack(Items.DIAMOND_PICKAXE, 1);
    static {
        mockPick.addEnchantment(Enchantments.SILK_TOUCH, 1);
    }

    public static void registerInteractions()
    {
        GooInteractions.registerSplat(fluidSupplier.get(), "silk_touch_blast", Primordial::silkTouchBlast, (context) -> true);

        GooInteractions.registerBlobHit(fluidSupplier.get(), "primordial_hit", Primordial::entityHit);
    }

    private static boolean entityHit(BlobHitContext c) {
        boolean isImmuneToInstantDeath = Entities.PRIMORDIAL_INSTANT_DEATH_IMMUNE_MOBS.contains(c.victim().getType());
        boolean isImmuneToEgging = !Entities.PRIMORDIAL_SPAWN_EGGS_ALLOWED.contains(c.victim().getType());
        if (isImmuneToEgging && isImmuneToInstantDeath) {
            return false;
        }
        if (!isImmuneToEgging) {
            c.victim().addPotionEffect(new EffectInstance(EggedEffect.instance, 600));
            return true;
        }
        return reduceToDust(c.owner(), c.victim());
    }

    // this is what happens if reduce to egg does nothing
    private static boolean reduceToDust(LivingEntity attackingEntity, LivingEntity victim) {
        if (attackingEntity instanceof PlayerEntity) {
            victim.setAttackingPlayer((PlayerEntity) attackingEntity);
        }
        victim.setHealth(0f);
        return true;
    }

    private static boolean silkTouchBlast(SplatContext context)
    {
        // in a radius centered around the block with a spherical distance of [configurable] or less
        // and a harvest level of wood (stone type blocks only) only
        // destroy blocks in the radius and yield full drops.
        // double radius = GooMod.config.energeticMiningBlastRadius();
        int radius = GooMod.config.primordialSilkTouchBlastDistance();
        List<BlockPos> blockPosList = blockPositionsByCuboid(context.blockPos(), radius);

        blockPosList.forEach((p) -> trySilkTouchBlast(p, context));
        // it's possible for the explosion to not *do* anything, and it looks really bizarre when there's no visual.
        // always hit at least one visual here.
        ((ServerWorld)context.world()).spawnParticle(ParticleTypes.EXPLOSION, context.blockCenterVec().x,
                context.blockCenterVec().y, context.blockCenterVec().z, 1, 0d, 0d, 0d, 0d);
        AudioHelper.headlessAudioEvent(context.world(), context.blockPos(), Registry.PRIMORDIAL_WARP_SOUND.get(), SoundCategory.BLOCKS,
                3.0f, AudioHelper.PitchFormulas.HalfToOne);

        return true;
    }

    private static void trySilkTouchBlast(BlockPos blockPos, SplatContext context)
    {
        BlockState state = context.world().getBlockState(blockPos);
        // we don't break blocks with tile entities, sorry not sorry
        if (state.getBlock().hasTileEntity(state)) {
            return;
        }
        Vector3d dropPos = Vector3d.copy(blockPos).add(0.5d, 0.5d, 0.5d);

        if (isExplosionOccluded(dropPos, blockPos, state, context)) {
            return;
        }

        if (state.getHarvestLevel() <= diamondHarvestLevel && state.getBlockHardness(context.world(), blockPos) != bedrockHardness) {
            if ((context.world() instanceof ServerWorld)) {
                // figure out drops for this block
                LootContext.Builder lootBuilder = new LootContext.Builder((ServerWorld) context.world());
                List<ItemStack> drops = state.getDrops(lootBuilder
                        .withParameter(LootParameters.ORIGIN, context.blockCenterVec())
                        .withParameter(LootParameters.TOOL, mockPick)
                );
                // throttle particles to look a bit less dense.
                // spawning roughly a 1/3 chance
                if (context.world().rand.nextFloat() <= particleChance) {
                    ((ServerWorld) context.world()).spawnParticle(ParticleTypes.EXPLOSION, dropPos.x, dropPos.y, dropPos.z, 1, 0d, 0d, 0d, 0d);
                }
                drops.forEach((d) -> context.world().addEntity(
                        new ItemEntity(context.world(), dropPos.getX(), dropPos.getY(), dropPos.getZ(), d)
                ));
                context.world().removeBlock(blockPos, false);
            }
        }
    }

    private static boolean isExplosionOccluded(Vector3d dropPos, BlockPos blockPos, BlockState state, SplatContext context) {
        // now also draw a line between the context center and the block position center. If it intersects *ANYTHING* abort.
        // context center in this case is the block we're touching, not the block we exist in.
        // if this changes, the block sorting function needs to match whatever this is doing.
        RayTraceContext rtc = new RayTraceContext(context.blockCenterVec(), dropPos, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.ANY, null);
        BlockRayTraceResult result = context.world().rayTraceBlocks(rtc);
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
