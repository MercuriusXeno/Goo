package com.xeno.goop;

import com.xeno.goop.events.ForgeCommonEvents;
import com.xeno.goop.events.ModClientEvents;
import com.xeno.goop.mappings.MappingHandler;
import com.xeno.goop.setup.*;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
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

        initializeConfiguration();

        Registry.init();

        initializeEventListeners();
    }

    private void initializeConfiguration()
    {
        config = new Config();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, config.server);
        config.loadConfig(config.server, FMLPaths.CONFIGDIR.get().resolve("goop-server.toml"));
    }

    private void initializeEventListeners()
    {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeCommonEvents::init);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ModClientEvents::init);
    }

    public static final ItemGroup ITEM_GROUP = new ItemGroup(MOD_ID)
    {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(Registry.SOLIDIFIER.get());
        }
    };
}
