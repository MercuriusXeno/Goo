package com.xeno.goo.client.render.entity;

import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.LightingBugModel;
import com.xeno.goo.entities.LightingBug;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class LightingBugRenderer extends MobRenderer<LightingBug, LightingBugModel<LightingBug>> {
	private static final ResourceLocation TEXTURE = new ResourceLocation(GooMod.MOD_ID, "textures/entity/lighting_bug/lighting_bug.png");
	public LightingBugRenderer(EntityRendererManager renderManager) {
		super(renderManager, new LightingBugModel<>(), 0.4F);
	}

	public static void register()
	{
		RenderingRegistry.registerEntityRenderingHandler(Registry.LIGHTING_BUG, LightingBugRenderer::new);
	}

	/**
	 * Returns the location of an entity's texture.
	 */
	public ResourceLocation getEntityTexture(LightingBug entity) {
		return TEXTURE;
	}
}
