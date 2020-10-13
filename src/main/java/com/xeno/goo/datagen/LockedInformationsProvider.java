package com.xeno.goo.datagen;

import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.information.locked.LockedInformationProvider;
import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.compound.GooCompoundType;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.CrystallizedGooAbstract;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;

import java.util.function.Supplier;

public class LockedInformationsProvider extends LockedInformationProvider
{
    LockedInformationsProvider(final DataGenerator dataGenerator)
    {
        super(GooMod.MOD_ID, dataGenerator);
    }

    @Override
    public void calculateDataToSave()
    {
        saveData(Items.ACACIA_LEAVES, floral(5), vital(1));
        saveData(Items.BIRCH_LEAVES, floral(5), vital(1));
        saveData(Items.DARK_OAK_LEAVES, floral(5), vital(1));
        saveData(Items.JUNGLE_LEAVES, floral(5), vital(1));
        saveData(Items.OAK_LEAVES, floral(5), vital(1));
        saveData(Items.SPRUCE_LEAVES, floral(5), vital(1));
        saveData(Items.ACACIA_LOG, floral(128), vital(16));
        saveData(Items.BIRCH_LOG, floral(128), vital(16));
        saveData(Items.DARK_OAK_LOG, floral(128), vital(16));
        saveData(Items.JUNGLE_LOG, floral(128), vital(16));
        saveData(Items.OAK_LOG, floral(128), vital(16));
        saveData(Items.SPRUCE_LOG, floral(128), vital(16));
        saveData(Items.CRIMSON_STEM, fungal(16), chromatic(128), vital (16));
        saveData(Items.WARPED_STEM, fungal(16), chromatic(128), vital (16));
        saveData(Items.STRIPPED_ACACIA_LOG, floral(128));
        saveData(Items.STRIPPED_BIRCH_LOG, floral(128));
        saveData(Items.STRIPPED_DARK_OAK_LOG, floral(128));
        saveData(Items.STRIPPED_JUNGLE_LOG, floral(128));
        saveData(Items.STRIPPED_OAK_LOG, floral(128));
        saveData(Items.STRIPPED_SPRUCE_LOG, floral(128));
        saveData(Items.STRIPPED_CRIMSON_STEM, fungal(16), chromatic(128));
        saveData(Items.STRIPPED_WARPED_STEM, fungal(16), chromatic(128));
        saveData(Items.ACACIA_SAPLING, floral(394), vital(16));
        saveData(Items.BIRCH_SAPLING, floral(394), vital(16));
        saveData(Items.DARK_OAK_SAPLING, floral(394), vital(16));
        saveData(Items.JUNGLE_SAPLING, floral(394), vital(16));
        saveData(Items.OAK_SAPLING, floral(394), vital(16));
        saveData(Items.SPRUCE_SAPLING, floral(394), vital(16));
        saveData(Items.ALLIUM, floral(5), chromatic(240), vital(1));
        saveData(Items.AZURE_BLUET, floral(5), chromatic(240), vital(1));
        saveData(Items.BLUE_ORCHID, floral(5), chromatic(240), vital(1));
        saveData(Items.COCOA_BEANS, floral(5), chromatic(240), vital(1));
        saveData(Items.CORNFLOWER, floral(5), chromatic(240), vital(1));
        saveData(Items.DANDELION, floral(5), chromatic(240), vital(1));
        saveData(Items.LILY_OF_THE_VALLEY, floral(5), chromatic(240), vital(1));
        saveData(Items.ORANGE_TULIP, floral(5), chromatic(240), vital(1));
        saveData(Items.OXEYE_DAISY, floral(5), chromatic(240), vital(1));
        saveData(Items.PINK_TULIP, floral(5), chromatic(240), vital(1));
        saveData(Items.POPPY, floral(5), chromatic(240), vital(1));
        saveData(Items.RED_TULIP, floral(5), chromatic(240), vital(1));
        saveData(Items.WHITE_TULIP, floral(5), chromatic(240), vital(1));
        saveData(Items.LILAC, floral(10), chromatic(480), vital(2));
        saveData(Items.PEONY, floral(10), chromatic(480), vital(2));
        saveData(Items.ROSE_BUSH, floral(10), chromatic(480), vital(2));
        saveData(Items.SUNFLOWER, floral(10), chromatic(480), vital(2));
        saveData(Items.BLACK_DYE, chromatic(240));
        saveData(Items.BLUE_DYE, chromatic(240));
        saveData(Items.BROWN_DYE, chromatic(240));
        saveData(Items.CYAN_DYE, chromatic(240));
        saveData(Items.GRAY_DYE, chromatic(240));
        saveData(Items.GREEN_DYE, chromatic(240));
        saveData(Items.LIGHT_BLUE_DYE, chromatic(240));
        saveData(Items.LIGHT_GRAY_DYE, chromatic(240));
        saveData(Items.LIME_DYE, chromatic(240));
        saveData(Items.MAGENTA_DYE, chromatic(240));
        saveData(Items.ORANGE_DYE, chromatic(240));
        saveData(Items.PINK_DYE, chromatic(240));
        saveData(Items.PURPLE_DYE, chromatic(240));
        saveData(Items.RED_DYE, chromatic(240));
        saveData(Items.WHITE_DYE, chromatic(240));
        saveData(Items.YELLOW_DYE, chromatic(240));
        saveData(Items.BRAIN_CORAL, faunal(5), vital(1));
        saveData(Items.BUBBLE_CORAL, faunal(5), vital(1));
        saveData(Items.DEAD_BRAIN_CORAL, faunal(5), decay(5));
        saveData(Items.DEAD_BUBBLE_CORAL, faunal(5), decay(5));
        saveData(Items.DEAD_FIRE_CORAL, faunal(5), decay(5));
        saveData(Items.DEAD_HORN_CORAL, faunal(5), decay(5));
        saveData(Items.DEAD_TUBE_CORAL, faunal(5), decay(5));
        saveData(Items.FIRE_CORAL, faunal(5), vital(1));
        saveData(Items.HORN_CORAL, faunal(5), vital(1));
        saveData(Items.TUBE_CORAL, faunal(5), vital(1));
        saveData(Items.BRAIN_CORAL_BLOCK, faunal(20), vital(4));
        saveData(Items.BUBBLE_CORAL_BLOCK, faunal(20), vital(4));
        saveData(Items.DEAD_BRAIN_CORAL_BLOCK, faunal(20), decay(20));
        saveData(Items.DEAD_BUBBLE_CORAL_BLOCK, faunal(20), decay(20));
        saveData(Items.DEAD_FIRE_CORAL_BLOCK, faunal(20), decay(20));
        saveData(Items.DEAD_HORN_CORAL_BLOCK, faunal(20), decay(20));
        saveData(Items.DEAD_TUBE_CORAL_BLOCK, faunal(20), decay(20));
        saveData(Items.FIRE_CORAL_BLOCK, faunal(20), vital(4));
        saveData(Items.HORN_CORAL_BLOCK, faunal(20), vital(4));
        saveData(Items.TUBE_CORAL_BLOCK, faunal(20), vital(4));
        saveData(Items.BRAIN_CORAL_FAN, faunal(5), vital(1));
        saveData(Items.BUBBLE_CORAL_FAN, faunal(5), vital(1));
        saveData(Items.DEAD_BRAIN_CORAL_FAN, faunal(5), decay(5));
        saveData(Items.DEAD_BUBBLE_CORAL_FAN, faunal(5), decay(5));
        saveData(Items.DEAD_FIRE_CORAL_FAN, faunal(5), decay(5));
        saveData(Items.DEAD_HORN_CORAL_FAN, faunal(5), decay(5));
        saveData(Items.DEAD_TUBE_CORAL_FAN, faunal(5), decay(5));
        saveData(Items.FIRE_CORAL_FAN, faunal(5), vital(1));
        saveData(Items.HORN_CORAL_FAN, faunal(5), vital(1));
        saveData(Items.TUBE_CORAL_FAN, faunal(5), vital(1));
        saveData(Items.DEAD_BUSH, floral(15), decay(5));
        saveData(Items.FERN, floral(15), vital(1));
        saveData(Items.GRASS, floral(15), vital(1));
        saveData(Items.CRIMSON_ROOTS, fungal(15), vital(1));
        saveData(Items.NETHER_SPROUTS, fungal(15), vital(1));
        saveData(Items.WARPED_ROOTS, fungal(15), vital(1));
        saveData(Items.LARGE_FERN, floral(30), vital(2));
        saveData(Items.TALL_GRASS, floral(30), vital(2));
        saveData(Items.BLACKSTONE, earthen(180));
        saveData(Items.COBBLESTONE, earthen(180));
        saveData(Items.DIRT, earthen(180));
        saveData(Items.FLINT, earthen(180));
        saveData(Items.GRAVEL, earthen(180));
        saveData(Items.PODZOL, earthen(180), fungal(15));
        saveData(Items.RED_SAND, earthen(180));
        saveData(Items.SAND, earthen(180));
        saveData(Items.APPLE, floral(15), vital(1));
        saveData(Items.BAMBOO, floral(8), vital(1));
        saveData(Items.BASALT, earthen(180), obsidian(6));
        saveData(Items.BEEF, faunal(15), vital(1));
        saveData(Items.BEETROOT, floral(15), vital(1), chromatic(240));
        saveData(Items.BEETROOT_SEEDS, floral(15));
        saveData(Items.BLAZE_ROD, molten(120));
        saveData(Items.BONE, faunal(30), chromatic(360), vital(3), decay(60));
        saveData(Items.BROWN_MUSHROOM, fungal(15), vital(1));
        saveData(Items.BROWN_MUSHROOM_BLOCK, fungal(5));
        saveData(Items.CACTUS, floral(120), chromatic(240), vital (1));
        saveData(Items.CARROT, floral(60), vital(1));
        saveData(Items.CARVED_PUMPKIN, floral(5), weird(1));
        saveData(Items.CHARCOAL, molten(128), floral(32), energetic(32));
        saveData(Items.CHICKEN, faunal(15), vital(1));
        saveData(Items.CHORUS_FLOWER, floral(30), weird(6), vital(1));
        saveData(Items.CHORUS_FRUIT, floral(15), weird(4), vital(1));
        saveData(Items.CLAY_BALL, earthen(60));
        saveData(Items.COAL, molten(128), earthen(16), energetic(32));
        saveData(Items.COBWEB, faunal(15));
        saveData(Items.COD, faunal(15), vital(1));
        saveData(Items.CRIMSON_NYLIUM, earthen(120), fungal(15), chromatic(60));
        saveData(Items.CRIMSON_FUNGUS, fungal(15));
        saveData(Items.CRYING_OBSIDIAN, weird(30), obsidian(960));
        saveData(Items.DIAMOND, crystal(120));
        saveData(Items.EGG, faunal(15), vital(15));
        saveData(Items.EMERALD, regal(60), crystal(60));
        saveData(Items.END_STONE, earthen(120), weird(5), vital(1));
        saveData(Items.ENDER_PEARL, weird(30));
        saveData(Items.FEATHER, faunal(15));
        saveData(Items.GHAST_TEAR, weird(30), crystal(24));
        saveData(Items.GILDED_BLACKSTONE, metal(240), regal(240), weird(60), earthen(240), obsidian(240));
        saveData(Items.GLOWSTONE_DUST, weird(5), energetic(30));
        saveData(Items.GOLD_INGOT, metal(36), regal(72));
        saveData(Items.BELL, earthen(360), regal(216), metal(180), floral(128));
        saveData(Items.GRASS_BLOCK, earthen(180), floral(15));
        saveData(Items.GUNPOWDER, energetic(120));
        saveData(Items.HONEYCOMB, honey(40), regal(32));
        saveData(Items.HONEY_BLOCK, honey(120), regal(96));
        saveData(Items.ICE, snow(60), aquatic(60));
        saveData(Items.INK_SAC, faunal(15), chromatic(240));
        saveData(Items.IRON_INGOT, metal(72));
        saveData(Items.KELP, floral(15), vital(1));
        saveData(Items.LAPIS_LAZULI, weird(4), chromatic(240), crystal(4));
        saveData(Items.LILY_PAD, floral(15), vital(1));
        saveData(Items.MAGMA_BLOCK, slime(10), molten(10), earthen(180), decay(30));
        saveData(Items.MELON_SLICE, floral(15), vital(1));
        saveData(Items.MUSHROOM_STEM, fungal(5));
        saveData(Items.MUTTON, faunal(15), vital(1));
        saveData(Items.MYCELIUM, earthen(180), fungal(30));
        saveData(Items.NETHER_WART, weird(1), fungal(5));
        saveData(Items.WARPED_WART_BLOCK, weird(9), fungal(45));
        saveData(Items.NETHERITE_SCRAP, metal(960), obsidian(120));
        saveData(Items.NETHERRACK, earthen(180), molten(5), decay(5));
        saveData(Items.OBSIDIAN, obsidian(120), molten(60), earthen(180));
        saveData(Items.PHANTOM_MEMBRANE, decay(60), vital(1), weird(60));
        saveData(Items.POISONOUS_POTATO, floral(15), weird(3));
        saveData(Items.PORKCHOP, faunal(15), vital(1));
        saveData(Items.POTATO, floral(15), vital(1));
        saveData(Items.PRISMARINE_CRYSTALS, aquatic(12), crystal(2), weird(2));
        saveData(Items.PRISMARINE_SHARD, aquatic(12), crystal(1), weird(1));
        saveData(Items.PUFFERFISH, faunal(15), weird(6));
        saveData(Items.PUMPKIN, floral(60));
        saveData(Items.QUARTZ, earthen(60), crystal (1));
        saveData(Items.RABBIT, faunal(15), vital(1));
        saveData(Items.RABBIT_FOOT, faunal(30), weird(30));
        saveData(Items.RABBIT_HIDE, faunal(15));
        saveData(Items.RED_MUSHROOM, fungal(15), vital(1));
        saveData(Items.RED_MUSHROOM_BLOCK, fungal(5));
        saveData(Items.REDSTONE, logic(15));
        saveData(Items.ROTTEN_FLESH, faunal(5), decay(60));
        saveData(Items.SALMON, faunal(15), vital(1));
        saveData(Items.SCUTE, faunal(60), weird(6), vital(1));
        saveData(Items.SEA_PICKLE, faunal(30), aquatic(60), chromatic(240));
        saveData(Items.SEAGRASS, floral(15), vital(1));
        saveData(Items.SHULKER_SHELL, faunal(60), weird(180));
        saveData(Items.SHROOMLIGHT, weird(30), fungal(60));
        saveData(Items.SLIME_BALL, slime(60));
        saveData(Items.SNOWBALL, snow(15), aquatic(15));
        saveData(Items.SOUL_SAND, earthen(180), vital(15), decay(60));
        saveData(Items.SOUL_SOIL, earthen(180), vital(15), decay(60));
        saveData(Items.SPIDER_EYE, faunal(15), weird(5));
        saveData(Items.STRING, faunal(15));
        saveData(Items.SUGAR_CANE, floral(15));
        saveData(Items.SWEET_BERRIES, floral(5), vital(1));
        saveData(Items.TROPICAL_FISH, faunal(15), vital(15));
        saveData(Items.TURTLE_EGG, faunal(30), vital(30), weird(60));
        saveData(Items.TWISTING_VINES, fungal(5));
        saveData(Items.VINE, floral(15));
        saveData(Items.WARPED_NYLIUM, earthen(180), molten(60), fungal(15));
        saveData(Items.WARPED_FUNGUS, fungal(15));
        saveData(Items.WEEPING_VINES, fungal(5));
        saveData(Items.WET_SPONGE, aquatic(60), weird(6), faunal(30), vital(15));
        saveData(Items.WHEAT, floral(15), vital(1));
        saveData(Items.WHEAT_SEEDS, floral(15), vital(1));

        // containers
        saveData(Items.LAVA_BUCKET, metal(216), molten(1080));
        saveData(Items.MILK_BUCKET, metal(216), faunal(120));
        saveData(Items.WATER_BUCKET, metal(216), aquatic(960));

        ItemsRegistry.CrystallizedGoo.forEach(this::registerLockedInfoForCrystallizedGoo);
    }

