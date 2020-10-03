package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BoneMealItem;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;

public class Floral
{
    public static void registerInteractions()
    {
        // splat interactions
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_grass", Floral::growGrass);
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_moss", Floral::growMoss);
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_lilypad", Floral::growLilypad);
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_bark", Floral::growBark);
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

    private static boolean flourish(SplatContext context) {
        if (context.block() instanceof GrassBlock) {
            if (context.world() instanceof ServerWorld) {
                ((GrassBlock) context.block()).grow((ServerWorld)context.world(),
                        context.world().rand, context.blockPos(), context.blockState());
            }
            doEffects(context);
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

    private static boolean growMoss(SplatContext context) {
        return exchangeBlock(context, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE)
                || exchangeBlock(context, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS);
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
        if (context.block().equals(source)) {
            Direction.Axis preservedAxis = context.blockState().get(BlockStateProperties.AXIS);
            if (!context.isRemote()) {
                context.setBlockState(target.getDefaultState().with(BlockStateProperties.AXIS, preservedAxis));
            }
            // spawn particles and stuff
            doEffects(context);
            return true;
        }
        return false;
    }

    public static final List<Tuple<Block, Block>> logBarkPairs = new ArrayList<>();
    public static void registerLogBarkPair(Block source, Block target) {
        logBarkPairs.add(new Tuple<>(source, target));
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

    private static boolean growBark(SplatContext context) {
        for(Tuple<Block, Block> blockPair : Floral.logBarkPairs) {
            if (exchangeLog(context, blockPair.getB(), blockPair.getA())) {
                return true;
            }
        }
        return false;
    }
}
