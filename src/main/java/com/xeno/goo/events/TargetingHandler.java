package com.xeno.goo.events;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.aequivaleo.GooValue;
import com.xeno.goo.blocks.*;
import com.xeno.goo.client.render.HighlightingHelper;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.overlay.RayTracing;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooContainerAbstraction;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.lwjgl.opengl.GL11;

import java.text.NumberFormat;
import java.util.*;

public class TargetingHandler
{
    private static final Minecraft mc = Minecraft.getInstance();
    private static final String PLACE_HOLDER = "\u00a76\u00a7r\u00a7r\u00a7r\u00a7r\u00a7r";
    private static final int ICON_WIDTH = 18;
    private static final int ICON_HEIGHT = 27;
    private static final int ICONS_BEFORE_TWO_LINES_LOOKS_LIKE_POO = 12;
    private static final int TEXT_START_Y_OFFSET = 20;
    private static final float TEXT_SCALE = 0.5f;
    private static final int ICONS_BEFORE_ONE_LINE_LOOKS_LIKE_POO = 5;

    public static ItemStack PATCHOULI_BOOK = ItemStack.EMPTY;
    private static boolean hasGooAndYou(PlayerEntity player)
    {
        if (PATCHOULI_BOOK.isEmpty()) {
            PATCHOULI_BOOK = new ItemStack(ItemsRegistry.GooAndYou.get());
        }
        return player.inventory.mainInventory.stream().anyMatch(i -> i.equals(PATCHOULI_BOOK, false));
    }

    private static boolean hasEntry(World entityWorld)
    {
        return Equivalencies.getEntry(entityWorld, currentStack.getItem()).values().size() > 0;
    }

    private static void prepGooContentsRealEstate(ItemTooltipEvent event)
    {
        if( mc.world == null || mc.player == null )
            return;

        List<FluidStack> gooEntry = getThisOrLastGooEntry();

        addPlaceholderSpaceForTooltipGooIcons(gooEntry.size(), event);
    }

    public static void onDraw(ItemTooltipEvent event)
    {

        //This method will make space for goo icons in the tooltip
        currentStack = event.getItemStack();

        handleDisplayingGooValuesOfThings(event);

        // special handler for goo bulbs and basins, which show their contents at rest, but not with shift held.
        if (hasGooContents() && !Screen.hasShiftDown()) {
            prepGooContentsRealEstate(event);
        }
    }

    private static void handleDisplayingGooValuesOfThings(ItemTooltipEvent event)
    {
        if (event.getPlayer() == null) {
            return;
        }

        // you can only see goo values while holding "Goo and You"
        if (!hasGooAndYou(event.getPlayer())) {
            return;
        }

        // these always show up
        boolean hasEntry = hasEntry(event.getPlayer().getEntityWorld());
        if (!hasEntry) {
            event.getToolTip().add(new TranslationTextComponent("tooltip.goo.composition.not_goo"));
        }

        // EVERYTHING shows its composition with shift held, bulbs are the exception
        if (Screen.hasShiftDown()) {
            prepGooCompositionRealEstate(event);
        } else if (hasEntry) {
            event.getToolTip().add(new TranslationTextComponent("tooltip.goo.composition.hold_key"));
        }
    }

