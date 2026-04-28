package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.tooltip.GooValueTooltipComponent;
import com.mercuriusxeno.goo.client.tooltip.VanillaFluidTooltipComponent;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.*;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Map;

/**
 * Injects goo value tooltip components into any item tooltip that has goo values.
 * Uses RenderTooltipEvent.GatherComponents to insert custom icon+text components.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooTooltipHandler {

    /**
     * One vanilla bucket in millibuckets / microblobs.
     */
    private static final int BUCKET_VOLUME = 1000;

    /**
     * Hint shown when shift is not held and the item has goo data.
     */
    private static final Component SHIFT_HINT =
            Component.literal("Hold [Shift] for goo values").withStyle(ChatFormatting.DARK_GRAY);

    /**
     * "+" separator between contents and container value rows.
     */
    private static final Component PLUS_SEPARATOR =
            Component.literal("+").withStyle(ChatFormatting.WHITE);

    private GooTooltipHandler() {
    }

    /**
     * Intercepts tooltip component gathering to inject goo value entries.
     * For blob items, shows the blob's actual volume instead of the registry base value.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        if (!isShiftHeld()) {
            if (hasGooData(stack)) {
                event.getTooltipElements().add(Either.left(SHIFT_HINT));
            }
            return;
        }
        handleTooltipOrchestration(event, stack);
    }

    private static void handleTooltipOrchestration(RenderTooltipEvent.GatherComponents event, ItemStack stack) {
        if (handleBlobTooltip(event.getTooltipElements(), stack)) {
            return;
        }
        if (handleContainerTooltip(event.getTooltipElements(), stack)) {
            return;
        }
        handleItemTooltip(event.getTooltipElements(), stack);
    }

    /**
     * Returns true if either shift key is currently held.
     *
     * @return true if left or right shift is pressed
     */
    private static boolean isShiftHeld() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    /**
     * Returns true if the item has any goo-relevant data worth showing.
     *
     * @param stack the item stack
     * @return true if goo tooltip would be non-empty
     */
    private static boolean hasGooData(ItemStack stack) {
        if (stack.getItem() instanceof GooBlobItem) {
            return true;
        }
        if (stack.getItem() instanceof GooOmniblobItem) {
            return true;
        }
        return getGooContentType(stack) != null || hasStoredGooData(stack);
    }

    /**
     * Returns true if the stack carries goo contents, canister fluid, or a base goo value.
     *
     * @param stack the item stack to inspect
     * @return true if any goo data component is present
     */
    private static boolean hasStoredGooData(ItemStack stack) {
        GooContents contents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        if (isEmptyContents(contents)) {
            return true;
        }
        CanisterFluidContent canister = stack.get(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        if (isEmptyCanister(canister)) {
            return true;
        }
        GooValue value = lookupValue(stack);
        return value != null && !value.isEmpty();
    }

    private static boolean isEmptyCanister(CanisterFluidContent canister) {
        return canister != null && !canister.isEmpty();
    }

    private static boolean isEmptyContents(GooContents contents) {
        return contents != null && !contents.isEmpty();
    }

    /**
     * Handles blob and omniblob items, returning true if a blob tooltip was appended.
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

    /**
     * Handles fluid containers (goo buckets, canisters with goo fluid):
     * single column with contents rows, a "+" separator, and container
     * value rows. Shows contents alone if the container has no goo value.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     * @return true if a container tooltip was appended
     */
    private static boolean handleContainerTooltip(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        GooType contentType = getGooContentType(stack);
        int contentAmount = getGooContentAmount(stack);
        if (contentType == null || contentAmount <= 0) {
            return false;
        }

        elements.add(Either.left(Component.empty()));
        elements.add(Either.right(new GooValueTooltipComponent(contentType, contentAmount)));
        appendContainerValue(elements, stack);
        return true;
    }

    /**
     * Appends the "+" separator and base-item goo value rows if the container has one.
     *
     * @param elements the tooltip element list
     * @param stack    the container item stack
     */
    private static void appendContainerValue(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        GooValue containerValue = lookupContainerValue(stack);
        if (containerValue == null || containerValue.isEmpty()) {
            return;
        }
        elements.add(Either.left(PLUS_SEPARATOR));
        for (Map.Entry<GooType, Integer> e : containerValue.getAll().entrySet()) {
            elements.add(Either.right(
                    new GooValueTooltipComponent(e.getKey(), e.getValue())));
        }
    }

    /**
     * Returns the goo type of the fluid contents, or null.
     *
     * @param stack the item stack
     * @return the goo type, or null
     */
    private static @org.jspecify.annotations.Nullable GooType getGooContentType(ItemStack stack) {
        if (stack.getItem() instanceof BucketItem bucket) {
            return GooFluids.getTypeFromFluid(bucket.content);
        }
        CanisterFluidContent content = stack.get(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        if (isEmptyCanister(content)) {
            return content.getGooType();
        }
        return null;
    }

    /**
     * Returns the fluid amount in microblobs, or 0.
     *
     * @param stack the item stack
     * @return the amount
     */
    private static int getGooContentAmount(ItemStack stack) {
        if (stack.getItem() instanceof BucketItem) {
            return BUCKET_VOLUME;
        }
        CanisterFluidContent content = stack.get(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        if (isEmptyCanister(content)) {
            return content.amount();
        }
        return 0;
    }

    /**
     * Looks up the goo value of the container itself (not its contents).
     *
     * @param stack the item stack
     * @return the container's decomposition value, or null
     */
    private static GooValue lookupContainerValue(ItemStack stack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        if ((value == null || value.isEmpty()) && stack.getItem() instanceof BucketItem) {
            return Goo.GOO_VALUES.lookup(BuiltInRegistries.ITEM.getKey(Items.BUCKET));
        }
        return value;
    }

    /**
     * Handles non-blob items: goo contents, upgrades, and base registry values.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     */
    private static void handleItemTooltip(List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        boolean appendedFluid = appendFluidComponents(elements, stack);
        appendUpgradeComponents(elements, stack);
        if (!appendedFluid) {
            appendBaseValueTooltip(elements, stack);
        }
    }

    /**
     * Appends goo contents or canister fluid rows, returning true if either was present.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack to inspect
     * @return true if fluid rows were appended
     */
    private static boolean appendFluidComponents(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        GooContents gooContents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        if (isEmptyContents(gooContents)) {
            appendGooContentsComponents(elements, gooContents);
            return true;
        }
        CanisterFluidContent canisterContent = stack.get(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        if (isEmptyCanister(canisterContent)) {
            appendCanisterFluidComponent(elements, canisterContent);
            return true;
        }
        return false;
    }

    /**
     * Appends registry base value tooltip lines for items without explicit goo contents.
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
     * @param type     the goo type
     * @param volume   the volume in microblobs
     */
    private static void appendBlobComponent(
            List<Either<FormattedText, TooltipComponent>> elements,
            GooType type, int volume) {
        if (volume <= 0) {
            return;
        }
        elements.add(Either.left(Component.empty()));
        elements.add(Either.right(
                new GooValueTooltipComponent(type, volume)));
    }

    /**
     * Inserts a blank separator line and one GooValueTooltipComponent per goo type.
     *
     * @param elements the tooltip element list
     * @param value    the goo value mapping
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
        for (Map.Entry<GooType, Integer> entry : contents.getAll().entrySet()) {
            elements.add(Either.right(
                    new GooValueTooltipComponent(entry.getKey(), entry.getValue())));
        }
    }

    /**
     * Appends a single-fluid tooltip line for canister items.
     * Handles both goo fluids (icon + amount) and vanilla fluids (text label + amount).
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
        } else {
            appendVanillaFluidTooltip(elements, content);
        }
    }

    /**
     * Appends a bucket icon + mB amount tooltip for vanilla fluids.
     *
     * @param elements the tooltip element list
     * @param content  the canister fluid content holding a vanilla fluid
     */
    private static void appendVanillaFluidTooltip(
            List<Either<FormattedText, TooltipComponent>> elements, CanisterFluidContent content) {
        elements.add(Either.left(Component.empty()));
        elements.add(Either.right(
                new VanillaFluidTooltipComponent(content.fluid(), content.amount())));
    }

    /**
     * Appends canister label from canister metadata.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     */
    private static void appendUpgradeComponents(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        appendCanisterUpgrades(elements, stack);
    }

    /**
     * Appends canister label from canister metadata.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     */
    private static void appendCanisterUpgrades(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        CanisterMetadata meta = stack.get(GooDataComponents.CANISTER_METADATA.get());
        if (meta == null || !meta.hasData()) {
            return;
        }
        if (meta.label() != null && !meta.label().isEmpty()) {
            elements.add(Either.left(
                    Component.literal(meta.label()).withStyle(ChatFormatting.GOLD)));
        }
    }


    /**
     * Delegates to {@link GooFormat#formatFluidDisplay    (int)}.
     *
     * @param microblobs the volume in microblobs
     * @return the formatted string
     */
    public static String formatFluidDisplay(int microblobs) {
        return GooFormat.formatFluidDisplay(microblobs);
    }

    /**
     * Delegates to {@link GooFormat#formatFluidDisplayCompact    (int)}.
     *
     * @param microblobs the volume in microblobs
     * @return the formatted string
     */
    public static String formatFluidDisplayCompact(int microblobs) {
        return GooFormat.formatFluidDisplayCompact(microblobs);
    }
}
