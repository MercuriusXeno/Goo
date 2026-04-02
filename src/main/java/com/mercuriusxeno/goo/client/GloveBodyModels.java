package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Standalone baked models for per-tier glove body rendering.
 * Each tier (leather, netherite, exorite) has its own body model and texture.
 */
public final class GloveBodyModels {

    private static final StandaloneModelKey<QuadCollection> LEATHER_KEY = makeKey("glove_body");
    private static final StandaloneModelKey<QuadCollection> NETHERITE_KEY = makeKey("glove_netherite_body");
    private static final StandaloneModelKey<QuadCollection> EXORITE_KEY = makeKey("glove_exorite_body");

    private GloveBodyModels() {}

    /** Registers all three tier body models as standalone models. */
    public static void register(ModelEvent.RegisterStandalone event) {
        registerModel(event, LEATHER_KEY, "item/glove_body");
        registerModel(event, NETHERITE_KEY, "item/glove_netherite_body");
        registerModel(event, EXORITE_KEY, "item/glove_exorite_body");
    }

    /** Returns the baked body model for the given glove item. */
    public static QuadCollection getModel(Item item) {
        StandaloneModelKey<QuadCollection> key;
        if (item == GooItems.EXO_GAUNTLET.get()) {
            key = EXORITE_KEY;
        } else if (item == GooItems.GOO_GAUNTLET.get()) {
            key = NETHERITE_KEY;
        } else {
            key = LEATHER_KEY;
        }
        return Minecraft.getInstance().getModelManager().getStandaloneModel(key);
    }

    private static StandaloneModelKey<QuadCollection> makeKey(String name) {
        return new StandaloneModelKey<>(new ModelDebugName() {
            @Override
            public String debugName() {
                return Goo.MODID + ":" + name;
            }
        });
    }

    private static void registerModel(ModelEvent.RegisterStandalone event,
            StandaloneModelKey<QuadCollection> key, String path) {
        event.register(key, SimpleUnbakedStandaloneModel.quadCollection(
            Identifier.fromNamespaceAndPath(Goo.MODID, path)));
    }
}
