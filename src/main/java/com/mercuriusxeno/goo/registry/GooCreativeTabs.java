package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative mode tab registration. Shows machines, intermediates, one blob per type,
 * and one sample omniblob per type.
 */
public class GooCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Goo.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GOO_TAB =
        TABS.register("goo_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.goo"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> BlobStacks.createBlobStack(GooType.ENDER, 1))
            .displayItems((params, output) -> {
                // Machines
                output.accept(GooItems.CRUCIBLE.get());
                output.accept(GooItems.CANISTER.get());
                output.accept(GooItems.HUB.get());
                output.accept(GooItems.PLEXER.get());
                output.accept(GooItems.VAT.get());
                output.accept(GooItems.TAP.get());
                // Intermediates
                output.accept(GooItems.CHORAL_GASKET.get());
                output.accept(GooItems.CHORAL_TUNER.get());
                output.accept(GooItems.EXORITE.get());
                // Equipment
                output.accept(GooItems.GOO_GLOVE.get());
                output.accept(GooItems.GOO_GAUNTLET.get());
                output.accept(GooItems.EXO_GAUNTLET.get());
                // Per-type: one blob + 1K-blob and 1M-blob omniblobs
                for (GooType type : GooType.values()) {
                    output.accept(BlobStacks.createBlobStack(type, 1));
                    output.accept(GooOmniblobItem.createWithVolume(type, 1_000_000L));
                    output.accept(GooOmniblobItem.createWithVolume(type, 1_000_000_000L));
                }
            })
            .build()
        );

}
