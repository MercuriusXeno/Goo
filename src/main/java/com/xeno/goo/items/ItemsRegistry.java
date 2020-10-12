package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.GooBulbItem;
import com.xeno.goo.client.ISTERProvider;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ItemsRegistry {

    private static final DeferredRegister<Item> Items = DeferredRegister.create(ForgeRegistries.ITEMS, GooMod.MOD_ID);

    public static void initialize() {
        Items.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    // major items
    public static final RegistryObject<GooAndYou> GooAndYou = Items.register("goo_and_you", GooAndYou::new);
    public static final RegistryObject<Gasket> Gasket = Items.register("gasket", Gasket::new);
    public static final RegistryObject<Basin> Basin = Items.register("basin", Basin::new);
    public static final RegistryObject<Gauntlet> Gauntlet = Items.register("gauntlet", Gauntlet::new);

    // block items
    public static final RegistryObject<Item> GooBulb = Items.register("goo_bulb", () -> new GooBulbItem(BlocksRegistry.GooBulb.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(ISTERProvider::gooBulb)));
    public static final RegistryObject<Item> GooPump = Items.register("goo_pump", () -> new BlockItem(BlocksRegistry.GooPump.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64)));
    public static final RegistryObject<Item> Gooifier = Items.register("gooifier", () -> new BlockItem(BlocksRegistry.Gooifier.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<Item> Crucible = Items.register("crucible", () -> new BlockItem(BlocksRegistry.Crucible.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(ISTERProvider::crucible)));
    public static final RegistryObject<Item> Mixer = Items.register("mixer", () -> new BlockItem(BlocksRegistry.Mixer.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(ISTERProvider::mixer)));
    public static final RegistryObject<Item> Solidifier = Items.register("solidifier", () -> new BlockItem(BlocksRegistry.Solidifier.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<Item> Lobber = Items.register("lobber", () -> new BlockItem(BlocksRegistry.Lobber.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64)));
    public static final RegistryObject<Item> Drain = Items.register("drain", () -> new BlockItem(BlocksRegistry.Drain.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(64)));

    private final static Map<String, Integer> crystallizedGooVariants = new HashMap<>();
    static {
        crystallizedGooVariants.put("sliver", 10);
        crystallizedGooVariants.put("shard", 100);
        crystallizedGooVariants.put("crystal", 1000);
        crystallizedGooVariants.put("chunk", 10000);
        crystallizedGooVariants.put("slab", 100000);
    }

    public static final Map<ResourceLocation, RegistryObject<Item>> CrystallizedGoo = new HashMap<>();
    // crystallized goo
    static {
        crystallizedGooVariants.forEach(ItemsRegistry::registerCrystalGooForType);
    }

    private static void registerCrystalGooForType(String crystalType, Integer gooAmount) {
        Registry.FluidSuppliers.forEach((k, v) ->
                CrystallizedGoo.put(gooCrystalRegistryKey(crystalType, k),
                        registerCrystalGooForType(gooCrystalRegistryKey(crystalType, k), crystalType, v, gooAmount))
        );
    }

    private static ResourceLocation gooCrystalRegistryKey(String crystalType, ResourceLocation k) {
        return new ResourceLocation(GooMod.MOD_ID, k.getPath() + "_" + crystalType);
    }

    private static RegistryObject<Item> registerCrystalGooForType(ResourceLocation crystalAndGooType, String crystalType, Supplier<GooFluid> v, Integer gooAmount) {
        Supplier<CrystallizedGooAbstract> result;
        switch (crystalType) {
            case "sliver":
                result = () -> new GooSliver(v);
                break;
            case "shard":
                result = () -> new GooShard(v);
                break;
            case "crystal":
                result = () -> new GooCrystal(v);
                break;
            case "chunk":
                result = () -> new GooChunk(v);
                break;
            case "slab":
                result = () -> new GooSlab(v);
                break;
            default:
                result = () -> new CrystallizedGooAbstract(v, 1);
        }
        return Items.register(crystalAndGooType.getPath(), result);
    }
}
