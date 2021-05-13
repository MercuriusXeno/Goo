package com.xeno.goo.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierManager;
import net.minecraft.entity.ai.goal.Goal.Flag;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectType;

public class HarmlessEffect extends Effect {

	public static Effect instance = new HarmlessEffect();

	protected HarmlessEffect() {
		super(EffectType.NEUTRAL, 0xffff0000);
	}

	@Override
	public void applyAttributesModifiersToEntity(LivingEntity e, AttributeModifierManager attributeMapIn, int amplifier) {
		if (e instanceof MobEntity) {
			GoalSelector selector = ((MobEntity) e).targetSelector;
			selector.disableFlag(Flag.TARGET);
		}
	}

	@Override
	public void removeAttributesModifiersFromEntity(LivingEntity e, AttributeModifierManager attributeMapIn, int amplifier) {
		if (e instanceof MobEntity) {
			GoalSelector selector = ((MobEntity) e).targetSelector;
			selector.enableFlag(Flag.TARGET);
		}
	}
}
