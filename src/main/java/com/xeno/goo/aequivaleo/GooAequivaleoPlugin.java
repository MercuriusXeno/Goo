package com.xeno.goo.aequivaleo;

import com.google.common.collect.Sets;
import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.information.locked.ILockedCompoundInformationRegistry;
import com.ldtteam.aequivaleo.api.plugin.AequivaleoPlugin;
import com.ldtteam.aequivaleo.api.plugin.IAequivaleoPlugin;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.Items;
import net.minecraft.world.server.ServerWorld;

@AequivaleoPlugin
public class GooAequivaleoPlugin implements IAequivaleoPlugin
{
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
    
    @Override
    public String getId()
    {
        return "Goo";
    }

    @Override
    public void onReloadStartedFor(final ServerWorld world)
    {
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.ACACIA_LEAVES, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BIRCH_LEAVES, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DARK_OAK_LEAVES, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.JUNGLE_LEAVES, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.OAK_LEAVES, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SPRUCE_LEAVES, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.ACACIA_LOG, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BIRCH_LOG, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DARK_OAK_LOG, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.JUNGLE_LOG, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.OAK_LOG, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SPRUCE_LOG, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CRIMSON_STEM, Sets.newHashSet(fungal(960), chromatic(60), vital (60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.WARPED_STEM, Sets.newHashSet(fungal(960), chromatic(60), vital (60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.STRIPPED_ACACIA_LOG, Sets.newHashSet(floral(960)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.STRIPPED_BIRCH_LOG, Sets.newHashSet(floral(960)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.STRIPPED_DARK_OAK_LOG, Sets.newHashSet(floral(960)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.STRIPPED_JUNGLE_LOG, Sets.newHashSet(floral(960)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.STRIPPED_OAK_LOG, Sets.newHashSet(floral(960)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.STRIPPED_SPRUCE_LOG, Sets.newHashSet(floral(960)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.STRIPPED_CRIMSON_STEM, Sets.newHashSet(fungal(960), chromatic(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.STRIPPED_WARPED_STEM, Sets.newHashSet(fungal(960), chromatic(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.ACACIA_SAPLING, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BIRCH_SAPLING, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DARK_OAK_SAPLING, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.JUNGLE_SAPLING, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.OAK_SAPLING, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SPRUCE_SAPLING, Sets.newHashSet(floral(960), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.ALLIUM, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.AZURE_BLUET, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BLUE_ORCHID, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.COCOA_BEANS, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CORNFLOWER, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DANDELION, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.LILY_OF_THE_VALLEY, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.ORANGE_TULIP, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.OXEYE_DAISY, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.PINK_TULIP, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.POPPY, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.RED_TULIP, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.WHITE_TULIP, Sets.newHashSet(floral(60), chromatic(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.LILAC, Sets.newHashSet(floral(120), chromatic(480), vital(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.PEONY, Sets.newHashSet(floral(120), chromatic(480), vital(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.ROSE_BUSH, Sets.newHashSet(floral(120), chromatic(480), vital(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SUNFLOWER, Sets.newHashSet(floral(120), chromatic(480), vital(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BLACK_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BLUE_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BROWN_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CYAN_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.GRAY_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.GREEN_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.LIGHT_BLUE_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.LIGHT_GRAY_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.LIME_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.MAGENTA_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.ORANGE_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.PINK_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.PURPLE_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.RED_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.WHITE_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.YELLOW_DYE, Sets.newHashSet(chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BRAIN_CORAL, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BUBBLE_CORAL, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_BRAIN_CORAL, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_BUBBLE_CORAL, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_FIRE_CORAL, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_HORN_CORAL, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_TUBE_CORAL, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.FIRE_CORAL, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.HORN_CORAL, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.TUBE_CORAL, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BRAIN_CORAL_BLOCK, Sets.newHashSet(faunal(480), vital(480)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BUBBLE_CORAL_BLOCK, Sets.newHashSet(faunal(480), vital(480)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_BRAIN_CORAL_BLOCK, Sets.newHashSet(faunal(480)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_BUBBLE_CORAL_BLOCK, Sets.newHashSet(faunal(480)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_FIRE_CORAL_BLOCK, Sets.newHashSet(faunal(480)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_HORN_CORAL_BLOCK, Sets.newHashSet(faunal(480)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_TUBE_CORAL_BLOCK, Sets.newHashSet(faunal(480)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.FIRE_CORAL_BLOCK, Sets.newHashSet(faunal(480), vital(480)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.HORN_CORAL_BLOCK, Sets.newHashSet(faunal(480), vital(480)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.TUBE_CORAL_BLOCK, Sets.newHashSet(faunal(480), vital(480)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BRAIN_CORAL_FAN, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BUBBLE_CORAL_FAN, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_BRAIN_CORAL_FAN, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_BUBBLE_CORAL_FAN, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_FIRE_CORAL_FAN, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_HORN_CORAL_FAN, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_TUBE_CORAL_FAN, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.FIRE_CORAL_FAN, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.HORN_CORAL_FAN, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.TUBE_CORAL_FAN, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DEAD_BUSH, Sets.newHashSet(floral(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.FERN, Sets.newHashSet(floral(120), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.GRASS, Sets.newHashSet(floral(120), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CRIMSON_ROOTS, Sets.newHashSet(fungal(120), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.NETHER_SPROUTS, Sets.newHashSet(fungal(120), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.WARPED_ROOTS, Sets.newHashSet(fungal(120), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.LARGE_FERN, Sets.newHashSet(floral(240), vital(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.TALL_GRASS, Sets.newHashSet(floral(240), vital(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BLACKSTONE, Sets.newHashSet(earthen(1080)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.COBBLESTONE, Sets.newHashSet(earthen(1080)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DIRT, Sets.newHashSet(earthen(1080)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.FLINT, Sets.newHashSet(earthen(1080)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.GRAVEL, Sets.newHashSet(earthen(1080)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.PODZOL, Sets.newHashSet(earthen(1080)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.RED_SAND, Sets.newHashSet(earthen(1080)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SAND, Sets.newHashSet(earthen(1080)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.APPLE, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BAMBOO, Sets.newHashSet(floral(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BASALT, Sets.newHashSet(earthen(960), obsidian(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BEEF, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BEETROOT, Sets.newHashSet(floral(60), vital(60), chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BEETROOT_SEEDS, Sets.newHashSet(floral(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BLAZE_ROD, Sets.newHashSet(molten(120), energetic(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BONE, Sets.newHashSet(faunal(180), chromatic(360), vital(180), decay(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BROWN_MUSHROOM, Sets.newHashSet(fungal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.BROWN_MUSHROOM_BLOCK, Sets.newHashSet(fungal(960)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CACTUS, Sets.newHashSet(floral(960), chromatic(240), vital (60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CARROT, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CARVED_PUMPKIN, Sets.newHashSet(floral(60), weird(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CHARCOAL, Sets.newHashSet(molten(96), floral(96)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CHICKEN, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CHORUS_FLOWER, Sets.newHashSet(floral(240), weird(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CHORUS_FRUIT, Sets.newHashSet(floral(60), weird(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CLAY_BALL, Sets.newHashSet(earthen(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.COAL, Sets.newHashSet(molten(96), earthen(72)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.COBWEB, Sets.newHashSet(faunal(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.COD, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CRIMSON_NYLIUM, Sets.newHashSet(earthen(480d), fungal(480d), chromatic(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CRIMSON_FUNGUS, Sets.newHashSet(fungal(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.CRYING_OBSIDIAN, Sets.newHashSet(weird(60), obsidian(960)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.DIAMOND, Sets.newHashSet(crystal(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.EGG, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.EMERALD, Sets.newHashSet(regal(60), crystal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.END_STONE, Sets.newHashSet(earthen(720), weird(240), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.ENDER_PEARL, Sets.newHashSet(weird(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.FEATHER, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.GHAST_TEAR, Sets.newHashSet(weird(240), crystal(24)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.GILDED_BLACKSTONE, Sets.newHashSet(metal(240), regal(240), weird(60), earthen(240), obsidian(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.GLOWSTONE_DUST, Sets.newHashSet(weird(60), energetic(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.GOLD_INGOT, Sets.newHashSet(metal(36), regal(72)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.GRASS_BLOCK, Sets.newHashSet(earthen(960), floral(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.GUNPOWDER, Sets.newHashSet(molten(60), energetic(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.HONEYCOMB, Sets.newHashSet(honey(120), regal(24)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.HONEY_BLOCK, Sets.newHashSet(honey(960), regal(60), crystal(4)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.ICE, Sets.newHashSet(snow(960), aquatic(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.INK_SAC, Sets.newHashSet(faunal(60), chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.IRON_INGOT, Sets.newHashSet(metal(72)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.KELP, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.LAPIS_LAZULI, Sets.newHashSet(weird(48), chromatic(240), crystal(4)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.LILY_PAD, Sets.newHashSet(floral(60), aquatic(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.MELON_SLICE, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.MUSHROOM_STEM, Sets.newHashSet(fungal(960)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.MUTTON, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.MYCELIUM, Sets.newHashSet(earthen(960d), fungal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.NETHER_WART, Sets.newHashSet(weird(60), fungal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.NETHERITE_SCRAP, Sets.newHashSet(metal(960), obsidian(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.NETHERRACK, Sets.newHashSet(earthen(960), molten(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.OBSIDIAN, Sets.newHashSet(obsidian(120), molten(60), earthen(840)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.PHANTOM_MEMBRANE, Sets.newHashSet(decay(60), vital(60), weird(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.POISONOUS_POTATO, Sets.newHashSet(floral(60), weird(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.PORKCHOP, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.POTATO, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.PRISMARINE_CRYSTALS, Sets.newHashSet(aquatic(36), crystal(2), weird(36), energetic(72)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.PRISMARINE_SHARD, Sets.newHashSet(aquatic(36), crystal(1), weird(72)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.PUFFERFISH, Sets.newHashSet(faunal(60), weird(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.PUMPKIN, Sets.newHashSet(floral(960)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.QUARTZ, Sets.newHashSet(earthen(60), crystal (1)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.RABBIT, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.RABBIT_FOOT, Sets.newHashSet(faunal(60), weird(180)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.RABBIT_HIDE, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.RED_MUSHROOM, Sets.newHashSet(fungal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.RED_MUSHROOM_BLOCK, Sets.newHashSet(fungal(960)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.REDSTONE, Sets.newHashSet(logic(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.ROTTEN_FLESH, Sets.newHashSet(faunal(60), decay(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SALMON, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SCUTE, Sets.newHashSet(faunal(60), weird(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SEA_PICKLE, Sets.newHashSet(faunal(60), aquatic(60), chromatic(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SEAGRASS, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SHULKER_SHELL, Sets.newHashSet(faunal(60), weird(180)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SHROOMLIGHT, Sets.newHashSet(weird(120), fungal(360)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SLIME_BALL, Sets.newHashSet(slime(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SNOWBALL, Sets.newHashSet(snow(120), aquatic(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SOUL_SAND, Sets.newHashSet(earthen(720), vital(240), decay(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SOUL_SOIL, Sets.newHashSet(earthen(720), vital(240), decay(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SPIDER_EYE, Sets.newHashSet(faunal(60), weird(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.STRING, Sets.newHashSet(faunal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SUGAR_CANE, Sets.newHashSet(floral(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.SWEET_BERRIES, Sets.newHashSet(floral(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.TROPICAL_FISH, Sets.newHashSet(faunal(60), vital(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.TURTLE_EGG, Sets.newHashSet(faunal(60), vital(180), weird(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.TWISTING_VINES, Sets.newHashSet(fungal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.VINE, Sets.newHashSet(floral(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.WARPED_NYLIUM, Sets.newHashSet(earthen(720), molten(60), fungal(240)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.WARPED_FUNGUS, Sets.newHashSet(fungal(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.WEEPING_VINES, Sets.newHashSet(fungal(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.WET_SPONGE, Sets.newHashSet(aquatic(60), weird(60), faunal(120), vital(720)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.WHEAT, Sets.newHashSet(floral(60)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.WHEAT_SEEDS, Sets.newHashSet((floral(60))));

        // containers
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.LAVA_BUCKET, Sets.newHashSet(metal(216), molten(1080)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.MILK_BUCKET, Sets.newHashSet(metal(216), faunal(120)));
        ILockedCompoundInformationRegistry.getInstance(world.func_234923_W_()).registerLocking(Items.WATER_BUCKET, Sets.newHashSet(metal(216), aquatic(960)));

        Equivalencies.resetFurnaceProducts(world);
    }
}
