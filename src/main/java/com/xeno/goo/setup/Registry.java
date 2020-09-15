package com.xeno.goo.setup;

import com.ldtteam.aequivaleo.api.compound.type.ICompoundType;
import com.ldtteam.aequivaleo.api.compound.type.group.ICompoundTypeGroup;
import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.compound.GooCompoundType;
import com.xeno.goo.aequivaleo.compound.GooCompoundTypeGroup;
import com.xeno.goo.blocks.*;
import com.xeno.goo.client.ISTERProvider;
import com.xeno.goo.enchantments.Geomancy;
import com.xeno.goo.enchantments.Holding;
import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.Basin;
import com.xeno.goo.items.Gasket;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.items.GooAndYou;
import com.xeno.goo.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.ParticleType;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public class Registry {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GooMod.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, GooMod.MOD_ID);
    private static final DeferredRegister<Item>              ITEMS     = DeferredRegister.create(ForgeRegistries.ITEMS, GooMod.MOD_ID);
    private static final DeferredRegister<ICompoundType>      COMPOUNDS = DeferredRegister.create(ICompoundType.class, GooMod.MOD_ID);
    private static final DeferredRegister<ICompoundTypeGroup> COMPOUND_GROUPS = DeferredRegister.create(ICompoundTypeGroup.class, GooMod.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, GooMod.MOD_ID);
    private static final DeferredRegister<TileEntityType<?>>  TILES     = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, GooMod.MOD_ID);
    private static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, GooMod.MOD_ID);
    private static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, GooMod.MOD_ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, GooMod.MOD_ID);

    public static void init () {
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FLUIDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        TILES.register(FMLJavaModLoadingContext.get().getModEventBus());
        CONTAINERS.register(FMLJavaModLoadingContext.get().getModEventBus());
        COMPOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        COMPOUND_GROUPS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ENCHANTMENTS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        PARTICLES.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public static final RegistryObject<EntityType<GooEntity>> GOO_ENTITY = ENTITIES.register("goo_entity",
            () -> EntityType.Builder.<GooEntity>create(GooEntity::new, EntityClassification.MISC)
                .size(1, 1)
            .setShouldReceiveVelocityUpdates(false)
            .build("goo_entity")
    );


    public static final RegistryObject<Gasket>   GASKET   = ITEMS.register("gasket", Gasket::new);
    public static final RegistryObject<Gauntlet> GAUNTLET = ITEMS.register("gauntlet", Gauntlet::new);
    public static final RegistryObject<Basin>    BASIN    = ITEMS.register("basin", Basin::new);

    public static final RegistryObject<GooAndYou> GOO_AND_YOU = ITEMS.register("goo_and_you", GooAndYou::new);

    // Goo Bulb registration
    public static final RegistryObject<GooBulb> GOO_BULB = BLOCKS.register("goo_bulb", GooBulb::new);
    public static final RegistryObject<Item> GOO_BULB_ITEM = ITEMS.register("goo_bulb", () -> new GooBulbItem(GOO_BULB.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(ISTERProvider::gooBulb)));
    public static final RegistryObject<TileEntityType<GooBulbTile>> GOO_BULB_TILE = TILES.register("goo_bulb", () -> TileEntityType.Builder.create(GooBulbTile::new, GOO_BULB.get()).build(null));

    // Goo Pumps registration
    public static final RegistryObject<GooPump> GOO_PUMP = BLOCKS.register("goo_pump", GooPump::new);
    public static final RegistryObject<Item> GOO_PUMP_ITEM = ITEMS.register("goo_pump", () -> new BlockItem(GOO_PUMP.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
    public static final RegistryObject<TileEntityType<GooPumpTile>> GOO_PUMP_TILE = TILES.register("goo_pump", () -> TileEntityType.Builder.create(GooPumpTile::new, GOO_PUMP.get()).build(null));

    // Mixer registration
    public static final RegistryObject<Mixer> MIXER = BLOCKS.register("mixer", Mixer::new);
    public static final RegistryObject<Item> MIXER_ITEM = ITEMS.register("mixer", () -> new BlockItem(MIXER.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(ISTERProvider::mixer)));
    public static final RegistryObject<TileEntityType<MixerTile>> MIXER_TILE = TILES.register("mixer", () -> TileEntityType.Builder.create(MixerTile::new, MIXER.get()).build(null));

    // Crucible registration
    public static final RegistryObject<Crucible> CRUCIBLE = BLOCKS.register("crucible", Crucible::new);
    public static final RegistryObject<Item> CRUCIBLE_ITEM = ITEMS.register("crucible", () -> new BlockItem(CRUCIBLE.get(), new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1).setISTER(ISTERProvider::crucible)));
    public static final RegistryObject<TileEntityType<CrucibleTile>> CRUCIBLE_TILE = TILES.register("crucible", () -> TileEntityType.Builder.create(CrucibleTile::new, CRUCIBLE.get()).build(null));

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

    // compound groups
    public static final RegistryObject<GooCompoundTypeGroup> GOO_GROUP = COMPOUND_GROUPS.register("goo", GooCompoundTypeGroup::new);

    // compounds
    public static final RegistryObject<GooCompoundType> AQUATIC = COMPOUNDS.register("aquatic", () -> new GooCompoundType(AQUATIC_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> CHROMATIC = COMPOUNDS.register("chromatic", () -> new GooCompoundType(CHROMATIC_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> CRYSTAL = COMPOUNDS.register("crystal", () -> new GooCompoundType(CRYSTAL_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> DECAY = COMPOUNDS.register("decay", () -> new GooCompoundType(DECAY_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> EARTHEN = COMPOUNDS.register("earthen", () -> new GooCompoundType(EARTHEN_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> ENERGETIC = COMPOUNDS.register("energetic", () -> new GooCompoundType(ENERGETIC_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> FAUNAL = COMPOUNDS.register("faunal", () -> new GooCompoundType(FAUNAL_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> FLORAL = COMPOUNDS.register("floral", () -> new GooCompoundType(FLORAL_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> FUNGAL = COMPOUNDS.register("fungal", () -> new GooCompoundType(FUNGAL_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> HONEY = COMPOUNDS.register("honey", () -> new GooCompoundType(HONEY_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> LOGIC = COMPOUNDS.register("logic", () -> new GooCompoundType(LOGIC_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> METAL = COMPOUNDS.register("metal", () -> new GooCompoundType(METAL_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> MOLTEN = COMPOUNDS.register("molten", () -> new GooCompoundType(MOLTEN_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> OBSIDIAN = COMPOUNDS.register("obsidian", () -> new GooCompoundType(OBSIDIAN_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> REGAL = COMPOUNDS.register("regal", () -> new GooCompoundType(REGAL_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> SLIME = COMPOUNDS.register("slime", () -> new GooCompoundType(SLIME_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> SNOW = COMPOUNDS.register("snow", () -> new GooCompoundType(SNOW_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> VITAL = COMPOUNDS.register("vital", () -> new GooCompoundType(VITAL_GOO, GOO_GROUP));
    public static final RegistryObject<GooCompoundType> WEIRD = COMPOUNDS.register("weird", () -> new GooCompoundType(WEIRD_GOO, GOO_GROUP));

    // enchantments
    public static final RegistryObject<Holding> HOLDING = ENCHANTMENTS.register("holding", Holding::new);
    public static final RegistryObject<Geomancy> GEOMANCY = ENCHANTMENTS.register("geomancy", Geomancy::new);

    // particles
    public static final RegistryObject<BasicParticleType> AQUATIC_FALLING_GOO_PARTICLE = PARTICLES.register("aquatic_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> AQUATIC_LANDING_GOO_PARTICLE = PARTICLES.register("aquatic_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> CHROMATIC_FALLING_GOO_PARTICLE = PARTICLES.register("chromatic_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> CHROMATIC_LANDING_GOO_PARTICLE = PARTICLES.register("chromatic_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> CRYSTAL_FALLING_GOO_PARTICLE = PARTICLES.register("crystal_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> CRYSTAL_LANDING_GOO_PARTICLE = PARTICLES.register("crystal_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> DECAY_FALLING_GOO_PARTICLE = PARTICLES.register("decay_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> DECAY_LANDING_GOO_PARTICLE = PARTICLES.register("decay_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> EARTHEN_FALLING_GOO_PARTICLE = PARTICLES.register("earthen_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> EARTHEN_LANDING_GOO_PARTICLE = PARTICLES.register("earthen_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> ENERGETIC_FALLING_GOO_PARTICLE = PARTICLES.register("energetic_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> ENERGETIC_LANDING_GOO_PARTICLE = PARTICLES.register("energetic_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FAUNAL_FALLING_GOO_PARTICLE = PARTICLES.register("faunal_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FAUNAL_LANDING_GOO_PARTICLE = PARTICLES.register("faunal_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FLORAL_FALLING_GOO_PARTICLE = PARTICLES.register("floral_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FLORAL_LANDING_GOO_PARTICLE = PARTICLES.register("floral_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FUNGAL_FALLING_GOO_PARTICLE = PARTICLES.register("fungal_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FUNGAL_LANDING_GOO_PARTICLE = PARTICLES.register("fungal_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> HONEY_FALLING_GOO_PARTICLE = PARTICLES.register("honey_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> HONEY_LANDING_GOO_PARTICLE = PARTICLES.register("honey_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> LOGIC_FALLING_GOO_PARTICLE = PARTICLES.register("logic_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> LOGIC_LANDING_GOO_PARTICLE = PARTICLES.register("logic_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> METAL_FALLING_GOO_PARTICLE = PARTICLES.register("metal_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> METAL_LANDING_GOO_PARTICLE = PARTICLES.register("metal_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> MOLTEN_FALLING_GOO_PARTICLE = PARTICLES.register("molten_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> MOLTEN_LANDING_GOO_PARTICLE = PARTICLES.register("molten_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> OBSIDIAN_FALLING_GOO_PARTICLE = PARTICLES.register("obsidian_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> OBSIDIAN_LANDING_GOO_PARTICLE = PARTICLES.register("obsidian_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> REGAL_FALLING_GOO_PARTICLE = PARTICLES.register("regal_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> REGAL_LANDING_GOO_PARTICLE = PARTICLES.register("regal_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> SLIME_FALLING_GOO_PARTICLE = PARTICLES.register("slime_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> SLIME_LANDING_GOO_PARTICLE = PARTICLES.register("slime_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> SNOW_FALLING_GOO_PARTICLE = PARTICLES.register("snow_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> SNOW_LANDING_GOO_PARTICLE = PARTICLES.register("snow_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> VITAL_FALLING_GOO_PARTICLE = PARTICLES.register("vital_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> VITAL_LANDING_GOO_PARTICLE = PARTICLES.register("vital_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> WEIRD_FALLING_GOO_PARTICLE = PARTICLES.register("weird_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> WEIRD_LANDING_GOO_PARTICLE = PARTICLES.register("weird_landing_goo", () -> new BasicParticleType(false));


    private static final Map<Fluid, BasicParticleType> fallingParticleLookupCache = new HashMap<>();
    private static void initializeParticleLookupCache() {
        fallingParticleLookupCache.put(AQUATIC_GOO.get(), AQUATIC_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(CHROMATIC_GOO.get(), CHROMATIC_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(CRYSTAL_GOO.get(), CRYSTAL_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(DECAY_GOO.get(), DECAY_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(EARTHEN_GOO.get(), EARTHEN_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(ENERGETIC_GOO.get(), ENERGETIC_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(FAUNAL_GOO.get(), FAUNAL_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(FLORAL_GOO.get(), FLORAL_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(FUNGAL_GOO.get(), FUNGAL_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(HONEY_GOO.get(), HONEY_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(LOGIC_GOO.get(), LOGIC_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(METAL_GOO.get(), METAL_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(MOLTEN_GOO.get(), MOLTEN_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(OBSIDIAN_GOO.get(), OBSIDIAN_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(REGAL_GOO.get(), REGAL_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(SLIME_GOO.get(), SLIME_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(SNOW_GOO.get(), SNOW_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(VITAL_GOO.get(), VITAL_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(WEIRD_GOO.get(), WEIRD_FALLING_GOO_PARTICLE.get());
    }

    public static BasicParticleType fallingParticleFromFluid(Fluid fluid)
    {
        if (fallingParticleLookupCache.size() == 0) {
            initializeParticleLookupCache();
        }
        if (!fallingParticleLookupCache.containsKey(fluid)) {
            return null;
        }

        return fallingParticleLookupCache.get(fluid);
    }

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
