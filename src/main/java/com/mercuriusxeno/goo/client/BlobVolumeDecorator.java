package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooBlobItem;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import org.jspecify.annotations.NonNull;

/**
 * Renders a goo type icon (top-right) on all blob and omniblob item slots.
 * Additionally renders a compact volume label (bottom-right) on omniblobs only,
 * since regular blobs use vanilla stack count display.
 */
public class BlobVolumeDecorator implements IItemDecorator {

    private static final int ICON_RENDER_SIZE = 6;
    private static final float TEXT_SCALE = 0.5f;

    @Override
    public boolean render(@NonNull GuiGraphicsExtractor graphics, @NonNull Font font, ItemStack stack, int xOffset, int yOffset) {
        if (stack.getItem() instanceof GooBlobItem blob) {
            renderTypeIcon(graphics, blob.getGooType(), xOffset, yOffset);
            return true;
        }

        if (stack.getItem() instanceof GooOmniblobItem omniblob) {
            long volume = GooOmniblobItem.getVolume(stack);
            if (volume <= 0) return false;
            renderTypeIcon(graphics, omniblob.getGooType(), xOffset, yOffset);
            renderVolumeLabel(graphics, font, volume, xOffset, yOffset);
            return true;
        }

        return false;
    }

    /** Blits the goo type icon in the top-right of the slot. */
    private void renderTypeIcon(GuiGraphicsExtractor graphics, GooType type, int x, int y) {
        Identifier texture = Identifier.fromNamespaceAndPath(
                "goo", "textures/item/" + type.getId() + "_icon_bordered.png");
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                x + 10, y, 0.0f, 0.0f,
                ICON_RENDER_SIZE, ICON_RENDER_SIZE, 11, 11, 11, 11);
    }

    /** Draws the volume label right-aligned at the bottom of the slot, scaled down. */
    private void renderVolumeLabel(GuiGraphicsExtractor graphics, Font font,
            long volume, int xOffset, int yOffset) {
        String label = GooTooltipHandler.formatFluidDisplayCompact(volume);
        int textWidth = font.width(label);

        int x = (int) ((xOffset + 15) / TEXT_SCALE) - textWidth;
        int y = (int) ((yOffset + 11) / TEXT_SCALE);

        graphics.pose().pushMatrix();
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE);
        graphics.text(font, label, x, y, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }
}
