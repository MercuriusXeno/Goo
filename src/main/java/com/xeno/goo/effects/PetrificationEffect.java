package com.xeno.goo.effects;

import com.xeno.goo.blobs.GooElement;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class PetrificationEffect extends AbstractGooEffect {
	private static final String ALREADY_NO_AI = "already_no_ai";
	public PetrificationEffect() {
		super();
	}

	@Override
	public List<ItemStack> getCurativeItems() {
		return List.of();
	}

	@Override
	public void addAttributeModifiers(LivingEntity pLivingEntity, AttributeMap pAttributeMap, int pAmplifier) {
		super.addAttributeModifiers(pLivingEntity, pAttributeMap, pAmplifier);
		if (pAmplifier == GooElement.EARTH.element().debuffMaxStacks() && pLivingEntity instanceof Mob m) {
			if (m.isNoAi()) {
				m.addTag(ALREADY_NO_AI);
			} else {
				m.setNoAi(true);
			}
		}
	}

	@Override
	public void removeAttributeModifiers(LivingEntity pLivingEntity, AttributeMap pAttributeMap, int pAmplifier) {
		super.removeAttributeModifiers(pLivingEntity, pAttributeMap, pAmplifier);
		if (pAmplifier == GooElement.EARTH.element().debuffMaxStacks() && pLivingEntity instanceof Mob m) {
			if (m.isNoAi() && !m.getTags().contains(ALREADY_NO_AI)) {
				m.setNoAi(false);
			} else if (m.getTags().contains(ALREADY_NO_AI)){
				m.removeTag(ALREADY_NO_AI); // don't need the tag anymore.
			}
		}
	}
}
