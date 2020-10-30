package com.xeno.goo.client.render;

import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.MutantBeeModel;
import com.xeno.goo.entities.MutantBee;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.entity.BeeRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.model.BeeModel;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class MutantBeeRendeerer extends MobRenderer<MutantBee, MutantBeeModel<MutantBee>> {
    private static final ResourceLocation justAngry = new ResourceLocation("textures/entity/goo_bee/bee_angry.png");
    private static final ResourceLocation angryWithNectar = new ResourceLocation("textures/entity/goo_bee/bee_angry_nectar.png");
    private static final ResourceLocation normal = new ResourceLocation(GooMod.MOD_ID, "textures/entity/goo_bee/mutant_bee.png");
    private static final ResourceLocation withNectar = new ResourceLocation(GooMod.MOD_ID, "textures/entity/goo_bee/mutant_bee_nectar.png");
    public MutantBeeRendeerer(EntityRendererManager renderManager) {
        super(renderManager, new MutantBeeModel<>(), 0.4f);
    }

    public static void register()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.MUTANT_BEE.get(), MutantBeeRendeerer::new);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getEntityTexture(MutantBee entity) {
        if (entity.func_233678_J__()) {
            return entity.hasNectar() ? angryWithNectar : justAngry;
        } else {
            return entity.hasNectar() ? withNectar : normal;
        }
    }
}
