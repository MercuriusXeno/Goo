package com.xeno.goo.setup;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBee;
import com.xeno.goo.entities.MutantBee;
import com.xeno.goo.interactions.GooInteractions;
import com.xeno.goo.network.Networking;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraftforge.fml.DeferredWorkQueue;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.lifecycle.ParallelDispatchEvent;
import net.minecraftforge.fml.loading.FMLPaths;

public class CommonSetup
{
    public static void init(final FMLCommonSetupEvent event)
    {
        Networking.registerNetworkMessages();

        GooInteractions.initialize();
    }

    public static void loadComplete(final FMLLoadCompleteEvent event)
    {
        loadConfig();
    }

    private static void loadConfig()
    {
        GooMod.config = new GooConfig();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, GooMod.config.server);
        GooMod.config.loadConfig(GooMod.config.server, FMLPaths.CONFIGDIR.get().resolve("goo-server.toml"));
    }

    public static void deferredWork(final ParallelDispatchEvent event) {
        event.enqueueWork(
                () -> GlobalEntityTypeAttributes.put(Registry.GOO_BEE.get(), GooBee.setCustomAttributes().create())
        );
        event.enqueueWork(
                () -> GlobalEntityTypeAttributes.put(Registry.MUTANT_BEE.get(), MutantBee.setCustomAttributes().create())
        );
    }
}
