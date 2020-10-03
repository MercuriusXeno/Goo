package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BoneMealItem;
import net.minecraft.state.BooleanProperty;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.server.ServerWorld;

public class Floral
{
    private static final float FLOURISH_CHANCE = 0.33f;

    public static void registerInteractions()
    {
        // splat interactions
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_grass", Floral::growGrass);
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_moss", Floral::growMoss);
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_lilypad", Floral::growLilypad);
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "flourish", Floral::flourish);

        GooInteractions.registerPassThroughPredicate(Registry.FLORAL_GOO.get(), Floral::blobPassThroughPredicate);

        // blob interactions
        GooInteractions.registerBlob(Registry.FLORAL_GOO.get(), "trigger_growable", Floral::growableTick);
        GooInteractions.registerBlob(Registry.FLORAL_GOO.get(), "grow_vines", Floral::growVines);
    }

    private static boolean growVines(BlobContext blobContext) {
        if (blobContext.block() instanceof LeavesBlock) {
            for (Direction face : Direction.values()) {
                Direction d = face.getOpposite();
                if (d == Direction.DOWN) {
                    continue;
                }
                if (blobContext.world().rand.nextFloat() <= FLOURISH_CHANCE) {
                    if (blobContext.world() instanceof ServerWorld) {
                        BooleanProperty prop = vinePlacementPropertyFromDirection(d);
                        BlockPos offset = blobContext.blockPos().offset(face);
                        BlockState state;
                        if (blobContext.world().getBlockState(offset).isAir(blobContext.world(), offset)) {
                            state = Blocks.VINE.getDefaultState().with(prop, true);
                        } else {
                            if (blobContext.world().getBlockState(offset).getBlock() instanceof VineBlock) {
                                state = blobContext.world().getBlockState(offset);
                                if (state.get(prop)) {
                                    continue;
                                } else {
                                    state = state.with(prop, true);
                                }
                            } else {
                                continue;
                            }
                        }

                        blobContext.world().setBlockState(offset, state);
                        doEffects(blobContext);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static net.minecraft.state.BooleanProperty vinePlacementPropertyFromDirection(Direction d) {
        switch (d) {
            case UP:
                return VineBlock.UP;
            case EAST:
                return VineBlock.EAST;
            case WEST:
                return VineBlock.WEST;
            case NORTH:
                return VineBlock.NORTH;
            case SOUTH:
                return VineBlock.SOUTH;
        }
        return VineBlock.UP;
    }

    private static boolean growableTick(BlobContext blobContext) {
        if (blobContext.block() instanceof IGrowable) {
            if (blobContext.world().rand.nextFloat() <= FLOURISH_CHANCE) {
                if (blobContext.world() instanceof ServerWorld) {
                    ((IGrowable) blobContext.block()).grow((ServerWorld) blobContext.world(), blobContext.world().rand,
                            blobContext.blockPos(), blobContext.blockState());
                }
            }
            doEffects(blobContext);
            return true;
        }
        return false;
    }

    private static boolean flourish(SplatContext splatContext) {
        if (splatContext.block() instanceof GrassBlock) {
            if (splatContext.world().rand.nextFloat() <= FLOURISH_CHANCE) {
                if (splatContext.world() instanceof ServerWorld) {
                    ((GrassBlock) splatContext.block()).grow((ServerWorld)splatContext.world(),
                            splatContext.world().rand, splatContext.blockPos(), splatContext.blockState());
                }
            }
            doEffects(splatContext);
            return true;
        }
        return false;
    }

    private static boolean growLilypad(SplatContext context) {
        // spawn lilypad
        if (context.fluidState().getFluid().isEquivalentTo(Fluids.WATER)) {
            if (!context.isRemote()) {
                if (context.fluidState().isSource() && context.isBlockAboveAir()) {
                    context.setBlockStateAbove(Blocks.LILY_PAD.getDefaultState());
                }
            }
            doEffects(context);
            return true;
        }
        return false;
    }

    private static boolean exchangeBlock(SplatContext context, Block target, Block... sources) {
        // do conversion
        for(Block source : sources) {
            if (context.block().equals(source)) {
                if (!context.isRemote()) {
                    context.setBlockState(target.getDefaultState());
                }
                // spawn particles and stuff
                doEffects(context);
                return true;
            }
        }
        return false;
    }

    private static void doEffects(SplatContext context) {
        BoneMealItem.spawnBonemealParticles(context.world(), context.blockPos(), 4);
    }

    private static void doEffects(BlobContext context) {
        BoneMealItem.spawnBonemealParticles(context.world(), context.blockPos(), 4);
    }

    private static boolean growMoss(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE)
                || exchangeBlock(splatContext, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS);
    }

    private static boolean growGrass(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.GRASS_BLOCK, Blocks.DIRT);
    }

    private static Boolean blobPassThroughPredicate(BlockRayTraceResult blockRayTraceResult, GooBlob gooBlob) {
        BlockState state = gooBlob.getEntityWorld().getBlockState(blockRayTraceResult.getPos());
        if (state.getBlock().hasTileEntity(state)) {
            return false;
        }
        return state.getBlock() instanceof LeavesBlock
                || (state.getBlock() instanceof IGrowable && !(state.getBlock() instanceof GrassBlock));
    }
}
