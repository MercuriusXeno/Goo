package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity type registry. Currently empty - world effects use blocks, not entities.
 */
public class GooEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, Goo.MODID);
}
