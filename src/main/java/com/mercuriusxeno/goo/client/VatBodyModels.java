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
 * Standalone baked model for vat item rendering. Uses the combined
 * vat model (cap + body + base) as a single quad collection.
 */
public final class VatBodyModels {

    /** Debug name suffix for the vat shell model key. */
    private static final String DEBUG_NAME = "vat_shell";
    /** Model path for the vat shell geometry. */
    private static final String MODEL_PATH = "block/vat";
    /** Namespace separator for debug name. */
    private static final String NS_SEP = ":";

    /**
     * Key for the vat shell model.
     *
     * @return the result
     */
    private static final StandaloneModelKey<QuadCollection> KEY =
        new StandaloneModelKey<>(new ModelDebugName() {
            @Override
            public String debugName() {
                return Goo.MODID + NS_SEP + DEBUG_NAME;
            }
        });

    private VatBodyModels() {}

    /**
     * Registers the vat shell model as a standalone model.
     *
     * @param event the event instance
     */
    public static void register(ModelEvent.RegisterStandalone event) {
        event.register(KEY,
            SimpleUnbakedStandaloneModel.quadCollection(
                Identifier.fromNamespaceAndPath(Goo.MODID, MODEL_PATH)));
    }

    /**
     * Returns the baked quad collection for the vat shell.
     *
     * @return the model
     */
    public static QuadCollection getModel() {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(KEY);
    }
}
