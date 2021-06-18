package com.xeno.goo.setup;

import com.ldtteam.aequivaleo.api.compound.type.ICompoundType;
import com.ldtteam.aequivaleo.api.compound.type.group.ICompoundTypeGroup;
import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.compound.GooCompoundType;
import com.xeno.goo.aequivaleo.compound.GooCompoundTypeGroup;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.effects.EggedEffect;
import com.xeno.goo.effects.FloralEffect;
import com.xeno.goo.effects.HarmlessEffect;
import com.xeno.goo.enchantments.Containment;
import com.xeno.goo.entities.*;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.tiles.*;
import net.minecraft.block.DispenserBlock;
import net.minecraft.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.dispenser.IDispenseItemBehavior;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.*;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.ParticleType;
import net.minecraft.potion.Effect;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.village.PointOfInterestType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Supplier;

public class Registry {
    public static final Map<ResourceLocation, Supplier<GooFluid>> FluidSuppliers = new TreeMap<>(Comparator.comparing(ResourceLocation::getPath));
    public static final Map<Supplier<GooFluid>, Supplier<Item>> BucketSuppliers = new HashMap<>();

    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, GooMod.MOD_ID);
    private static final DeferredRegister<ICompoundType>      COMPOUNDS = DeferredRegister.create(ICompoundType.class, GooMod.MOD_ID);
    private static final DeferredRegister<ICompoundTypeGroup> COMPOUND_GROUPS = DeferredRegister.create(ICompoundTypeGroup.class, GooMod.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, GooMod.MOD_ID);
    private static final DeferredRegister<TileEntityType<?>>  TILES     = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, GooMod.MOD_ID);
    private static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, GooMod.MOD_ID);
    private static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, GooMod.MOD_ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, GooMod.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GooMod.MOD_ID);
    private static final DeferredRegister<PointOfInterestType> POINTS_OF_INTEREST = DeferredRegister.create(ForgeRegistries.POI_TYPES, GooMod.MOD_ID);
    private static final DeferredRegister<Effect> EFFECTS = DeferredRegister.create(ForgeRegistries.POTIONS, GooMod.MOD_ID);

    public static void init () {
        // fluids needs to be before items
        FLUIDS.register(FMLJavaModLoadingContext.get().getModEventBus());

        BlocksRegistry.initialize();


        // spawn egg relies on entities registered.
        ItemsRegistry.initialize();

        TILES.register(FMLJavaModLoadingContext.get().getModEventBus());
        CONTAINERS.register(FMLJavaModLoadingContext.get().getModEventBus());
        COMPOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        COMPOUND_GROUPS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ENCHANTMENTS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        PARTICLES.register(FMLJavaModLoadingContext.get().getModEventBus());
        SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        POINTS_OF_INTEREST.register(FMLJavaModLoadingContext.get().getModEventBus());
        EFFECTS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public static final RegistryObject<EntityType<GooBlob>> GOO_BLOB = ENTITIES.register("goo_blob",
            () -> EntityType.Builder.<GooBlob>create(GooBlob::new, EntityClassification.MISC)
                .size(0.1f, 0.1f)
            .setTrackingRange(64)
            .setUpdateInterval(1)
            .setShouldReceiveVelocityUpdates(true)
            .build("goo_blob")
    );

    public static final RegistryObject<EntityType<GooSplat>> GOO_SPLAT = ENTITIES.register("goo_splat",
            () -> EntityType.Builder.<GooSplat>create(GooSplat::new, EntityClassification.MISC)
                .size(0.1f, 0.1f)
            .setTrackingRange(64)
            .setUpdateInterval(1)
            .setShouldReceiveVelocityUpdates(true)
            .build("goo_splat")
    );


    public static final EntityType<GooBee> GOO_BEE;
    public static final EntityType<MutantBee> MUTANT_BEE;
    public static final EntityType<GooSnail> GOO_SNAIL;
    public static final EntityType<LightingBug> LIGHTING_BUG;
    static {
        ENTITIES.register("goo_bee", makeSupplier(GOO_BEE = EntityType.Builder.<GooBee>create(GooBee::new, EntityClassification.CREATURE)
                .size(0.7f, 0.6f) // actual size is halved by being dwarfism
                .setTrackingRange(64)
                .setUpdateInterval(1)
                .setShouldReceiveVelocityUpdates(true)
                .build("goo_bee")
        ));
        ENTITIES.register("mutant_bee",
                makeSupplier(MUTANT_BEE = EntityType.Builder.<MutantBee>create(MutantBee::new, EntityClassification.CREATURE)
                        .size(0.7f, 0.6f)
                        .setTrackingRange(64)
                        .setUpdateInterval(1)
                        .setShouldReceiveVelocityUpdates(true)
                        .build("mutant_bee")
        ));
        ENTITIES.register("goo_snail", makeSupplier(GOO_SNAIL = EntityType.Builder.<GooSnail>create(GooSnail::new, EntityClassification.CREATURE)
                .size(0.375f, 0.75f)
                .setTrackingRange(64)
                .setUpdateInterval(1)
                .setShouldReceiveVelocityUpdates(true)
                .build("goo_snail")
        ));

        ENTITIES.register("lighting_bug", makeSupplier(LIGHTING_BUG = EntityType.Builder.create(LightingBug::new, EntityClassification.CREATURE)
                        .size(0.5f, 0.5f)
                        .setTrackingRange(64)
                        .setUpdateInterval(1)
                        .setShouldReceiveVelocityUpdates(true)
                        .build("lighting_bug")
        ));
    }

    private static Supplier<EntityType<?>> makeSupplier(EntityType<?> entityType) {
        return () -> entityType;
    }

    public static final IDispenseItemBehavior DISPENSE_EGG = new DefaultDispenseItemBehavior() {
        public ItemStack dispenseStack(IBlockSource source, ItemStack stack) {
            Direction direction = source.getBlockState().get(DispenserBlock.FACING);
            EntityType<?> entitytype = ((SpawnEggItem)stack.getItem()).getType(stack.getTag());
            entitytype.spawn(source.getWorld(), stack, null, source.getBlockPos().offset(direction), SpawnReason.DISPENSER, direction != Direction.UP, false);
            stack.shrink(1);
            return stack;
        }
    };

    public static final List<SpawnEggItem> EGGS = new ArrayList<>();

    // register eggs!
    public static Supplier<SpawnEggItem> makeEgg(EntityType<?> entity, int primary, int secondary) {
        return () -> {
            SpawnEggItem egg = new SpawnEggItem(entity, primary, secondary, new Item.Properties().group(GooMod.ITEM_GROUP));
            DispenserBlock.registerDispenseBehavior(egg, DISPENSE_EGG);
            EGGS.add(egg);
            return egg;
        };
    }

    // sound events to overload vanilla sounds and subsequently give them the correct captions
    public static final RegistryObject<SoundEvent> GOO_CHOP_SOUND = SOUNDS.register("goo_chop_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "goo_chop_sound")));
    public static final RegistryObject<SoundEvent> GOO_SPLAT_SOUND = SOUNDS.register("goo_splat_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "goo_splat_sound")));
    public static final RegistryObject<SoundEvent> GOO_LOB_SOUND = SOUNDS.register("goo_lob_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "goo_lob_sound")));
    public static final RegistryObject<SoundEvent> GOO_DEPOSIT_SOUND = SOUNDS.register("goo_deposit_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "goo_deposit_sound")));
    public static final RegistryObject<SoundEvent> GOO_WITHDRAW_SOUND = SOUNDS.register("goo_withdraw_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "goo_withdraw_sound")));
    public static final RegistryObject<SoundEvent> DEGRADER_SOUND = SOUNDS.register("degrader_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "degrader_sound")));
    public static final RegistryObject<SoundEvent> GOOIFIER_SOUND = SOUNDS.register("gooifier_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "gooifier_sound")));
    public static final RegistryObject<SoundEvent> DETERIORATE_SOUND = SOUNDS.register("deteriorate_block_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "deteriorate_block_sound")));
    public static final RegistryObject<SoundEvent> EDIFY_SOUND = SOUNDS.register("edify_block_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "edify_block_sound")));
    public static final RegistryObject<SoundEvent> FREEZE_SOUND = SOUNDS.register("freeze_water_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "freeze_water_sound")));
    public static final RegistryObject<SoundEvent> TWITTERPATE_ANIMAL_SOUND = SOUNDS.register("twitterpate_animal_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "twitterpate_animal_sound")));
    public static final RegistryObject<SoundEvent> WEIRD_TELEPORT_SOUND = SOUNDS.register("weird_teleport_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "weird_teleport_sound")));
    public static final RegistryObject<SoundEvent> GOO_SIZZLE_SOUND = SOUNDS.register("goo_sizzle_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "goo_sizzle_sound")));
    public static final RegistryObject<SoundEvent> CRYSTALLIZE_SOUND = SOUNDS.register("crystallize_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "crystallize_sound")));
    public static final RegistryObject<SoundEvent> GOO_BEE_SHATTER_SOUND = SOUNDS.register("goo_bee_shatter_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "goo_bee_shatter_sound")));
    public static final RegistryObject<SoundEvent> SNAIL_POOP_SOUND = SOUNDS.register("snail_poop_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "snail_poop_sound")));
    public static final RegistryObject<SoundEvent> SNAIL_EAT_SOUND = SOUNDS.register("snail_eat_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "snail_eat_sound")));
    public static final RegistryObject<SoundEvent> PRIMORDIAL_WARP_SOUND = SOUNDS.register("primordial_warp_sound", () -> new SoundEvent(new ResourceLocation(GooMod.MOD_ID, "primordial_warp_sound")));

    // Tile registrations
    public static final RegistryObject<TileEntityType<GooBulbTile>> GOO_BULB_TILE = TILES.register("goo_bulb", () -> TileEntityType.Builder.create(GooBulbTile::new, BlocksRegistry.Bulb.get()).build(null));
    public static final RegistryObject<TileEntityType<GooPumpTile>> GOO_PUMP_TILE = TILES.register("goo_pump", () -> TileEntityType.Builder.create(GooPumpTile::new, BlocksRegistry.Pump.get()).build(null));
    public static final RegistryObject<TileEntityType<MixerTile>> MIXER_TILE = TILES.register("mixer", () -> TileEntityType.Builder.create(MixerTile::new, BlocksRegistry.Mixer.get()).build(null));
    public static final RegistryObject<TileEntityType<DegraderTile>> DEGRADER_TILE = TILES.register("crucible", () -> TileEntityType.Builder.create(DegraderTile::new, BlocksRegistry.Degrader
			.get()).build(null));
    public static final RegistryObject<TileEntityType<GooifierTile>> GOOIFIER_TILE = TILES.register("gooifier", () -> TileEntityType.Builder.create(GooifierTile::new, BlocksRegistry.Gooifier.get()).build(null));
    public static final RegistryObject<TileEntityType<SolidifierTile>> SOLIDIFIER_TILE = TILES.register("solidifier", () -> TileEntityType.Builder.create(SolidifierTile::new, BlocksRegistry.Solidifier.get()).build(null));
    public static final RegistryObject<TileEntityType<LobberTile>> LOBBER_TILE = TILES.register("lobber", () -> TileEntityType.Builder.create(LobberTile::new, BlocksRegistry.Lobber.get()).build(null));
    public static final RegistryObject<TileEntityType<DrainTile>> DRAIN_TILE = TILES.register("drain", () -> TileEntityType.Builder.create(DrainTile::new, BlocksRegistry.Drain.get()).build(null));
    public static final RegistryObject<TileEntityType<CrystalNestTile>> CRYSTAL_NEST_TILE = TILES.register("crystal_nest", () -> TileEntityType.Builder.create(CrystalNestTile::new, BlocksRegistry.CrystalNest.get()).build(null));
    public static final RegistryObject<TileEntityType<TroughTile>> TROUGH_TILE = TILES.register("goo_trough", () -> TileEntityType.Builder.create(TroughTile::new, BlocksRegistry.Trough.get()).build(null));
    public static final RegistryObject<TileEntityType<PadTile>> PAD_TILE = TILES.register("goo_pad", () -> TileEntityType.Builder.create(PadTile::new, BlocksRegistry.Pad.get()).build(null));

    // Points of interest
    public static final RegistryObject<PointOfInterestType> CRYSTAL_NEST_POI = POINTS_OF_INTEREST.register("crystal_nest",
            () -> new PointOfInterestType("crystal_nest", PointOfInterestType.getAllStates(BlocksRegistry.CrystalNest.get()), 0, 1));
    public static final RegistryObject<PointOfInterestType> GOO_TROUGH_POI = POINTS_OF_INTEREST.register("goo_trough",
            () -> new PointOfInterestType("goo_trough", PointOfInterestType.getAllStates(BlocksRegistry.Trough.get()), 0, 1));

    // Custom "potion" effects
    public static final RegistryObject<Effect> HARMLESS_EFFECT = EFFECTS.register("harmless_effect", HarmlessEffect::new);
    public static final RegistryObject<Effect> EGGED_EFFECT = EFFECTS.register("egged_effect", EggedEffect::new);
    public static final RegistryObject<Effect> FLORAL_EFFECT = EFFECTS.register("floral_effect", FloralEffect::new);

    // Goo!
    public static final RegistryObject<GooFluid> AQUATIC_GOO = registerGooFluid("aquatic_goo", Resources.Still.AQUATIC_GOO, Resources.Flowing.AQUATIC_GOO, Resources.Icon.AQUATIC_GOO, 0.001f, 0);
    public static final RegistryObject<GooFluid> CHROMATIC_GOO = registerGooFluid("chromatic_goo", Resources.Still.CHROMATIC_GOO, Resources.Flowing.CHROMATIC_GOO, Resources.Icon.CHROMATIC_GOO, 0.002f, 12);
    public static final RegistryObject<GooFluid> CRYSTAL_GOO = registerGooFluid("crystal_goo", Resources.Still.CRYSTAL_GOO, Resources.Flowing.CRYSTAL_GOO, Resources.Icon.CRYSTAL_GOO, 0.003f, 0);
    public static final RegistryObject<GooFluid> DECAY_GOO = registerGooFluid("decay_goo", Resources.Still.DECAY_GOO, Resources.Flowing.DECAY_GOO, Resources.Icon.DECAY_GOO, 0.004f, 0);
    public static final RegistryObject<GooFluid> EARTHEN_GOO = registerGooFluid("earthen_goo", Resources.Still.EARTHEN_GOO, Resources.Flowing.EARTHEN_GOO, Resources.Icon.EARTHEN_GOO, 0.005f, 0);
    public static final RegistryObject<GooFluid> ENERGETIC_GOO = registerGooFluid("energetic_goo", Resources.Still.ENERGETIC_GOO, Resources.Flowing.ENERGETIC_GOO, Resources.Icon.ENERGETIC_GOO, 0.006f, 15);
    public static final RegistryObject<GooFluid> FAUNAL_GOO = registerGooFluid("faunal_goo", Resources.Still.FAUNAL_GOO, Resources.Flowing.FAUNAL_GOO, Resources.Icon.FAUNAL_GOO, 0.007f, 0);
    public static final RegistryObject<GooFluid> FLORAL_GOO = registerGooFluid("floral_goo", Resources.Still.FLORAL_GOO, Resources.Flowing.FLORAL_GOO, Resources.Icon.FLORAL_GOO, 0.008f, 0);
    public static final RegistryObject<GooFluid> FUNGAL_GOO = registerGooFluid("fungal_goo", Resources.Still.FUNGAL_GOO, Resources.Flowing.FUNGAL_GOO, Resources.Icon.FUNGAL_GOO, 0.009f, 12);
    public static final RegistryObject<GooFluid> HONEY_GOO = registerGooFluid("honey_goo", Resources.Still.HONEY_GOO, Resources.Flowing.HONEY_GOO, Resources.Icon.HONEY_GOO, 0.010f, 0);
    public static final RegistryObject<GooFluid> LOGIC_GOO = registerGooFluid("logic_goo", Resources.Still.LOGIC_GOO, Resources.Flowing.LOGIC_GOO, Resources.Icon.LOGIC_GOO, 0.011f, 7);
    public static final RegistryObject<GooFluid> METAL_GOO = registerGooFluid("metal_goo", Resources.Still.METAL_GOO, Resources.Flowing.METAL_GOO, Resources.Icon.METAL_GOO, 0.012f, 0);
    public static final RegistryObject<GooFluid> MOLTEN_GOO = registerGooFluid("molten_goo", Resources.Still.MOLTEN_GOO, Resources.Flowing.MOLTEN_GOO, Resources.Icon.MOLTEN_GOO, 0.013f, 15);
    public static final RegistryObject<GooFluid> PRIMORDIAL_GOO = registerGooFluid("primordial_goo", Resources.Still.PRIMORDIAL_GOO, Resources.Flowing.PRIMORDIAL_GOO, Resources.Icon.PRIMORDIAL_GOO, 0.014f, 15);
    public static final RegistryObject<GooFluid> RADIANT_GOO = registerGooFluid("radiant_goo", Resources.Still.RADIANT_GOO, Resources.Flowing.RADIANT_GOO, Resources.Icon.RADIANT_GOO, 0.016f, 15);
    public static final RegistryObject<GooFluid> REGAL_GOO = registerGooFluid("regal_goo", Resources.Still.REGAL_GOO, Resources.Flowing.REGAL_GOO, Resources.Icon.REGAL_GOO, 0.017f, 0);
    public static final RegistryObject<GooFluid> SLIME_GOO = registerGooFluid("slime_goo", Resources.Still.SLIME_GOO, Resources.Flowing.SLIME_GOO, Resources.Icon.SLIME_GOO, 0.018f, 0);
    public static final RegistryObject<GooFluid> SNOW_GOO = registerGooFluid("snow_goo", Resources.Still.SNOW_GOO, Resources.Flowing.SNOW_GOO, Resources.Icon.SNOW_GOO, 0.019f, 0);
    public static final RegistryObject<GooFluid> VITAL_GOO = registerGooFluid("vital_goo", Resources.Still.VITAL_GOO, Resources.Flowing.VITAL_GOO, Resources.Icon.VITAL_GOO, 0.020f, 0);
    public static final RegistryObject<GooFluid> WEIRD_GOO = registerGooFluid("weird_goo", Resources.Still.WEIRD_GOO, Resources.Flowing.WEIRD_GOO, Resources.Icon.WEIRD_GOO, 0.021f, 0);

    public static RegistryObject<GooFluid> registerGooFluid(String name, ResourceLocation still, ResourceLocation flowing, ResourceLocation icon, float overrideIndex, int lightLevel) {
        RegistryObject<GooFluid> registeredObject = FLUIDS.register(name, () -> new GooFluid(still, flowing, icon, overrideIndex, lightLevel));
        FluidSuppliers.put(new ResourceLocation(GooMod.MOD_ID, name), registeredObject);
        return registeredObject;
    }



    public static final RegistryObject<Item> AQUATIC_BUCKET = registerGooBucket("aquatic_goo", AQUATIC_GOO);
    public static final RegistryObject<Item> CHROMATIC_BUCKET = registerGooBucket("chromatic_goo", CHROMATIC_GOO);
    public static final RegistryObject<Item> CRYSTAL_BUCKET = registerGooBucket("crystal_goo", CRYSTAL_GOO);
    public static final RegistryObject<Item> DECAY_BUCKET = registerGooBucket("decay_goo", DECAY_GOO);
    public static final RegistryObject<Item> EARTHEN_BUCKET = registerGooBucket("earthen_goo", EARTHEN_GOO);
    public static final RegistryObject<Item> ENERGETIC_BUCKET = registerGooBucket("energetic_goo", ENERGETIC_GOO);
    public static final RegistryObject<Item> FAUNAL_BUCKET = registerGooBucket("faunal_goo", FAUNAL_GOO);
    public static final RegistryObject<Item> FLORAL_BUCKET = registerGooBucket("floral_goo", FLORAL_GOO);
    public static final RegistryObject<Item> FUNGAL_BUCKET = registerGooBucket("fungal_goo", FUNGAL_GOO);
    public static final RegistryObject<Item> HONEY_BUCKET = registerGooBucket("honey_goo", HONEY_GOO);
    public static final RegistryObject<Item> LOGIC_BUCKET = registerGooBucket("logic_goo", LOGIC_GOO);
    public static final RegistryObject<Item> METAL_BUCKET = registerGooBucket("metal_goo", METAL_GOO);
    public static final RegistryObject<Item> MOLTEN_BUCKET = registerGooBucket("molten_goo", MOLTEN_GOO);
    public static final RegistryObject<Item> PRIMORDIAL_BUCKET = registerGooBucket("primordial_goo", PRIMORDIAL_GOO);
    public static final RegistryObject<Item> RADIANT_BUCKET = registerGooBucket("radiant_goo", RADIANT_GOO);
    public static final RegistryObject<Item> REGAL_BUCKET = registerGooBucket("regal_goo", REGAL_GOO);
    public static final RegistryObject<Item> SLIME_BUCKET = registerGooBucket("slime_goo", SLIME_GOO);
    public static final RegistryObject<Item> SNOW_BUCKET = registerGooBucket("snow_goo", SNOW_GOO);
    public static final RegistryObject<Item> VITAL_BUCKET = registerGooBucket("vital_goo", VITAL_GOO);
    public static final RegistryObject<Item> WEIRD_BUCKET = registerGooBucket("weird_goo", WEIRD_GOO);

    public static RegistryObject<Item> registerGooBucket(String name, Supplier<GooFluid> fluid) {
        RegistryObject<Item> registeredBucket = ItemsRegistry.ITEMS.register(name + "_bucket", () -> new BucketItem(fluid, new Item.Properties().group(GooMod.ITEM_GROUP).maxStackSize(1)));
        BucketSuppliers.put(fluid, registeredBucket);
        return registeredBucket;
    }

    // compound groups
    public static final RegistryObject<GooCompoundTypeGroup> GOO_GROUP = COMPOUND_GROUPS.register("goo", GooCompoundTypeGroup::new);

    public static final Map<Supplier<GooFluid>, RegistryObject<GooCompoundType>> FluidToCompoundMap = new HashMap<>();
    // compounds
    public static final RegistryObject<GooCompoundType> AQUATIC = registerCompound("aquatic", AQUATIC_GOO);
    public static final RegistryObject<GooCompoundType> CHROMATIC = registerCompound("chromatic", CHROMATIC_GOO);
    public static final RegistryObject<GooCompoundType> CRYSTAL = registerCompound("crystal", CRYSTAL_GOO);
    public static final RegistryObject<GooCompoundType> DECAY = registerCompound("decay", DECAY_GOO);
    public static final RegistryObject<GooCompoundType> EARTHEN = registerCompound("earthen", EARTHEN_GOO);
    public static final RegistryObject<GooCompoundType> ENERGETIC = registerCompound("energetic", ENERGETIC_GOO);
    public static final RegistryObject<GooCompoundType> FAUNAL = registerCompound("faunal", FAUNAL_GOO);
    public static final RegistryObject<GooCompoundType> FLORAL = registerCompound("floral", FLORAL_GOO);
    public static final RegistryObject<GooCompoundType> FUNGAL = registerCompound("fungal", FUNGAL_GOO);
    public static final RegistryObject<GooCompoundType> HONEY = registerCompound("honey", HONEY_GOO);
    public static final RegistryObject<GooCompoundType> LOGIC = registerCompound("logic", LOGIC_GOO);
    public static final RegistryObject<GooCompoundType> METAL = registerCompound("metal", METAL_GOO);
    public static final RegistryObject<GooCompoundType> MOLTEN = registerCompound("molten", MOLTEN_GOO);
    public static final RegistryObject<GooCompoundType> PRIMORDIAL = registerCompound("primordial", PRIMORDIAL_GOO);
    public static final RegistryObject<GooCompoundType> RADIANT = registerCompound("radiant", RADIANT_GOO);
    public static final RegistryObject<GooCompoundType> REGAL = registerCompound("regal", REGAL_GOO);
    public static final RegistryObject<GooCompoundType> SLIME = registerCompound("slime", SLIME_GOO);
    public static final RegistryObject<GooCompoundType> SNOW = registerCompound("snow", SNOW_GOO);
    public static final RegistryObject<GooCompoundType> VITAL = registerCompound("vital", VITAL_GOO);
    public static final RegistryObject<GooCompoundType> WEIRD = registerCompound("weird", WEIRD_GOO);

    public static final RegistryObject<GooCompoundType> FORBIDDEN = registerCompound("logic_forbidden", () -> null);

    private static RegistryObject<GooCompoundType> registerCompound(String name, Supplier<GooFluid> f) {
        RegistryObject<GooCompoundType> type = COMPOUNDS.register(name, () -> new GooCompoundType(f, GOO_GROUP));
        FluidToCompoundMap.put(f, type);
        return type;
    }
    
    // enchantments
    public static final RegistryObject<Containment> CONTAINMENT = ENCHANTMENTS.register("containment", Containment::new);

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
    public static final RegistryObject<BasicParticleType> PRIMORDIAL_FALLING_GOO_PARTICLE = PARTICLES.register("primordial_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> PRIMORDIAL_LANDING_GOO_PARTICLE = PARTICLES.register("primordial_landing_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> RADIANT_FALLING_GOO_PARTICLE = PARTICLES.register("radiant_falling_goo", () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> RADIANT_LANDING_GOO_PARTICLE = PARTICLES.register("radiant_landing_goo", () -> new BasicParticleType(false));
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

    // vapor particles
    public static final RegistryObject<BasicParticleType> AQUATIC_VAPOR_PARTICLE = PARTICLES.register("aquatic_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> CHROMATIC_VAPOR_PARTICLE = PARTICLES.register("chromatic_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> CRYSTAL_VAPOR_PARTICLE = PARTICLES.register("crystal_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> DECAY_VAPOR_PARTICLE = PARTICLES.register("decay_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> EARTHEN_VAPOR_PARTICLE = PARTICLES.register("earthen_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> ENERGETIC_VAPOR_PARTICLE = PARTICLES.register("energetic_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FAUNAL_VAPOR_PARTICLE = PARTICLES.register("faunal_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FLORAL_VAPOR_PARTICLE = PARTICLES.register("floral_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FUNGAL_VAPOR_PARTICLE = PARTICLES.register("fungal_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> HONEY_VAPOR_PARTICLE = PARTICLES.register("honey_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> LOGIC_VAPOR_PARTICLE = PARTICLES.register("logic_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> METAL_VAPOR_PARTICLE = PARTICLES.register("metal_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> MOLTEN_VAPOR_PARTICLE = PARTICLES.register("molten_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> PRIMORDIAL_VAPOR_PARTICLE = PARTICLES.register("primordial_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> RADIANT_VAPOR_PARTICLE = PARTICLES.register("radiant_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> REGAL_VAPOR_PARTICLE = PARTICLES.register("regal_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> SLIME_VAPOR_PARTICLE = PARTICLES.register("slime_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> SNOW_VAPOR_PARTICLE = PARTICLES.register("snow_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> VITAL_VAPOR_PARTICLE = PARTICLES.register("vital_vapor",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> WEIRD_VAPOR_PARTICLE = PARTICLES.register("weird_vapor",  () -> new BasicParticleType(false));

    // vapor particles
    public static final RegistryObject<BasicParticleType> AQUATIC_SPRAY_PARTICLE = PARTICLES.register("aquatic_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> CHROMATIC_SPRAY_PARTICLE = PARTICLES.register("chromatic_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> CRYSTAL_SPRAY_PARTICLE = PARTICLES.register("crystal_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> DECAY_SPRAY_PARTICLE = PARTICLES.register("decay_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> EARTHEN_SPRAY_PARTICLE = PARTICLES.register("earthen_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> ENERGETIC_SPRAY_PARTICLE = PARTICLES.register("energetic_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FAUNAL_SPRAY_PARTICLE = PARTICLES.register("faunal_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FLORAL_SPRAY_PARTICLE = PARTICLES.register("floral_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> FUNGAL_SPRAY_PARTICLE = PARTICLES.register("fungal_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> HONEY_SPRAY_PARTICLE = PARTICLES.register("honey_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> LOGIC_SPRAY_PARTICLE = PARTICLES.register("logic_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> METAL_SPRAY_PARTICLE = PARTICLES.register("metal_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> MOLTEN_SPRAY_PARTICLE = PARTICLES.register("molten_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> PRIMORDIAL_SPRAY_PARTICLE = PARTICLES.register("primordial_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> RADIANT_SPRAY_PARTICLE = PARTICLES.register("radiant_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> REGAL_SPRAY_PARTICLE = PARTICLES.register("regal_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> SLIME_SPRAY_PARTICLE = PARTICLES.register("slime_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> SNOW_SPRAY_PARTICLE = PARTICLES.register("snow_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> VITAL_SPRAY_PARTICLE = PARTICLES.register("vital_spray",  () -> new BasicParticleType(false));
    public static final RegistryObject<BasicParticleType> WEIRD_SPRAY_PARTICLE = PARTICLES.register("weird_spray",  () -> new BasicParticleType(false));

    private static final Map<Fluid, BasicParticleType> fallingParticleLookupCache = new HashMap<>();
    private static void initializeFallingParticleLookupCache() {
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
        fallingParticleLookupCache.put(PRIMORDIAL_GOO.get(), PRIMORDIAL_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(RADIANT_GOO.get(), RADIANT_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(REGAL_GOO.get(), REGAL_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(SLIME_GOO.get(), SLIME_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(SNOW_GOO.get(), SNOW_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(VITAL_GOO.get(), VITAL_FALLING_GOO_PARTICLE.get());
        fallingParticleLookupCache.put(WEIRD_GOO.get(), WEIRD_FALLING_GOO_PARTICLE.get());
    }

    private static final Map<Fluid, BasicParticleType> vaporParticleLookupCache = new HashMap<>();
    private static void initializeVaporParticleLookupCache() {
        vaporParticleLookupCache.put(AQUATIC_GOO.get(), AQUATIC_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(CHROMATIC_GOO.get(), CHROMATIC_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(CRYSTAL_GOO.get(), CRYSTAL_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(DECAY_GOO.get(), DECAY_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(EARTHEN_GOO.get(), EARTHEN_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(ENERGETIC_GOO.get(), ENERGETIC_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(FAUNAL_GOO.get(), FAUNAL_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(FLORAL_GOO.get(), FLORAL_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(FUNGAL_GOO.get(), FUNGAL_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(HONEY_GOO.get(), HONEY_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(LOGIC_GOO.get(), LOGIC_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(METAL_GOO.get(), METAL_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(MOLTEN_GOO.get(), MOLTEN_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(PRIMORDIAL_GOO.get(), PRIMORDIAL_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(RADIANT_GOO.get(), RADIANT_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(REGAL_GOO.get(), REGAL_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(SLIME_GOO.get(), SLIME_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(SNOW_GOO.get(), SNOW_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(VITAL_GOO.get(), VITAL_VAPOR_PARTICLE.get());
        vaporParticleLookupCache.put(WEIRD_GOO.get(), WEIRD_VAPOR_PARTICLE.get());
    }

    private static final Map<Fluid, BasicParticleType> sprayParticleLookupCache = new HashMap<>();
    private static void initializeSprayParticleLookupCache() {
        sprayParticleLookupCache.put(AQUATIC_GOO.get(), AQUATIC_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(CHROMATIC_GOO.get(), CHROMATIC_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(CRYSTAL_GOO.get(), CRYSTAL_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(DECAY_GOO.get(), DECAY_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(EARTHEN_GOO.get(), EARTHEN_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(ENERGETIC_GOO.get(), ENERGETIC_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(FAUNAL_GOO.get(), FAUNAL_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(FLORAL_GOO.get(), FLORAL_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(FUNGAL_GOO.get(), FUNGAL_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(HONEY_GOO.get(), HONEY_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(LOGIC_GOO.get(), LOGIC_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(METAL_GOO.get(), METAL_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(MOLTEN_GOO.get(), MOLTEN_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(PRIMORDIAL_GOO.get(), PRIMORDIAL_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(RADIANT_GOO.get(), RADIANT_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(REGAL_GOO.get(), REGAL_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(SLIME_GOO.get(), SLIME_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(SNOW_GOO.get(), SNOW_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(VITAL_GOO.get(), VITAL_SPRAY_PARTICLE.get());
        sprayParticleLookupCache.put(WEIRD_GOO.get(), WEIRD_SPRAY_PARTICLE.get());
    }
    
    public static BasicParticleType fallingParticleFromFluid(Fluid fluid)
    {
        if (fallingParticleLookupCache.size() == 0) {
            initializeFallingParticleLookupCache();
        }
        if (!fallingParticleLookupCache.containsKey(fluid)) {
            return null;
        }

        return fallingParticleLookupCache.get(fluid);
    }

    public static BasicParticleType vaporParticleFromFluid(Fluid fluid)
    {
        if (vaporParticleLookupCache.size() == 0) {
            initializeVaporParticleLookupCache();
        }
        if (!vaporParticleLookupCache.containsKey(fluid)) {
            return null;
        }

        return vaporParticleLookupCache.get(fluid);
    }

    public static BasicParticleType sprayParticleFromFluid(Fluid fluid) {
        if (sprayParticleLookupCache.size() == 0) {
            initializeSprayParticleLookupCache();
        }
        if (!sprayParticleLookupCache.containsKey(fluid)) {
            return null;
        }

        return sprayParticleLookupCache.get(fluid);
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

    private static final Map<Fluid, Item> FluidBuckets = new HashMap<>();
    public static Item getBucket(GooFluid gooFluid) {
        if (FluidBuckets.isEmpty()) {
            initFluidBuckets();
        }
        if (!FluidBuckets.containsKey(gooFluid)) {
            return Items.BUCKET;
        }
        return FluidBuckets.get(gooFluid);
    }

    private static void initFluidBuckets() {
        for(Entry<Supplier<GooFluid>, Supplier<Item>> entry : BucketSuppliers.entrySet()) {
            FluidBuckets.put(entry.getKey().get(), entry.getValue().get());
        }
    }

    public static ICompoundType compoundFromFluid(Fluid f) {
        ICompoundType[] result = {null};
        FluidToCompoundMap.forEach((k, v) -> result[0] = resultOrMatch(result, k, v, f));
        return result[0];
    }

    private static ICompoundType resultOrMatch(ICompoundType[] result, Supplier<GooFluid> k, RegistryObject<GooCompoundType> v, Fluid f) {
        if (result[0] != null) {
            return result[0];
        }

        final GooFluid g = k.get();
        if (g == null && f == null)
            return v.get();
        if (g == null)
            return result[0];

        if (k.get().equals(f)) {
            return v.get();
        }
        return result[0];
    }
}
