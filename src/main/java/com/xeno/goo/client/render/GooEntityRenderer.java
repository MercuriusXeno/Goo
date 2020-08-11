package com.xeno.goo.client.render;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

import java.util.Objects;

public class GooEntityRenderer extends EntityRenderer<GooEntity>
{
    public GooEntityRenderer(EntityRendererManager manager) {
        super(manager);
    }

    public static void register()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.GOO.get(), GooEntityRenderer::new);
    }

    @Override
    public ResourceLocation getEntityTexture(GooEntity entity)
    {
        return new ResourceLocation(GooMod.MOD_ID, Objects.requireNonNull(entity.goo().getFluid().getRegistryName()).getPath());
    }
}
