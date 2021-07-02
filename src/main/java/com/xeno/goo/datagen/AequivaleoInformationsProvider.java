package com.xeno.goo.datagen;

import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.information.datagen.ForcedInformationProvider;
import com.ldtteam.aequivaleo.api.compound.type.ICompoundType;
import com.xeno.goo.GooMod;
import com.xeno.goo.items.CrystallizedGooAbstract;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;

public class AequivaleoInformationsProvider extends ForcedInformationProvider
{
    AequivaleoInformationsProvider(final DataGenerator dataGenerator)
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
        saveData(Items.CRIMSON_STEM, fungal(64), chromatic(64), vital (16));
        saveData(Items.WARPED_STEM, fungal(64), chromatic(64), vital (16));
        saveData(Items.STRIPPED_ACACIA_LOG, floral(64));
        saveData(Items.STRIPPED_BIRCH_LOG, floral(64));
        saveData(Items.STRIPPED_DARK_OAK_LOG, floral(64));
        saveData(Items.STRIPPED_JUNGLE_LOG, floral(64));
        saveData(Items.STRIPPED_OAK_LOG, floral(64));
        saveData(Items.STRIPPED_SPRUCE_LOG, floral(64));
        saveData(Items.STRIPPED_CRIMSON_STEM, fungal(64), chromatic(64));
        saveData(Items.STRIPPED_WARPED_STEM, fungal(64), chromatic(64));
        saveData(Items.ACACIA_SAPLING, floral(96), vital(16));
        saveData(Items.BIRCH_SAPLING, floral(96), vital(16));
        saveData(Items.DARK_OAK_SAPLING, floral(96), vital(16));
        saveData(Items.JUNGLE_SAPLING, floral(96), vital(16));
        saveData(Items.OAK_SAPLING, floral(96), vital(16));
        saveData(Items.SPRUCE_SAPLING, floral(96), vital(16));
        saveData(Items.ALLIUM, floral(5), chromatic(48), vital(1));
        saveData(Items.AZURE_BLUET, floral(5), chromatic(48), vital(1));
        saveData(Items.BLUE_ORCHID, floral(5), chromatic(48), vital(1));
        saveData(Items.COCOA_BEANS, floral(5), chromatic(48), vital(1));
        saveData(Items.CORNFLOWER, floral(5), chromatic(48), vital(1));
        saveData(Items.DANDELION, floral(5), chromatic(48), vital(1));
        saveData(Items.LILY_OF_THE_VALLEY, floral(5), chromatic(48), vital(1));
        saveData(Items.ORANGE_TULIP, floral(5), chromatic(48), vital(1));
        saveData(Items.OXEYE_DAISY, floral(5), chromatic(48), vital(1));
        saveData(Items.PINK_TULIP, floral(5), chromatic(48), vital(1));
        saveData(Items.POPPY, floral(5), chromatic(48), vital(1));
        saveData(Items.RED_TULIP, floral(5), chromatic(48), vital(1));
        saveData(Items.WHITE_TULIP, floral(5), chromatic(48), vital(1));
        saveData(Items.LILAC, floral(10), chromatic(96), vital(2));
        saveData(Items.PEONY, floral(10), chromatic(96), vital(2));
        saveData(Items.ROSE_BUSH, floral(10), chromatic(96), vital(2));
        saveData(Items.SUNFLOWER, floral(10), chromatic(96), vital(2));
        saveData(Items.BLACK_DYE, chromatic(48));
        saveData(Items.BLUE_DYE, chromatic(48));
        saveData(Items.BROWN_DYE, chromatic(48));
        saveData(Items.CYAN_DYE, chromatic(48));
        saveData(Items.GRAY_DYE, chromatic(48));
        saveData(Items.GREEN_DYE, chromatic(48));
        saveData(Items.LIGHT_BLUE_DYE, chromatic(48));
        saveData(Items.LIGHT_GRAY_DYE, chromatic(48));
        saveData(Items.LIME_DYE, chromatic(48));
        saveData(Items.MAGENTA_DYE, chromatic(48));
        saveData(Items.ORANGE_DYE, chromatic(48));
        saveData(Items.PINK_DYE, chromatic(48));
        saveData(Items.PURPLE_DYE, chromatic(48));
        saveData(Items.RED_DYE, chromatic(48));
        saveData(Items.WHITE_DYE, chromatic(48));
        saveData(Items.YELLOW_DYE, chromatic(48));
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
        saveData(Items.BLACKSTONE, earthen(60));
        saveData(Items.COBBLESTONE, earthen(60));
        saveData(Items.INFESTED_COBBLESTONE, earthen(60));
        saveData(Items.MOSSY_COBBLESTONE, earthen(60), floral(15));
        saveData(Items.DIRT, earthen(60));
        saveData(Items.FLINT, earthen(60));
        saveData(Items.GRAVEL, earthen(60));
        saveData(Items.PODZOL, earthen(60), fungal(15));
        saveData(Items.RED_SAND, earthen(60));
        saveData(Items.SAND, earthen(60));
        saveData(Items.GLASS, earthen(24), crystal(3));
        saveData(Items.APPLE, floral(15), vital(1));
        saveData(Items.BAMBOO, floral(8));
        saveData(Items.BASALT, earthen(60), molten(15));
        saveData(Items.BEEF, faunal(15), vital(1));
        saveData(Items.BEETROOT, floral(15), vital(1), chromatic(48));
        saveData(Items.BEETROOT_SEEDS, floral(15));
        saveData(Items.BLAZE_ROD, molten(120));
        saveData(Items.BONE, faunal(30), chromatic(144), vital(3), decay(60));
        saveData(Items.BROWN_MUSHROOM, fungal(15), vital(1));
        saveData(Items.BROWN_MUSHROOM_BLOCK, fungal(5));
        saveData(Items.CACTUS, floral(120), chromatic(48), vital (1));
        saveData(Items.CARROT, floral(60), vital(1));
        saveData(Items.CARVED_PUMPKIN, floral(5), weird(1));
        saveData(Items.CHARCOAL, molten(80), floral(32));
        saveData(Items.CHICKEN, faunal(15), vital(1));
        saveData(Items.CHORUS_FLOWER, floral(30), weird(6), vital(1));
        saveData(Items.CHORUS_FRUIT, floral(15), weird(4), vital(1));
        saveData(Items.CLAY_BALL, earthen(30));
        saveData(Items.COBWEB, faunal(15));
        saveData(Items.COD, faunal(15), vital(1));
        saveData(Items.CRIMSON_NYLIUM, earthen(60), fungal(15), chromatic(60));
        saveData(Items.CRIMSON_FUNGUS, fungal(15));
        saveData(Items.CRYING_OBSIDIAN, weird(60), molten(60), crystal(90), primordial(3));
        saveData(Items.EGG, faunal(15), vital(15));
        saveData(Items.END_ROD, radiant(20));
        saveData(Items.END_STONE, earthen(60), weird(5), vital(1));
        saveData(Items.ENDER_PEARL, weird(30));
        saveData(Items.FEATHER, faunal(15));
        saveData(Items.GHAST_TEAR, weird(30), crystal(24));
        saveData(Items.GILDED_BLACKSTONE, metal(240), regal(240), weird(60), earthen(60), molten(60));
        saveData(Items.GLOWSTONE_DUST, radiant(10));
        saveData(Items.BELL, earthen(120), regal(216), metal(180), floral(128));
        saveData(Items.GRASS_BLOCK, earthen(60), floral(15));
        saveData(Items.GUNPOWDER, energetic(60));
        saveData(Items.HONEYCOMB, honey(40), regal(32));
        saveData(Items.HONEY_BLOCK, honey(120), regal(96));
        saveData(Items.ICE, snow(60));
        saveData(Items.INK_SAC, faunal(15), chromatic(48));
        saveData(Items.KELP, floral(15), vital(1));
        saveData(Items.LILY_PAD, floral(15), vital(1));
        saveData(Items.MAGMA_BLOCK, slime(10), molten(10), earthen(60), decay(30));
        saveData(Items.MELON_SLICE, floral(15), vital(1));
        saveData(Items.MUSHROOM_STEM, fungal(5));
        saveData(Items.MUTTON, faunal(15), vital(1));
        saveData(Items.MYCELIUM, earthen(60), fungal(30));
        saveData(Items.NETHER_WART, weird(1), fungal(5));
        saveData(Items.WARPED_WART_BLOCK, weird(9), fungal(45));
        saveData(Items.NETHERRACK, earthen(60), molten(5), decay(5));
        saveData(Items.OBSIDIAN, molten(60), earthen(60), weird(15), crystal(15));
        saveData(Items.PHANTOM_MEMBRANE, decay(60), vital(1), weird(60));
        saveData(Items.POISONOUS_POTATO, floral(15), weird(3));
        saveData(Items.PORKCHOP, faunal(15), vital(1));
        saveData(Items.POTATO, floral(15), vital(1));
        saveData(Items.PRISMARINE_CRYSTALS, aquatic(12), crystal(3), radiant(6));
        saveData(Items.PRISMARINE_SHARD, aquatic(8), crystal(2), radiant(4));
        saveData(Items.PUFFERFISH, faunal(15), weird(6));
        saveData(Items.PUMPKIN, floral(60));
        saveData(Items.RABBIT, faunal(15), vital(1));
        saveData(Items.RABBIT_FOOT, faunal(30), weird(30));
        saveData(Items.RABBIT_HIDE, faunal(15));
        saveData(Items.RED_MUSHROOM, fungal(15), vital(1));
        saveData(Items.RED_MUSHROOM_BLOCK, fungal(5));
        saveData(Items.ROTTEN_FLESH, faunal(5), decay(60));
        saveData(Items.SALMON, faunal(15), vital(1));
        saveData(Items.SCUTE, faunal(60), primordial(3), vital(1));
        saveData(Items.SEA_PICKLE, faunal(30), aquatic(30), chromatic(48), radiant(30));
        saveData(Items.SEAGRASS, floral(15), vital(1));
        saveData(Items.SHULKER_SHELL, faunal(60), primordial(1), weird(15));
        saveData(Items.SHROOMLIGHT, radiant(40), fungal(60));
        saveData(Items.SLIME_BALL, slime(60));
        saveData(Items.SNOWBALL, snow(15));
        saveData(Items.SOUL_SAND, earthen(60), vital(15), decay(60));
        saveData(Items.SOUL_SOIL, earthen(60), vital(15), decay(60));
        saveData(Items.SPIDER_EYE, faunal(15), weird(5));
        saveData(Items.STRING, faunal(15));
        saveData(Items.WHITE_WOOL, faunal(15));
        saveData(Items.RED_WOOL, faunal(15), chromatic(15));
        saveData(Items.ORANGE_WOOL, faunal(15), chromatic(15));
        saveData(Items.YELLOW_WOOL, faunal(15), chromatic(15));
        saveData(Items.GREEN_WOOL, faunal(15), chromatic(15));
        saveData(Items.BLUE_WOOL, faunal(15), chromatic(15));
        saveData(Items.PURPLE_WOOL, faunal(15), chromatic(15));
        saveData(Items.PINK_WOOL, faunal(15), chromatic(15));
        saveData(Items.LIME_WOOL, faunal(15), chromatic(15));
        saveData(Items.CYAN_WOOL, faunal(15), chromatic(15));
        saveData(Items.GRAY_WOOL, faunal(15), chromatic(15));
        saveData(Items.LIGHT_GRAY_WOOL, faunal(15), chromatic(15));
        saveData(Items.BLACK_WOOL, faunal(15), chromatic(15));
        saveData(Items.BROWN_WOOL, faunal(15), chromatic(15));
        saveData(Items.LIGHT_BLUE_WOOL, faunal(15), chromatic(15));
        saveData(Items.MAGENTA_WOOL, faunal(15), chromatic(15));
        saveData(Items.SUGAR_CANE, floral(15));
        saveData(Items.SWEET_BERRIES, floral(5), vital(1));
        saveData(Items.TROPICAL_FISH, faunal(15), vital(15));
        saveData(Items.TURTLE_EGG, faunal(30), vital(30), primordial(1));
        saveData(Items.TWISTING_VINES, fungal(5));
        saveData(Items.VINE, floral(15));
        saveData(Items.WARPED_NYLIUM, earthen(60), molten(60), fungal(15));
        saveData(Items.WARPED_FUNGUS, fungal(15));
        saveData(Items.WEEPING_VINES, fungal(5));
        saveData(Items.WET_SPONGE, aquatic(60), weird(6), faunal(30), vital(15));
        saveData(Items.WHEAT, floral(15), vital(1));
        saveData(Items.WHEAT_SEEDS, floral(15), vital(1));

