package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.tooltip.GooValueTooltipComponent;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.GooBlobItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import java.util.List;
import java.util.Map;

/**
 * Injects goo value tooltip components into any item tooltip that has goo values.
 * Uses RenderTooltipEvent.GatherComponents to insert custom icon+text components.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooTooltipHandler {

    private GooTooltipHandler() {}

    /**
     * Intercepts tooltip component gathering to inject goo value entries.
     * For blob items, shows the blob's actual volume instead of the registry base value.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) { return; }
        if (handleBlobTooltip(event.getTooltipElements(), stack)) { return; }
        handleItemTooltip(event.getTooltipElements(), stack);
    }

    /** Handles blob and omniblob items, returning true if a blob tooltip was appended.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     * @return true if the stack was a blob type
     */
    private static boolean handleBlobTooltip(List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        if (stack.getItem() instanceof GooBlobItem blobItem) {
            appendBlobComponent(elements, blobItem.getGooType(), BlobStacks.volumeOf(stack));
            return true;
        }
        if (stack.getItem() instanceof GooOmniblobItem omniblob) {
            appendBlobComponent(elements, omniblob.getGooType(), GooOmniblobItem.getVolume(stack));
            return true;
        }
        return false;
    }

    /** Handles non-blob items: goo contents, upgrades, and base registry values.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     */
    private static void handleItemTooltip(List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        GooContents gooContents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        boolean hasGooContents = gooContents != null && !gooContents.isEmpty();
        CanisterFluidContent canisterContent = stack.get(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        boolean hasCanisterContent = canisterContent != null && !canisterContent.isEmpty();
        if (hasGooContents) {
            appendGooContentsComponents(elements, gooContents);
        } else if (hasCanisterContent) {
            appendCanisterFluidComponent(elements, canisterContent);
        }
        appendUpgradeComponents(elements, stack);
        if (!hasGooContents && !hasCanisterContent) {
            appendBaseValueTooltip(elements, stack);
        }
    }

    /** Appends registry base value tooltip lines for items without explicit goo contents.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     */
    private static void appendBaseValueTooltip(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        GooValue value = lookupValue(stack);
        if (value != null && !value.isEmpty()) {
            appendGooComponents(elements, value);
        }
    }

    /**
     * Looks up the effective goo value for an item stack, including component-aware values.
     *
     * @param stack the item stack
     * @return the value, or null if not found
     */
    private static GooValue lookupValue(ItemStack stack) {
        return Goo.GOO_VALUES.lookup(stack);
    }

    /**
     * Appends a single tooltip line showing the blob/omniblob's volume and type.
     *
     * @param elements the tooltip element list
     * @param type the goo type
     * @param volume the volume in microblobs
     */
    private static void appendBlobComponent(
            List<Either<FormattedText, TooltipComponent>> elements,
            GooType type, long volume) {
        if (volume <= 0) { return; }
        elements.add(Either.left(Component.empty()));
        elements.add(Either.right(
                new GooValueTooltipComponent(type, volume)));
    }

    /**
     * Inserts a blank separator line and one GooValueTooltipComponent per goo type.
     *
     * @param elements the tooltip element list
     * @param value the goo value mapping
     */
    private static void appendGooComponents(
            List<Either<FormattedText, TooltipComponent>> elements, GooValue value) {
        elements.add(Either.left(Component.empty()));
        for (Map.Entry<GooType, Integer> entry : value.getAll().entrySet()) {
            elements.add(Either.right(
                    new GooValueTooltipComponent(entry.getKey(), entry.getValue())));
        }
    }

    /**
     * Inserts icon tooltip lines for each goo type in GooContents (PMI, canister).
     *
     * @param elements the tooltip element list
     * @param contents the goo contents to measure
     */
    private static void appendGooContentsComponents(
            List<Either<FormattedText, TooltipComponent>> elements, GooContents contents) {
        elements.add(Either.left(Component.empty()));
        for (Map.Entry<GooType, Long> entry : contents.getAll().entrySet()) {
            elements.add(Either.right(
                    new GooValueTooltipComponent(entry.getKey(), entry.getValue())));
        }
    }

    /** Appends a single-fluid tooltip line for canister items.
     *
     * @param elements the tooltip element list
     * @param content  the canister fluid content
     */
    private static void appendCanisterFluidComponent(
            List<Either<FormattedText, TooltipComponent>> elements, CanisterFluidContent content) {
        GooType gooType = content.getGooType();
        if (gooType != null) {
            elements.add(Either.left(Component.empty()));
            elements.add(Either.right(
                    new GooValueTooltipComponent(gooType, content.amount())));
        }
    }

    /**
     * Appends canister label from canister metadata.
     *
     * @param elements the tooltip element list
     * @param stack the item stack
     */
    private static void appendUpgradeComponents(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        appendCanisterUpgrades(elements, stack);
    }

    /**
     * Appends canister label from canister metadata.
     *
     * @param elements the tooltip element list
     * @param stack the item stack
     */
    private static void appendCanisterUpgrades(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        CanisterMetadata meta = stack.get(GooDataComponents.CANISTER_METADATA.get());
        if (meta == null || !meta.hasData()) { return; }
        if (meta.label() != null && !meta.label().isEmpty()) {
            elements.add(Either.left(
                Component.literal(meta.label()).withStyle(ChatFormatting.GOLD)));
        }
    }


    /**
     * Delegates to {@link GooFormat#formatFluidDisplay(long)}.
     *
     * @param microblobs the volume in microblobs
     * @return the formatted string
     */
    public static String formatFluidDisplay(long microblobs) {
        return GooFormat.formatFluidDisplay(microblobs);
    }

    /**
     * Delegates to {@link GooFormat#formatFluidDisplayCompact(long)}.
     *
     * @param microblobs the volume in microblobs
     * @return the formatted string
     */
    public static String formatFluidDisplayCompact(long microblobs) {
        return GooFormat.formatFluidDisplayCompact(microblobs);
    }
}
