package com.xeno.goo.aequivaleo.bootstrap;

import com.google.common.collect.Sets;
import com.ldtteam.aequivaleo.api.compound.ICompoundInstance;
import com.ldtteam.aequivaleo.api.compound.implementation.SimpleCompoundInstance;
import com.ldtteam.aequivaleo.api.compound.information.locked.ILockedCompoundInformationRegistry;
import com.ldtteam.aequivaleo.api.event.OnWorldDataReloadedEvent;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.xeno.goo.aequivaleo.EntryHelper.*;

public class GooValueBootstrapper
{
    public static ICompoundInstance aquatic(double d) { return new SimpleCompoundInstance(Registry.AQUATIC.get(), d); }
    public static ICompoundInstance chromatic(double d) { return new SimpleCompoundInstance(Registry.CHROMATIC.get(), d); }
    public static ICompoundInstance crystal(double d) { return new SimpleCompoundInstance(Registry.CRYSTAL.get(), d); }
    public static ICompoundInstance decay(double d) { return new SimpleCompoundInstance(Registry.DECAY.get(), d); }
    public static ICompoundInstance earthen(double d) { return new SimpleCompoundInstance(Registry.EARTHEN.get(), d); }
    public static ICompoundInstance energetic(double d) { return new SimpleCompoundInstance(Registry.ENERGETIC.get(), d); }
    public static ICompoundInstance faunal(double d) { return new SimpleCompoundInstance(Registry.FAUNAL.get(), d); }
    public static ICompoundInstance floral(double d) { return new SimpleCompoundInstance(Registry.FLORAL.get(), d); }
    public static ICompoundInstance fungal(double d) { return new SimpleCompoundInstance(Registry.FUNGAL.get(), d); }
    public static ICompoundInstance honey(double d) { return new SimpleCompoundInstance(Registry.HONEY.get(), d); }
    public static ICompoundInstance logic(double d) { return new SimpleCompoundInstance(Registry.LOGIC.get(), d); }
    public static ICompoundInstance metal(double d) { return new SimpleCompoundInstance(Registry.METAL.get(), d); }
    public static ICompoundInstance molten(double d) { return new SimpleCompoundInstance(Registry.MOLTEN.get(), d); }
    public static ICompoundInstance obsidian(double d) { return new SimpleCompoundInstance(Registry.OBSIDIAN.get(), d); }
    public static ICompoundInstance regal(double d) { return new SimpleCompoundInstance(Registry.REGAL.get(), d); }
    public static ICompoundInstance slime(double d) { return new SimpleCompoundInstance(Registry.SLIME.get(), d); }
    public static ICompoundInstance snow(double d) { return new SimpleCompoundInstance(Registry.SNOW.get(), d); }
    public static ICompoundInstance vital(double d) { return new SimpleCompoundInstance(Registry.VITAL.get(), d); }
    public static ICompoundInstance weird(double d) { return new SimpleCompoundInstance(Registry.WEIRD.get(), d); }

