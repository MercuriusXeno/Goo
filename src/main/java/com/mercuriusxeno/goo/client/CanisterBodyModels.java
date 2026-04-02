package com.mercuriusxeno.goo.client;

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

    private static final StandaloneModelKey<QuadCollection> KEY =
        new StandaloneModelKey<>(new ModelDebugName() {
            @Override
            public String debugName() {
                return Goo.MODID + ":canister_body";
            }
        });

    private CanisterBodyModels() {}

    /** Registers the canister body model as a standalone model. */
    public static void register(ModelEvent.RegisterStandalone event) {
        Identifier modelId = Identifier.fromNamespaceAndPath(
            Goo.MODID, "block/canister_body_m0");
        event.register(KEY,
            SimpleUnbakedStandaloneModel.quadCollection(modelId));
    }

    /** Returns the baked quad collection for the canister body. */
    public static QuadCollection getModel() {
        return Minecraft.getInstance().getModelManager()
            .getStandaloneModel(KEY);
    }
}
