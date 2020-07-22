package com.xeno.goop;

import com.xeno.goop.setup.*;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("goop")
public class GoopMod
{
    public static final String MOD_ID = "goop";

    public static final boolean DEBUG = true;

    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger();

    public GoopMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);

        Registry.init();

        // Register the setup method for mod-loading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CommonSetup::init);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientSetup::init);

        // configuration things

        Config.loadConfig(Config.COMMON_CONFIG, FMLPaths.CONFIGDIR.get().resolve("goop-common.toml"));
    }
}
