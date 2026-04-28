package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooPotions;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * Registers brewing recipes: blob + awkward potion → goo potion.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GooBrewingRecipes {

    private GooBrewingRecipes() {}

    /**
     * Registers one brewing recipe per goo type: blob + awkward potion yields goo potion.
     *
     * @param event the brewing recipe registration event
     */
    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        var builder = event.getBuilder();
        for (GooType type : GooType.values()) {
            var blobHolder = GooItems.BLOBS.get(type);
            var potionHolder = GooPotions.GOO_POTIONS.get(type);
            if (blobHolder != null && potionHolder != null) {
                builder.addMix(Potions.AWKWARD, blobHolder.get(), potionHolder);
            }
        }
    }
}
