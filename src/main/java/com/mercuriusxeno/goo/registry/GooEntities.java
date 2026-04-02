package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.effect.ChainMarkerEffect;
import com.mercuriusxeno.goo.effect.GooWorldEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity type registry. One type per entity base class - goo type determines
 * behavior via profiles, not separate entity registrations.
 */
public class GooEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, Goo.MODID);

    /** Generic world effect marker - base type for simple persistent effects. */
    public static final DeferredHolder<EntityType<?>, EntityType<GooWorldEffect>> GOO_WORLD_EFFECT =
            ENTITIES.register("goo_world_effect", () ->
                    EntityType.Builder.<GooWorldEffect>of(GooWorldEffect::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .noSummon()
                            .fireImmune()
                            .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                    Identifier.fromNamespaceAndPath(Goo.MODID, "goo_world_effect"))));

    /** Chain marker - short-lived fuse entity for Blaze, Frost, Nether, Rock. */
    public static final DeferredHolder<EntityType<?>, EntityType<ChainMarkerEffect>> CHAIN_MARKER =
            ENTITIES.register("chain_marker", () ->
                    EntityType.Builder.<ChainMarkerEffect>of(ChainMarkerEffect::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .noSummon()
                            .fireImmune()
                            .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                    Identifier.fromNamespaceAndPath(Goo.MODID, "chain_marker"))));
}