    private void registerLockedInfoForCrystallizedGoo(ResourceLocation resourceLocation, RegistryObject<CrystallizedGooAbstract> crystallizedGooAbstractRegistryObject) {
        Fluid f = crystallizedGooAbstractRegistryObject.get().gooType();
        saveData(crystallizedGooAbstractRegistryObject.get(), compoundsFromFluid(f, crystallizedGooAbstractRegistryObject.get().amount()));
    }

    private CompoundInstance compoundsFromFluid(Fluid f, int amount) {
        return new CompoundInstance(Registry.compoundFromFluid(f), amount);
    }

    private static CompoundInstance aquatic(double d) { return new CompoundInstance(Registry.AQUATIC.get(), d); }
    private static CompoundInstance chromatic(double d) { return new CompoundInstance(Registry.CHROMATIC.get(), d); }
    private static CompoundInstance crystal(double d) { return new CompoundInstance(Registry.CRYSTAL.get(), d); }
    private static CompoundInstance decay(double d) { return new CompoundInstance(Registry.DECAY.get(), d); }
    private static CompoundInstance earthen(double d) { return new CompoundInstance(Registry.EARTHEN.get(), d); }
    private static CompoundInstance energetic(double d) { return new CompoundInstance(Registry.ENERGETIC.get(), d); }
    private static CompoundInstance faunal(double d) { return new CompoundInstance(Registry.FAUNAL.get(), d); }
    private static CompoundInstance floral(double d) { return new CompoundInstance(Registry.FLORAL.get(), d); }
    private static CompoundInstance fungal(double d) { return new CompoundInstance(Registry.FUNGAL.get(), d); }
    private static CompoundInstance honey(double d) { return new CompoundInstance(Registry.HONEY.get(), d); }
    private static CompoundInstance logic(double d) { return new CompoundInstance(Registry.LOGIC.get(), d); }
    private static CompoundInstance metal(double d) { return new CompoundInstance(Registry.METAL.get(), d); }
    private static CompoundInstance molten(double d) { return new CompoundInstance(Registry.MOLTEN.get(), d); }
    private static CompoundInstance obsidian(double d) { return new CompoundInstance(Registry.OBSIDIAN.get(), d); }
    private static CompoundInstance regal(double d) { return new CompoundInstance(Registry.REGAL.get(), d); }
    private static CompoundInstance slime(double d) { return new CompoundInstance(Registry.SLIME.get(), d); }
    private static CompoundInstance snow(double d) { return new CompoundInstance(Registry.SNOW.get(), d); }
    private static CompoundInstance vital(double d) { return new CompoundInstance(Registry.VITAL.get(), d); }
    private static CompoundInstance weird(double d) { return new CompoundInstance(Registry.WEIRD.get(), d); }
}