    private static void addPlaceholderSpaceForTooltipGooIcons(int size, ItemTooltipEvent event)
    {
        if (size == 0) {
            return;
        }
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

    private static void prepGooCompositionRealEstate(ItemTooltipEvent event)
    {
        if( mc.world == null || mc.player == null )
            return;

        GooEntry gooEntry = Equivalencies.getEntry(mc.world, currentStack.getItem());
        if (gooEntry.isUnusable()) {
            return;
        }

        addPlaceholderSpaceForTooltipGooIcons(gooEntry.values().size(), event);
    }

    public static void postDraw(RenderTooltipEvent.PostText event)
    {
        // special handler for goo bulbs, goo bulbs show their contents at rest, but not with shift held.
        if (hasGooContents() && !Screen.hasShiftDown()) {
            tryDrawingGooContents(event);
        }

        if(mc.player == null) {
            return;
        }

        if (!hasGooAndYou(mc.player)) {
            return;
        }

        // EVERYTHING shows its composition with shift held, bulbs are the exception
        if (Screen.hasShiftDown()) {
            tryDrawingGooComposition(event);
        }
    }

    private static Set<Item> GOO_CONTAINERS = new HashSet<>();
    private static Set<Item> GOO_ITEM_CONTAINERS = new HashSet<>();
    private static Set<Item> GOO_DOUBLE_MAPS = new HashSet<>();
    private static void initializeGooContainers() {
        GOO_CONTAINERS.addAll(
                Sets.newHashSet(
                        ItemsRegistry.Crucible.get(),
                        ItemsRegistry.Mixer.get(),
                        ItemsRegistry.GooBulb.get()
                )
        );
    }
    private static void initializeGooItemContainers() {
        GOO_ITEM_CONTAINERS.addAll(
                Sets.newHashSet(
                        ItemsRegistry.Basin.get(),
                        ItemsRegistry.Gauntlet.get()
                )
        );
    }
    private static void initializeGooDoubleMaps() {
        GOO_DOUBLE_MAPS.addAll(
                Sets.newHashSet(
                        ItemsRegistry.Gooifier.get(),
                        ItemsRegistry.Solidifier.get()
                )
        );
    }

    private static boolean hasGooContents()
    {
        if (GOO_DOUBLE_MAPS.size() == 0) {
            initializeGooDoubleMaps();
        }
        if (GOO_CONTAINERS.size() == 0) {
            initializeGooContainers();
        }
        if (GOO_ITEM_CONTAINERS.size() == 0) {
            initializeGooItemContainers();
        }
        return GOO_CONTAINERS.contains(currentStack.getItem()) || GOO_ITEM_CONTAINERS.contains(currentStack.getItem())
                || GOO_DOUBLE_MAPS.contains(currentStack.getItem());
    }

    // some client side caching to speed things up a little.
    private static ItemStack currentStack = ItemStack.EMPTY;
    private static ItemStack lastStack = ItemStack.EMPTY;
    private static List<FluidStack> lastGooEntry = new ArrayList<>();
    private static void tryDrawingGooContents(RenderTooltipEvent.PostText event)
    {
        int fontHeight = mc.fontRenderer.FONT_HEIGHT + 1;
        MatrixStack matrices = event.getMatrixStack();

        if( mc.world == null || mc.player == null )
            return;

        List<FluidStack> gooEntry = getThisOrLastGooEntry();

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

    private static List<FluidStack> getThisOrLastGooEntry()
    {
        if (currentStack.equals(lastStack, false)) {
            return lastGooEntry;
        } else {
            lastStack = currentStack;
            lastGooEntry = new ArrayList<>();
            if (!tryFetchingGooContentsAsItem()) {
                if (!tryFetchingGooContentsAsDoubleMap()) {
                    tryFetchingGooContentsAsGooContainerAbstraction();
                }
            }
            return lastGooEntry;
        }
    }

    private static boolean tryFetchingGooContentsAsItem()
    {
        if (!GOO_ITEM_CONTAINERS.contains(currentStack.getItem())) {
            return false;
        }

        IFluidHandlerItem cap = FluidHandlerHelper.capability(currentStack);
        if (cap == null) {
            return false;
        }

        if (cap.getFluidInTank(0).isEmpty()) {
            return false;
        }

        lastGooEntry.add(cap.getFluidInTank(0));
        return true;
    }

    private static void tryFetchingGooContentsAsGooContainerAbstraction()
    {
        String id = "";
        Item item = currentStack.getItem();
        if (item.equals(ItemsRegistry.Mixer.get())) {
            id = Objects.requireNonNull(Registry.MIXER_TILE.get().getRegistryName()).toString();
        } else if (item.equals(ItemsRegistry.Crucible.get())) {
            id = Objects.requireNonNull(Registry.CRUCIBLE_TILE.get().getRegistryName()).toString();
        } else if (item.equals(ItemsRegistry.GooBulb.get())) {
            id = Objects.requireNonNull(Registry.GOO_BULB_TILE.get().getRegistryName()).toString();
        }
        CompoundNBT bulbTag = FluidHandlerHelper.getOrCreateTileTag(currentStack, id);
        CompoundNBT gooTag = bulbTag.getCompound("goo");
        lastGooEntry = GooContainerAbstraction.deserializeGooForDisplay(gooTag);
        lastGooEntry.sort((v, v2) -> v2.getAmount() - v.getAmount());
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

    private static void tryDrawingGooComposition(RenderTooltipEvent.PostText event)
    {
        Minecraft mc = Minecraft.getInstance();
        int fontHeight = mc.fontRenderer.FONT_HEIGHT + 1;
        MatrixStack matrices = event.getMatrixStack();

        if( mc.world == null || mc.player == null )
            return;

        GooEntry gooEntry = Equivalencies.getEntry(mc.world, currentStack.getItem());
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

    public static void renderGooIcon(MatrixStack matrices, ResourceLocation icon, int x, int y, int count) {
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

    public static IFormattableTextComponent getGooAmountForDisplay(int count)
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

    // cached player targets allow us to recall what the player is targeting when sending
    // goo grab requests to the server since our control scheme is unconventional.
    public static Entity lastTargetedEntity = null;
    public static BlockPos lastTargetedBlock = null;
    public static Direction lastHitSide = null;
    public static Vector3d lastHitVector = null;
    public static void onGameOverlay(RenderGameOverlayEvent.Post event)
    {
        if (Minecraft.getInstance().getRenderViewEntity() == null) {
            return;
        }

        Entity e = Minecraft.getInstance().getRenderViewEntity();

        if (!HighlightingHelper.needsHighlightForItemHeld(e)) {
            return;
        }

        RayTracing.INSTANCE.fire();
        if (!RayTracing.INSTANCE.hasTarget()) {
            return;
        }

        // clear the cached BlockPos and entity targets
        lastTargetedBlock = null;
        lastTargetedEntity = null;
        lastHitSide = null;
        lastHitVector = null;
        if (!tryBlockRayTrace(e, event)) {
            tryEntityRayTrace(e, event);
        }
    }

    private static boolean tryEntityRayTrace(Entity e, RenderGameOverlayEvent.Post event)
    {
        EntityRayTraceResult target = RayTracing.INSTANCE.entityTarget();

        if (target == null) {
            return false;
        }

        if (hasGooContentsAsEntity(target)) {
            renderGooContentsOfEntity(event, target);
            lastTargetedEntity = target.getEntity();
            return true;
        }

        return false;
    }

    private static boolean tryBlockRayTrace(Entity e, RenderGameOverlayEvent.Post event)
    {
        BlockRayTraceResult target = RayTracing.INSTANCE.blockTarget();

        if (target == null) {
            return false;
        }
        ClientWorld world = (ClientWorld)e.getEntityWorld();

        BlockState state = world.getBlockState(target.getPos());
        if (hasGooContents(state)) {
            TileEntity t = world.getTileEntity(target.getPos());
            if (t instanceof GooContainerAbstraction) {
                renderGooContents(event, target, (GooContainerAbstraction)t);
                lastTargetedBlock = target.getPos();
                lastHitSide = target.getFace();
                lastHitVector = target.getHitVec();
                return true;
            }
            return false;
        }
        return false;
    }

    private static Map<String, Double> deserializeGooForDisplay(CompoundNBT tag)
    {
        Map<String, Double> unsorted = new HashMap<>();
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT gooTag = tag.getCompound("goo" + i);
            String key = gooTag.getString("key");
            double value = gooTag.getDouble("value");
            unsorted.put(key, value);
        }

        Map<String, Double> sorted = new LinkedHashMap<>();
        unsorted.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEachOrdered(x -> sorted.put(x.getKey(), x.getValue()));

        return sorted;
    }

    private static boolean tryFetchingGooContentsAsDoubleMap()
    {
        String id = "";
        if (currentStack.getItem().equals(ItemsRegistry.Gooifier.get())) {
            id = Objects.requireNonNull(Registry.GOOIFIER_TILE.get().getRegistryName()).toString();
        }

        if (currentStack.getItem().equals(ItemsRegistry.Solidifier.get())) {
            id = Objects.requireNonNull(Registry.SOLIDIFIER_TILE.get().getRegistryName()).toString();
        }

        if (id.equals("")) {
            return false;
        }

        CompoundNBT bulbTag = FluidHandlerHelper.getOrCreateTileTag(currentStack, id);
        CompoundNBT gooTag = bulbTag.getCompound("goo");
        Map<String, Double> sortedValues = deserializeGooForDisplay(gooTag);

        List<FluidStack> gooEntryForDisplay = new ArrayList<>();

        for(Map.Entry<String, Double> v : sortedValues.entrySet()) {
            if (v.getValue() == 0D) {
                continue;
            }

            Fluid fluid = Registry.getFluid(v.getKey());
            if (fluid == null || fluid.equals(Fluids.EMPTY)) {
                continue;
            }
            int fluidAmount = (int)Math.ceil(v.getValue());
            gooEntryForDisplay.add(new FluidStack(fluid, fluidAmount));
        }

        lastGooEntry.addAll(gooEntryForDisplay);
        lastGooEntry.sort((v, v2) -> v2.getAmount() - v.getAmount());
        return true;
    }

    private static void renderGooContents(RenderGameOverlayEvent.Post event, BlockRayTraceResult target, GooContainerAbstraction e)
    {
        MatrixStack matrices = event.getMatrixStack();

        if( mc.world == null || mc.player == null )
            return;

        FluidStack entry = e.getGooFromTargetRayTraceResult(target, RayTraceTargetSource.JUST_LOOKING);

        int bx = (int)(event.getWindow().getScaledWidth() * 0.55f);
        int by = (int)(event.getWindow().getScaledHeight() * 0.45f);

        if (!(entry.getFluid() instanceof GooFluid)) {
            return;
        }
        renderGooIcon(matrices, ((GooFluid)entry.getFluid()).getIcon(), bx, by, (int)Math.floor(entry.getAmount()));
    }

    private static void renderGooContentsOfEntity(RenderGameOverlayEvent.Post event, EntityRayTraceResult e)
    {
        MatrixStack matrices = event.getMatrixStack();

        if( mc.world == null || mc.player == null )
            return;

        FluidStack entry = gooInEntity(e);

        int bx = (int)(event.getWindow().getScaledWidth() * 0.55f);
        int by = (int)(event.getWindow().getScaledHeight() * 0.45f);

        if (!(entry.getFluid() instanceof GooFluid)) {
            return;
        }
        renderGooIcon(matrices, ((GooFluid)entry.getFluid()).getIcon(), bx, by, (int)Math.floor(entry.getAmount()));
    }

    private static FluidStack gooInEntity(EntityRayTraceResult e)
    {
        if (e.getEntity() instanceof GooSplat) {
            return ((GooSplat) e.getEntity()).goo();
        }

        return FluidStack.EMPTY;
    }

    private static boolean hasGooContents(BlockState state)
    {
        return state.getBlock() instanceof GooBulb
                || state.getBlock() instanceof Mixer
                || state.getBlock() instanceof Crucible
                || state.getBlock() instanceof Solidifier
                || state.getBlock() instanceof Gooifier;
    }

    private static boolean hasGooContentsAsEntity(EntityRayTraceResult target)
    {
        return target.getEntity() instanceof GooSplat;
    }

    public static ResourceLocation iconFromFluidStack(FluidStack s) {
        if (s.getFluid() instanceof GooFluid) {
            return ((GooFluid) s.getFluid()).getIcon();
        }
        return null;
    }
}
