package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.CrystalBlock;
import com.xeno.goo.blocks.GooBulbItem;
import com.xeno.goo.client.ISTERProvider;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ItemsRegistry {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GooMod.MOD_ID);
    public static void initialize() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    // major items
    public static final RegistryObject<GooAndYou> GOO_AND_YOU = ITEMS.register("goo_and_you", GooAndYou::new);
    public static final RegistryObject<Gasket> GASKET = ITEMS.register("gasket", Gasket::new);
    public static final RegistryObject<NetheriteAsh> NETHERITE_ASH = ITEMS.register("netherite_ash", NetheriteAsh::new);
    public static final RegistryObject<StygianWeepings> STYGIAN_WEEPINGS = ITEMS.register("stygian_weepings", StygianWeepings::new);
    public static final RegistryObject<PassivatedNugget> PASSIVATED_NUGGET = ITEMS.register("passivated_nugget", PassivatedNugget::new);
    public static final RegistryObject<PassivatedIngot> PASSIVATED_INGOT = ITEMS.register("passivated_ingot", PassivatedIngot::new);
    public static final RegistryObject<PassivatedAmalgam> PASSIVATED_AMALGAM = ITEMS.register("passivated_amalgam", PassivatedAmalgam::new);
    public static final RegistryObject<CrystalComb> CRYSTAL_COMB = ITEMS.register("crystal_comb", CrystalComb::new);
    public static final RegistryObject<Vessel> VESSEL = ITEMS.register("basin", Vessel::new);
    public static final RegistryObject<Gauntlet> GAUNTLET = ITEMS.register("gauntlet", Gauntlet::new);
    public static final RegistryObject<GooSnailCaptured> SNAIL = ITEMS.register("snail", GooSnailCaptured::new);

    // block items
    public static final RegistryObject<Item> GOO_BULB = ITEMS
            .register("goo_bulb", () -> new GooBulbItem(BlocksRegistry.Bulb.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64).setISTER(ISTERProvider::gooBulb)));
    public static final RegistryObject<Item> GOO_PUMP = ITEMS
            .register("goo_pump", () -> new BlockItem(BlocksRegistry.Pump.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64).setISTER(ISTERProvider::pump)));
    public static final RegistryObject<Item> GOOIFIER = ITEMS
            .register("gooifier", () -> new BlockItem(BlocksRegistry.Gooifier.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64)));
    public static final RegistryObject<Item> DEGRADER = ITEMS
            .register("crucible", () -> new BlockItem(BlocksRegistry.Degrader.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64).setISTER(ISTERProvider::degrader)));
    public static final RegistryObject<Item> MIXER = ITEMS
            .register("mixer", () -> new BlockItem(BlocksRegistry.Mixer.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64).setISTER(ISTERProvider::mixer)));
    public static final RegistryObject<Item> SOLIDIFIER = ITEMS
            .register("solidifier", () -> new BlockItem(BlocksRegistry.Solidifier.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64)));
    public static final RegistryObject<Item> LOBBER = ITEMS
            .register("lobber", () -> new BlockItem(BlocksRegistry.Lobber.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64)));
    public static final RegistryObject<Item> DRAIN = ITEMS
            .register("drain", () -> new BlockItem(BlocksRegistry.Drain.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64)));
    public static final RegistryObject<Item> RADIANT_LIGHT = ITEMS
            .register("radiant_light", () -> new BlockItem(BlocksRegistry.RadiantLight.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64)));
    public static final RegistryObject<Item> CRYSTAL_NEST = ITEMS
            .register("crystal_nest", () -> new BlockItem(BlocksRegistry.CrystalNest.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64)));
    public static final RegistryObject<Item> TROUGH = ITEMS
            .register("goo_trough", () -> new BlockItem(BlocksRegistry.Trough.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64).setISTER(ISTERProvider::trough)));
    public static final RegistryObject<Item> CRUCIBLE = ITEMS
            .register("melter", () -> new BlockItem(BlocksRegistry.Crucible.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64).setISTER(ISTERProvider::crucible)));
    public static final RegistryObject<Item> PAD = ITEMS
            .register("goo_pad", () -> new BlockItem(BlocksRegistry.Pad.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64).setISTER(ISTERProvider::pad)));
    public static final RegistryObject<Item> PASSIVATED_BLOCK = ITEMS
            .register("passivated_block", () -> new BlockItem(BlocksRegistry.PassivatedBlock.get(), new Item.Properties().group(GooMod.ITEM_GROUP)
                    .maxStackSize(64)));

    // eggs
    public static final RegistryObject<Item> GOO_BEE_SPAWN_EGG = ITEMS.register("goo_bee_spawn_egg", Registry.makeEgg(Registry.GOO_BEE, 0xff7cbdc4, 0xffa674cf));
    public static final RegistryObject<Item> GOO_SNAIL_SPAWN_EGG = ITEMS.register("goo_snail_spawn_egg", Registry.makeEgg(Registry.GOO_SNAIL, 0xff6b6656, 0xffc7c3b3));
    // public static final RegistryObject<Item> LIGHTNING_BUG_SPAWN_EGG = ITEMS.register("lightning_bug_spawn_egg", Registry.makeEgg(Registry.LIGHTING_BUG, 0xffffffcc, 0xffffcc99));

    private final static Map<String, Integer> crystallizedGooVariants = new HashMap<>();
    static {
        crystallizedGooVariants.put("sliver", 10);
        crystallizedGooVariants.put("shard", 100);
        crystallizedGooVariants.put("crystal", 1000);
        crystallizedGooVariants.put("chunk", 10000);
        crystallizedGooVariants.put("slab", 100000);
    }

    public static final Map<ResourceLocation, RegistryObject<CrystallizedGooAbstract>> CRYSTALLIZED_GOO = new HashMap<>();
    public static final Map<ResourceLocation, RegistryObject<Item>> CRYSTAL_BLOCKS = new HashMap<>();
    // crystallized goo
    static {
        crystallizedGooVariants.forEach(ItemsRegistry::registerCrystalGooForType);
        BlocksRegistry.CrystalBlocks.forEach(ItemsRegistry::registerCrystalBlockItem);
    }

    private static void registerCrystalBlockItem(ResourceLocation resourceLocation, RegistryObject<CrystalBlock> crystalBlockRegistryObject) {
        CRYSTAL_BLOCKS.put(resourceLocation, ITEMS
                .register(resourceLocation.getPath(), () -> new BlockItem(crystalBlockRegistryObject.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64))));
    }

    private static void registerCrystalGooForType(String crystalType, Integer gooAmount) {
        Registry.FluidSuppliers.forEach((k, v) ->
                CRYSTALLIZED_GOO.put(gooCrystalRegistryKey(crystalType, k),
                        registerCrystalGooForType(gooCrystalRegistryKey(crystalType, k), k,  crystalType, v, gooAmount))
        );
    }

    public static RegistryObject<CrystallizedGooAbstract> gooCrystalByFluidAndType(Fluid f, String type) {

        if (f.getRegistryName() == null) {
            return null;
        }
        ResourceLocation key = gooCrystalRegistryKey(type, f.getRegistryName());
        if (!CRYSTALLIZED_GOO.containsKey(key)) {
            return null;
        }
        return CRYSTALLIZED_GOO.get(key);
    }

    private static ResourceLocation gooCrystalRegistryKey(String crystalType, ResourceLocation k) {
        return new ResourceLocation(GooMod.MOD_ID, k.getPath() + "_" + crystalType);
    }

    private static RegistryObject<CrystallizedGooAbstract> registerCrystalGooForType(ResourceLocation crystalAndGooType, ResourceLocation fluidKey, String crystalType, Supplier<GooFluid> v, Integer gooAmount) {
        Supplier<CrystallizedGooAbstract> result;
        Supplier<Item> source;
        String sourceName = fluidKey.getPath();
        switch (crystalType) {
            case "sliver":
                source = () -> net.minecraft.item.Items.QUARTZ;
                result = () -> new GooSliver(v, source);
                break;
            case "shard":
                source = () -> CRYSTALLIZED_GOO.get(new ResourceLocation(GooMod.MOD_ID, sourceName + "_sliver")).get();
                result = () -> new GooShard(v, source);
                break;
            case "crystal":
                source = () -> CRYSTALLIZED_GOO.get(new ResourceLocation(GooMod.MOD_ID, sourceName + "_shard")).get();
                result = () -> new GooCrystal(v, source);
                break;
            case "chunk":
                source = () -> CRYSTALLIZED_GOO.get(new ResourceLocation(GooMod.MOD_ID, sourceName + "_crystal")).get();
                result = () -> new GooChunk(v, source);
                break;
            case "slab":
                source = () -> CRYSTALLIZED_GOO.get(new ResourceLocation(GooMod.MOD_ID, sourceName + "_chunk")).get();
                result = () -> new GooSlab(v, source);
                break;
            default:
                source = () -> net.minecraft.item.Items.AIR;
                result = () -> new CrystallizedGooAbstract(v, source, 1);
        }
        return ITEMS.register(crystalAndGooType.getPath(), result);
    }
}
