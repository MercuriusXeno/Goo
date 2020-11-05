package com.xeno.goo.setup;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBee;
import com.xeno.goo.entities.MutantBee;
import com.xeno.goo.interactions.GooInteractions;
import com.xeno.goo.network.Networking;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraft.village.PointOfInterestType;
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

        event.enqueueWork(() -> {
            GlobalEntityTypeAttributes.put(Registry.GOO_BEE.get(), GooBee.setCustomAttributes().create());
            GlobalEntityTypeAttributes.put(Registry.MUTANT_BEE.get(), MutantBee.setCustomAttributes().create());
            PointOfInterestType.registerBlockStates(Registry.CRYSTAL_NEST_POI.get());
            PointOfInterestType.BLOCKS_OF_INTEREST.addAll(Registry.CRYSTAL_NEST_POI.get().blockStates);
            PointOfInterestType.registerBlockStates(Registry.GOO_TROUGH_POI.get());
            PointOfInterestType.BLOCKS_OF_INTEREST.addAll(Registry.GOO_TROUGH_POI.get().blockStates);
        });
    }

    public static void loadComplete(final FMLLoadCompleteEvent event)
    {
        loadConfig();
    }

    private static void loadConfig()
    {
        GooMod.config = new GooConfig();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, GooMod.config.server);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, GooMod.config.client);
        GooMod.config.loadConfig(GooMod.config.server, FMLPaths.CONFIGDIR.get().resolve("goo-server.toml"));
        GooMod.config.loadConfig(GooMod.config.client, FMLPaths.CONFIGDIR.get().resolve("goo-client.toml"));
    }

    private static void toggleClientSideGooVisibilityPreference(boolean f) {
        GooMod.config.setValuesVisibleWithoutBook(f);
    }
}
