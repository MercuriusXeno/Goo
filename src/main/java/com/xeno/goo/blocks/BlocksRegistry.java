package com.xeno.goo.blocks;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class BlocksRegistry {

    private static final DeferredRegister<Block> Blocks = DeferredRegister.create(ForgeRegistries.BLOCKS, GooMod.MOD_ID);

    public static void initialize() {
        Blocks.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public static final RegistryObject<PassivatedBlock> PassivatedBlock = Blocks.register("passivated_block", PassivatedBlock::new);
    public static final RegistryObject<GooBulb> Bulb = Blocks.register("goo_bulb", GooBulb::new);
    public static final RegistryObject<GooPump> Pump = Blocks.register("goo_pump", GooPump::new);
    public static final RegistryObject<Gooifier> Gooifier = Blocks.register("gooifier", Gooifier::new);
    public static final RegistryObject<Mixer> Mixer = Blocks.register("mixer", Mixer::new);
    public static final RegistryObject<Degrader> Degrader = Blocks.register("crucible", Degrader::new);
    public static final RegistryObject<Solidifier> Solidifier = Blocks.register("solidifier", Solidifier::new);
    public static final RegistryObject<Drain> Drain = Blocks.register("drain", Drain::new);
    public static final RegistryObject<Lobber> Lobber = Blocks.register("lobber", Lobber::new);
    public static final RegistryObject<RadiantLight> RadiantLight = Blocks.register("radiant_light", RadiantLight::new);
    public static final RegistryObject<CrystalNest> CrystalNest = Blocks.register("crystal_nest", CrystalNest::new);
    public static final RegistryObject<GooTrough> Trough = Blocks.register("goo_trough", GooTrough::new);
    public static final RegistryObject<GooPad> Pad = Blocks.register("goo_pad", GooPad::new);

    public static final Map<ResourceLocation, RegistryObject<CrystalBlock>> CrystalBlocks = new HashMap<>();


    public static final String[] CRYSTAL_BLOCK_VARIANTS = { "bricks", "bulbous", "bundled", "craggy", "keystone", "marbled", "ornate", "smooth", "solid"};
    public static final String[] PILLAR_CRYSTAL_BLOCK_VARIANTS = { "debris", "pillar"};
    static {
        Registry.FluidSuppliers.forEach(BlocksRegistry::registerCrystalBlockVariants);
    }

    private static void registerCrystalBlockVariants(ResourceLocation k, Supplier<GooFluid> v) {
        for(String variant : CRYSTAL_BLOCK_VARIANTS) {
            registerCrystalBlock(k.getPath() +  "_" + variant, v);
        }

        for (String variant : PILLAR_CRYSTAL_BLOCK_VARIANTS) {
            registerCrystalBlock(k.getPath() +  "_" + variant, v);
        }
    }

    public static RegistryObject<CrystalBlock> registerCrystalBlock(String name, Supplier<GooFluid> f) {
        RegistryObject<CrystalBlock> registeredObject = Blocks.register(name, () -> new CrystalBlock(f));
        CrystalBlocks.put(new ResourceLocation(GooMod.MOD_ID, name), registeredObject);
        return registeredObject;
    }
}
