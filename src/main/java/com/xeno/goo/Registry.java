package com.xeno.goo;

import com.ldtteam.aequivaleo.api.compound.type.ICompoundType;
import com.ldtteam.aequivaleo.api.compound.type.group.ICompoundTypeGroup;
import com.xeno.goo.aequivaleo.compound.GooCompoundType;
import com.xeno.goo.aequivaleo.compound.GooCompoundTypeGroup;
import com.xeno.goo.blobs.GooElement;
import com.xeno.goo.items.TestGooItem;
import net.minecraft.resources.ResourceLocation;
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

	// GOO TYPES, THIS WHOLE CHONK
	private static final ResourceLocation AEQ_COMPOUND_TYPES_LOC = new ResourceLocation("aequivaleo", "compound_type");
	private static final ResourceLocation AEQ_COMPOUND_TYPE_GROUPS_LOC = new ResourceLocation("aequivaleo", "compound_type_group");
	public static final DeferredRegister<ICompoundType> TYPES = DeferredRegister.create(AEQ_COMPOUND_TYPES_LOC, GooMod.MOD_ID);
	public static final DeferredRegister<ICompoundTypeGroup> TYPE_GROUPS = DeferredRegister.create(AEQ_COMPOUND_TYPE_GROUPS_LOC, GooMod.MOD_ID);
	public static final RegistryObject<GooCompoundTypeGroup> GOO_TYPES = TYPE_GROUPS.register("goo_types", GooCompoundTypeGroup::new);
	public static final RegistryObject<ICompoundType> EARTH = TYPES.register("earth", () -> new GooCompoundType(GooElement.EARTH, GOO_TYPES));
	public static final RegistryObject<ICompoundType> AIR = TYPES.register("air", () -> new GooCompoundType(GooElement.AIR, GOO_TYPES));
	public static final RegistryObject<ICompoundType> FIRE = TYPES.register("fire", () -> new GooCompoundType(GooElement.FIRE, GOO_TYPES));
	public static final RegistryObject<ICompoundType> WATER = TYPES.register("water", () -> new GooCompoundType(GooElement.WATER, GOO_TYPES));
	public static final RegistryObject<ICompoundType> ICE = TYPES.register("ice", () -> new GooCompoundType(GooElement.ICE, GOO_TYPES));
	public static final RegistryObject<ICompoundType> LIGHTNING = TYPES.register("lightning", () -> new GooCompoundType(GooElement.LIGHTNING, GOO_TYPES));
	public static final RegistryObject<ICompoundType> METAL = TYPES.register("metal", () -> new GooCompoundType(GooElement.METAL, GOO_TYPES));
	public static final RegistryObject<ICompoundType> CRYSTAL = TYPES.register("crystal", () -> new GooCompoundType(GooElement.CRYSTAL, GOO_TYPES));
	public static final RegistryObject<ICompoundType> DARK = TYPES.register("dark", () -> new GooCompoundType(GooElement.DARK, GOO_TYPES));
	public static final RegistryObject<ICompoundType> LIGHT = TYPES.register("light", () -> new GooCompoundType(GooElement.LIGHT, GOO_TYPES));
	public static final RegistryObject<ICompoundType> NATURE = TYPES.register("nature", () -> new GooCompoundType(GooElement.NATURE, GOO_TYPES));
	public static final RegistryObject<ICompoundType> ENDER = TYPES.register("ender", () -> new GooCompoundType(GooElement.ENDER, GOO_TYPES));
	public static final RegistryObject<ICompoundType> NETHER = TYPES.register("nether", () -> new GooCompoundType(GooElement.NETHER, GOO_TYPES));
	public static final RegistryObject<ICompoundType> FORBIDDEN = TYPES.register("forbidden", () -> new GooCompoundType(GooElement.FORBIDDEN, GOO_TYPES));
	public static void init() {
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		BLOCKS.register(bus);
		ITEMS.register(bus);
		TYPES.register(bus);
		TYPE_GROUPS.register(bus);
	}

	public static final RegistryObject<Item> EARTH_TEST_ITEM = ITEMS.register("earth_test_item", () -> new TestGooItem(GooElement.EARTH));
	public static final RegistryObject<Item> AIR_TEST_ITEM = ITEMS.register("air_test_item", () -> new TestGooItem(GooElement.AIR));
	public static final RegistryObject<Item> FIRE_TEST_ITEM = ITEMS.register("fire_test_item", () -> new TestGooItem(GooElement.FIRE));
	public static final RegistryObject<Item> WATER_TEST_ITEM = ITEMS.register("water_test_item", () -> new TestGooItem(GooElement.WATER));
	public static final RegistryObject<Item> ICE_TEST_ITEM = ITEMS.register("ice_test_item", () -> new TestGooItem(GooElement.ICE));
	public static final RegistryObject<Item> LIGHTNING_TEST_ITEM = ITEMS.register("lightning_test_item", () -> new TestGooItem(GooElement.LIGHTNING));
	public static final RegistryObject<Item> METAL_TEST_ITEM = ITEMS.register("metal_test_item", () -> new TestGooItem(GooElement.METAL));
	public static final RegistryObject<Item> CRYSTAL_TEST_ITEM = ITEMS.register("crystal_test_item", () -> new TestGooItem(GooElement.CRYSTAL));
	public static final RegistryObject<Item> LIGHT_TEST_ITEM = ITEMS.register("light_test_item", () -> new TestGooItem(GooElement.LIGHT));
	public static final RegistryObject<Item> DARK_TEST_ITEM = ITEMS.register("dark_test_item", () -> new TestGooItem(GooElement.DARK));
	public static final RegistryObject<Item> NATURE_TEST_ITEM = ITEMS.register("nature_test_item", () -> new TestGooItem(GooElement.NATURE));
	public static final RegistryObject<Item> ENDER_TEST_ITEM = ITEMS.register("ender_test_item", () -> new TestGooItem(GooElement.ENDER));
	public static final RegistryObject<Item> NETHER_TEST_ITEM = ITEMS.register("nether_test_item", () -> new TestGooItem(GooElement.NETHER));



}
