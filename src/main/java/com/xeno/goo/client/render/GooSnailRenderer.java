package com.xeno.goo.client.render;

import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.GooSnailModel;
import com.xeno.goo.entities.GooSnail;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class GooSnailRenderer extends MobRenderer<GooSnail, GooSnailModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(GooMod.MOD_ID, "textures/entity/goo_snail/goo_snail.png");
    public GooSnailRenderer(EntityRendererManager renderManager) {
        super(renderManager, new GooSnailModel(), 0.4F);
    }

    public static void register()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.GOO_SNAIL.get(), GooSnailRenderer::new);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getEntityTexture(GooSnail entity) {
        return TEXTURE;
    }
}
