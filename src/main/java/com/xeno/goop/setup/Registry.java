package com.xeno.goop.setup;

import com.xeno.goop.GoopMod;
import com.xeno.goop.blocks.GoopBulb;
import com.xeno.goop.blocks.Goopifier;
import com.xeno.goop.blocks.Solidifier;
import com.xeno.goop.fluids.*;
import com.xeno.goop.items.Gasket;
import com.xeno.goop.tiles.GoopBulbTile;
import com.xeno.goop.tiles.GoopifierTile;
import com.xeno.goop.tiles.SolidifierTile;
import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class Registry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GoopMod.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, GoopMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GoopMod.MOD_ID);
    private static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, GoopMod.MOD_ID);
    private static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, GoopMod.MOD_ID);

    public static void init () {
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FLUIDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        TILES.register(FMLJavaModLoadingContext.get().getModEventBus());
        CONTAINERS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    // Gasket registration
    public static final RegistryObject<Gasket> GASKET = ITEMS.register("gasket", Gasket::new);

    // Goop Bulbs registration
    public static final RegistryObject<GoopBulb> GOOP_BULB = BLOCKS.register("goop_bulb", GoopBulb::new);
    public static final RegistryObject<Item> GOOP_BULB_ITEM = ITEMS.register("goop_bulb", () -> new BlockItem(GOOP_BULB.get(), new Item.Properties().group(CommonSetup.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<TileEntityType<GoopBulbTile>> GOOP_BULB_TILE = TILES.register("goop_bulb", () -> TileEntityType.Builder.create(GoopBulbTile::new, GOOP_BULB.get()).build(null));

    // Goopifier registration
    public static final RegistryObject<Goopifier> GOOPIFIER = BLOCKS.register("goopifier", Goopifier::new);
    public static final RegistryObject<Item> GOOPIFIER_ITEM = ITEMS.register("goopifier", () -> new BlockItem(GOOPIFIER.get(), new Item.Properties().group(CommonSetup.ITEM_GROUP)));
    public static final RegistryObject<TileEntityType<GoopifierTile>> GOOPIFIER_TILE = TILES.register("goopifier", () -> TileEntityType.Builder.create(GoopifierTile::new, GOOPIFIER.get()).build(null));

    // Solidifier registration
    public static final RegistryObject<Solidifier> SOLIDIFIER = BLOCKS.register("solidifier", Solidifier::new);
    public static final RegistryObject<Item> SOLIDIFIER_ITEM = ITEMS.register("solidifier", () -> new BlockItem(SOLIDIFIER.get(), new Item.Properties().group(CommonSetup.ITEM_GROUP)));
    public static final RegistryObject<TileEntityType<SolidifierTile>> SOLIDIFIER_TILE = TILES.register("solidifier", () -> TileEntityType.Builder.create(SolidifierTile::new, SOLIDIFIER.get()).build(null));

    // Goop!
    public static final RegistryObject<Fluid> VOLATILE_GOOP = FLUIDS.register("volatile_goop", () -> new VolatileGoop(null, FluidAttributes.builder(new ResourceLocation(GoopMod.MOD_ID, "block/fluid/volatile_still"), new ResourceLocation(GoopMod.MOD_ID, "block/fluid/volatile_flow"))));
    public static final RegistryObject<Fluid> AQUATIC_GOOP = FLUIDS.register("aquatic_goop", () -> new AquaticGoop(null, FluidAttributes.builder(new ResourceLocation(GoopMod.MOD_ID, "block/fluid/aquatic_still"), new ResourceLocation(GoopMod.MOD_ID, "block/fluid/aquatic_flow"))));
    public static final RegistryObject<Fluid> EARTHEN_GOOP = FLUIDS.register("earthen_goop", () -> new EarthenGoop(null, FluidAttributes.builder(new ResourceLocation(GoopMod.MOD_ID, "block/fluid/earthen_still"), new ResourceLocation(GoopMod.MOD_ID, "block/fluid/earthen_flow"))));
    public static final RegistryObject<Fluid> ESOTERIC_GOOP = FLUIDS.register("esoteric_goop", () -> new EsotericGoop(null, FluidAttributes.builder(new ResourceLocation(GoopMod.MOD_ID, "block/fluid/esoteric_still"), new ResourceLocation(GoopMod.MOD_ID, "block/fluid/esoteric_flow"))));
    public static final RegistryObject<Fluid> FLORAL_GOOP = FLUIDS.register("floral_goop", () -> new FloralGoop(null, FluidAttributes.builder(new ResourceLocation(GoopMod.MOD_ID, "block/fluid/floral_still"), new ResourceLocation(GoopMod.MOD_ID, "block/fluid/floral_flow"))));
    public static final RegistryObject<Fluid> FAUNAL_GOOP = FLUIDS.register("faunal_goop", () -> new FaunalGoop(null, FluidAttributes.builder(new ResourceLocation(GoopMod.MOD_ID, "block/fluid/faunal_still"), new ResourceLocation(GoopMod.MOD_ID, "block/fluid/faunal_flow"))));
    public static final RegistryObject<Fluid> FUNGAL_GOOP = FLUIDS.register("fungal_goop", () -> new FungalGoop(null, FluidAttributes.builder(new ResourceLocation(GoopMod.MOD_ID, "block/fluid/fungal_still"), new ResourceLocation(GoopMod.MOD_ID, "block/fluid/fungal_flow"))));
    public static final RegistryObject<Fluid> REGAL_GOOP = FLUIDS.register("regal_goop", () -> new RegalGoop(null, FluidAttributes.builder(new ResourceLocation(GoopMod.MOD_ID, "block/fluid/regal_still"), new ResourceLocation(GoopMod.MOD_ID, "block/fluid/regal_flow"))));
}