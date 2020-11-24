package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.GooBulbItem;
import com.xeno.goo.client.ISTERProvider;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
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

    private static final DeferredRegister<Item> Items = DeferredRegister.create(ForgeRegistries.ITEMS, GooMod.MOD_ID);

    public static void initialize() {
        Items.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    // major items
    public static final RegistryObject<GooAndYou> GooAndYou = Items.register("goo_and_you", GooAndYou::new);
    public static final RegistryObject<Gasket> Gasket = Items.register("gasket", Gasket::new);
    public static final RegistryObject<CrystalComb> CrystalComb = Items.register("crystal_comb", CrystalComb::new);
    public static final RegistryObject<Basin> Basin = Items.register("basin", Basin::new);
    public static final RegistryObject<Gauntlet> Gauntlet = Items.register("gauntlet", Gauntlet::new);

    // block items
    public static final RegistryObject<Item> GooBulb = Items.register("goo_bulb", () -> new GooBulbItem(BlocksRegistry.Bulb.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64).setISTER(ISTERProvider::gooBulb)));
    public static final RegistryObject<Item> GooPump = Items.register("goo_pump", () -> new BlockItem(BlocksRegistry.Pump.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64)));
    public static final RegistryObject<Item> Gooifier = Items.register("gooifier", () -> new BlockItem(BlocksRegistry.Gooifier.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64)));
    public static final RegistryObject<Item> Crucible = Items.register("crucible", () -> new BlockItem(BlocksRegistry.Crucible.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64).setISTER(ISTERProvider::crucible)));
    public static final RegistryObject<Item> Mixer = Items.register("mixer", () -> new BlockItem(BlocksRegistry.Mixer.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64).setISTER(ISTERProvider::mixer)));
    public static final RegistryObject<Item> Solidifier = Items.register("solidifier", () -> new BlockItem(BlocksRegistry.Solidifier.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64)));
    public static final RegistryObject<Item> Lobber = Items.register("lobber", () -> new BlockItem(BlocksRegistry.Lobber.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64)));
    public static final RegistryObject<Item> Drain = Items.register("drain", () -> new BlockItem(BlocksRegistry.Drain.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64)));
    public static final RegistryObject<Item> RadiantLight = Items.register("radiant_light", () -> new BlockItem(BlocksRegistry.RadiantLight.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64)));
    public static final RegistryObject<Item> CrystalNest = Items.register("crystal_nest", () -> new BlockItem(BlocksRegistry.CrystalNest.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64)));
    public static final RegistryObject<Item> Trough = Items.register("goo_trough", () -> new BlockItem(BlocksRegistry.Trough.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64).setISTER(ISTERProvider::trough)));

    // spawn eggs
    public static final RegistryObject<GooBeeSpawnEgg> GooBeeSpawnEgg = Items.register("goo_bee_spawn_egg", GooBeeSpawnEgg::new);
    public static final RegistryObject<GooSnailSpawnEgg> GooSnailSpawnEgg = Items.register("goo_snail_spawn_egg", GooSnailSpawnEgg::new);

    private final static Map<String, Integer> crystallizedGooVariants = new HashMap<>();
    static {
        crystallizedGooVariants.put("sliver", 10);
        crystallizedGooVariants.put("shard", 100);
        crystallizedGooVariants.put("crystal", 1000);
        crystallizedGooVariants.put("chunk", 10000);
        crystallizedGooVariants.put("slab", 100000);
    }

    public static final Map<ResourceLocation, RegistryObject<CrystallizedGooAbstract>> CrystallizedGoo = new HashMap<>();
    // crystallized goo
    static {
        crystallizedGooVariants.forEach(ItemsRegistry::registerCrystalGooForType);
    }

    private static void registerCrystalGooForType(String crystalType, Integer gooAmount) {
        Registry.FluidSuppliers.forEach((k, v) ->
                CrystallizedGoo.put(gooCrystalRegistryKey(crystalType, k),
                        registerCrystalGooForType(gooCrystalRegistryKey(crystalType, k), k,  crystalType, v, gooAmount))
        );
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
                source = () -> CrystallizedGoo.get(new ResourceLocation(GooMod.MOD_ID, sourceName + "_sliver")).get();
                result = () -> new GooShard(v, source);
                break;
            case "crystal":
                source = () -> CrystallizedGoo.get(new ResourceLocation(GooMod.MOD_ID, sourceName + "_shard")).get();
                result = () -> new GooCrystal(v, source);
                break;
            case "chunk":
                source = () -> CrystallizedGoo.get(new ResourceLocation(GooMod.MOD_ID, sourceName + "_crystal")).get();
                result = () -> new GooChunk(v, source);
                break;
            case "slab":
                source = () -> CrystallizedGoo.get(new ResourceLocation(GooMod.MOD_ID, sourceName + "_chunk")).get();
                result = () -> new GooSlab(v, source);
                break;
            default:
                source = () -> net.minecraft.item.Items.AIR;
                result = () -> new CrystallizedGooAbstract(v, source, 1);
        }
        return Items.register(crystalAndGooType.getPath(), result);
    }
}