    public static void onReload(OnWorldDataReloadedEvent event)
    {
        registerLocking(getRegistry(event), Items.ACACIA_LEAVES, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.BIRCH_LEAVES, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.DARK_OAK_LEAVES, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.JUNGLE_LEAVES, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.OAK_LEAVES, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.SPRUCE_LEAVES, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.ACACIA_LOG, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.BIRCH_LOG, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.DARK_OAK_LOG, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.JUNGLE_LOG, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.OAK_LOG, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.SPRUCE_LOG, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.CRIMSON_STEM, fungal(960), chromatic(60), vital (60));
        registerLocking(getRegistry(event), Items.WARPED_STEM, fungal(960), chromatic(60), vital (60));
        registerLocking(getRegistry(event), Items.STRIPPED_ACACIA_LOG, floral(960));
        registerLocking(getRegistry(event), Items.STRIPPED_BIRCH_LOG, floral(960));
        registerLocking(getRegistry(event), Items.STRIPPED_DARK_OAK_LOG, floral(960));
        registerLocking(getRegistry(event), Items.STRIPPED_JUNGLE_LOG, floral(960));
        registerLocking(getRegistry(event), Items.STRIPPED_OAK_LOG, floral(960));
        registerLocking(getRegistry(event), Items.STRIPPED_SPRUCE_LOG, floral(960));
        registerLocking(getRegistry(event), Items.STRIPPED_CRIMSON_STEM, fungal(960), chromatic(60));
        registerLocking(getRegistry(event), Items.STRIPPED_WARPED_STEM, fungal(960), chromatic(60));
        registerLocking(getRegistry(event), Items.ACACIA_SAPLING, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.BIRCH_SAPLING, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.DARK_OAK_SAPLING, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.JUNGLE_SAPLING, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.OAK_SAPLING, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.SPRUCE_SAPLING, floral(960), vital(60));
        registerLocking(getRegistry(event), Items.ALLIUM, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.AZURE_BLUET, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.BLUE_ORCHID, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.COCOA_BEANS, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.CORNFLOWER, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.DANDELION, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.LILY_OF_THE_VALLEY, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.ORANGE_TULIP, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.OXEYE_DAISY, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.PINK_TULIP, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.POPPY, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.RED_TULIP, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.WHITE_TULIP, floral(60), chromatic(240), vital(60));
        registerLocking(getRegistry(event), Items.LILAC, floral(120), chromatic(480), vital(120));
        registerLocking(getRegistry(event), Items.PEONY, floral(120), chromatic(480), vital(120));
        registerLocking(getRegistry(event), Items.ROSE_BUSH, floral(120), chromatic(480), vital(120));
        registerLocking(getRegistry(event), Items.SUNFLOWER, floral(120), chromatic(480), vital(120));
        registerLocking(getRegistry(event), Items.BLACK_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.BLUE_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.BROWN_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.CYAN_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.GRAY_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.GREEN_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.LIGHT_BLUE_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.LIGHT_GRAY_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.LIME_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.MAGENTA_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.ORANGE_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.PINK_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.PURPLE_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.RED_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.WHITE_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.YELLOW_DYE, chromatic(240));
        registerLocking(getRegistry(event), Items.BRAIN_CORAL, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.BUBBLE_CORAL, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.DEAD_BRAIN_CORAL, faunal(60));
        registerLocking(getRegistry(event), Items.DEAD_BUBBLE_CORAL, faunal(60));
        registerLocking(getRegistry(event), Items.DEAD_FIRE_CORAL, faunal(60));
        registerLocking(getRegistry(event), Items.DEAD_HORN_CORAL, faunal(60));
        registerLocking(getRegistry(event), Items.DEAD_TUBE_CORAL, faunal(60));
        registerLocking(getRegistry(event), Items.FIRE_CORAL, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.HORN_CORAL, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.TUBE_CORAL, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.BRAIN_CORAL_BLOCK, faunal(480), vital(480));
        registerLocking(getRegistry(event), Items.BUBBLE_CORAL_BLOCK, faunal(480), vital(480));
        registerLocking(getRegistry(event), Items.DEAD_BRAIN_CORAL_BLOCK, faunal(480));
        registerLocking(getRegistry(event), Items.DEAD_BUBBLE_CORAL_BLOCK, faunal(480));
        registerLocking(getRegistry(event), Items.DEAD_FIRE_CORAL_BLOCK, faunal(480));
        registerLocking(getRegistry(event), Items.DEAD_HORN_CORAL_BLOCK, faunal(480));
        registerLocking(getRegistry(event), Items.DEAD_TUBE_CORAL_BLOCK, faunal(480));
        registerLocking(getRegistry(event), Items.FIRE_CORAL_BLOCK, faunal(480), vital(480));
        registerLocking(getRegistry(event), Items.HORN_CORAL_BLOCK, faunal(480), vital(480));
        registerLocking(getRegistry(event), Items.TUBE_CORAL_BLOCK, faunal(480), vital(480));
        registerLocking(getRegistry(event), Items.BRAIN_CORAL_FAN, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.BUBBLE_CORAL_FAN, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.DEAD_BRAIN_CORAL_FAN, faunal(60));
        registerLocking(getRegistry(event), Items.DEAD_BUBBLE_CORAL_FAN, faunal(60));
        registerLocking(getRegistry(event), Items.DEAD_FIRE_CORAL_FAN, faunal(60));
        registerLocking(getRegistry(event), Items.DEAD_HORN_CORAL_FAN, faunal(60));
        registerLocking(getRegistry(event), Items.DEAD_TUBE_CORAL_FAN, faunal(60));
        registerLocking(getRegistry(event), Items.FIRE_CORAL_FAN, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.HORN_CORAL_FAN, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.TUBE_CORAL_FAN, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.DEAD_BUSH, floral(120));
        registerLocking(getRegistry(event), Items.FERN, floral(120), vital(60));
        registerLocking(getRegistry(event), Items.GRASS, floral(120), vital(60));
        registerLocking(getRegistry(event), Items.CRIMSON_ROOTS, fungal(120), vital(60));
        registerLocking(getRegistry(event), Items.NETHER_SPROUTS, fungal(120), vital(60));
        registerLocking(getRegistry(event), Items.WARPED_ROOTS, fungal(120), vital(60));
        registerLocking(getRegistry(event), Items.LARGE_FERN, floral(240), vital(120));
        registerLocking(getRegistry(event), Items.TALL_GRASS, floral(240), vital(120));
        registerLocking(getRegistry(event), Items.BLACKSTONE, earthen(1080));
        registerLocking(getRegistry(event), Items.COBBLESTONE, earthen(1080));
        registerLocking(getRegistry(event), Items.DIRT, earthen(1080));
        registerLocking(getRegistry(event), Items.FLINT, earthen(1080));
        registerLocking(getRegistry(event), Items.GRAVEL, earthen(1080));
        registerLocking(getRegistry(event), Items.PODZOL, earthen(1080));
        registerLocking(getRegistry(event), Items.RED_SAND, earthen(1080));
        registerLocking(getRegistry(event), Items.SAND, earthen(1080));
        registerLocking(getRegistry(event), Items.APPLE, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.BAMBOO, floral(60));
        registerLocking(getRegistry(event), Items.BASALT, earthen(960), obsidian(60));
        registerLocking(getRegistry(event), Items.BEEF, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.BEETROOT, floral(60), vital(60), chromatic(240));
        registerLocking(getRegistry(event), Items.BEETROOT_SEEDS, floral(60));
        registerLocking(getRegistry(event), Items.BLAZE_ROD, molten(120), energetic(60), vital(60));
        registerLocking(getRegistry(event), Items.BONE, faunal(180), chromatic(360), vital(180), decay(60));
        registerLocking(getRegistry(event), Items.BROWN_MUSHROOM, fungal(60), vital(60));
        registerLocking(getRegistry(event), Items.BROWN_MUSHROOM_BLOCK, fungal(960));
        registerLocking(getRegistry(event), Items.CACTUS, floral(960), chromatic(240), vital (60));
        registerLocking(getRegistry(event), Items.CARROT, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.CARVED_PUMPKIN, floral(60), weird(60));
        registerLocking(getRegistry(event), Items.CHARCOAL, molten(96), floral(96));
        registerLocking(getRegistry(event), Items.CHICKEN, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.CHORUS_FLOWER, floral(240), weird(240), vital(60));
        registerLocking(getRegistry(event), Items.CHORUS_FRUIT, floral(60), weird(60), vital(60));
        registerLocking(getRegistry(event), Items.CLAY_BALL, earthen(240));
        registerLocking(getRegistry(event), Items.COAL, molten(96), earthen(72));
        registerLocking(getRegistry(event), Items.COBWEB, faunal(120));
        registerLocking(getRegistry(event), Items.COD, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.CRIMSON_NYLIUM, earthen(480d), fungal(480d), chromatic(60));
        registerLocking(getRegistry(event), Items.CRIMSON_FUNGUS, fungal(120));
        registerLocking(getRegistry(event), Items.CRYING_OBSIDIAN, weird(60), obsidian(960));
        registerLocking(getRegistry(event), Items.DIAMOND, crystal(120));
        registerLocking(getRegistry(event), Items.EGG, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.EMERALD, regal(60), crystal(60));
        registerLocking(getRegistry(event), Items.END_STONE, earthen(720), weird(240), vital(60));
        registerLocking(getRegistry(event), Items.ENDER_PEARL, weird(120));
        registerLocking(getRegistry(event), Items.FEATHER, faunal(60));
        registerLocking(getRegistry(event), Items.GHAST_TEAR, weird(240), crystal(24));
        registerLocking(getRegistry(event), Items.GILDED_BLACKSTONE, metal(240), regal(240), weird(60), earthen(240), obsidian(240));
        registerLocking(getRegistry(event), Items.GLOWSTONE_DUST, weird(60), energetic(60));
        registerLocking(getRegistry(event), Items.GOLD_INGOT, metal(36), regal(72));
        registerLocking(getRegistry(event), Items.GRASS_BLOCK, earthen(960), floral(60));
        registerLocking(getRegistry(event), Items.GUNPOWDER, molten(60), energetic(120));
        registerLocking(getRegistry(event), Items.HONEYCOMB, honey(120), regal(24));
        registerLocking(getRegistry(event), Items.HONEY_BLOCK, honey(960), regal(60));
        registerLocking(getRegistry(event), Items.ICE, snow(960), aquatic(60));
        registerLocking(getRegistry(event), Items.INK_SAC, faunal(60), chromatic(240));
        registerLocking(getRegistry(event), Items.IRON_INGOT, metal(72));
        registerLocking(getRegistry(event), Items.KELP, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.LAPIS_LAZULI, weird(48), chromatic(240), crystal(4));
        registerLocking(getRegistry(event), Items.LILY_PAD, floral(60), aquatic(60), vital(60));
        registerLocking(getRegistry(event), Items.MELON_SLICE, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.MUSHROOM_STEM, fungal(960));
        registerLocking(getRegistry(event), Items.MUTTON, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.MYCELIUM, earthen(960d), fungal(60));
        registerLocking(getRegistry(event), Items.NETHER_WART, weird(60), fungal(60));
        registerLocking(getRegistry(event), Items.NETHERITE_SCRAP, metal(960), obsidian(60));
        registerLocking(getRegistry(event), Items.NETHERRACK, earthen(960), molten(60));
        registerLocking(getRegistry(event), Items.OBSIDIAN, obsidian(960), molten(60));
        registerLocking(getRegistry(event), Items.PHANTOM_MEMBRANE, decay(60), vital(60), weird(60));
        registerLocking(getRegistry(event), Items.POISONOUS_POTATO, floral(60), weird(60), vital(60));
        registerLocking(getRegistry(event), Items.PORKCHOP, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.POTATO, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.PRISMARINE_CRYSTALS, aquatic(36), crystal(2), weird(36), energetic(72));
        registerLocking(getRegistry(event), Items.PRISMARINE_SHARD, aquatic(36), crystal(1), weird(72));
        registerLocking(getRegistry(event), Items.PUFFERFISH, faunal(60), weird(60));
        registerLocking(getRegistry(event), Items.PUMPKIN, floral(960));
        registerLocking(getRegistry(event), Items.QUARTZ, earthen(60), crystal (1));
        registerLocking(getRegistry(event), Items.RABBIT, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.RABBIT_FOOT, faunal(60), weird(180));
        registerLocking(getRegistry(event), Items.RABBIT_HIDE, faunal(60));
        registerLocking(getRegistry(event), Items.RED_MUSHROOM, fungal(60));
        registerLocking(getRegistry(event), Items.RED_MUSHROOM_BLOCK, fungal(960));
        registerLocking(getRegistry(event), Items.REDSTONE, logic(120));
        registerLocking(getRegistry(event), Items.ROTTEN_FLESH, faunal(60), decay(60));
        registerLocking(getRegistry(event), Items.SALMON, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.SCUTE, faunal(60), weird(60), vital(60));
        registerLocking(getRegistry(event), Items.SEA_PICKLE, faunal(60), aquatic(60), chromatic(240));
        registerLocking(getRegistry(event), Items.SEAGRASS, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.SHULKER_SHELL, faunal(60), weird(180));
        registerLocking(getRegistry(event), Items.SHROOMLIGHT, weird(120), fungal(360));
        registerLocking(getRegistry(event), Items.SLIME_BALL, slime(240));
        registerLocking(getRegistry(event), Items.SNOWBALL, snow(120), aquatic(60));
        registerLocking(getRegistry(event), Items.SOUL_SAND, earthen(720), vital(240), decay(60));
        registerLocking(getRegistry(event), Items.SOUL_SOIL, earthen(720), vital(240), decay(60));
        registerLocking(getRegistry(event), Items.SPIDER_EYE, faunal(60), weird(60));
        registerLocking(getRegistry(event), Items.STRING, faunal(60));
        registerLocking(getRegistry(event), Items.SUGAR_CANE, floral(60));
        registerLocking(getRegistry(event), Items.SWEET_BERRIES, floral(60), vital(60));
        registerLocking(getRegistry(event), Items.TROPICAL_FISH, faunal(60), vital(60));
        registerLocking(getRegistry(event), Items.TURTLE_EGG, faunal(60), vital(180), weird(60));
        registerLocking(getRegistry(event), Items.TWISTING_VINES, fungal(60));
        registerLocking(getRegistry(event), Items.VINE, floral(60));
        registerLocking(getRegistry(event), Items.WARPED_NYLIUM, earthen(720), molten(60), fungal(240));
        registerLocking(getRegistry(event), Items.WARPED_FUNGUS, fungal(120));
        registerLocking(getRegistry(event), Items.WEEPING_VINES, fungal(60));
        registerLocking(getRegistry(event), Items.WET_SPONGE, aquatic(60), weird(60), faunal(120), vital(720));
        registerLocking(getRegistry(event), Items.WHEAT, floral(60));
        registerLocking(getRegistry(event), Items.WHEAT_SEEDS, (floral(60)));

        // containers
        registerLocking(getRegistry(event), Items.LAVA_BUCKET, metal(216), molten(1080));
        registerLocking(getRegistry(event), Items.MILK_BUCKET, metal(216), faunal(120));
        registerLocking(getRegistry(event), Items.WATER_BUCKET, metal(216), aquatic(960));
    }

    private static ILockedCompoundInformationRegistry getRegistry(OnWorldDataReloadedEvent event)
    {
        return event.getApi().getLockedCompoundWrapperToTypeRegistry(event.getWorld().getWorld().func_234923_W_());
    }

    public static void registerLocking(ILockedCompoundInformationRegistry reg, Item stack, ICompoundInstance... compounds) {
        reg.registerLocking(new ItemStack(stack), Sets.newHashSet(makeSet(compounds)));
    }

    private static Set<ICompoundInstance> makeSet(ICompoundInstance[] compounds)
    {
        Set<ICompoundInstance> result = new HashSet<>();
        Collections.addAll(result, compounds);
        return result;
    }
}
