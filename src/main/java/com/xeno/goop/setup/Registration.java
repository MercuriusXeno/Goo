package com.xeno.goop.setup;

import com.xeno.goop.GoopMod;
import com.xeno.goop.blocks.GoopBulb;
import com.xeno.goop.blocks.Goopifier;
import com.xeno.goop.blocks.Solidifier;
import com.xeno.goop.items.Gasket;
import com.xeno.goop.tiles.TileGoopBulb;
import com.xeno.goop.tiles.TileGoopifier;
import com.xeno.goop.tiles.TileSolidifier;
import net.minecraft.block.Block;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class Registration {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GoopMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GoopMod.MOD_ID);
    private static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, GoopMod.MOD_ID);
    private static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, GoopMod.MOD_ID);

    public static void init () {
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        TILES.register(FMLJavaModLoadingContext.get().getModEventBus());
        CONTAINERS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    // Gasket registration
    public static final RegistryObject<Gasket> GASKET = ITEMS.register("gasket", Gasket::new);

    // Goop Bulbs registration
    public static final RegistryObject<GoopBulb> GOOP_BULB = BLOCKS.register("goop_bulb", GoopBulb::new);
    public static final RegistryObject<Item> GOOP_BULB_ITEM = ITEMS.register("goop_bulb", () -> new BlockItem(GOOP_BULB.get(), new Item.Properties().group(ModSetup.ITEM_GROUP)));
    public static final RegistryObject<TileEntityType<TileGoopBulb>> GOOP_BULB_TILE = TILES.register("goop_bulb", () -> TileEntityType.Builder.create(TileGoopBulb::new, GOOP_BULB.get()).build(null));

    // Goopifier registration
    public static final RegistryObject<Goopifier> GOOPIFIER = BLOCKS.register("goopifier", Goopifier::new);
    public static final RegistryObject<Item> GOOPIFIER_ITEM = ITEMS.register("goopifier", () -> new BlockItem(GOOPIFIER.get(), new Item.Properties().group(ModSetup.ITEM_GROUP)));
    public static final RegistryObject<TileEntityType<TileGoopifier>> GOOPIFIER_TILE = TILES.register("goopifier", () -> TileEntityType.Builder.create(TileGoopifier::new, GOOPIFIER.get()).build(null));

    // Solidifier registration
    public static final RegistryObject<Solidifier> SOLIDIFIER = BLOCKS.register("solidifier", Solidifier::new);
    public static final RegistryObject<Item> SOLIDIFIER_ITEM = ITEMS.register("solidifier", () -> new BlockItem(SOLIDIFIER.get(), new Item.Properties().group(ModSetup.ITEM_GROUP)));
    public static final RegistryObject<TileEntityType<TileSolidifier>> SOLIDIFIER_TILE = TILES.register("solidifier", () -> TileEntityType.Builder.create(TileSolidifier::new, SOLIDIFIER.get()).build(null));


}
