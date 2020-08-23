package com.xeno.goo.setup;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.*;
import com.xeno.goo.enchantments.Armstrong;
import com.xeno.goo.enchantments.Holding;
import com.xeno.goo.entities.CrystalEntity;
import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.fluids.*;
import com.xeno.goo.items.*;
import com.xeno.goo.tiles.GooBulbTile;
import com.xeno.goo.tiles.GooifierTile;
import com.xeno.goo.tiles.SolidifierTile;
import net.minecraft.block.Block;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.obj.MaterialLibrary;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.stream.Collectors;

public class Registry {


    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GooMod.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, GooMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GooMod.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, GooMod.MOD_ID);
    private static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, GooMod.MOD_ID);
    private static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, GooMod.MOD_ID);
    private static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, GooMod.MOD_ID);

    public static void init () {
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FLUIDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        TILES.register(FMLJavaModLoadingContext.get().getModEventBus());
        CONTAINERS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ENCHANTMENTS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public static final RegistryObject<Gasket> GASKET = ITEMS.register("gasket", Gasket::new);
    public static final RegistryObject<Gauntlet> GAUNTLET = ITEMS.register("gauntlet", Gauntlet::new);
    public static final RegistryObject<Crucible> CRUCIBLE = ITEMS.register("crucible", Crucible::new);
//    public static final RegistryObject<ComboGauntlet> COMBO_GAUNTLET = ITEMS.register("combo_gauntlet", ComboGauntlet::new);
//    public static final RegistryObject<MobiusCrucible> MOBIUS_CRUCIBLE = ITEMS.register("mobius_crucible", MobiusCrucible::new);

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
    public static final RegistryObject<GooBase> AQUATIC_GOO = FLUIDS.register("aquatic_goo", AquaticGoo.Source::new);
    public static final RegistryObject<GooBase> AQUATIC_GOO_FLOWING = FLUIDS.register("aquatic_goo_flowing", AquaticGoo.Flowing::new);
    public static final RegistryObject<GooBase> CHROMATIC_GOO = FLUIDS.register("chromatic_goo", ChromaticGoo.Source::new);
    public static final RegistryObject<GooBase> CHROMATIC_GOO_FLOWING = FLUIDS.register("chromatic_goo_flowing", ChromaticGoo.Flowing::new);
    public static final RegistryObject<GooBase> CRYSTAL_GOO = FLUIDS.register("crystal_goo", CrystalGoo.Source::new);
    public static final RegistryObject<GooBase> CRYSTAL_GOO_FLOWING = FLUIDS.register("crystal_goo_flowing", CrystalGoo.Flowing::new);
    public static final RegistryObject<GooBase> DECAY_GOO = FLUIDS.register("decay_goo", DecayGoo.Source::new);
    public static final RegistryObject<GooBase> DECAY_GOO_FLOWING = FLUIDS.register("decay_goo_flowing", DecayGoo.Flowing::new);
    public static final RegistryObject<GooBase> EARTHEN_GOO = FLUIDS.register("earthen_goo", EarthenGoo.Source::new);
    public static final RegistryObject<GooBase> EARTHEN_GOO_FLOWING = FLUIDS.register("earthen_goo_flowing", EarthenGoo.Flowing::new);
    public static final RegistryObject<GooBase> ENERGETIC_GOO = FLUIDS.register("energetic_goo", EnergeticGoo.Source::new);
    public static final RegistryObject<GooBase> ENERGETIC_GOO_FLOWING = FLUIDS.register("energetic_goo_flowing", EnergeticGoo.Flowing::new);
    public static final RegistryObject<GooBase> FAUNAL_GOO = FLUIDS.register("faunal_goo", FaunalGoo.Source::new);
    public static final RegistryObject<GooBase> FAUNAL_GOO_FLOWING = FLUIDS.register("faunal_goo_flowing", FaunalGoo.Flowing::new);
    public static final RegistryObject<GooBase> FLORAL_GOO = FLUIDS.register("floral_goo", FloralGoo.Source::new);
    public static final RegistryObject<GooBase> FLORAL_GOO_FLOWING = FLUIDS.register("floral_goo_flowing", FloralGoo.Flowing::new);
    public static final RegistryObject<GooBase> FUNGAL_GOO = FLUIDS.register("fungal_goo", FungalGoo.Source::new);
    public static final RegistryObject<GooBase> FUNGAL_GOO_FLOWING = FLUIDS.register("fungal_goo_flowing", FungalGoo.Flowing::new);
    public static final RegistryObject<GooBase> HONEY_GOO = FLUIDS.register("honey_goo", HoneyGoo.Source::new);
    public static final RegistryObject<GooBase> HONEY_GOO_FLOWING = FLUIDS.register("honey_goo_flowing", HoneyGoo.Flowing::new);
    public static final RegistryObject<GooBase> LOGIC_GOO = FLUIDS.register("logic_goo", LogicGoo.Source::new);
    public static final RegistryObject<GooBase> LOGIC_GOO_FLOWING = FLUIDS.register("logic_goo_flowing", LogicGoo.Flowing::new);
    public static final RegistryObject<GooBase> METAL_GOO = FLUIDS.register("metal_goo", MetalGoo.Source::new);
    public static final RegistryObject<GooBase> METAL_GOO_FLOWING = FLUIDS.register("metal_goo_flowing", MetalGoo.Flowing::new);
    public static final RegistryObject<GooBase> MOLTEN_GOO = FLUIDS.register("molten_goo", MoltenGoo.Source::new);
    public static final RegistryObject<GooBase> MOLTEN_GOO_FLOWING = FLUIDS.register("molten_goo_flowing", MoltenGoo.Flowing::new);
    public static final RegistryObject<GooBase> OBSIDIAN_GOO = FLUIDS.register("obsidian_goo", ObsidianGoo.Source::new);
    public static final RegistryObject<GooBase> OBSIDIAN_GOO_FLOWING = FLUIDS.register("obsidian_goo_flowing", ObsidianGoo.Flowing::new);
    public static final RegistryObject<GooBase> REGAL_GOO = FLUIDS.register("regal_goo", RegalGoo.Source::new);
    public static final RegistryObject<GooBase> REGAL_GOO_FLOWING = FLUIDS.register("regal_goo_flowing", RegalGoo.Flowing::new);
    public static final RegistryObject<GooBase> SLIME_GOO = FLUIDS.register("slime_goo", SlimeGoo.Source::new);
    public static final RegistryObject<GooBase> SLIME_GOO_FLOWING = FLUIDS.register("slime_goo_flowing", SlimeGoo.Flowing::new);
    public static final RegistryObject<GooBase> SNOW_GOO = FLUIDS.register("snow_goo", SnowGoo.Source::new);
    public static final RegistryObject<GooBase> SNOW_GOO_FLOWING = FLUIDS.register("snow_goo_flowing", SnowGoo.Flowing::new);
    public static final RegistryObject<GooBase> VITAL_GOO = FLUIDS.register("vital_goo", VitalGoo.Source::new);
    public static final RegistryObject<GooBase> VITAL_GOO_FLOWING = FLUIDS.register("vital_goo_flowing", VitalGoo.Flowing::new);
    public static final RegistryObject<GooBase> WEIRD_GOO = FLUIDS.register("weird_goo", WeirdGoo.Source::new);
    public static final RegistryObject<GooBase> WEIRD_GOO_FLOWING = FLUIDS.register("weird_goo_flowing", WeirdGoo.Flowing::new);

    // Block variants of flowing fluids
    public static final RegistryObject<GooBlockBase> AQUATIC_GOO_BLOCK = BLOCKS.register("aquatic_goo_block", AquaticGooBlock::new);
    public static final RegistryObject<GooBlockBase> CHROMATIC_GOO_BLOCK = BLOCKS.register("chromatic_goo_block", ChromaticGooBlock::new);
    public static final RegistryObject<GooBlockBase> CRYSTAL_GOO_BLOCK = BLOCKS.register("crystal_goo_block", CrystalGooBlock::new);
    public static final RegistryObject<GooBlockBase> DECAY_GOO_BLOCK = BLOCKS.register("decay_goo_block", DecayGooBlock::new);
    public static final RegistryObject<GooBlockBase> EARTHEN_GOO_BLOCK = BLOCKS.register("earthen_goo_block", EarthenGooBlock::new);
    public static final RegistryObject<GooBlockBase> ENERGETIC_GOO_BLOCK = BLOCKS.register("energetic_goo_block", EnergeticGooBlock::new);
    public static final RegistryObject<GooBlockBase> FAUNAL_GOO_BLOCK = BLOCKS.register("faunal_goo_block", FaunalGooBlock::new);
    public static final RegistryObject<GooBlockBase> FLORAL_GOO_BLOCK = BLOCKS.register("floral_goo_block", FloralGooBlock::new);
    public static final RegistryObject<GooBlockBase> FUNGAL_GOO_BLOCK = BLOCKS.register("fungal_goo_block", FungalGooBlock::new);
    public static final RegistryObject<GooBlockBase> HONEY_GOO_BLOCK = BLOCKS.register("honey_goo_block", HoneyGooBlock::new);
    public static final RegistryObject<GooBlockBase> LOGIC_GOO_BLOCK = BLOCKS.register("logic_goo_block", LogicGooBlock::new);
    public static final RegistryObject<GooBlockBase> METAL_GOO_BLOCK = BLOCKS.register("metal_goo_block", MetalGooBlock::new);
    public static final RegistryObject<GooBlockBase> MOLTEN_GOO_BLOCK = BLOCKS.register("molten_goo_block", MoltenGooBlock::new);
    public static final RegistryObject<GooBlockBase> OBSIDIAN_GOO_BLOCK = BLOCKS.register("obsidian_goo_block", ObsidianGooBlock::new);
    public static final RegistryObject<GooBlockBase> REGAL_GOO_BLOCK = BLOCKS.register("regal_goo_block", RegalGooBlock::new);
    public static final RegistryObject<GooBlockBase> SLIME_GOO_BLOCK = BLOCKS.register("slime_goo_block", SlimeGooBlock::new);
    public static final RegistryObject<GooBlockBase> SNOW_GOO_BLOCK = BLOCKS.register("snow_goo_block", SnowGooBlock::new);
    public static final RegistryObject<GooBlockBase> VITAL_GOO_BLOCK = BLOCKS.register("vital_goo_block", VitalGooBlock::new);
    public static final RegistryObject<GooBlockBase> WEIRD_GOO_BLOCK = BLOCKS.register("weird_goo_block", WeirdGooBlock::new);


    // Enchantments
    public static final RegistryObject<Holding> HOLDING_ENCHANTMENT = ENCHANTMENTS.register("holding", Holding::new);
    public static final RegistryObject<Armstrong> ARMSTRONG_ENCHANTMENT = ENCHANTMENTS.register("armstrong", Armstrong::new);

    // entity
    public static final RegistryObject<EntityType<CrystalEntity>> CRYSTAL = ENTITIES.register("crystal",
            () -> EntityType.Builder.<CrystalEntity>create(CrystalEntity::new, EntityClassification.MISC)
            .setCustomClientFactory((s, w) -> new CrystalEntity(w))
            .size(0.1f, 0.1f)
            .setShouldReceiveVelocityUpdates(true)
            .setUpdateInterval(3)
            .setTrackingRange(32)
            .immuneToFire()
            .build("crystal_entity"));

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
