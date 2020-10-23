package com.xeno.goo.client.render;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.entity.BeeRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class GooBeeRenderer extends BeeRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(GooMod.MOD_ID, "textures/entity/bee/goo_bee.png");
    public GooBeeRenderer(EntityRendererManager p_i226033_1_) {
        super(p_i226033_1_);
    }

    public static void register()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.GOO_BEE.get(), GooBeeRenderer::new);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getEntityTexture(BeeEntity entity) {
        return TEXTURE;
    }
}
