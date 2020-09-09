package com.xeno.goo.events;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import com.xeno.goo.blocks.Crucible;
import com.xeno.goo.blocks.GooBulbAbstraction;
import com.xeno.goo.blocks.Mixer;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.overlay.RayTracing;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooContainerAbstraction;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

import java.text.NumberFormat;
import java.util.*;

public class TooltipHandler
{
    private static final String PLACE_HOLDER = "\u00a76\u00a7r\u00a7r\u00a7r\u00a7r\u00a7r";
    private static final int ICON_WIDTH = 18;
    private static final int ICON_HEIGHT = 27;
    private static final int ICONS_BEFORE_TWO_LINES_LOOKS_LIKE_POO = 12;
    private static final int TEXT_START_Y_OFFSET = 20;
    private static final float TEXT_SCALE = 0.5f;
    private static final int ICONS_BEFORE_ONE_LINE_LOOKS_LIKE_POO = 5;

    public static ItemStack PATCHOULI_BOOK = ItemStack.EMPTY;
    private static boolean isHoldingPatchouliBook(PlayerEntity player)
    {
        if (PATCHOULI_BOOK.isEmpty()) {
            PATCHOULI_BOOK = new ItemStack(Registry.GOO_AND_YOU.get());
        }
        return player.getHeldItemOffhand()
                .equals(PATCHOULI_BOOK, false);
    }

    private static boolean cantSolidify(ItemStack stack, World entityWorld)
    {
        return Equivalencies.getEntry(entityWorld, stack.getItem()).isUnattainable();
    }

    private static boolean hasEntry(ItemStack stack, World entityWorld)
    {
        return Equivalencies.getEntry(entityWorld, stack.getItem()).values().size() > 0;
    }

    private static void prepGooContentsRealEstate(ItemStack stack, ItemTooltipEvent event)
    {
        Minecraft mc = Minecraft.getInstance();

        if( mc.world == null || mc.player == null )
            return;

        List<FluidStack> gooEntry = getThisOrLastGooEntry(stack);

        addPlaceholderSpaceForTooltipGooIcons(gooEntry.size(), event);
    }

    public static void onDraw(ItemTooltipEvent event)
    {

        //This method will make space for goo icons in the tooltip
        ItemStack stack = event.getItemStack();

        // you can only see goo values while holding "Goo and You"
        if (event.getPlayer() != null && isHoldingPatchouliBook(event.getPlayer())) {
            // EVERYTHING shows its composition with shift held, bulbs are the exception
            if (Screen.hasShiftDown()) {
                if (hasEntry(stack, event.getPlayer().getEntityWorld())) {
                    if (cantSolidify(stack, event.getPlayer().getEntityWorld())) {
                        event.getToolTip().add(new TranslationTextComponent("tooltip.goo.composition.cant_solidify"));
                    }
                    prepGooCompositionRealEstate(stack, event);
                } else {
                    event.getToolTip().add(new TranslationTextComponent("tooltip.goo.composition.not_goo"));
                }
            } else {
                if (hasEntry(stack, event.getPlayer().getEntityWorld())) {
                    if (cantSolidify(stack, event.getPlayer().getEntityWorld())) {
                        event.getToolTip().add(new TranslationTextComponent("tooltip.goo.composition.cant_solidify"));
                    }
                    event.getToolTip().add(new TranslationTextComponent("tooltip.goo.composition.hold_key"));
                } else {
                    event.getToolTip().add(new TranslationTextComponent("tooltip.goo.composition.not_goo"));
                }
            }
        }

        // special handler for goo bulbs, goo bulbs show their contents at rest, but not with shift held.
        if (hasGooContents(stack) && !Screen.hasShiftDown()) {
            prepGooContentsRealEstate(stack, event);
        }
    }

