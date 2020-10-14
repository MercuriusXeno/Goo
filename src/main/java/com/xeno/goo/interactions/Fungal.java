package com.xeno.goo.interactions;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.item.BoneMealItem;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.server.ServerWorld;

public class Fungal
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.FUNGAL_GOO.get(), "grow_podzol", Fungal::growPodzol, Fungal::isDirt);
        GooInteractions.registerSplat(Registry.FUNGAL_GOO.get(), "grow_mycelium", Fungal::growMycelium, Fungal::isPodzol);
        GooInteractions.registerSplat(Registry.FUNGAL_GOO.get(), "grow_nylium", Fungal::growNylium, Fungal::isNetherrack);
        GooInteractions.registerSplat(Registry.FUNGAL_GOO.get(), "grow_shroom", Fungal::growShroom, Fungal::isShroomSoil);
        GooInteractions.registerSplat(Registry.FLORAL_GOO.get(), "grow_bark", Fungal::growBark, Fungal::isStrippedStem);
        GooInteractions.registerSplat(Registry.FUNGAL_GOO.get(), "grow_vines", Fungal::growVines, Fungal::canGrowLeaves);


        GooInteractions.registerPassThroughPredicate(Registry.FUNGAL_GOO.get(), Fungal::blobPassThroughPredicate);

        // blob interactions
        GooInteractions.registerBlob(Registry.FUNGAL_GOO.get(), "trigger_growable", Fungal::growableTick);
    }

    private static boolean isShroomSoil(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.WARPED_NYLIUM, Blocks.CRIMSON_NYLIUM, Blocks.MYCELIUM);
    }

    private static boolean growShroom(SplatContext context) {
        Block variant = context.block().equals(Blocks.WARPED_NYLIUM) ? Blocks.WARPED_FUNGUS :
                (context.block().equals(Blocks.CRIMSON_NYLIUM) ? Blocks.CRIMSON_FUNGUS :
                        (context.block().equals(Blocks.MYCELIUM) ?
                                (context.world().rand.nextFloat() < 0.5f ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM) : null

                        )
                );
        if (variant == null) {
            return false;
        }
        if (!context.isBlockAboveAir()) {
            return false;
        }

        doEffects(context);

        if (context.world() instanceof ServerWorld) {
            context.setBlockStateBelow(variant.getDefaultState());
        }
        return true;
    }

    private static boolean canGrowLeaves(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.WARPED_WART_BLOCK, Blocks.NETHER_WART_BLOCK);
    }

    private static boolean growVines(SplatContext context) {
        Block variant = context.block().equals(Blocks.WARPED_WART_BLOCK) ? Blocks.TWISTING_VINES_PLANT :
                (context.block().equals(Blocks.NETHER_WART_BLOCK) ? Blocks.WEEPING_VINES_PLANT : null);
        if (variant == null) {
            return false;
        }
        if (!context.isBlockBelowAir()) {
            return false;
        }

        doEffects(context);

        context.setBlockStateBelow(variant.getDefaultState());
        return true;
    }

    private static boolean isPodzol(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.PODZOL);
    }

    private static boolean growMycelium(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.MYCELIUM, Blocks.PODZOL);
    }

    private static boolean isDirt(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT);
    }

    private static boolean growPodzol(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.PODZOL, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT);
    }

    private static boolean isNetherrack(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.NETHERRACK);
    }

    private static boolean growNylium(SplatContext splatContext) {
        boolean isCrimson = splatContext.world().rand.nextFloat() < 0.5f;
        return exchangeBlock(splatContext, isCrimson ? Blocks.CRIMSON_NYLIUM : Blocks.WARPED_NYLIUM,
                Blocks.NETHERRACK);
    }

    private static boolean isStrippedStem(SplatContext splatContext) {
        // inverse of stemPairs is strippedStemPairs, essentially, where stripped stem is the key.
        return stemPairs.containsValue(splatContext.block());
    }

    private static boolean growBark(SplatContext context) {
        return exchangeBlock(context, stemPairs.inverse().get(context.block()), context.block());
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

    private static Boolean blobPassThroughPredicate(BlockRayTraceResult blockRayTraceResult, GooBlob gooBlob) {
        BlockState state = gooBlob.getEntityWorld().getBlockState(blockRayTraceResult.getPos());
        if (state.getBlock().hasTileEntity(state)) {
            return false;
        }
        return state.getBlock() instanceof LeavesBlock
                || (state.getBlock() instanceof IGrowable && !(state.getBlock() instanceof GrassBlock));
    }

    private static boolean growableTick(BlobContext blobContext) {
        if (blobContext.block() instanceof IGrowable && blobContext.block() instanceof MushroomBlock) {
            if (blobContext.world() instanceof ServerWorld) {
                ((IGrowable) blobContext.block()).grow((ServerWorld) blobContext.world(), blobContext.world().rand,
                        blobContext.blockPos(), blobContext.blockState());
            }
            doEffects(blobContext);
            return true;
        }
        return false;
    }

    public static final BiMap<Block, Block> stemPairs = HashBiMap.create();
    public static void registerStemPair(Block target, Block source) {
        stemPairs.put(target, source);
    }

    static {
        registerStemPair(Blocks.WARPED_STEM, Blocks.STRIPPED_WARPED_STEM);
        registerStemPair(Blocks.WARPED_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE);
        registerStemPair(Blocks.CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_STEM);
        registerStemPair(Blocks.CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_HYPHAE);}
}
