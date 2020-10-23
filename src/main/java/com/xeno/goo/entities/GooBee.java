package com.xeno.goo.entities;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.world.World;

public class GooBee extends BeeEntity {
    public GooBee(EntityType<GooBee> gooBeeEntityType, World world) {
        super(gooBeeEntityType, world);
    }

    public static AttributeModifierMap.MutableAttribute setCustomAttributes() {
        return AnimalEntity.func_233666_p_()
                .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.5D)
                .createMutableAttribute(Attributes.MAX_HEALTH, 1.0D)
                .createMutableAttribute(Attributes.ATTACK_DAMAGE, 1.0D);
    }
}
