package com.xeno.goop;

import com.xeno.goop.setup.*;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("goop")
public class GoopMod
{
    public static final String MOD_ID = "goop";

    public static Config config;

    public static MappingHandler mappingHandler;

    public GoopMod() {
        mappingHandler = new MappingHandler();

        // configuration things
        config = new Config();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, config.server);
        config.loadConfig(config.server, FMLPaths.CONFIGDIR.get().resolve("goop-server.toml"));

        Registry.init();

        // Register the setup method for mod-loading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CommonSetup::init);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientSetup::init);
    }
}
