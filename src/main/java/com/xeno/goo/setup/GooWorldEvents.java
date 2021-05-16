package com.xeno.goo.setup;

import com.xeno.goo.GooMod;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.world.gen.Heightmap.Type;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID)
public class GooWorldEvents {

	@SubscribeEvent
	public static void entitySpawners(final RegistryEvent.Register<EntityType<?>> event) {
		EntitySpawnPlacementRegistry.register(Registry.GOO_SNAIL,
				EntitySpawnPlacementRegistry.PlacementType.ON_GROUND,
				Type.MOTION_BLOCKING_NO_LEAVES,
				EntitySpawnConditions::snailSpawnConditions);
	}
}
