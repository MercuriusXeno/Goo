package com.xeno.goo;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(GooMod.MOD_ID)
public class GooMod {
	public static final String MOD_ID = "goo";
	// Directly reference a log4j logger.
	private static final Logger LOGGER = LogManager.getLogger();

	public GooMod() {
		Registry.init();
	}

	public static ResourceLocation location(String objectName) {
		return new ResourceLocation(MOD_ID, objectName);
	}

	@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class ClientEvents {

		@SubscribeEvent
		public static void init(final FMLClientSetupEvent event)
		{
			// rendering stuff
			setRenderLayers();
		}

		private static void setRenderLayers() {
			// ItemBlockRenderTypes.setRenderLayer(BlocksRegistry.MORTAR.get(), RenderType.cutoutMipped());
		}
	}

	public static final CreativeModeTab ITEM_GROUP = new GooCreativeTab(MOD_ID);
}
