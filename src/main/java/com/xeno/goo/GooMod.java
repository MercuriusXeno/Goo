package com.xeno.goo;

import com.xeno.goo.client.render.PetrificationLayer;
import com.xeno.goo.effects.PetrificationEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map.Entry;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(GooMod.MOD_ID)
public class GooMod {
	public static final String MOD_ID = "goo";
	// Directly reference a log4j logger.
	public static final Logger LOGGER = LogManager.getLogger();

	public GooMod() {
		Registry.init();
	}

	public static ResourceLocation location(String objectName) {
		return new ResourceLocation(MOD_ID, objectName);
	}

	@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
	public static class ForgeEvents {
		@SubscribeEvent
		public static void onLivingUpdate(final LivingUpdateEvent event) {
			PetrificationEffect.handlePetrificationTicks(event);
		}

//		@SubscribeEvent
//		public static void onLivingHurt(final LivingHurtEvent event) {
//			PetrificationEffect.handlePetrificationResistance(event);
//		}

		// this event is stupidly named. It's not firing when the target of the event sends an attack. It's on receipt.
		@SubscribeEvent
		public static void onLivingAttacked(final LivingAttackEvent event) {
			PetrificationEffect.handlePetrificationResistance(event);
		}
	}

	@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class ClientEvents {

		@SubscribeEvent
		public static void init(final FMLClientSetupEvent event)
		{
			// rendering stuff
			setRenderLayers();

			setEntityRenderers();
		}

		@SubscribeEvent
		public static void addLayers(EntityRenderersEvent.AddLayers event) {
			for (Entry<EntityType<?>, EntityRenderer<?>> entry : Minecraft.getInstance().getEntityRenderDispatcher().renderers.entrySet()) {
				// the armor stand, it is alive! lol
				// let's not petrify the armor stands.
				if (entry.getKey().equals(EntityType.ARMOR_STAND)) {
					continue;
				}
				EntityRenderer<?> renderer = entry.getValue();
				if (renderer instanceof LivingEntityRenderer) {
					EntityType<?> entityType = entry.getKey();
					//noinspection unchecked,rawtypes
					addPetrificationLayerToLivingEntityRenderer(entityType, event.getRenderer((EntityType) entityType));
				}
			}
		}

		private static void addPetrificationLayerToLivingEntityRenderer(EntityType<?> entityType, LivingEntityRenderer renderer) {
			renderer.addLayer(new PetrificationLayer(renderer));
			LOGGER.debug("Added Petrification layer to entity type: {}", entityType.getRegistryName());
		}

		private static void setEntityRenderers()
		{
			setEntityRenderersForThrownBlobs();
		}

		private static void setEntityRenderersForThrownBlobs()
		{
			EntityRenderers.register(Registry.THROWN_EARTH_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_AIR_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_FIRE_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_WATER_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_ICE_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_LIGHTNING_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_DARK_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_LIGHT_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_METAL_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_CRYSTAL_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_NATURE_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_NATURE_BLOB.get(), ThrownItemRenderer::new);
			EntityRenderers.register(Registry.THROWN_ENDER_BLOB.get(), ThrownItemRenderer::new);
		}

		private static void setRenderLayers() {
			// ItemBlockRenderTypes.setRenderLayer(BlocksRegistry.MORTAR.get(), RenderType.cutoutMipped());
		}
	}

	public static final CreativeModeTab ITEM_GROUP = new GooCreativeTab(MOD_ID);
}