    private static void addPlaceholderSpaceForTooltipGooIcons(int size, ItemTooltipEvent event)
    {
        if (size == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int fontHeight = mc.fontRenderer.FONT_HEIGHT + 1;
        if (mc.world == null || mc.player == null) //populateSearchTreeManager...
            return;

        int stacksPerLine = getArrangementStacksPerLine(size);
        int rows = (int)Math.ceil(size / (float)stacksPerLine);
        int lines = (int)Math.ceil((rows * ICON_HEIGHT) / (float)fontHeight + 1);
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

    public static void postDraw(RenderTooltipEvent.PostText event)
    {
        //This method will draw goo icons on the tooltip
        ItemStack stack = event.getStack();

        // special handler for goo bulbs, goo bulbs show their contents at rest, but not with shift held.
        if (hasGooContents(stack) && !Screen.hasShiftDown()) {
            tryDrawingGooContents(stack, event);
        }

        // you can only see goo values while holding "Goo and You"
        Minecraft mc = Minecraft.getInstance();

        if(mc.player == null) {
            return;
        }

        if (!isHoldingPatchouliBook(mc.player)) {
            return;
        }

        // EVERYTHING shows its composition with shift held, bulbs are the exception
        if (Screen.hasShiftDown()) {
            tryDrawingGooComposition(stack, event);
        }
    }

    private static Set<Item> GOO_CONTAINERS = new HashSet<>();
    private static void initializeGooContainers() {
        GOO_CONTAINERS.addAll(
                Sets.newHashSet(
                        Registry.GOO_BULB_ITEM.get(),
                        Registry.GOO_BULB_ITEM_MK2.get(),
                        Registry.GOO_BULB_ITEM_MK3.get(),
                        Registry.GOO_BULB_ITEM_MK4.get(),
                        Registry.GOO_BULB_ITEM_MK5.get(),
                        Registry.MIXER_ITEM.get(),
                        Registry.CRUCIBLE_ITEM.get()
                )
        );
    }


    private static boolean hasGooContents(ItemStack stack)
    {
        if (GOO_CONTAINERS.size() == 0) {
            initializeGooContainers();
        }
        return GOO_CONTAINERS.contains(stack.getItem());
    }

    // some client side caching to speed things up a little.
    private static ItemStack lastStack = ItemStack.EMPTY;
    private static List<FluidStack> lastGooEntry = new ArrayList<>();
    private static void tryDrawingGooContents(ItemStack stack, RenderTooltipEvent.PostText event)
    {
        Minecraft mc = Minecraft.getInstance();
        int fontHeight = mc.fontRenderer.FONT_HEIGHT + 1;
        MatrixStack matrices = event.getMatrixStack();

        if( mc.world == null || mc.player == null )
            return;

        List<FluidStack> gooEntry = getThisOrLastGooEntry(stack);

        int bx = event.getX();
        int by = event.getY();
        int j = 0;

        int size = gooEntry.size();
        int stacksPerLine = getArrangementStacksPerLine(size);
        if (stacksPerLine == 0) {
            return;
        }
        int rows = (int)Math.ceil(size / (float)stacksPerLine);
        int neededHeight = rows * ICON_HEIGHT;
        int allocatedHeight = (int)Math.ceil(neededHeight / (float)fontHeight) * fontHeight;
        int wastedSpace = allocatedHeight - neededHeight;
        int centeringVerticalOffset = (int)Math.ceil(wastedSpace / 2f) + (int)Math.floor(fontHeight / 2f);

        List<? extends ITextProperties> tooltip = event.getLines();
        for (ITextProperties s : tooltip) {
            if (s.getString().trim().equals(PLACE_HOLDER))
                break;
            by += fontHeight;
        }
        by += centeringVerticalOffset;
        for (FluidStack entry : gooEntry) {
            if (!(entry.getFluid() instanceof GooFluid)) {
                continue;
            }
            int x = bx + (j % stacksPerLine) * (ICON_WIDTH - 1);
            int y = by + (j / stacksPerLine) * (ICON_HEIGHT - 1);
            renderGooIcon(matrices, ((GooFluid)entry.getFluid()).getIcon(), x, y, (int)Math.floor(entry.getAmount()));
            j++;
        }
    }

    private static List<FluidStack> getThisOrLastGooEntry(ItemStack stack)
    {
        if (stack.equals(lastStack, false)) {
            return lastGooEntry;
        } else {
            lastStack = stack;
            lastGooEntry = new ArrayList<>();
            CompoundNBT stackTag = stack.getTag();
            if (stackTag == null) {
                return lastGooEntry;
            }

            if (!stackTag.contains("BlockEntityTag")) {
                return lastGooEntry;
            }

            CompoundNBT bulbTag = stackTag.getCompound("BlockEntityTag");

            if (!bulbTag.contains("goo")) {
                return lastGooEntry;
            }

            CompoundNBT gooTag = bulbTag.getCompound("goo");
            lastGooEntry = GooContainerAbstraction.deserializeGooForDisplay(gooTag);
            lastGooEntry.sort((v, v2) -> v2.getAmount() - v.getAmount());
            return lastGooEntry;
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
        int fontHeight = mc.fontRenderer.FONT_HEIGHT + 1;
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

        int size = gooEntry.values().size();
        int stacksPerLine = getArrangementStacksPerLine(size);
        int rows = (int)Math.ceil(size / (float)stacksPerLine);
        int neededHeight = rows * ICON_HEIGHT;
        int allocatedHeight = (int)Math.ceil(neededHeight / (float)fontHeight) * fontHeight;
        int wastedSpace = allocatedHeight - neededHeight;
        int centeringVerticalOffset = (int)Math.ceil(wastedSpace / 2f) + (int)Math.floor(fontHeight / 2f);

        List<? extends ITextProperties> tooltip = event.getLines();
        for (ITextProperties s : tooltip) {
            if (s.getString().trim().equals(PLACE_HOLDER))
                break;
            by += fontHeight;
        }
        by += centeringVerticalOffset;
        gooEntry.values().sort((v, v2) -> (int)v2.amount() - (int)v.amount());
        for (GooValue entry : gooEntry.values()) {
            int x = bx + (j % stacksPerLine) * (ICON_WIDTH - 1);
            int y = by + (j / stacksPerLine) * (ICON_HEIGHT - 1);
            renderGooIcon(matrices, fluid(entry).getIcon(), x, y, (int)Math.floor(entry.amount()));
            j++;
        }
    }

    private static void renderGooIcon(MatrixStack matrices, ResourceLocation icon, int x, int y, int count) {
        Minecraft mc = Minecraft.getInstance();

        mc.getTextureManager().bindTexture(icon);
        matrices.push();
        drawTexturedModalRect(x, y, 0, 0, ICON_WIDTH, ICON_HEIGHT, 500f);

        IFormattableTextComponent t1 = getGooAmountForDisplay(count);
        String s1 = t1.getString();
        int w1 = mc.fontRenderer.getStringWidth(s1);
        int color = 0xFFFFFFFF;
        //translating on the z axis here works like above. If too low, it'll draw the text behind items in the GUI. Items are drawn around zlevel 200 btw
        matrices.translate(x + (ICON_WIDTH / 2f) - w1 / 4f, y + TEXT_START_Y_OFFSET, 600);
        matrices.scale(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);
        mc.fontRenderer.drawStringWithShadow(matrices, s1, 0, 0, color);
        matrices.pop();
    }

    private static IFormattableTextComponent getGooAmountForDisplay(int count)
    {
        String s = Integer.toString(count);
        int oom = 0;
        int length = s.length();
        float r = 0f;
        // over a thousand buckets (a million millis)
        // we really want to compress the values by more than usual.
        while(length > 3) {
            length -= 3;
            oom++;
            // capture the remainder's significant digits
            r = (count % 1000) / 1000f;
            count /= 1000;
        }
        // count digits > 2? truncate an additional place (10 instead of 100)
        int truncate = 100;
        if (count >= 100) {
            truncate = 10;
        }
        r = (int)Math.ceil(r * truncate) / (float)truncate;
        TranslationTextComponent notationMarker = new TranslationTextComponent(getOrderOfMagnitudeNotation(oom));
        String result = NumberFormat.getNumberInstance(Locale.ROOT).format((float)count + r);

        return  new TranslationTextComponent(result).append(notationMarker);
    }

    private static String getOrderOfMagnitudeNotation(int oom)
    {
        switch(oom) {
            case 1:
                return "numbers.notation.thousand";
            case 2:
                return "numbers.notation.million";
            case 3:
                return "numbers.notation.billion";
        }
        return "";
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

    private static Map<String, GooFluid> fluidCache = new HashMap<>();
    private static GooFluid fluid(String gooFluidName)
    {
        if (!fluidCache.containsKey(gooFluidName)) {
            fluidCache.put(gooFluidName,(GooFluid)Registry.getFluid(gooFluidName));
        }
        return fluidCache.get(gooFluidName);
    }

    public static void onGameOverlay(RenderGameOverlayEvent.Post event)
    {
        if (Minecraft.getInstance().getRenderViewEntity() == null) {
            return;
        }

        Entity e = Minecraft.getInstance().getRenderViewEntity();

        RayTracing.INSTANCE.fire();
        if (!RayTracing.INSTANCE.hasTarget()) {
            return;
        }
        BlockRayTraceResult target = RayTracing.INSTANCE.target();
        ClientWorld world = (ClientWorld)e.getEntityWorld();

        BlockState state = world.getBlockState(target.getPos());
        if (hasGooContents(state)) {
            TileEntity t = world.getTileEntity(target.getPos());
            if (!(t instanceof GooContainerAbstraction)) {
                return;
            }
            renderGooContents(event, (GooContainerAbstraction)t);
        }
    }

    private static void renderGooContents(RenderGameOverlayEvent.Post event, GooContainerAbstraction e)
    {
        Minecraft mc = Minecraft.getInstance();
        MatrixStack matrices = event.getMatrixStack();

        if( mc.world == null || mc.player == null )
            return;


        List<FluidStack> gooEntry = e.goo();

        int size = gooEntry.size();
        int stacksPerLine = getArrangementStacksPerLine(size);
        if (stacksPerLine == 0) {
            return;
        }

        int bx = (int)(event.getWindow().getScaledWidth() * 0.55f);
        int by = (int)((event.getWindow().getScaledHeight() + Math.ceil(size / (float)stacksPerLine)) * 0.45f);
        int j = 0;

        for (FluidStack entry : gooEntry) {
            if (!(entry.getFluid() instanceof GooFluid)) {
                continue;
            }
            int x = bx + (j % stacksPerLine) * (ICON_WIDTH - 1);
            int y = by + (j / stacksPerLine) * (ICON_HEIGHT - 1);
            renderGooIcon(matrices, ((GooFluid)entry.getFluid()).getIcon(), x, y, (int)Math.floor(entry.getAmount()));
            j++;
        }
    }

    private static boolean hasGooContents(BlockState state)
    {
        return state.getBlock() instanceof GooBulbAbstraction
                || state.getBlock() instanceof Mixer
                || state.getBlock() instanceof Crucible;
    }
}
