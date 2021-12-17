package com.xeno.goo.interactions;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.xeno.goo.entities.HexController;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.item.BoneMealItem;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.world.server.ServerWorld;

import java.util.function.Supplier;

public class Fungal
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.FUNGAL_GOO;
    public static void registerInteractions()
    {
        GooInteractions.registerBlobHit(fluidSupplier.get(), "grow_podzol", Fungal::growPodzol, Fungal::isDirt);
        GooInteractions.registerBlobHit(fluidSupplier.get(), "grow_mycelium", Fungal::growMycelium, Fungal::isPodzol);
        GooInteractions.registerBlobHit(fluidSupplier.get(), "grow_nylium", Fungal::growNylium, Fungal::isNetherrack);
        GooInteractions.registerBlobHit(fluidSupplier.get(), "grow_shroom", Fungal::growShroom, Fungal::isShroomSoil);
        GooInteractions.registerBlobHit(Registry.FLORAL_GOO.get(), "grow_bark", Fungal::growBark, Fungal::isStrippedStem);
        GooInteractions.registerBlobHit(fluidSupplier.get(), "grow_vines", Fungal::growVines, Fungal::canGrowLeaves);


        GooInteractions.registerPassThroughPredicate(fluidSupplier.get(), Fungal::blobPassThroughPredicate);

        // blob interactions
        GooInteractions.registerBlobHit(fluidSupplier.get(), "trigger_growable", Fungal::growableTick);

        GooInteractions.registerBlobHit(fluidSupplier.get(), "fungal_hit", Fungal::entityHit);
    }

    private static boolean entityHit(BlobHitContext c) {
        c.victim().addPotionEffect(new EffectInstance(Effects.POISON, 120, 2));
        return true;
    }

    private static boolean isShroomSoil(BlobHitContext splatContext) {
        return splatContext.isBlock(Blocks.WARPED_NYLIUM, Blocks.CRIMSON_NYLIUM, Blocks.MYCELIUM);
    }

    private static boolean growShroom(BlobHitContext context) {
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

    private static boolean canGrowLeaves(BlobHitContext splatContext) {
        return splatContext.isBlock(Blocks.WARPED_WART_BLOCK, Blocks.NETHER_WART_BLOCK);
    }

    private static boolean growVines(BlobHitContext context) {
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

    private static boolean isPodzol(BlobHitContext splatContext) {
        return splatContext.isBlock(Blocks.PODZOL);
    }

    private static boolean growMycelium(BlobHitContext splatContext) {
        return exchangeBlock(splatContext, Blocks.MYCELIUM, Blocks.PODZOL);
    }

    private static boolean isDirt(BlobHitContext splatContext) {
        return splatContext.isBlock(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT);
    }

    private static boolean growPodzol(BlobHitContext splatContext) {
        return exchangeBlock(splatContext, Blocks.PODZOL, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT);
    }

    private static boolean isNetherrack(BlobHitContext splatContext) {
        return splatContext.isBlock(Blocks.NETHERRACK);
    }

    private static boolean growNylium(BlobHitContext splatContext) {
        boolean isCrimson = splatContext.world().rand.nextFloat() < 0.5f;
        return exchangeBlock(splatContext, isCrimson ? Blocks.CRIMSON_NYLIUM : Blocks.WARPED_NYLIUM,
                Blocks.NETHERRACK);
    }

    private static boolean isStrippedStem(BlobHitContext splatContext) {
        // inverse of stemPairs is strippedStemPairs, essentially, where stripped stem is the key.
        return stemPairs.containsValue(splatContext.block());
    }

    private static boolean growBark(BlobHitContext context) {
        return exchangeBlock(context, stemPairs.inverse().get(context.block()), context.block());
    }

    private static boolean exchangeBlock(BlobHitContext context, Block target, Block... sources) {
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

    private static void doEffects(BlobHitContext context) {
        if (context.isRemote()) {
            BoneMealItem.spawnBonemealParticles(context.world(), context.blockPos(), 4);
        }
    }

    private static Boolean blobPassThroughPredicate(BlockState state, HexController gooBlob) {
        if (state.getBlock().hasTileEntity(state)) {
            return false;
        }
        return state.getBlock() instanceof LeavesBlock;
    }

    private static boolean growableTick(BlobHitContext blobContext) {
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
