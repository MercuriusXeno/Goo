package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.tooltip.GooValueTooltipComponent;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.BlobStacks;
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
public class GooTooltipHandler {

    /**
     * Intercepts tooltip component gathering to inject goo value entries.
     * For blob items, shows the blob's actual volume instead of the registry base value.
     */
    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        if (stack.getItem() instanceof GooBlobItem blobItem) {
            appendBlobComponent(event.getTooltipElements(), blobItem.getGooType(),
                BlobStacks.volumeOf(stack));
            return;
        }

        if (stack.getItem() instanceof GooOmniblobItem omniblob) {
            appendBlobComponent(event.getTooltipElements(), omniblob.getGooType(),
                GooOmniblobItem.getVolume(stack));
            return;
        }

        GooContents gooContents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        if (gooContents != null && !gooContents.isEmpty()) {
            appendGooContentsComponents(event.getTooltipElements(), gooContents);
        }

        appendUpgradeComponents(event.getTooltipElements(), stack);

        if (gooContents != null && !gooContents.isEmpty()) return;

        GooValue value = lookupValue(stack);
        if (value == null || value.isEmpty()) return;

        appendGooComponents(event.getTooltipElements(), value);
    }

    /** Looks up the effective goo value for an item stack, including component-aware values. */
    private static GooValue lookupValue(ItemStack stack) {
        return Goo.GOO_VALUES.lookup(stack);
    }

    /** Appends a single tooltip line showing the blob/omniblob's volume and type. */
    private static void appendBlobComponent(
            List<Either<FormattedText, TooltipComponent>> elements,
            GooType type, long volume) {
        if (volume <= 0) return;
        elements.add(Either.left(Component.empty()));
        elements.add(Either.right(
                new GooValueTooltipComponent(type, volume)));
    }

    /** Inserts a blank separator line and one GooValueTooltipComponent per goo type. */
    private static void appendGooComponents(
            List<Either<FormattedText, TooltipComponent>> elements, GooValue value) {
        elements.add(Either.left(Component.empty()));
        for (Map.Entry<GooType, Integer> entry : value.getAll().entrySet()) {
            elements.add(Either.right(
                    new GooValueTooltipComponent(entry.getKey(), entry.getValue())));
        }
    }

    /** Inserts icon tooltip lines for each goo type in GooContents (PMI, bucket, canister). */
    private static void appendGooContentsComponents(
            List<Either<FormattedText, TooltipComponent>> elements, GooContents contents) {
        elements.add(Either.left(Component.empty()));
        for (Map.Entry<GooType, Long> entry : contents.getAll().entrySet()) {
            elements.add(Either.right(
                    new GooValueTooltipComponent(entry.getKey(), entry.getValue())));
        }
    }

    /** Appends canister label from canister metadata. */
    private static void appendUpgradeComponents(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        appendCanisterUpgrades(elements, stack);
    }

    /** Appends canister label from canister metadata. */
    private static void appendCanisterUpgrades(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        CanisterMetadata meta = stack.get(GooDataComponents.CANISTER_METADATA.get());
        if (meta == null || !meta.hasData()) return;
        if (meta.label() != null && !meta.label().isEmpty()) {
            elements.add(Either.left(
                Component.literal(meta.label()).withStyle(ChatFormatting.GOLD)));
        }
    }


    /** Delegates to {@link GooFormat#formatFluidDisplay(long)}. */
    public static String formatFluidDisplay(long microblobs) {
        return GooFormat.formatFluidDisplay(microblobs);
    }

    /** Delegates to {@link GooFormat#formatFluidDisplayCompact(long)}. */
    public static String formatFluidDisplayCompact(long microblobs) {
        return GooFormat.formatFluidDisplayCompact(microblobs);
    }
}
