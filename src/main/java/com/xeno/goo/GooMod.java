package com.xeno.goo;

import com.ldtteam.aequivaleo.api.IAequivaleoAPI;
import com.xeno.goo.setup.*;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.logging.Logger;

@Mod("goo")
public class GooMod
{
    public static final String MOD_ID = "goo";

    public static final Logger logger = Logger.getLogger(MOD_ID);

    public static ServerConfiguration config;

    public GooMod() {
        initializeConfiguration();

        Registry.init();

        initializeEventListeners();
    }

    public static void warn(String s) {
        logger.warning(s);
    }

    public static void error(String s) {
        logger.severe(s);
    }

    public static void debug(String s) {
        logger.info(s);
    }

    private void initializeConfiguration()
    {
        // separate configs because the mapper functionality is a mess
        config = new ServerConfiguration();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, config.server);
        config.loadConfig(config.server, FMLPaths.CONFIGDIR.get().resolve("goo-server.toml"));
    }

    private void initializeEventListeners()
    {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CommonSetup::init);
    }

    public static final ItemGroup ITEM_GROUP = new ItemGroup(MOD_ID)
    {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(Registry.SOLIDIFIER.get());
        }
    };
}
