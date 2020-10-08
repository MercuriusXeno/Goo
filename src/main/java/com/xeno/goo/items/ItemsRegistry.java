package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.GooBulbItem;
import com.xeno.goo.client.ISTERProvider;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ItemsRegistry {

    private static final DeferredRegister<Item> Items = DeferredRegister.create(ForgeRegistries.ITEMS, GooMod.MOD_ID);

    public static void initialize() {
        Items.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public static final RegistryObject<GooAndYou> GooAndYou = Items.register("goo_and_you", GooAndYou::new);

    public static final RegistryObject<Gasket> Gasket = Items.register("gasket", Gasket::new);
    public static final RegistryObject<Basin> Basin = Items.register("basin", Basin::new);

    public static final RegistryObject<Gauntlet> Gauntlet = Items.register("gauntlet", Gauntlet::new);

    public static final RegistryObject<Item> GooBulb = Items.register("goo_bulb", () -> new GooBulbItem(BlocksRegistry.GooBulb.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(ISTERProvider::gooBulb)));
    public static final RegistryObject<Item> GooPump = Items.register("goo_pump", () -> new BlockItem(BlocksRegistry.GooPump.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<Item> Gooifier = Items.register("gooifier", () -> new BlockItem(BlocksRegistry.Gooifier.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<Item> Crucible = Items.register("crucible", () -> new BlockItem(BlocksRegistry.Crucible.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(ISTERProvider::crucible)));
    public static final RegistryObject<Item> Mixer = Items.register("mixer", () -> new BlockItem(BlocksRegistry.Mixer.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(ISTERProvider::mixer)));
    public static final RegistryObject<Item> Solidifier = Items.register("solidifier", () -> new BlockItem(BlocksRegistry.Solidifier.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<Item> Lobber = Items.register("lobber", () -> new BlockItem(BlocksRegistry.Lobber.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<Item> Drain = Items.register("drain", () -> new BlockItem(BlocksRegistry.Drain.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
}
