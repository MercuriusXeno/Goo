package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.client.GooTooltipHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.NonNull;

/**
 * Renders a vanilla fluid tooltip line: bucket item icon + "NNNN mB" text.
 */
public class VanillaFluidClientTooltipComponent implements ClientTooltipComponent {

    /** Displayed icon size after scaling. */
    private static final int ICON_SIZE = 10;
    /** Native item render size before scaling. */
    private static final int NATIVE_ITEM_SIZE = 16;
    /** Scale factor to shrink the 16px item icon to ICON_SIZE. */
    private static final float ICON_SCALE = (float) ICON_SIZE / NATIVE_ITEM_SIZE;
    /** Gap between icon and text. */
    private static final int ICON_GAP = 2;
    /** Total line height. */
    private static final int LINE_HEIGHT = 15;
    /** Y offset to vertically center the icon with text. */
    private static final int ICON_Y_OFFSET = -1;
    /** Fully opaque white in ARGB for tooltip text rendering. */
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final ItemStack bucketIcon;
    private final Component displayText;

    /**
     * Constructs the client tooltip from the data model.
     *
     * @param data the vanilla fluid tooltip data
     */
    public VanillaFluidClientTooltipComponent(VanillaFluidTooltipComponent data) {
        this.bucketIcon = getBucketForFluid(data.fluid());
        this.displayText = Component.literal(
                GooTooltipHandler.formatFluidDisplay(data.amount()));
    }

    /**
     * Returns the bucket item for the given fluid.
     *
     * @param fluid the vanilla fluid
     * @return the corresponding bucket item stack
     */
    private static ItemStack getBucketForFluid(Fluid fluid) {
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            return new ItemStack(Items.LAVA_BUCKET);
        }
        return new ItemStack(Items.BUCKET);
    }

    @Override
    public int getHeight(@NonNull Font font) {
        return LINE_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        return ICON_SIZE + ICON_GAP + font.width(displayText);
    }

    @Override
    public void extractImage(@NonNull Font font, int x, int y,
            int width, int height, @NonNull GuiGraphicsExtractor guiGraphics) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(x, y + ICON_Y_OFFSET);
        guiGraphics.pose().scale(ICON_SCALE, ICON_SCALE);
        guiGraphics.fakeItem(bucketIcon, 0, 0);
        guiGraphics.pose().popMatrix();
        guiGraphics.text(font, displayText,
                x + ICON_SIZE + ICON_GAP, y, TEXT_COLOR);
    }
}
