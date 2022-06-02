package com.xeno.goo.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xeno.goo.GooMod;
import com.xeno.goo.Registry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class PetrificationLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
	private final EntityModel<T> model;
	public static final ResourceLocation PETRIFICATION_LOCATION = GooMod.location("textures/entity/petrification_alt.png");
	public PetrificationLayer(RenderLayerParent<T, M> layer) {
		super(layer);
		this.model = layer.getModel();
	}


	@Override
	public void render(PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight, T pLivingEntity, float pLimbSwing, float pLimbSwingAmount, float pPartialTicks,
			float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
		if (pLivingEntity.getActiveEffectsMap().containsKey(Registry.PETRIFICATION_EFFECT.get())) {
			var amp = pLivingEntity.getActiveEffectsMap().get(Registry.PETRIFICATION_EFFECT.get()).getAmplifier();
			var opacity = amp > 0 ? (amp > 1 ? 0.9f : 0.7f) : 0.4f; // 0.4, 0.7 and 0.9
			var darkness = 1F;
			EntityModel<T> entityModel = this.model;
			this.getParentModel().copyPropertiesTo(entityModel);
			entityModel.prepareMobModel(pLivingEntity, pLimbSwing, pLimbSwingAmount, pPartialTicks);
			this.model.setupAnim(pLivingEntity, pLimbSwing, pLimbSwingAmount, pAgeInTicks, pNetHeadYaw, pHeadPitch);


			VertexConsumer vertexconsumer = pBuffer.getBuffer(RenderType.entityTranslucent(PETRIFICATION_LOCATION));
			entityModel.renderToBuffer(pMatrixStack, vertexconsumer, pPackedLight, OverlayTexture.NO_OVERLAY, darkness, darkness, darkness, opacity);
		}
	}
}
