package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.*;
import com.mercuriusxeno.goo.item.gasket.ChoralGasketItem;
import com.mercuriusxeno.goo.item.gasket.ChoralTunerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.EnumMap;
import java.util.Map;

public class GooItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Goo.MODID);

    // --- Blob items (one per goo type, stackable to 64, each = 1,000 mB) ---
    public static final Map<GooType, DeferredItem<GooBlobItem>> BLOBS = new EnumMap<>(GooType.class);

    // --- Omniblob items (one per goo type, unstackable, uncapped volume) ---
    public static final Map<GooType, DeferredItem<GooOmniblobItem>> OMNIBLOBS = new EnumMap<>(GooType.class);

    // --- Bucket items (one per goo type, holds 1000 mB) ---
    public static final Map<GooType, DeferredItem<BucketItem>> BUCKETS = new EnumMap<>(GooType.class);
    // --- Block items ---
    public static final DeferredItem<BlockItem> CRUCIBLE = ITEMS.registerSimpleBlockItem("crucible", GooBlocks.CRUCIBLE);
    public static final DeferredItem<BlockItem> HUB = ITEMS.registerSimpleBlockItem("hub", GooBlocks.HUB);
    public static final DeferredItem<BlockItem> PLEXER = ITEMS.registerSimpleBlockItem("plexer", GooBlocks.PLEXER);
    public static final DeferredItem<BlockItem> REACTOR = ITEMS.registerSimpleBlockItem("reactor", GooBlocks.REACTOR);
    public static final DeferredItem<VatBlockItem> VAT = ITEMS.registerItem("vat",
            props -> new VatBlockItem(GooBlocks.VAT.get(), props.useBlockDescriptionPrefix()));
    public static final DeferredItem<BlockItem> TAP = ITEMS.registerSimpleBlockItem("tap", GooBlocks.TAP);
    // --- Canister ---
    public static final DeferredItem<CanisterItem> CANISTER = ITEMS.registerItem("canister",
            props -> new CanisterItem(GooBlocks.CANISTER.get(), props.stacksTo(1).useBlockDescriptionPrefix()));
    // --- Intermediate items ---
    public static final DeferredItem<ChoralGasketItem> CHORAL_GASKET = ITEMS.registerItem("choral_gasket",
            ChoralGasketItem::new);
    public static final DeferredItem<ChoralTunerItem> CHORAL_TUNER = ITEMS.registerItem("choral_tuner",
            ChoralTunerItem::new);
    public static final DeferredItem<Item> EXORITE = ITEMS.registerSimpleItem("exorite");
    // --- Equipment (gloves: right-click throw / radial select) ---
    public static final DeferredItem<GooGloveItem> GOO_GLOVE = ITEMS.registerItem("goo_glove",
            props -> new GooGloveItem(props.stacksTo(1)));
    public static final DeferredItem<GooGloveItem> GOO_GAUNTLET = ITEMS.registerItem("goo_gauntlet",
            props -> new GooGloveItem(props.stacksTo(1).fireResistant()));
    public static final DeferredItem<GooGloveItem> EXO_GAUNTLET = ITEMS.registerItem("exo_gauntlet",
            props -> new GooGloveItem(props.stacksTo(1).fireResistant()));
    // --- Partially Melted Item (crucible intermediate, not in creative tab) ---
    public static final DeferredItem<PartiallyMeltedItem> PARTIALLY_MELTED_ITEM = ITEMS.registerItem(
            "partially_melted_item", props -> new PartiallyMeltedItem(props.stacksTo(1)));
    // --- Depleted Blaze Rod (crucible fuel intermediate, not in creative tab) ---
    public static final DeferredItem<DepletedBlazeRodItem> DEPLETED_BLAZE_ROD = ITEMS.registerItem(
            "depleted_blaze_rod", props -> new DepletedBlazeRodItem(props.stacksTo(1)));

    static {
        for (GooType type : GooType.values()) {
            BLOBS.put(type, ITEMS.registerItem(type.getId() + "_blob",
                    props -> new GooBlobItem(type, props)));
            OMNIBLOBS.put(type, ITEMS.registerItem(type.getId() + "_omniblob",
                    props -> new GooOmniblobItem(type, props.stacksTo(1))));
            BUCKETS.put(type, ITEMS.registerItem(type.getId() + "_goo_bucket",
                    props -> new BucketItem(GooFluids.SOURCES.get(type).get(),
                            props.craftRemainder(Items.BUCKET).stacksTo(1))));
        }
    }
}
