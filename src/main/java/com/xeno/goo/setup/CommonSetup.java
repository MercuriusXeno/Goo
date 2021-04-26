package com.xeno.goo.setup;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBee;
import com.xeno.goo.entities.GooSnail;
import com.xeno.goo.entities.MutantBee;
import com.xeno.goo.interactions.GooInteractions;
import com.xeno.goo.network.Networking;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraft.village.PointOfInterestType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.Heightmap.Type;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.spawner.WorldEntitySpawner;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import static net.minecraft.world.spawner.WorldEntitySpawner.func_234967_a_;

public class CommonSetup
{
    public static void init(final FMLCommonSetupEvent event)
    {
        Networking.registerNetworkMessages();

        GooInteractions.initialize();

        EntityClassification.create("goo:goo_snail", "goo:goo_snail", 6, true, true, 128);

        event.enqueueWork(() -> {
            PointOfInterestType.registerBlockStates(Registry.CRYSTAL_NEST_POI.get());
            PointOfInterestType.BLOCKS_OF_INTEREST.addAll(Registry.CRYSTAL_NEST_POI.get().blockStates);
            PointOfInterestType.registerBlockStates(Registry.GOO_TROUGH_POI.get());
            PointOfInterestType.BLOCKS_OF_INTEREST.addAll(Registry.GOO_TROUGH_POI.get().blockStates);
        });
    }

    public static void entityAttributeCreation(final EntityAttributeCreationEvent event) {
        event.put(Registry.GOO_BEE.get(), GooBee.setCustomAttributes().create());
        event.put(Registry.MUTANT_BEE.get(), MutantBee.setCustomAttributes().create());
        event.put(Registry.GOO_SNAIL.get(), GooSnail.setCustomAttributes().create());
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
}
