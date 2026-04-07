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

    /** Namespace separator for debug name formatting. */
    private static final String NS_SEP = ":";
    /** Model path for the leather glove body. */
    private static final String LEATHER_MODEL = "item/glove_body";
    /** Model path for the netherite glove body. */
    private static final String NETHERITE_MODEL = "item/glove_netherite_body";
    /** Model path for the exorite glove body. */
    private static final String EXORITE_MODEL = "item/glove_exorite_body";

    private static final StandaloneModelKey<QuadCollection> LEATHER_KEY = makeKey("glove_body");
    private static final StandaloneModelKey<QuadCollection> NETHERITE_KEY = makeKey("glove_netherite_body");
    private static final StandaloneModelKey<QuadCollection> EXORITE_KEY = makeKey("glove_exorite_body");

    private GloveBodyModels() {}

    /**
     * Registers all three tier body models as standalone models.
     *
     * @param event the event instance
     */
    public static void register(ModelEvent.RegisterStandalone event) {
        registerModel(event, LEATHER_KEY, LEATHER_MODEL);
        registerModel(event, NETHERITE_KEY, NETHERITE_MODEL);
        registerModel(event, EXORITE_KEY, EXORITE_MODEL);
    }

    /**
     * Returns the baked body model for the given glove item.
     *
     * @param item the item instance
     * @return the model
     */
    public static QuadCollection getModel(Item item) {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(keyForItem(item));
    }

    /** Returns the model key for the given glove tier.
     *
     * @param item the glove item
     * @return the standalone model key
     */
    private static StandaloneModelKey<QuadCollection> keyForItem(Item item) {
        if (item == GooItems.EXO_GAUNTLET.get()) { return EXORITE_KEY; }
        if (item == GooItems.GOO_GAUNTLET.get()) { return NETHERITE_KEY; }
        return LEATHER_KEY;
    }

    private static StandaloneModelKey<QuadCollection> makeKey(String name) {
        return new StandaloneModelKey<>(new ModelDebugName() {
            @Override
            public String debugName() {
                return Goo.MODID + NS_SEP + name;
            }
        });
    }

    private static void registerModel(ModelEvent.RegisterStandalone event,
            StandaloneModelKey<QuadCollection> key, String path) {
        event.register(key, SimpleUnbakedStandaloneModel.quadCollection(
            Identifier.fromNamespaceAndPath(Goo.MODID, path)));
    }
}
