package com.xeno.goo.client.render.entity;

import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.GooBeeModel;
import com.xeno.goo.entities.GooBee;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class GooBeeRenderer  extends MobRenderer<GooBee, GooBeeModel<GooBee>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(GooMod.MOD_ID, "textures/entity/goo_bee/goo_bee.png");
    public GooBeeRenderer(EntityRendererManager renderManager) {
        super(renderManager, new GooBeeModel<>(), 0.4F);
    }

    public static void register()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.GOO_BEE, GooBeeRenderer::new);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getEntityTexture(GooBee entity) {
        return TEXTURE;
    }
}
