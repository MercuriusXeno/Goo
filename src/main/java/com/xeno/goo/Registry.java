package com.xeno.goo;

import com.ldtteam.aequivaleo.api.compound.type.ICompoundType;
import com.ldtteam.aequivaleo.api.compound.type.group.ICompoundTypeGroup;
import com.xeno.goo.aequivaleo.compound.GooCompoundType;
import com.xeno.goo.aequivaleo.compound.GooCompoundTypeGroup;
import com.xeno.goo.elements.ElementEnum;
import com.xeno.goo.effects.AbstractGooEffect;
import com.xeno.goo.effects.PetrificationEffect;
import com.xeno.goo.entities.*;
import com.xeno.goo.items.BlobOfGooItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class Registry {
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GooMod.MOD_ID);
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, GooMod.MOD_ID);
	private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, GooMod.MOD_ID);
	private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, GooMod.MOD_ID);

	// GOO TYPES, THIS WHOLE CHONK
	private static final ResourceLocation AEQ_COMPOUND_TYPES_LOC = new ResourceLocation("aequivaleo", "compound_type");
	private static final ResourceLocation AEQ_COMPOUND_TYPE_GROUPS_LOC = new ResourceLocation("aequivaleo", "compound_type_group");
	public static final DeferredRegister<ICompoundType> TYPES = DeferredRegister.create(AEQ_COMPOUND_TYPES_LOC, GooMod.MOD_ID);
	public static final DeferredRegister<ICompoundTypeGroup> TYPE_GROUPS = DeferredRegister.create(AEQ_COMPOUND_TYPE_GROUPS_LOC, GooMod.MOD_ID);
	public static final RegistryObject<GooCompoundTypeGroup> GOO_TYPES = TYPE_GROUPS.register("goo_types", GooCompoundTypeGroup::new);
	public static final RegistryObject<ICompoundType> EARTH = TYPES.register("earth", () -> new GooCompoundType(ElementEnum.EARTH, GOO_TYPES));
	public static final RegistryObject<ICompoundType> AIR = TYPES.register("air", () -> new GooCompoundType(ElementEnum.AIR, GOO_TYPES));
	public static final RegistryObject<ICompoundType> FIRE = TYPES.register("fire", () -> new GooCompoundType(ElementEnum.FIRE, GOO_TYPES));
	public static final RegistryObject<ICompoundType> WATER = TYPES.register("water", () -> new GooCompoundType(ElementEnum.WATER, GOO_TYPES));
	public static final RegistryObject<ICompoundType> ICE = TYPES.register("ice", () -> new GooCompoundType(ElementEnum.ICE, GOO_TYPES));
	public static final RegistryObject<ICompoundType> LIGHTNING = TYPES.register("lightning", () -> new GooCompoundType(ElementEnum.LIGHTNING, GOO_TYPES));
	public static final RegistryObject<ICompoundType> METAL = TYPES.register("metal", () -> new GooCompoundType(ElementEnum.METAL, GOO_TYPES));
	public static final RegistryObject<ICompoundType> CRYSTAL = TYPES.register("crystal", () -> new GooCompoundType(ElementEnum.CRYSTAL, GOO_TYPES));
	public static final RegistryObject<ICompoundType> DARK = TYPES.register("dark", () -> new GooCompoundType(ElementEnum.DARK, GOO_TYPES));
	public static final RegistryObject<ICompoundType> LIGHT = TYPES.register("light", () -> new GooCompoundType(ElementEnum.LIGHT, GOO_TYPES));
	public static final RegistryObject<ICompoundType> NATURE = TYPES.register("nature", () -> new GooCompoundType(ElementEnum.NATURE, GOO_TYPES));
	public static final RegistryObject<ICompoundType> ENDER = TYPES.register("ender", () -> new GooCompoundType(ElementEnum.ENDER, GOO_TYPES));
	public static final RegistryObject<ICompoundType> NETHER = TYPES.register("nether", () -> new GooCompoundType(ElementEnum.NETHER, GOO_TYPES));
	public static final RegistryObject<ICompoundType> FORBIDDEN = TYPES.register("forbidden", () -> new GooCompoundType(ElementEnum.FORBIDDEN, GOO_TYPES));
	public static void init() {
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		BLOCKS.register(bus);
		ITEMS.register(bus);
		TYPES.register(bus);
		TYPE_GROUPS.register(bus);
		ENTITIES.register(bus);
		EFFECTS.register(bus);
	}

	// TEST ITEMS FOR LOBBING THE GOO
	public static final RegistryObject<Item> EARTH_BLOB_ITEM = ITEMS.register("earth_blob_item", () -> new BlobOfGooItem(ElementEnum.EARTH));
	public static final RegistryObject<Item> AIR_BLOB_ITEM = ITEMS.register("air_blob_item", () -> new BlobOfGooItem(ElementEnum.AIR));
	public static final RegistryObject<Item> FIRE_BLOB_ITEM = ITEMS.register("fire_blob_item", () -> new BlobOfGooItem(ElementEnum.FIRE));
	public static final RegistryObject<Item> WATER_BLOB_ITEM = ITEMS.register("water_blob_item", () -> new BlobOfGooItem(ElementEnum.WATER));
	public static final RegistryObject<Item> ICE_BLOB_ITEM = ITEMS.register("ice_blob_item", () -> new BlobOfGooItem(ElementEnum.ICE));
	public static final RegistryObject<Item> LIGHTNING_BLOB_ITEM = ITEMS.register("lightning_blob_item", () -> new BlobOfGooItem(ElementEnum.LIGHTNING));
	public static final RegistryObject<Item> METAL_BLOB_ITEM = ITEMS.register("metal_blob_item", () -> new BlobOfGooItem(ElementEnum.METAL));
	public static final RegistryObject<Item> CRYSTAL_BLOB_ITEM = ITEMS.register("crystal_blob_item", () -> new BlobOfGooItem(ElementEnum.CRYSTAL));
	public static final RegistryObject<Item> LIGHT_BLOB_ITEM = ITEMS.register("light_blob_item", () -> new BlobOfGooItem(ElementEnum.LIGHT));
	public static final RegistryObject<Item> DARK_BLOB_ITEM = ITEMS.register("dark_blob_item", () -> new BlobOfGooItem(ElementEnum.DARK));
	public static final RegistryObject<Item> NATURE_BLOB_ITEM = ITEMS.register("nature_blob_item", () -> new BlobOfGooItem(ElementEnum.NATURE));
	public static final RegistryObject<Item> ENDER_BLOB_ITEM = ITEMS.register("ender_blob_item", () -> new BlobOfGooItem(ElementEnum.ENDER));
	public static final RegistryObject<Item> NETHER_BLOB_ITEM = ITEMS.register("nether_blob_item", () -> new BlobOfGooItem(ElementEnum.NETHER));

	// GOO BLOB ENTITY FOR THE LOBBING
	public static final RegistryObject<EntityType<ThrownEarthBlob>> THROWN_EARTH_BLOB = ENTITIES.register("thrown_earth_blob",
			() -> EntityType.Builder.<ThrownEarthBlob>of(ThrownEarthBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_earth_blob"));
	public static final RegistryObject<EntityType<ThrownAirBlob>> THROWN_AIR_BLOB = ENTITIES.register("thrown_air_blob",
			() -> EntityType.Builder.<ThrownAirBlob>of(ThrownAirBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_air_blob"));
	public static final RegistryObject<EntityType<ThrownFireBlob>> THROWN_FIRE_BLOB = ENTITIES.register("thrown_fire_blob",
			() -> EntityType.Builder.<ThrownFireBlob>of(ThrownFireBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_fire_blob"));
	public static final RegistryObject<EntityType<ThrownWaterBlob>> THROWN_WATER_BLOB = ENTITIES.register("thrown_water_blob",
			() -> EntityType.Builder.<ThrownWaterBlob>of(ThrownWaterBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_water_blob"));
	public static final RegistryObject<EntityType<ThrownIceBlob>> THROWN_ICE_BLOB = ENTITIES.register("thrown_ice_blob",
			() -> EntityType.Builder.<ThrownIceBlob>of(ThrownIceBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_ice_blob"));
	public static final RegistryObject<EntityType<ThrownLightningBlob>> THROWN_LIGHTNING_BLOB = ENTITIES.register("thrown_lightning_blob",
			() -> EntityType.Builder.<ThrownLightningBlob>of(ThrownLightningBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_lightning_blob"));
	public static final RegistryObject<EntityType<ThrownDarkBlob>> THROWN_DARK_BLOB = ENTITIES.register("thrown_dark_blob",
			() -> EntityType.Builder.<ThrownDarkBlob>of(ThrownDarkBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_dark_blob"));
	public static final RegistryObject<EntityType<ThrownLightBlob>> THROWN_LIGHT_BLOB = ENTITIES.register("thrown_light_blob",
			() -> EntityType.Builder.<ThrownLightBlob>of(ThrownLightBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_light_blob"));
	public static final RegistryObject<EntityType<ThrownCrystalBlob>> THROWN_CRYSTAL_BLOB = ENTITIES.register("thrown_crystal_blob",
			() -> EntityType.Builder.<ThrownCrystalBlob>of(ThrownCrystalBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_crystal_blob"));
	public static final RegistryObject<EntityType<ThrownMetalBlob>> THROWN_METAL_BLOB = ENTITIES.register("thrown_metal_blob",
			() -> EntityType.Builder.<ThrownMetalBlob>of(ThrownMetalBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_metal_blob"));
	public static final RegistryObject<EntityType<ThrownNatureBlob>> THROWN_NATURE_BLOB = ENTITIES.register("thrown_nature_blob",
			() -> EntityType.Builder.<ThrownNatureBlob>of(ThrownNatureBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_nature_blob"));
	public static final RegistryObject<EntityType<ThrownNetherBlob>> THROWN_NETHER_BLOB = ENTITIES.register("thrown_nether_blob",
			() -> EntityType.Builder.<ThrownNetherBlob>of(ThrownNetherBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_nether_blob"));
	public static final RegistryObject<EntityType<ThrownEnderBlob>> THROWN_ENDER_BLOB = ENTITIES.register("thrown_ender_blob",
			() -> EntityType.Builder.<ThrownEnderBlob>of(ThrownEnderBlob::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(32).updateInterval(2).build("thrown_ender_blob"));

	// STATUS EFFECTS, AILMENTS, BUFFS ETC
	public static final PetrificationEffect PETRIFICATION_EFFECT_SINGLETON = (PetrificationEffect)new PetrificationEffect()
			.addAttributeModifier(Attributes.MOVEMENT_SPEED, "530806B2-4908-433E-8728-46D920B2A796", (double)-0.01F, AttributeModifier.Operation.MULTIPLY_TOTAL)
			.addAttributeModifier(Attributes.ATTACK_SPEED, "AD42B3CC-5EE3-41FC-A81C-3B544202CA6F", (double)-0.01F, AttributeModifier.Operation.MULTIPLY_TOTAL);
	public static final RegistryObject<AbstractGooEffect> PETRIFICATION_EFFECT = EFFECTS.register("petrification", () -> PETRIFICATION_EFFECT_SINGLETON);

}