        // containers
        saveData(Items.LAVA_BUCKET, metal(216), molten(1000));
        saveData(Items.MILK_BUCKET, metal(216), faunal(15));
        saveData(Items.WATER_BUCKET, metal(216), aquatic(60));

        // goo buckets already contain their respective goo so, they're just buckets and their contents
        saveData(Registry.AQUATIC_BUCKET.get(), metal(216));
        saveData(Registry.CHROMATIC_BUCKET.get(), metal(216));
        saveData(Registry.CRYSTAL_BUCKET.get(), metal(216));
        saveData(Registry.DECAY_BUCKET.get(), metal(216));
        saveData(Registry.EARTHEN_BUCKET.get(), metal(216));
        saveData(Registry.ENERGETIC_BUCKET.get(), metal(216));
        saveData(Registry.FAUNAL_BUCKET.get(), metal(216));
        saveData(Registry.FLORAL_BUCKET.get(), metal(216));
        saveData(Registry.FUNGAL_BUCKET.get(), metal(216));
        saveData(Registry.HONEY_BUCKET.get(), metal(216));
        saveData(Registry.LOGIC_BUCKET.get(), metal(216));
        saveData(Registry.METAL_BUCKET.get(), metal(216));
        saveData(Registry.MOLTEN_BUCKET.get(), metal(216));
        saveData(Registry.PRIMORDIAL_BUCKET.get(), metal(216));
        saveData(Registry.RADIANT_BUCKET.get(), metal(216));
        saveData(Registry.REGAL_BUCKET.get(), metal(216));
        saveData(Registry.SLIME_BUCKET.get(), metal(216));
        saveData(Registry.SNOW_BUCKET.get(), metal(216));
        saveData(Registry.VITAL_BUCKET.get(), metal(216));
        saveData(Registry.WEIRD_BUCKET.get(), metal(216));

