package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

import java.text.DecimalFormat;

public class GooOverlayRenderer
{
    private static final String[] NUM_SUFFIXES = new String[]{"", "k", "m", "b", "t"};
    private static final int MAX_LENGTH = 4;
    private static final Minecraft CLIENT = Minecraft.getInstance();

    public static void renderStackSize(MatrixStack matrixStack, FontRenderer fr, FluidStack stack, int xPosition, int yPosition) {
        if (!stack.isEmpty() && stack.getAmount() != 1) {
            String s = shortHandNumber(stack.getAmount());

            if (stack.getAmount() < 1)
                s = TextFormatting.RED + String.valueOf(stack.getAmount());

            fr.drawStringWithShadow(matrixStack, s, (float) (xPosition + 19 - 2 - fr.getStringWidth(s)), (float) (yPosition + 6 + 3), 16777215);

        }
    }

    private static String shortHandNumber(Number number) {
        String shorthand = new DecimalFormat("##0E0").format(number);
        shorthand = shorthand.replaceAll("E[0-9]", NUM_SUFFIXES[Character.getNumericValue(shorthand.charAt(shorthand.length() - 1)) / 3]);
        while (shorthand.length() > MAX_LENGTH || shorthand.matches("[0-9]+\\.[a-z]"))
            shorthand = shorthand.substring(0, shorthand.length() - 2) + shorthand.substring(shorthand.length() - 1);

        return shorthand;
    }

    public static void renderIcon(int x, int y, int sx, int sy, TextureAtlasSprite iconSprite) {
        CLIENT.getTextureManager().bindTexture(AbstractGui.GUI_ICONS_LOCATION);

        if (iconSprite == null)
            return;

        drawTexturedModalRect(x, y, iconSprite.getMinU(), iconSprite.getMinV(), sx, sy, iconSprite.getMaxU(), iconSprite.getMaxV());
    }

    public static void drawTexturedModalRect(int x, int y, float textureX, float textureY, int width, int height, float tw, float th) {
        float f = 0.00390625F;
        float f1 = 0.00390625F;
        float zLevel = 0.0F;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, zLevel).tex(textureX * f, (textureY + th) * f1).endVertex();
        buffer.pos(x + width, y + height, zLevel).tex((textureX + tw) * f, (textureY + th) * f1).endVertex();
        buffer.pos(x + width, y, zLevel).tex((textureX + tw) * f, textureY * f1).endVertex();
        buffer.pos(x, y, zLevel).tex(textureX * f, textureY * f1).endVertex();
        tessellator.draw();
    }
}
