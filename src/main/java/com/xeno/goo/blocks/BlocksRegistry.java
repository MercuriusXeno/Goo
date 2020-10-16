package com.xeno.goo.blocks;

import com.xeno.goo.GooMod;
import net.minecraft.block.Block;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class BlocksRegistry {

    private static final DeferredRegister<Block> Blocks = DeferredRegister.create(ForgeRegistries.BLOCKS, GooMod.MOD_ID);

    public static void initialize() {
        Blocks.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public static final RegistryObject<GooBulb> Bulb = Blocks.register("goo_bulb", GooBulb::new);
    public static final RegistryObject<GooPump> Pump = Blocks.register("goo_pump", GooPump::new);
    public static final RegistryObject<Gooifier> Gooifier = Blocks.register("gooifier", Gooifier::new);
    public static final RegistryObject<Mixer> Mixer = Blocks.register("mixer", Mixer::new);
    public static final RegistryObject<Crucible> Crucible = Blocks.register("crucible", Crucible::new);
    public static final RegistryObject<Solidifier> Solidifier = Blocks.register("solidifier", Solidifier::new);
    public static final RegistryObject<Drain> Drain = Blocks.register("drain", Drain::new);
    public static final RegistryObject<Lobber> Lobber = Blocks.register("lobber", Lobber::new);
}
