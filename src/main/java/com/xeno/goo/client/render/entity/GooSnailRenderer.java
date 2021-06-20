package com.xeno.goo.client.render.entity;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.GooSnailModel;
import com.xeno.goo.entities.GooSnail;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class GooSnailRenderer extends MobRenderer<GooSnail, GooSnailModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(GooMod.MOD_ID, "textures/entity/goo_snail/goo_snail.png");
    private final GooSnailModel hiddenModel;
    private final GooSnailModel revealedModel;
    public GooSnailRenderer(EntityRendererManager renderManager) {
        super(renderManager, new GooSnailModel(false), 0.4F);
        revealedModel = this.entityModel;
        hiddenModel = new GooSnailModel(true);
    }

    public static void register()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.GOO_SNAIL, GooSnailRenderer::new);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getEntityTexture(GooSnail entity) {
        return TEXTURE;
    }

    @Override
    public void render(GooSnail entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn) {
        this.entityModel = entityIn.isSpooked() ? hiddenModel : revealedModel;

        super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
    }
}
