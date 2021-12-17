package com.xeno.goo.interactions;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.xeno.goo.entities.HexController;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BoneMealItem;
import net.minecraft.potion.EffectInstance;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.function.Supplier;

public class Floral
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.FLORAL_GOO;
    public static void registerInteractions()
    {
        // splat interactions
        GooInteractions.registerBlobHit(fluidSupplier.get(), "grow_grass", Floral::growGrass, Floral::isDirt);
        GooInteractions.registerBlobHit(fluidSupplier.get(), "grow_moss", Floral::growMoss, Floral::canSupportMoss);
        GooInteractions.registerBlobHit(fluidSupplier.get(), "grow_lilypad", Floral::growLilypad, Floral::canSupportLilypad);
        GooInteractions.registerBlobHit(fluidSupplier.get(), "grow_bark", Floral::growBark, Floral::isStrippedLog);
        GooInteractions.registerBlobHit(fluidSupplier.get(), "flourish", Floral::flourish, Floral::isGrassBlockButNotTallGrassBlock);

        GooInteractions.registerPassThroughPredicate(fluidSupplier.get(), Floral::blobPassThroughPredicate);

        // blob interactions
        GooInteractions.registerBlobHit(fluidSupplier.get(), "trigger_growable", Floral::growableTick);
        GooInteractions.registerBlobHit(fluidSupplier.get(), "grow_vines", Floral::growVines);

        GooInteractions.registerBlobHit(fluidSupplier.get(), "floral_hit", Floral::entityHit);
    }

    private static boolean entityHit(BlobHitContext c) {
        doEffects(c);
        c.victim().addPotionEffect(new EffectInstance(Registry.FLORAL_EFFECT.get(), 320));
        return true;
    }

    private static boolean growVines(BlobHitContext blobContext) {
        doEffects(blobContext);
        if (blobContext.block() instanceof LeavesBlock) {
            for (Direction face : Direction.values()) {
                Direction d = face.getOpposite();
                if (d == Direction.DOWN) {
                    continue;
                }
                
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

    private static boolean growableTick(BlobHitContext blobContext) {
        if (blobContext.block() instanceof IGrowable) {
            doEffects(blobContext);
            if (!((IGrowable)blobContext.block()).canGrow(blobContext.world(),
                    blobContext.blockPos(), blobContext.blockState(), blobContext.isRemote())) {
                return false;
            }
            if (blobContext.block() instanceof MushroomBlock) {
                return false;
            }
            if (blobContext.world() instanceof ServerWorld) {
                ((IGrowable) blobContext.block()).grow((ServerWorld) blobContext.world(), blobContext.world().rand,
                        blobContext.blockPos(), blobContext.blockState());
            }
            return true;
        }
        return false;
    }

    private static boolean isGrassBlockButNotTallGrassBlock(BlobHitContext context) {
        return context.block() instanceof GrassBlock && !(context.block() instanceof TallGrassBlock);
    }

    private static boolean flourish(BlobHitContext context) {
        doEffects(context);
        // grow needs a server world in scope, this one is weird.
        if (context.world() instanceof ServerWorld) {
            ((GrassBlock) context.block()).grow((ServerWorld)context.world(),
                    context.world().rand, context.blockPos(), context.blockState());
        }
        return true;
    }

    private static boolean canSupportLilypad(BlobHitContext context) {
        return context.fluidState().getFluid().isEquivalentTo(Fluids.WATER)
                && context.fluidState().isSource() && context.isBlockAboveAir();
    }

    private static boolean growLilypad(BlobHitContext context) {
        // spawn lilypad
        doEffects(context);
        if (!context.isRemote()) {
            boolean hasChanges = context.setBlockStateAbove(Blocks.LILY_PAD.getDefaultState());
            if (!hasChanges) {
                return false;
            }
        }
        return true;
    }

    private static boolean exchangeBlock(BlobHitContext context, Block target, Block... sources) {
        // spawn particles and stuff
        doEffects(context);
        // do conversion
        for(Block source : sources) {
            if (context.block().equals(source)) {
                if (!context.isRemote()) {
                    boolean hasChanges = context.setBlockState(target.getDefaultState());
                    if (!hasChanges) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static void doEffects(BlobHitContext c) {
        if (c.isRemote()) {
            BoneMealItem.spawnBonemealParticles(c.world(), c.blob().getPosition(), 4);
        }
    }

    private static boolean canSupportMoss(BlobHitContext splatContext) {
        return splatContext.isBlock(Blocks.COBBLESTONE, Blocks.STONE_BRICKS);
    }

    private static boolean growMoss(BlobHitContext context) {
        return exchangeBlock(context, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE)
                || exchangeBlock(context, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS);
    }

    private static boolean isDirt(BlobHitContext splatContext) {
        return splatContext.isBlock(Blocks.DIRT);
    }

    private static boolean growGrass(BlobHitContext context) {
        return exchangeBlock(context, Blocks.GRASS_BLOCK, Blocks.DIRT);
    }

    private static Boolean blobPassThroughPredicate(BlockState state, HexController gooBlob) {
        if (state.getBlock().hasTileEntity(state)) {
            return false;
        }
        return state.getBlock() instanceof LeavesBlock;
    }

    // similar to exchange block but respect the state of the original log
    private static boolean exchangeLog(BlobHitContext context, Block source, Block target) {
        if (!context.block().equals(source)) {
            return false;
        }
        // spawn particles and stuff
        doEffects(context);
        if (!context.isRemote()) {
            Direction.Axis preservedAxis = context.blockState().get(BlockStateProperties.AXIS);
            boolean hasChanges = context.setBlockState(target.getDefaultState().with(BlockStateProperties.AXIS, preservedAxis));
            if (!hasChanges) {
                return false;
            }
        }
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

    private static boolean isStrippedLog(BlobHitContext splatContext) {
        // inverse of logBarkPairs is strippedBarkPairs, essentially, where stripped bark is the key.
        return logBarkPairs.containsValue(splatContext.block());
    }

    private static boolean growBark(BlobHitContext context) {
        return exchangeLog(context, logBarkPairs.inverse().get(context.block()), context.block());
    }
}