        // ore blocks
        saveData(Items.COAL_ORE, molten(80), earthen(16), forbidden(1));
        saveData(Items.COAL, molten(80), earthen(16));
        saveData(Items.DIAMOND_ORE, crystal(240), primordial(6), forbidden(1));
        saveData(Items.DIAMOND, crystal(240), primordial(6));
        saveData(Items.EMERALD_ORE, regal(120), crystal(120), forbidden(1));
        saveData(Items.EMERALD, regal(120), crystal(120));
        saveData(Items.NETHER_GOLD_ORE, metal(36), regal(72), forbidden(1));
        saveData(Items.GOLD_ORE, metal(36), regal(72), forbidden(1));
        saveData(Items.GOLD_INGOT, metal(36), regal(72));
        saveData(Items.IRON_ORE, metal(72), forbidden(1));
        saveData(Items.IRON_INGOT, metal(72));
        saveData(Items.REDSTONE_ORE, logic(15), forbidden(1));
        saveData(Items.REDSTONE, logic(15));
        saveData(Items.LAPIS_ORE, weird(4), chromatic(48), crystal(30), forbidden(1));
        saveData(Items.LAPIS_LAZULI, weird(4), chromatic(48), crystal(30));
        saveData(Items.NETHER_QUARTZ_ORE, earthen(12), crystal (12), forbidden(1));
        saveData(Items.QUARTZ, earthen(12), crystal (12));
        saveData(Items.ANCIENT_DEBRIS, metal(960), molten(120), primordial(15), forbidden(1));
        saveData(Items.NETHERITE_SCRAP, metal(960), molten(120), primordial(15));

