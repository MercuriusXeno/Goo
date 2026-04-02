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

    /** Key for the vat shell model. */
    private static final StandaloneModelKey<QuadCollection> KEY =
        new StandaloneModelKey<>(new ModelDebugName() {
            @Override
            public String debugName() {
                return Goo.MODID + ":vat_shell";
            }
        });

    private VatBodyModels() {}

    /** Registers the vat shell model as a standalone model. */
    public static void register(ModelEvent.RegisterStandalone event) {
        event.register(KEY,
            SimpleUnbakedStandaloneModel.quadCollection(
                Identifier.fromNamespaceAndPath(Goo.MODID, "block/vat")));
    }

    /** Returns the baked quad collection for the vat shell. */
    public static QuadCollection getModel() {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(KEY);
    }
}
