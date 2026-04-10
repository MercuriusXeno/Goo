package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Standalone baked model for canister body rendering.
 * Registered during model loading, retrieved in the BER at render time.
 */
public final class CanisterBodyModels {

    /** Debug name suffix for the canister body model key. */
    private static final String DEBUG_NAME = "canister_body";
    /** Model path for the canister body geometry. */
    private static final String MODEL_PATH = "block/canister_body_m0";
    /** Namespace separator for debug name. */
    private static final String NS_SEP = ":";

    private static final StandaloneModelKey<QuadCollection> KEY =
        new StandaloneModelKey<>(new ModelDebugName() {
            @Override
            public String debugName() {
                return Goo.MODID + NS_SEP + DEBUG_NAME;
            }
        });

    private CanisterBodyModels() {}

    /**
     * Registers the canister body model as a standalone model.
     *
     * @param event the event instance
     */
    public static void register(ModelEvent.RegisterStandalone event) {
        Identifier modelId = Identifier.fromNamespaceAndPath(
            Goo.MODID, MODEL_PATH);
        event.register(KEY,
            SimpleUnbakedStandaloneModel.quadCollection(modelId));
    }

    /**
     * Returns the baked quad collection for the canister body.
     *
     * @return the model
     */
    public static QuadCollection getModel() {
        return Minecraft.getInstance().getModelManager()
            .getStandaloneModel(KEY);
    }
}
