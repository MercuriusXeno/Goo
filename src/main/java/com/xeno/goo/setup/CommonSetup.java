package com.xeno.goo.setup;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBee;
import com.xeno.goo.entities.GooSnail;
import com.xeno.goo.entities.LightingBug;
import com.xeno.goo.entities.MutantBee;
import com.xeno.goo.interactions.GooInteractions;
import com.xeno.goo.network.Networking;
import net.minecraft.village.PointOfInterestType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.loading.FMLPaths;

public class CommonSetup
{
    public static void init(final FMLCommonSetupEvent event)
    {
        Networking.registerNetworkMessages();

        GooInteractions.initialize();

        GooEntityClassifications.init();

        event.enqueueWork(() -> {
            PointOfInterestType.registerBlockStates(Registry.CRYSTAL_NEST_POI.get());
            PointOfInterestType.BLOCKS_OF_INTEREST.addAll(Registry.CRYSTAL_NEST_POI.get().blockStates);
            PointOfInterestType.registerBlockStates(Registry.GOO_TROUGH_POI.get());
            PointOfInterestType.BLOCKS_OF_INTEREST.addAll(Registry.GOO_TROUGH_POI.get().blockStates);
        });
    }

    public static void entityAttributeCreation(final EntityAttributeCreationEvent event) {
        event.put(Registry.GOO_BEE, GooBee.setCustomAttributes().create());
        event.put(Registry.MUTANT_BEE, MutantBee.setCustomAttributes().create());
        event.put(Registry.GOO_SNAIL, GooSnail.setCustomAttributes().create());
        event.put(Registry.LIGHTING_BUG, LightingBug.setCustomAttributes().create());
    }

    public static void loadComplete(final FMLLoadCompleteEvent event)
    {
        loadConfig();
    }

    private static void loadConfig()
    {
        GooMod.config = new Config();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, GooMod.config.server);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, GooMod.config.client);
        GooMod.config.loadConfig(GooMod.config.server, FMLPaths.CONFIGDIR.get().resolve("goo-server.toml"));
        GooMod.config.loadConfig(GooMod.config.client, FMLPaths.CONFIGDIR.get().resolve("goo-client.toml"));
    }
}
