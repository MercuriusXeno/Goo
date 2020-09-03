package com.xeno.goo.events;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeClientEvents
{
    private static final String PLACE_HOLDER = "\u00a76\u00a7r\u00a7r\u00a7r\u00a7r\u00a7r";
    private static final int ICON_WIDTH = 16;
    private static final int ICON_HEIGHT = 24;
    private static final int ICONS_BEFORE_TWO_LINES_LOOKS_LIKE_POO = 12;
    private static final int TEXT_START_Y_OFFSET = 18;
    private static final float TEXT_SCALE = 0.5f;
    private static final int ICONS_BEFORE_ONE_LINE_LOOKS_LIKE_POO = 5;

    @SubscribeEvent
    public static void onDrawTooltip(ItemTooltipEvent event) {
        //This method will make space for goo icons in the tooltip
        ItemStack stack = event.getItemStack();

        // special handler for goo bulbs, goo bulbs show their contents at rest, but not with shift held.
        if (stack.getItem().equals(Registry.GOO_BULB_ITEM.get())) {
            prepGooContentsRealEstate(stack, event);
        }

        // EVERYTHING shows its composition with shift held, bulbs are the exception
        if (Screen.hasShiftDown()) {
            prepGooCompositionRealEstate(stack, event);
        }
    }

    private static void prepGooContentsRealEstate(ItemStack stack, ItemTooltipEvent event)
    {
        Minecraft mc = Minecraft.getInstance();

        if( mc.world == null || mc.player == null )
            return;

        CompoundNBT stackTag = stack.getTag();
        if (stackTag == null) {
            return;
        }

        if (!stackTag.contains("BlockEntityTag")) {
            return;
        }

        CompoundNBT bulbTag = stackTag.getCompound("BlockEntityTag");

        if (!bulbTag.contains("goo")) {
            return;
        }

        CompoundNBT gooTag = bulbTag.getCompound("goo");
        List<FluidStack> gooEntry = GooBulbTile.deserializeGooForDisplay(gooTag);
        if (gooEntry.size() == 0) {
            return;
        }

        addPlaceholderSpaceForTooltipGooIcons(gooEntry.size(), event);
    }

    private static void addPlaceholderSpaceForTooltipGooIcons(int size, ItemTooltipEvent event)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.world == null || mc.player == null) //populateSearchTreeManager...
            return;

        int stacksPerLine = getArrangementStacksPerLine(size);
        int rows = (int)Math.ceil(size / (float)stacksPerLine);
        int lines = (int)Math.ceil((rows * ICON_HEIGHT) / (float)mc.fontRenderer.FONT_HEIGHT);
        int width = (stacksPerLine * (ICON_WIDTH - 1) + 1);
        String spaces = PLACE_HOLDER;
        while(mc.fontRenderer.getStringWidth(spaces) < width) {
            spaces += " ";
        }
        for (int j = 0; j < lines; j++) {
            event.getToolTip().add(new StringTextComponent(spaces));
        }
    }

    private static void prepGooCompositionRealEstate(ItemStack stack, ItemTooltipEvent event)
    {
        Minecraft mc = Minecraft.getInstance();

        if( mc.world == null || mc.player == null )
            return;

        GooEntry gooEntry = Equivalencies.getEntry(mc.world, stack.getItem());
        if (gooEntry.isUnusable()) {
            return;
        }

        addPlaceholderSpaceForTooltipGooIcons(gooEntry.values().size(), event);
    }

    @SubscribeEvent
    public static void postDrawTooltip(RenderTooltipEvent.PostText event) {
        //This method will draw goo icons on the tooltip
        ItemStack stack = event.getStack();

        // special handler for goo bulbs, goo bulbs show their contents at rest, but not with shift held.
        if (stack.getItem().equals(Registry.GOO_BULB_ITEM.get())) {
            tryDrawingGooContents(stack, event);
        }

        // EVERYTHING shows its composition with shift held, bulbs are the exception
        if (Screen.hasShiftDown()) {
            tryDrawingGooComposition(stack, event);
        }
    }

    private static void tryDrawingGooContents(ItemStack stack, RenderTooltipEvent.PostText event)
    {
        Minecraft mc = Minecraft.getInstance();
        MatrixStack matrices = event.getMatrixStack();

        if( mc.world == null || mc.player == null )
            return;

        CompoundNBT stackTag = stack.getTag();
        if (stackTag == null) {
            return;
        }

        if (!stackTag.contains("BlockEntityTag")) {
            return;
        }

        CompoundNBT bulbTag = stackTag.getCompound("BlockEntityTag");

        if (!bulbTag.contains("goo")) {
            return;
        }

        CompoundNBT gooTag = bulbTag.getCompound("goo");
        List<FluidStack> gooEntry = GooBulbTile.deserializeGooForDisplay(gooTag);
        if (gooEntry.size() == 0) {
            return;
        }

        int bx = event.getX();
        int by = event.getY();
        int j = 0;

        List<? extends ITextProperties> tooltip = event.getLines();
        for (ITextProperties s : tooltip) {
            if (s.getString().trim().equals(PLACE_HOLDER))
                break;
            by += mc.fontRenderer.FONT_HEIGHT;
        }
        gooEntry.sort((v, v2) -> v2.getAmount() - v.getAmount());
        int compoundCount = gooEntry.size();
        int stacksPerLine = getArrangementStacksPerLine(compoundCount);
        for (FluidStack entry : gooEntry) {
            int x = bx + (j % stacksPerLine) * (ICON_WIDTH - 1);
            int y = by - (j / stacksPerLine) * (ICON_HEIGHT - 1);
            renderGooIcon(matrices, fluid(Objects.requireNonNull(entry.getFluid().getRegistryName())).getIcon(), x, y, (int)Math.floor(entry.getAmount()));
            j++;
        }
    }

    private static int getArrangementStacksPerLine(int compoundCount)
    {
        if (compoundCount <= ICONS_BEFORE_ONE_LINE_LOOKS_LIKE_POO) {
            return compoundCount;
            // at 12 or fewer goo types, I just want two rows. Anything higher and I spread it out to 3 rows.        
        } else if (compoundCount <= ICONS_BEFORE_TWO_LINES_LOOKS_LIKE_POO) {
            return (int)Math.ceil(compoundCount / 2f);
        } else {
            return (int)Math.ceil(compoundCount / 3f);
        }
    }

    private static void tryDrawingGooComposition(ItemStack stack, RenderTooltipEvent.PostText event)
    {
        Minecraft mc = Minecraft.getInstance();
        MatrixStack matrices = event.getMatrixStack();

        if( mc.world == null || mc.player == null )
            return;

        GooEntry gooEntry = Equivalencies.getEntry(mc.world, stack.getItem());
        if (gooEntry.isUnusable()) {
            return;
        }

        int bx = event.getX();
        int by = event.getY();
        int j = 0;

        List<? extends ITextProperties> tooltip = event.getLines();
        for (ITextProperties s : tooltip) {
            if (s.getString().trim().equals(PLACE_HOLDER))
                break;
            by += mc.fontRenderer.FONT_HEIGHT;
        }

        gooEntry.values().sort((v, v2) -> (int)v2.amount() - (int)v.amount());
        int compoundCount = gooEntry.values().size();
        int stacksPerLine = getArrangementStacksPerLine(compoundCount);
        for (GooValue entry : gooEntry.values()) {
            int x = bx + (j % stacksPerLine) * (ICON_WIDTH - 1);
            int y = by - (j / stacksPerLine) * (ICON_HEIGHT - 1);
            renderGooIcon(matrices, fluid(entry).getIcon(), x, y, (int)Math.floor(entry.amount()));
            j++;
        }
    }

    private static void renderGooIcon(MatrixStack matrices, ResourceLocation icon, int x, int y, int count) {
        Minecraft mc = Minecraft.getInstance();

        mc.getTextureManager().bindTexture(icon);
        drawTexturedModalRect(x, y, 0, 0, ICON_WIDTH, ICON_HEIGHT, 500f);

        String s1 = Integer.toString(count);
        int w1 = mc.fontRenderer.getStringWidth(s1);
        int color = 0xFFFFFF;
        RenderSystem.pushMatrix();
        //translating on the z axis here works like above. If too low, it'll draw the text behind items in the GUI. Items are drawn around zlevel 200 btw
        RenderSystem.translatef(x + (ICON_WIDTH / 2f) - w1 / 4f, y + TEXT_START_Y_OFFSET, 500);
        RenderSystem.scalef(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);
        mc.fontRenderer.drawStringWithShadow(matrices, s1, 0, 0, color);
        RenderSystem.popMatrix();
    }

    public static void drawTexturedModalRect(int x, int y, int u, int v, int width, int height, float zLevel)
    {
        final float uScale = 1f / ICON_WIDTH;
        final float vScale = 1f / ICON_HEIGHT;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder wr = tessellator.getBuffer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x        , y + height, zLevel).tex( u          * uScale, ((v + height) * vScale)).endVertex();
        wr.pos(x + width, y + height, zLevel).tex((u + width) * uScale, ((v + height) * vScale)).endVertex();
        wr.pos(x + width, y         , zLevel).tex((u + width) * uScale, ( v           * vScale)).endVertex();
        wr.pos(x        , y         , zLevel).tex( u          * uScale, ( v           * vScale)).endVertex();
        tessellator.draw();
    }

    private static GooFluid fluid(GooValue entry)
    {
        return fluid(entry.getFluidResourceLocation());
    }

    private static GooFluid fluid(ResourceLocation registryName)
    {
        return fluid(registryName.toString());
    }

    private static GooFluid fluid(String gooFluidName)
    {
        return (GooFluid)Registry.getFluid(gooFluidName);
    }
}
