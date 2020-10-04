package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.item.BoneMealItem;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;

public class Fungal
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.FUNGAL_GOO.get(), "grow_podzol", Fungal::growPodzol);
        GooInteractions.registerSplat(Registry.FUNGAL_GOO.get(), "grow_mycelium", Fungal::growMycelium);
        GooInteractions.registerSplat(Registry.FUNGAL_GOO.get(), "grow_nylium", Fungal::growNylium);
        GooInteractions.registerSplat(Registry.FUNGAL_GOO.get(), "grow_shroom", Fungal::growShroom);
        GooInteractions.registerSplat(Registry.FUNGAL_GOO.get(), "grow_vines", Fungal::growVines);


        GooInteractions.registerPassThroughPredicate(Registry.FUNGAL_GOO.get(), Fungal::blobPassThroughPredicate);

        // blob interactions
        GooInteractions.registerBlob(Registry.FUNGAL_GOO.get(), "trigger_growable", Fungal::growableTick);
    }

    private static boolean growShroom(SplatContext context) {
        Block variant = context.block().equals(Blocks.WARPED_NYLIUM) ? Blocks.WARPED_FUNGUS :
                (context.block().equals(Blocks.CRIMSON_NYLIUM) ? Blocks.CRIMSON_NYLIUM :
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

    private static boolean growMycelium(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.MYCELIUM, Blocks.PODZOL);
    }

    private static boolean growPodzol(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.PODZOL, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT);
    }

    private static boolean growNylium(SplatContext splatContext) {
        boolean isCrimson = splatContext.world().rand.nextFloat() < 0.5f;
        return exchangeBlock(splatContext, isCrimson ? Blocks.CRIMSON_NYLIUM : Blocks.WARPED_NYLIUM,
                Blocks.NETHERRACK);
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

    public static final List<Tuple<Block, Block>> stemPairs = new ArrayList<>();
    public static void registerStemPair(Block source, Block target) {
        stemPairs.add(new Tuple<>(source, target));
    }

    static {
        registerStemPair(Blocks.WARPED_STEM, Blocks.STRIPPED_WARPED_STEM);
        registerStemPair(Blocks.WARPED_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE);
        registerStemPair(Blocks.CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_STEM);
        registerStemPair(Blocks.CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_HYPHAE);}
}
