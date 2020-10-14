package com.xeno.goo.interactions;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BoneMealItem;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.server.ServerWorld;

public class Floral
{
    public static void registerInteractions()
    {
        // splat interactions
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_grass", Floral::growGrass, Floral::isDirt);
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_moss", Floral::growMoss, Floral::canSupportMoss);
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_lilypad", Floral::growLilypad, Floral::canSupportLilypad);
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_bark", Floral::growBark, Floral::isStrippedLog);
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "flourish", Floral::flourish, Floral::isGrassBlock);

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
                doEffects(blobContext);
                
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
                    return true;
                }
            }
        }
        return false;
    }

    private static BooleanProperty vinePlacementPropertyFromDirection(Direction d) {
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
            if (blobContext.block() instanceof MushroomBlock) {
                return false;
            }
            if (blobContext.world() instanceof ServerWorld) {
                ((IGrowable) blobContext.block()).grow((ServerWorld) blobContext.world(), blobContext.world().rand,
                        blobContext.blockPos(), blobContext.blockState());
            }
            doEffects(blobContext);
            return true;
        }
        return false;
    }

    private static boolean isGrassBlock(SplatContext context) {
        return context.block() instanceof GrassBlock;
    }

    private static boolean flourish(SplatContext context) {
        // grow needs a server world in scope, this one is weird.
        if (context.world() instanceof ServerWorld) {
            ((GrassBlock) context.block()).grow((ServerWorld)context.world(),
                    context.world().rand, context.blockPos(), context.blockState());
        }
        doEffects(context);
        return true;
    }

    private static boolean canSupportLilypad(SplatContext context) {
        return context.fluidState().getFluid().isEquivalentTo(Fluids.WATER)
                && context.fluidState().isSource() && context.isBlockAboveAir();
    }

    private static boolean growLilypad(SplatContext context) {
        // spawn lilypad
        if (!context.isRemote()) {
            boolean hasChanges = context.setBlockStateAbove(Blocks.LILY_PAD.getDefaultState());
            if (!hasChanges) {
                return false;
            }
        }
        doEffects(context);
        return true;
    }

    private static boolean exchangeBlock(SplatContext context, Block target, Block... sources) {
        // do conversion
        for(Block source : sources) {
            if (context.block().equals(source)) {
                if (!context.isRemote()) {
                    boolean hasChanges = context.setBlockState(target.getDefaultState());
                    if (!hasChanges) {
                        return false;
                    }
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

    private static boolean canSupportMoss(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.COBBLESTONE, Blocks.STONE_BRICKS);
    }

    private static boolean growMoss(SplatContext context) {
        return exchangeBlock(context, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE)
                || exchangeBlock(context, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS);
    }

    private static boolean isDirt(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.DIRT);
    }

    private static boolean growGrass(SplatContext context) {
        return exchangeBlock(context, Blocks.GRASS_BLOCK, Blocks.DIRT);
    }

    private static Boolean blobPassThroughPredicate(BlockRayTraceResult blockRayTraceResult, GooBlob gooBlob) {
        BlockState state = gooBlob.getEntityWorld().getBlockState(blockRayTraceResult.getPos());
        if (state.getBlock().hasTileEntity(state)) {
            return false;
        }
        return state.getBlock() instanceof LeavesBlock
                || (state.getBlock() instanceof IGrowable && !(state.getBlock() instanceof GrassBlock));
    }

    // similar to exchange block but respect the state of the original log
    private static boolean exchangeLog(SplatContext context, Block source, Block target) {
        if (!context.block().equals(source)) {
            return false;
        }
        if (!context.isRemote()) {
            Direction.Axis preservedAxis = context.blockState().get(BlockStateProperties.AXIS);
            boolean hasChanges = context.setBlockState(target.getDefaultState().with(BlockStateProperties.AXIS, preservedAxis));
            if (!hasChanges) {
                return false;
            }
        }
        // spawn particles and stuff
        doEffects(context);
        return true;
    }

    public static final BiMap<Block, Block> logBarkPairs = HashBiMap.create();
    public static void registerLogBarkPair(Block target, Block source) {
        logBarkPairs.put(target, source);
    }

    static {
        registerLogBarkPair(Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG);
        registerLogBarkPair(Blocks.ACACIA_WOOD, Blocks.STRIPPED_ACACIA_WOOD);
        registerLogBarkPair(Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG);
        registerLogBarkPair(Blocks.BIRCH_WOOD, Blocks.STRIPPED_BIRCH_WOOD);
        registerLogBarkPair(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG);
        registerLogBarkPair(Blocks.DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD);
        registerLogBarkPair(Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG);
        registerLogBarkPair(Blocks.JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_WOOD);
        registerLogBarkPair(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG);
        registerLogBarkPair(Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_WOOD);
        registerLogBarkPair(Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG);
        registerLogBarkPair(Blocks.SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_WOOD);
    }

    private static boolean isStrippedLog(SplatContext splatContext) {
        // inverse of logBarkPairs is strippedBarkPairs, essentially, where stripped bark is the key.
        return logBarkPairs.containsValue(splatContext.block());
    }

    private static boolean growBark(SplatContext context) {
        return exchangeLog(context, logBarkPairs.inverse().get(context.block()), context.block());
    }
}