        // controversial
        saveData(Items.NETHER_STAR, weird(120), radiant(120), decay(960), primordial(15), forbidden(1));
        saveData(Items.HEART_OF_THE_SEA, aquatic(120), weird(120), primordial(15), forbidden(1));
        saveData(Items.NAUTILUS_SHELL, aquatic(320), weird(60), primordial(3));

        ItemsRegistry.CRYSTALLIZED_GOO.forEach(this::registerLockedInfoForCrystallizedGoo);
        saveData(ItemsRegistry.CRYSTAL_COMB.get(), crystal(32));
    }

    private void saveData(Item item, CompoundInstance... instances) {
        saveData(newLinkedHashSet(item, new ItemStack(item)), instances);
    }

    private void saveData(LinkedHashSet<Object> items, CompoundInstance... instances) {
        save(specFor(items).withCompounds(instances));
    }

    private LinkedHashSet<Object> newLinkedHashSet(final Object... internal) {
        return new LinkedHashSet<>(Arrays.asList(internal));
    }

    private void registerLockedInfoForCrystallizedGoo(ResourceLocation resourceLocation, RegistryObject<CrystallizedGooAbstract> crystallizedGooAbstractRegistryObject) {
        Fluid f = crystallizedGooAbstractRegistryObject.get().gooType();

        final CompoundInstance instance = compoundsFromFluid(f, crystallizedGooAbstractRegistryObject.get().amount());
        if (instance == null)
            return;

        saveData(newLinkedHashSet(crystallizedGooAbstractRegistryObject.get(), new ItemStack(crystallizedGooAbstractRegistryObject.get())), instance);
    }

    @Nullable
    private CompoundInstance compoundsFromFluid(Fluid f, int amount) {
        final ICompoundType type = Registry.compoundFromFluid(f);
        if (type == null)
            return null;

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
    private static CompoundInstance primordial(double d) { return new CompoundInstance(Registry.PRIMORDIAL.get(), d); }
    private static CompoundInstance radiant(double d) { return new CompoundInstance(Registry.RADIANT.get(), d); }
    private static CompoundInstance regal(double d) { return new CompoundInstance(Registry.REGAL.get(), d); }
    private static CompoundInstance slime(double d) { return new CompoundInstance(Registry.SLIME.get(), d); }
    private static CompoundInstance snow(double d) { return new CompoundInstance(Registry.SNOW.get(), d); }
    private static CompoundInstance vital(double d) { return new CompoundInstance(Registry.VITAL.get(), d); }
    private static CompoundInstance weird(double d) { return new CompoundInstance(Registry.WEIRD.get(), d); }

    private static CompoundInstance forbidden(double d) { return new CompoundInstance(Registry.FORBIDDEN.get(), d); }
}
