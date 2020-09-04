package com.xeno.goo.setup;

import com.ldtteam.aequivaleo.api.compound.ICompoundType;
import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.compound.GooCompoundType;
import com.xeno.goo.blocks.*;
import com.xeno.goo.client.render.GooBulbItemRenderer;
import com.xeno.goo.fluids.*;
import com.xeno.goo.items.*;
import com.xeno.goo.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class Registry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GooMod.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, GooMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GooMod.MOD_ID);
    private static final DeferredRegister<ICompoundType> COMPOUNDS = DeferredRegister.create(ICompoundType.class, GooMod.MOD_ID);
    // private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, GooMod.MOD_ID);
    private static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, GooMod.MOD_ID);
    private static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, GooMod.MOD_ID);
    // private static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, GooMod.MOD_ID);

    public static void init () {
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FLUIDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        TILES.register(FMLJavaModLoadingContext.get().getModEventBus());
        CONTAINERS.register(FMLJavaModLoadingContext.get().getModEventBus());
        COMPOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        // ENCHANTMENTS.register(FMLJavaModLoadingContext.get().getModEventBus());
        // ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public static final RegistryObject<Gasket> GASKET = ITEMS.register("gasket", Gasket::new);
//    public static final RegistryObject<Gauntlet> GAUNTLET = ITEMS.register("gauntlet", Gauntlet::new);
//    public static final RegistryObject<Crucible> CRUCIBLE = ITEMS.register("crucible", Crucible::new);

    // Goo Bulbs registration
    public static final RegistryObject<GooBulb> GOO_BULB = BLOCKS.register("goo_bulb", GooBulb::new);
    public static final RegistryObject<Item> GOO_BULB_ITEM = ITEMS.register("goo_bulb", () -> new BlockItem(GOO_BULB.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(() -> () -> GooBulbItemRenderer.instance)));
    public static final RegistryObject<TileEntityType<GooBulbTile>> GOO_BULB_TILE = TILES.register("goo_bulb", () -> TileEntityType.Builder.create(GooBulbTile::new, GOO_BULB.get()).build(null));

    public static final RegistryObject<GooBulbMk2> GOO_BULB_MK2 = BLOCKS.register("goo_bulb_mk2", GooBulbMk2::new);
    public static final RegistryObject<Item> GOO_BULB_ITEM_MK2 = ITEMS.register("goo_bulb_mk2", () -> new BlockItem(GOO_BULB_MK2.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(() -> () -> GooBulbItemRenderer.instance)));
    public static final RegistryObject<TileEntityType<GooBulbTileMk2>> GOO_BULB_TILE_MK2 = TILES.register("goo_bulb_mk2", () -> TileEntityType.Builder.create(GooBulbTileMk2::new, GOO_BULB_MK2.get()).build(null));

    public static final RegistryObject<GooBulbMk3> GOO_BULB_MK3 = BLOCKS.register("goo_bulb_mk3", GooBulbMk3::new);
    public static final RegistryObject<Item> GOO_BULB_ITEM_MK3 = ITEMS.register("goo_bulb_mk3", () -> new BlockItem(GOO_BULB_MK3.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(() -> () -> GooBulbItemRenderer.instance)));
    public static final RegistryObject<TileEntityType<GooBulbTileMk3>> GOO_BULB_TILE_MK3 = TILES.register("goo_bulb_mk3", () -> TileEntityType.Builder.create(GooBulbTileMk3::new, GOO_BULB_MK3.get()).build(null));

    public static final RegistryObject<GooBulbMk4> GOO_BULB_MK4 = BLOCKS.register("goo_bulb_mk4", GooBulbMk4::new);
    public static final RegistryObject<Item> GOO_BULB_ITEM_MK4 = ITEMS.register("goo_bulb_mk4", () -> new BlockItem(GOO_BULB_MK4.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(() -> () -> GooBulbItemRenderer.instance)));
    public static final RegistryObject<TileEntityType<GooBulbTileMk4>> GOO_BULB_TILE_MK4 = TILES.register("goo_bulb_mk4", () -> TileEntityType.Builder.create(GooBulbTileMk4::new, GOO_BULB_MK4.get()).build(null));

    public static final RegistryObject<GooBulbMk5> GOO_BULB_MK5 = BLOCKS.register("goo_bulb_mk5", GooBulbMk5::new);
    public static final RegistryObject<Item> GOO_BULB_ITEM_MK5 = ITEMS.register("goo_bulb_mk5", () -> new BlockItem(GOO_BULB_MK5.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(() -> () -> GooBulbItemRenderer.instance)));
    public static final RegistryObject<TileEntityType<GooBulbTileMk5>> GOO_BULB_TILE_MK5 = TILES.register("goo_bulb_mk5", () -> TileEntityType.Builder.create(GooBulbTileMk5::new, GOO_BULB_MK5.get()).build(null));

    // Goo Pumps registration
    public static final RegistryObject<GooPump> GOO_PUMP = BLOCKS.register("goo_pump", GooPump::new);
    public static final RegistryObject<Item> GOO_PUMP_ITEM = ITEMS.register("goo_pump", () -> new BlockItem(GOO_PUMP.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<TileEntityType<GooPumpTile>> GOO_PUMP_TILE = TILES.register("goo_pump", () -> TileEntityType.Builder.create(GooPumpTile::new, GOO_PUMP.get()).build(null));

    // Gooifier registration
    public static final RegistryObject<Gooifier> GOOIFIER = BLOCKS.register("gooifier", Gooifier::new);
    public static final RegistryObject<Item> GOOIFIER_ITEM = ITEMS.register("gooifier", () -> new BlockItem(GOOIFIER.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<TileEntityType<GooifierTile>> GOOIFIER_TILE = TILES.register("gooifier", () -> TileEntityType.Builder.create(GooifierTile::new, GOOIFIER.get()).build(null));

    // Solidifier registration
    public static final RegistryObject<Solidifier> SOLIDIFIER = BLOCKS.register("solidifier", Solidifier::new);
    public static final RegistryObject<Item> SOLIDIFIER_ITEM = ITEMS.register("solidifier", () -> new BlockItem(SOLIDIFIER.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<TileEntityType<SolidifierTile>> SOLIDIFIER_TILE = TILES.register("solidifier", () -> TileEntityType.Builder.create(SolidifierTile::new, SOLIDIFIER.get()).build(null));

    // Goo!
    public static final RegistryObject<GooFluid> AQUATIC_GOO = FLUIDS.register("aquatic_goo", () -> new GooFluid(Resources.Still.AQUATIC_GOO, Resources.Flowing.AQUATIC_GOO, Resources.Icon.AQUATIC_GOO));
    public static final RegistryObject<GooFluid> CHROMATIC_GOO = FLUIDS.register("chromatic_goo", () -> new GooFluid(Resources.Still.CHROMATIC_GOO, Resources.Flowing.CHROMATIC_GOO, Resources.Icon.CHROMATIC_GOO));
    public static final RegistryObject<GooFluid> CRYSTAL_GOO = FLUIDS.register("crystal_goo", () -> new GooFluid(Resources.Still.CRYSTAL_GOO, Resources.Flowing.CRYSTAL_GOO, Resources.Icon.CRYSTAL_GOO));
    public static final RegistryObject<GooFluid> DECAY_GOO = FLUIDS.register("decay_goo", () -> new GooFluid(Resources.Still.DECAY_GOO, Resources.Flowing.DECAY_GOO, Resources.Icon.DECAY_GOO));
    public static final RegistryObject<GooFluid> EARTHEN_GOO = FLUIDS.register("earthen_goo", () -> new GooFluid(Resources.Still.EARTHEN_GOO, Resources.Flowing.EARTHEN_GOO, Resources.Icon.EARTHEN_GOO));
    public static final RegistryObject<GooFluid> ENERGETIC_GOO = FLUIDS.register("energetic_goo", () -> new GooFluid(Resources.Still.ENERGETIC_GOO, Resources.Flowing.ENERGETIC_GOO, Resources.Icon.ENERGETIC_GOO));
    public static final RegistryObject<GooFluid> FAUNAL_GOO = FLUIDS.register("faunal_goo", () -> new GooFluid(Resources.Still.FAUNAL_GOO, Resources.Flowing.FAUNAL_GOO, Resources.Icon.FAUNAL_GOO));
    public static final RegistryObject<GooFluid> FLORAL_GOO = FLUIDS.register("floral_goo", () -> new GooFluid(Resources.Still.FLORAL_GOO, Resources.Flowing.FLORAL_GOO, Resources.Icon.FLORAL_GOO));
    public static final RegistryObject<GooFluid> FUNGAL_GOO = FLUIDS.register("fungal_goo", () -> new GooFluid(Resources.Still.FUNGAL_GOO, Resources.Flowing.FUNGAL_GOO, Resources.Icon.FUNGAL_GOO));
    public static final RegistryObject<GooFluid> HONEY_GOO = FLUIDS.register("honey_goo", () -> new GooFluid(Resources.Still.HONEY_GOO, Resources.Flowing.HONEY_GOO, Resources.Icon.HONEY_GOO));
    public static final RegistryObject<GooFluid> LOGIC_GOO = FLUIDS.register("logic_goo", () -> new GooFluid(Resources.Still.LOGIC_GOO, Resources.Flowing.LOGIC_GOO, Resources.Icon.LOGIC_GOO));
    public static final RegistryObject<GooFluid> METAL_GOO = FLUIDS.register("metal_goo", () -> new GooFluid(Resources.Still.METAL_GOO, Resources.Flowing.METAL_GOO, Resources.Icon.METAL_GOO));
    public static final RegistryObject<GooFluid> MOLTEN_GOO = FLUIDS.register("molten_goo", () -> new GooFluid(Resources.Still.MOLTEN_GOO, Resources.Flowing.MOLTEN_GOO, Resources.Icon.MOLTEN_GOO));
    public static final RegistryObject<GooFluid> OBSIDIAN_GOO = FLUIDS.register("obsidian_goo", () -> new GooFluid(Resources.Still.OBSIDIAN_GOO, Resources.Flowing.OBSIDIAN_GOO, Resources.Icon.OBSIDIAN_GOO));
    public static final RegistryObject<GooFluid> REGAL_GOO = FLUIDS.register("regal_goo", () -> new GooFluid(Resources.Still.REGAL_GOO, Resources.Flowing.REGAL_GOO, Resources.Icon.REGAL_GOO));
    public static final RegistryObject<GooFluid> SLIME_GOO = FLUIDS.register("slime_goo", () -> new GooFluid(Resources.Still.SLIME_GOO, Resources.Flowing.SLIME_GOO, Resources.Icon.SLIME_GOO));
    public static final RegistryObject<GooFluid> SNOW_GOO = FLUIDS.register("snow_goo", () -> new GooFluid(Resources.Still.SNOW_GOO, Resources.Flowing.SNOW_GOO, Resources.Icon.SNOW_GOO));
    public static final RegistryObject<GooFluid> VITAL_GOO = FLUIDS.register("vital_goo", () -> new GooFluid(Resources.Still.VITAL_GOO, Resources.Flowing.VITAL_GOO, Resources.Icon.VITAL_GOO));
    public static final RegistryObject<GooFluid> WEIRD_GOO = FLUIDS.register("weird_goo", () -> new GooFluid(Resources.Still.WEIRD_GOO, Resources.Flowing.WEIRD_GOO, Resources.Icon.WEIRD_GOO));

    // compounds
    public static final RegistryObject<GooCompoundType> AQUATIC = COMPOUNDS.register("aquatic", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "aquatic"), AQUATIC_GOO));
    public static final RegistryObject<GooCompoundType> CHROMATIC = COMPOUNDS.register("chromatic", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "chromatic"), CHROMATIC_GOO));
    public static final RegistryObject<GooCompoundType> CRYSTAL = COMPOUNDS.register("crystal", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "crystal"), CRYSTAL_GOO));
    public static final RegistryObject<GooCompoundType> DECAY = COMPOUNDS.register("decay", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "decay"), DECAY_GOO));
    public static final RegistryObject<GooCompoundType> EARTHEN = COMPOUNDS.register("earthen", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "earthen"), EARTHEN_GOO));
    public static final RegistryObject<GooCompoundType> ENERGETIC = COMPOUNDS.register("energetic", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "energetic"), ENERGETIC_GOO));
    public static final RegistryObject<GooCompoundType> FAUNAL = COMPOUNDS.register("faunal", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "faunal"), FAUNAL_GOO));
    public static final RegistryObject<GooCompoundType> FLORAL = COMPOUNDS.register("floral", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "floral"), FLORAL_GOO));
    public static final RegistryObject<GooCompoundType> FUNGAL = COMPOUNDS.register("fungal", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "fungal"), FUNGAL_GOO));
    public static final RegistryObject<GooCompoundType> HONEY = COMPOUNDS.register("honey", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "honey"), HONEY_GOO));
    public static final RegistryObject<GooCompoundType> LOGIC = COMPOUNDS.register("logic", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "logic"), LOGIC_GOO));
    public static final RegistryObject<GooCompoundType> METAL = COMPOUNDS.register("metal", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "metal"), METAL_GOO));
    public static final RegistryObject<GooCompoundType> MOLTEN = COMPOUNDS.register("molten", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "molten"), MOLTEN_GOO));
    public static final RegistryObject<GooCompoundType> OBSIDIAN = COMPOUNDS.register("obsidian", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "obsidian"), OBSIDIAN_GOO));
    public static final RegistryObject<GooCompoundType> REGAL = COMPOUNDS.register("regal", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "regal"), REGAL_GOO));
    public static final RegistryObject<GooCompoundType> SLIME = COMPOUNDS.register("slime", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "slime"), SLIME_GOO));
    public static final RegistryObject<GooCompoundType> SNOW = COMPOUNDS.register("snow", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "snow"), SNOW_GOO));
    public static final RegistryObject<GooCompoundType> VITAL = COMPOUNDS.register("vital", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "vital"), VITAL_GOO));
    public static final RegistryObject<GooCompoundType> WEIRD = COMPOUNDS.register("weird", () -> new GooCompoundType(new ResourceLocation(GooMod.MOD_ID, "weird"), WEIRD_GOO));

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
