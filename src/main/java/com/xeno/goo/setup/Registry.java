package com.xeno.goo.setup;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.GooBulb;
import com.xeno.goo.blocks.Gooifier;
import com.xeno.goo.blocks.Solidifier;
import com.xeno.goo.fluids.*;
import com.xeno.goo.items.Gasket;
import com.xeno.goo.tiles.GooBulbTile;
import com.xeno.goo.tiles.GooifierTile;
import com.xeno.goo.tiles.SolidifierTile;
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
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GooMod.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, GooMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GooMod.MOD_ID);
    private static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, GooMod.MOD_ID);
    private static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, GooMod.MOD_ID);

    public static void init () {
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FLUIDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        TILES.register(FMLJavaModLoadingContext.get().getModEventBus());
        CONTAINERS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    // Gasket registration
    public static final RegistryObject<Gasket> GASKET = ITEMS.register("gasket", Gasket::new);

    // Goo Bulbs registration
    public static final RegistryObject<GooBulb> GOO_BULB = BLOCKS.register("goo_bulb", GooBulb::new);
    public static final RegistryObject<Item> GOO_BULB_ITEM = ITEMS.register("goo_bulb", () -> new BlockItem(GOO_BULB.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<TileEntityType<GooBulbTile>> GOO_BULB_TILE = TILES.register("goo_bulb", () -> TileEntityType.Builder.create(GooBulbTile::new, GOO_BULB.get()).build(null));

    // Gooifier registration
    public static final RegistryObject<Gooifier> GOOIFIER = BLOCKS.register("gooifier", Gooifier::new);
    public static final RegistryObject<Item> GOOIFIER_ITEM = ITEMS.register("gooifier", () -> new BlockItem(GOOIFIER.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<TileEntityType<GooifierTile>> GOOIFIER_TILE = TILES.register("gooifier", () -> TileEntityType.Builder.create(GooifierTile::new, GOOIFIER.get()).build(null));

    // Solidifier registration
    public static final RegistryObject<Solidifier> SOLIDIFIER = BLOCKS.register("solidifier", Solidifier::new);
    public static final RegistryObject<Item> SOLIDIFIER_ITEM = ITEMS.register("solidifier", () -> new BlockItem(SOLIDIFIER.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<TileEntityType<SolidifierTile>> SOLIDIFIER_TILE = TILES.register("solidifier", () -> TileEntityType.Builder.create(SolidifierTile::new, SOLIDIFIER.get()).build(null));

    // Goo!
    public static final RegistryObject<Fluid> MOLTEN_GOO = FLUIDS.register("molten_goo", () -> new MoltenGoo(null, FluidAttributes.builder(new ResourceLocation(GooMod.MOD_ID, "block/fluid/molten_still"), new ResourceLocation(GooMod.MOD_ID, "block/fluid/molten_flow"))));
    public static final RegistryObject<Fluid> AQUATIC_GOO = FLUIDS.register("aquatic_goo", () -> new AquaticGoo(null, FluidAttributes.builder(new ResourceLocation(GooMod.MOD_ID, "block/fluid/aquatic_still"), new ResourceLocation(GooMod.MOD_ID, "block/fluid/aquatic_flow"))));
    public static final RegistryObject<Fluid> EARTHEN_GOO = FLUIDS.register("earthen_goo", () -> new EarthenGoo(null, FluidAttributes.builder(new ResourceLocation(GooMod.MOD_ID, "block/fluid/earthen_still"), new ResourceLocation(GooMod.MOD_ID, "block/fluid/earthen_flow"))));
    public static final RegistryObject<Fluid> ESOTERIC_GOO = FLUIDS.register("esoteric_goo", () -> new EsotericGoo(null, FluidAttributes.builder(new ResourceLocation(GooMod.MOD_ID, "block/fluid/esoteric_still"), new ResourceLocation(GooMod.MOD_ID, "block/fluid/esoteric_flow"))));
    public static final RegistryObject<Fluid> FLORAL_GOO = FLUIDS.register("floral_goo", () -> new FloralGoo(null, FluidAttributes.builder(new ResourceLocation(GooMod.MOD_ID, "block/fluid/floral_still"), new ResourceLocation(GooMod.MOD_ID, "block/fluid/floral_flow"))));
    public static final RegistryObject<Fluid> FAUNAL_GOO = FLUIDS.register("faunal_goo", () -> new FaunalGoo(null, FluidAttributes.builder(new ResourceLocation(GooMod.MOD_ID, "block/fluid/faunal_still"), new ResourceLocation(GooMod.MOD_ID, "block/fluid/faunal_flow"))));
    public static final RegistryObject<Fluid> FUNGAL_GOO = FLUIDS.register("fungal_goo", () -> new FungalGoo(null, FluidAttributes.builder(new ResourceLocation(GooMod.MOD_ID, "block/fluid/fungal_still"), new ResourceLocation(GooMod.MOD_ID, "block/fluid/fungal_flow"))));
    public static final RegistryObject<Fluid> REGAL_GOO = FLUIDS.register("regal_goo", () -> new RegalGoo(null, FluidAttributes.builder(new ResourceLocation(GooMod.MOD_ID, "block/fluid/regal_still"), new ResourceLocation(GooMod.MOD_ID, "block/fluid/regal_flow"))));
    public static final RegistryObject<Fluid> VITAL_GOO = FLUIDS.register("vital_goo", () -> new VitalGoo(null, FluidAttributes.builder(new ResourceLocation(GooMod.MOD_ID, "block/fluid/vital_still"), new ResourceLocation(GooMod.MOD_ID, "block/fluid/vital_flow"))));
    public static final RegistryObject<Fluid> METAL_GOO = FLUIDS.register("metal_goo", () -> new MetalGoo(null, FluidAttributes.builder(new ResourceLocation(GooMod.MOD_ID, "block/fluid/metal_still"), new ResourceLocation(GooMod.MOD_ID, "block/fluid/metal_flow"))));
    public static final RegistryObject<Fluid> CHROMATIC_GOO = FLUIDS.register("chromatic_goo", () -> new ChromaticGoo(null, FluidAttributes.builder(new ResourceLocation(GooMod.MOD_ID, "block/fluid/chromatic_still"), new ResourceLocation(GooMod.MOD_ID, "block/fluid/chromatic_flow"))));

    public static String getFluidTranslationKey(String key)
    {
        Fluid f = getFluid(key);
        if (f == null) {
            return null;
        }
        return f.getAttributes().getTranslationKey();
    }

    public static Fluid getFluid(String key)
    {
        RegistryObject<Fluid> fluid = FLUIDS.getEntries().stream().filter(f -> f.getId().toString().equals(key)).findFirst().orElse(null);
        if (fluid == null) {
            return null;
        }
        return fluid.get();
    }
}
