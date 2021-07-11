package com.xeno.goo.events;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.blocks.*;
import com.xeno.goo.client.render.HighlightingHelper;
import com.xeno.goo.entities.IGooContainingEntity;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.overlay.RayTracing;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.CrucibleTile;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooContainerAbstraction;
import com.xeno.goo.tiles.GooPumpTile;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
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
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Post;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fluids.FluidStack;

import java.text.NumberFormat;
import java.util.*;

public class TargetingHandler
{
    private static final String PLACE_HOLDER = "\u00a76\u00a7r\u00a7r\u00a7r\u00a7r\u00a7r";
    private static final int ICON_WIDTH = 18;
    private static final int ICON_HEIGHT = 27;
    private static final int SHORT_ICON_WIDTH = 18;
    private static final int SHORT_ICON_HEIGHT = 18;
    private static final int ICONS_BEFORE_TWO_LINES_LOOKS_LIKE_POO = 12;
    private static final int TEXT_START_Y_OFFSET = 20;
    private static final float TEXT_SCALE = 0.5f;
    private static final int ICONS_BEFORE_ONE_LINE_LOOKS_LIKE_POO = 5;
    private static final float Z_LEVEL_OF_MODAL = 500;

    public static ItemStack PATCHOULI_BOOK = ItemStack.EMPTY;
    public static boolean lastHitIsGooContainer = false;
    public static boolean isGooReady = false;

    private static boolean hasGooAndYou(PlayerEntity player)
    {
        if (PATCHOULI_BOOK.isEmpty()) {
            PATCHOULI_BOOK = new ItemStack(ItemsRegistry.GOO_AND_YOU.get());
        }
        return player.inventory.mainInventory.stream().anyMatch(i -> i.getItem().equals(PATCHOULI_BOOK.getItem()));
    }

    /**
     * @return {@code null} if goo not ready, false if no goo, true if goo
     */
    private static Boolean hasEntry(World entityWorld)
    {
        // TODO: this is part of a nicer unavailable gooification display
        if (!isGooReady) return null;
        return Equivalencies.getEntry(entityWorld, currentStack.getItem()).values().size() > 0;
    }

    private static void prepGooContentsRealEstate(ItemTooltipEvent event)
    {
        if( Minecraft.getInstance().world == null || Minecraft.getInstance().player == null )
            return;

        List<FluidStack> gooEntry = getThisOrLastGooEntry();

        addPlaceholderSpaceForTooltipGooIcons(gooEntry.size(), event);
    }

    public static void onDraw(ItemTooltipEvent event)
    {
        lastStack = currentStack;
        //This method will make space for goo icons in the tooltip
        currentStack = event.getItemStack();

        prepHandlingOfGooValuesOfThings(event);

        // special handler for goo bulbs and vessels, which show their contents at rest, but not with shift held.
        if (hasGooContents() && !Screen.hasShiftDown()) {
            prepGooContentsRealEstate(event);
        }
    }

    private static void prepHandlingOfGooValuesOfThings(ItemTooltipEvent event)
    {
        if (event.getPlayer() == null) {
            return;
        }

        // you can only see goo values while holding "Goo and You"
        // unless the client option specifies otherwise.
        if (shouldHideGooValues(event.getPlayer())) {
            return;
        }

        // these always show up
        Boolean hasEntry = hasEntry(event.getPlayer().getEntityWorld());
        if (Boolean.TRUE != hasEntry) {
            event.getToolTip().add(new TranslationTextComponent(hasEntry == null ? "tooltip.goo.composition.goo_not_ready" : "tooltip.goo.composition.not_goo"));
        }

        // EVERYTHING shows its composition with shift held, bulbs are the exception
        if (Screen.hasShiftDown()) {
            prepGooCompositionRealEstate(event);
        } else if (hasEntry == Boolean.TRUE) {
            event.getToolTip().add(new TranslationTextComponent("tooltip.goo.composition.hold_key"));
        }
    }

    private static void addPlaceholderSpaceForTooltipGooIcons(int size, ItemTooltipEvent event)
    {
        if (size == 0) {
            return;
        }
        int fontHeight = Minecraft.getInstance().fontRenderer.FONT_HEIGHT + 1;
        if (Minecraft.getInstance().world == null || Minecraft.getInstance().player == null) //populateSearchTreeManager...
            return;

        int stacksPerLine = getArrangementStacksPerLine(size);
        int rows = (int)Math.ceil(size / (float)stacksPerLine);
        int lines = (int)Math.ceil((rows * ICON_HEIGHT) / (float)fontHeight + 1);
        int width = (stacksPerLine * (ICON_WIDTH - 1) + 1);
        String spaces = PLACE_HOLDER;
        while(Minecraft.getInstance().fontRenderer.getStringWidth(spaces) < width) {
            spaces += " ";
        }
        for (int j = 0; j < lines; j++) {
            event.getToolTip().add(new StringTextComponent(spaces));
        }
    }

    private static void prepGooCompositionRealEstate(ItemTooltipEvent event)
    {
        if( Minecraft.getInstance().world == null || Minecraft.getInstance().player == null )
            return;

        GooEntry gooEntry = Equivalencies.getEntry(Minecraft.getInstance().world, currentStack.getItem());
        if (gooEntry.isUnusable()) {
            return;
        } else {
            if (gooEntry.deniesSolidification()) {
                event.getToolTip().add(new TranslationTextComponent("tooltip.goo.composition.cant_solidify"));
            }
        }

        addPlaceholderSpaceForTooltipGooIcons(gooEntry.values().size(), event);
    }

    public static void tryDraw(RenderTooltipEvent.PostText event)
    {
        // special handler for goo bulbs, goo bulbs show their contents at rest, but not with shift held.
        if (hasGooContents() && !Screen.hasShiftDown()) {
            tryDrawingGooContents(event);
        }

        if(Minecraft.getInstance().player == null) {
            return;
        }

        if (shouldHideGooValues(Minecraft.getInstance().player)) {
            return;
        }

        // EVERYTHING shows its composition with shift held, bulbs are the exception
        if (Screen.hasShiftDown()) {
            tryDrawingGooComposition(event);
        }
    }

    private static boolean shouldHideGooValues(PlayerEntity player) {
        return !GooMod.config.gooValuesAlwaysVisible() && !hasGooAndYou(player);
    }

    private static final Set<Item> GOO_CONTAINERS = new HashSet<>();
    private static final Set<Item> GOO_ITEM_CONTAINERS = new HashSet<>();
    private static final Set<Item> GOO_DOUBLE_MAPS = new HashSet<>();
    private static void initializeGooContainers() {
        GOO_CONTAINERS.addAll(
                Sets.newHashSet(
                        ItemsRegistry.DEGRADER.get(),
                        ItemsRegistry.MIXER.get(),
                        ItemsRegistry.GOO_BULB.get(),
                        ItemsRegistry.TROUGH.get()
                )
        );
    }
    private static void initializeGooItemContainers() {
        GOO_ITEM_CONTAINERS.addAll(
                Sets.newHashSet(
                        ItemsRegistry.VESSEL.get(),
                        ItemsRegistry.GAUNTLET.get()
                )
        );
    }
    private static void initializeGooDoubleMaps() {
        GOO_DOUBLE_MAPS.addAll(
                Sets.newHashSet(
                        ItemsRegistry.GOOIFIER.get(),
                        ItemsRegistry.SOLIDIFIER.get()
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
                || GOO_DOUBLE_MAPS.contains(currentStack.getItem()) || currentStack.getItem() instanceof BucketItem;
    }

    // some client side caching to speed things up a little.
    private static ItemStack currentStack = ItemStack.EMPTY;
    private static ItemStack lastStack = ItemStack.EMPTY;
    private static List<FluidStack> lastGooEntry = new ArrayList<>();
    private static void tryDrawingGooContents(RenderTooltipEvent.PostText event)
    {
        if( Minecraft.getInstance().world == null || Minecraft.getInstance().player == null )
            return;

        List<FluidStack> gooEntry = getThisOrLastGooEntry();
        
        drawGooForEvent(event, gooEntry);
    }

    private static List<FluidStack> getThisOrLastGooEntry()
    {
        if (currentStack != lastStack) {
            lastGooEntry = new ArrayList<>();
            if (!tryFetchingGooContentsAsItem()) {
                if (!tryFetchingGooContentsAsDoubleMap()) {
                    tryFetchingGooContentsAsGooContainerAbstraction();
                }
            }
        }
        return lastGooEntry;
    }

    private static boolean tryFetchingGooContentsAsItem()
    {
        if (!(currentStack.getItem() instanceof BucketItem) && !GOO_ITEM_CONTAINERS.contains(currentStack.getItem())) {
            return false;
        }
        List<FluidStack> contents = FluidHandlerHelper.contentsOfItemStack(currentStack);
        if (contents.isEmpty()) {
            return false;
        }

        lastGooEntry = contents;
        return true;
    }

    private static void tryFetchingGooContentsAsGooContainerAbstraction()
    {
        lastGooEntry = contentsOfCurrentItemStackAsGooContainer();
    }

    private static List<FluidStack> contentsOfCurrentItemStackAsGooContainer() {
        return FluidHandlerHelper.contentsOfTileStack(currentStack);
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

        if( Minecraft.getInstance().world == null || Minecraft.getInstance().player == null )
            return;

        GooEntry gooEntry = Equivalencies.getEntry(Minecraft.getInstance().world, currentStack.getItem());
        if (gooEntry.isUnusable()) {
            return;
        }

        List<FluidStack> itemHandlerContents = FluidHandlerHelper.contentsOfItemStack(currentStack);
        List<FluidStack> tileHandlerContents = FluidHandlerHelper.contentsOfTileStack(currentStack);
        if (!itemHandlerContents.isEmpty()) {
            gooEntry = gooEntry.addGooContentsToMapping(itemHandlerContents);
        } else if (!tileHandlerContents.isEmpty()) {
            gooEntry = gooEntry.addGooContentsToMapping(tileHandlerContents);
        } else if (GooMod.config.canDamagedItemsBeGooified()) {
            // you may not melt down items that are damageable *and damaged*. Sorry not sorry
            if (currentStack.isDamageable() && currentStack.isDamaged()) {
                gooEntry = gooEntry.scale((currentStack.getMaxDamage() * 1d - currentStack.getDamage()) / currentStack.getMaxDamage());
            }
        } else {
            gooEntry = GooEntry.UNKNOWN;
        }
        
        drawGooForEvent(event, gooEntry);
    }

    private static void drawGooForEvent(RenderTooltipEvent.PostText event, List<FluidStack> gooEntry) {
        int j = 0;
        int size = gooEntry.size();
        int stacksPerLine = getArrangementStacksPerLine(size);
        if (stacksPerLine == 0) {
            return;
        }
        int rows = (int)Math.ceil(size / (float)stacksPerLine);
        int neededHeight = rows * ICON_HEIGHT;
        int fontHeight = Minecraft.getInstance().fontRenderer.FONT_HEIGHT + 1;
        int allocatedHeight = (int)Math.ceil(neededHeight / (float)fontHeight) * fontHeight;
        int wastedSpace = allocatedHeight - neededHeight;
        int centeringVerticalOffset = (int)Math.ceil(wastedSpace / 2f) + (int)Math.floor(fontHeight / 2f);

        int bx = event.getX();
        int by = event.getY();
        List<? extends ITextProperties> tooltip = event.getLines();
        for (ITextProperties s : tooltip) {
            if (s.getString().trim().equals(PLACE_HOLDER))
                break;
            by += fontHeight;
        }
        by += centeringVerticalOffset;
        MatrixStack matrices = event.getMatrixStack();
        RenderSystem.pushMatrix();
        RenderSystem.translatef(bx, by, Z_LEVEL_OF_MODAL);
        RenderSystem.translatef(0, 0, Minecraft.getInstance().getItemRenderer().zLevel);
        for (FluidStack entry : gooEntry) {
            if (!(entry.getFluid() instanceof GooFluid)) {
                continue;
            }
            int x = (j % stacksPerLine) * (ICON_WIDTH - 1);
            int y = (j / stacksPerLine) * (ICON_HEIGHT - 1);
            renderGooIcon(matrices, ((GooFluid)entry.getFluid()).icon(), x, y, (int)Math.floor(entry.getAmount()));
            j++;
        }
        RenderSystem.popMatrix();
    }

    private static void drawGooForEvent(RenderTooltipEvent.PostText event, GooEntry gooEntry) {
        List<FluidStack> stacks = convertToFluidStacks(gooEntry);
        drawGooForEvent(event, stacks);
    }

    private static List<FluidStack> convertToFluidStacks(GooEntry gooEntry) {
        List<FluidStack> result = new ArrayList<>();
        gooEntry.values().forEach((v) -> result.add(
                new FluidStack(Objects.requireNonNull(Registry.getFluid(v.getFluidResourceLocation())), (int)Math.ceil(v.amount())))
        );
        return result;
    }

    public static void renderGooIcon(MatrixStack matrices, ResourceLocation icon, int x, int y, int count) {
        matrices.push();
        matrices.translate(0, 0, 2d);
        drawModalIcons(matrices, x, y, icon);

        IFormattableTextComponent t1 = getGooAmountForDisplay(count);
        String s1 = t1.getString();
        int w1 = Minecraft.getInstance().fontRenderer.getStringWidth(s1);
        int color = 0xFFFFFFFF;
        matrices.translate(x + (ICON_WIDTH / 2f) - w1 / 4f, y + TEXT_START_Y_OFFSET, 3d);
        matrices.scale(TEXT_SCALE, TEXT_SCALE, 1f);
        Minecraft.getInstance().fontRenderer.drawStringWithShadow(matrices, s1, 0, 0, color);
        matrices.pop();
    }

    public static void renderGooIconWithoutAmount(MatrixStack matrices, ResourceLocation icon, int x, int y) {
        matrices.push();
        matrices.translate(0, 0, 2d);
        drawModalShortIcons(matrices, x, y, icon);
        matrices.pop();
    }

    public static void renderGooShortIcon(MatrixStack matrices, ResourceLocation icon, int x, int y,
                                          int width, int height, boolean isToggled) {
        drawModalIcons(matrices, x, y, icon, width, height, isToggled);
    }


    public static void renderConfigName(MatrixStack matrixStack, ITextComponent message, int x, int y) {
        if (Minecraft.getInstance().currentScreen == null) {
            return;

        }
        Minecraft.getInstance().currentScreen.renderTooltip(matrixStack, message, x, y);
    }

    private static void drawModalIcons(MatrixStack transform, int x, int y, ResourceLocation icon,
                                       int width, int height, boolean isToggled) {
        IRenderTypeBuffer.Impl buffer = IRenderTypeBuffer.getImpl(Tessellator.getInstance().getBuffer());
        // IVertexBuilder builder = buffer.getBuffer(GooRenderHelper.getGui(icon));
        float color = isToggled ? 1f : 0.2f;
        RenderSystem.color3f(color, color, color);
        Minecraft.getInstance().getTextureManager().bindTexture(icon);
        AbstractGui.blit(transform, x, y, width, height, 0f, 0f, ICON_WIDTH, ICON_HEIGHT, ICON_WIDTH, ICON_HEIGHT);
        // ClientUtils.drawTexturedRect(builder, transform, x, y, width, height, color, color, color, 1f, 0f, 1f, 0f, 1f);
        buffer.finish();
    }

    private static void drawModalIcons(MatrixStack transform, int x, int y, ResourceLocation icon) {
        drawModalIcons(transform, x, y, icon, ICON_WIDTH, ICON_HEIGHT, true);
    }

    private static void drawModalShortIcons(MatrixStack transform, int x, int y, ResourceLocation icon) {
        drawModalIcons(transform, x, y, icon, SHORT_ICON_WIDTH, SHORT_ICON_HEIGHT, true);
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
        r = Math.round(r * truncate) / (float)truncate;
        TranslationTextComponent notationMarker = new TranslationTextComponent(getOrderOfMagnitudeNotation(oom));
        String result = NumberFormat.getNumberInstance(Locale.ROOT).format((float)count + r);

        return  new TranslationTextComponent(result).appendSibling(notationMarker);
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
        lastHitIsGooContainer = false;
        if (!tryBlockRayTrace(e, event)) {
            tryEntityRayTrace(event);
        }
    }

    private static void tryEntityRayTrace(RenderGameOverlayEvent.Post event)
    {
        EntityRayTraceResult target = RayTracing.INSTANCE.entityTarget();

        if (target == null) {
            return;
        }

        if (hasGooContentsAsEntity(target)) {
            renderGooContentsOfEntity(event, target);
            lastTargetedEntity = target.getEntity();
        }
    }

    private static boolean tryBlockRayTrace(Entity e, RenderGameOverlayEvent.Post event)
    {
        BlockRayTraceResult target = RayTracing.INSTANCE.blockTarget();

        if (target == null) {
            return false;
        }
        World world = e.getEntityWorld();

        BlockState state = world.getBlockState(target.getPos());
        if (state.getBlock().isAir(state, world, target.getPos())) {
            return false;
        }
        lastTargetedBlock = target.getPos();
        lastHitSide = target.getFace();
        lastHitVector = target.getHitVec();
        if (hasGooContents(state)) {
            TileEntity t = world.getTileEntity(target.getPos());
            if (t instanceof GooContainerAbstraction) {
                lastHitIsGooContainer = true;
                renderGooContents(event, target, (GooContainerAbstraction)t);
                return true;
            }
            return false;
        }
        if (state.getBlock() instanceof GooPump) {
            TileEntity t = world.getTileEntity(target.getPos());
            if (t instanceof GooPumpTile) {
                GooEntry gooEntry = Equivalencies.getEntry(world.getDimensionKey(), ((GooPumpTile)t).getDisplayedItem());
                renderGooFilter(event, gooEntry);
            }
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
        if (currentStack.getItem().equals(ItemsRegistry.GOOIFIER.get())) {
            id = Objects.requireNonNull(Registry.GOOIFIER_TILE.get().getRegistryName()).toString();
        }

        if (currentStack.getItem().equals(ItemsRegistry.SOLIDIFIER.get())) {
            id = Objects.requireNonNull(Registry.SOLIDIFIER_TILE.get().getRegistryName()).toString();
        }

        if (id.equals("")) {
            return false;
        }

        CompoundNBT bulbTag = FluidHandlerHelper.getOrCreateTileTag(currentStack, id);
        if (bulbTag == null) {
            return false;
        }
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

        if( Minecraft.getInstance().world == null || Minecraft.getInstance().player == null )
            return;

        if (e instanceof CrucibleTile && target.getFace() != Direction.UP) {
            List<FluidStack> goo = ((CrucibleTile) e).getAllGooContentsFromTile();
            renderCrucibleGooContents(goo, event);
        } else {
            FluidStack entry = e.getGooFromTargetRayTraceResult(target, RayTraceTargetSource.JUST_LOOKING);

            int bx = (int) (event.getWindow().getScaledWidth() * 0.55f);
            int by = (int) (event.getWindow().getScaledHeight() * 0.45f);

            if (!(entry.getFluid() instanceof GooFluid)) {
                return;
            }
            renderGooIcon(matrices, ((GooFluid) entry.getFluid()).icon(), bx, by, (int) Math.floor(entry.getAmount()));
        }
    }

    private static void renderCrucibleGooContents(List<FluidStack> goo, Post event) {
        MatrixStack matrices = event.getMatrixStack();
        int bx = (int)(event.getWindow().getScaledWidth() * 0.55f);
        int by = (int)(event.getWindow().getScaledHeight() * 0.45f);
        int xOff = -ICON_WIDTH;
        int yOff = -ICON_HEIGHT;
        int entriesPerRow = (int)Math.ceil(Math.sqrt(goo.size()));
        int procCount = 0;

        for(int i = 0; i < goo.size(); i++) {
            procCount++;
            Fluid fluid = goo.get(i).getFluid();
            if (!(fluid instanceof GooFluid)) {
                return;
            }
            xOff += ICON_WIDTH;

            renderGooIcon(matrices, ((GooFluid)fluid).icon(), bx + xOff, by + yOff, goo.get(i).getAmount());
            if(procCount >= entriesPerRow) {
                xOff = -ICON_WIDTH;
                yOff += ICON_HEIGHT;
                procCount = 0;
            }
        }
    }

    private static void renderGooFilter(Post event, GooEntry gooEntry) {
        MatrixStack matrices = event.getMatrixStack();

        if( Minecraft.getInstance().world == null || Minecraft.getInstance().player == null )
            return;


        int bx = (int)(event.getWindow().getScaledWidth() * 0.55f);
        int by = (int)(event.getWindow().getScaledHeight() * 0.45f);
        int xOff = -SHORT_ICON_WIDTH;
        int yOff = -SHORT_ICON_HEIGHT;
        int entriesPerRow = (int)Math.ceil(Math.sqrt(gooEntry.values().size()));
        int procCount = 0;

        for(int i = 0; i < gooEntry.values().size(); i++) {
            procCount++;
            Fluid fluid = Registry.getFluid(gooEntry.values().get(i).getFluidResourceLocation());
            if (!(fluid instanceof GooFluid)) {
                return;
            }
            xOff += SHORT_ICON_WIDTH;

            renderGooIconWithoutAmount(matrices, ((GooFluid)fluid).shortIcon(), bx + xOff, by + yOff);
            if(procCount >= entriesPerRow) {
                xOff = -SHORT_ICON_WIDTH;
                yOff += SHORT_ICON_HEIGHT;
                procCount = 0;
            }
        }
    }

    private static void renderGooContentsOfEntity(RenderGameOverlayEvent.Post event, EntityRayTraceResult e)
    {
        MatrixStack matrices = event.getMatrixStack();

        if( Minecraft.getInstance().world == null || Minecraft.getInstance().player == null )
            return;

        List<FluidStack> entries = Collections.singletonList(gooInEntity(e));

        int bx = (int)(event.getWindow().getScaledWidth() * 0.55f);
        int by = (int)(event.getWindow().getScaledHeight() * 0.45f);
        int xOff = -ICON_WIDTH;
        int yOff = -ICON_HEIGHT;
        int entriesPerRow = (int)Math.ceil(Math.sqrt(entries.size()));
        int procCount = 0;
        for(FluidStack entry : entries) {
            procCount++;
            if (!(entry.getFluid() instanceof GooFluid)) {
                return;
            }
            xOff += ICON_WIDTH;
            if(procCount >= entriesPerRow) {
                yOff += ICON_HEIGHT;
                procCount = 0;
            }

            renderGooIcon(matrices, ((GooFluid) entry.getFluid()).icon(), bx + xOff, by + yOff, (int) Math.floor(entry.getAmount()));
        }
    }

    private static FluidStack gooInEntity(EntityRayTraceResult e)
    {
        if (e.getEntity() instanceof IGooContainingEntity) {
            return ((IGooContainingEntity) e.getEntity()).goo();
        }
        return (FluidStack.EMPTY);
    }

    private static boolean hasGooContents(BlockState state)
    {
        return state.getBlock() instanceof GooBulb
                || state.getBlock() instanceof Mixer
                || state.getBlock() instanceof Degrader
                || state.getBlock() instanceof Solidifier
                || state.getBlock() instanceof Gooifier
                || state.getBlock() instanceof GooTrough
                || state.getBlock() instanceof Crucible;
    }

    private static boolean hasGooContentsAsEntity(EntityRayTraceResult target)
    {
        return target.getEntity() instanceof IGooContainingEntity;
    }

    public static ResourceLocation iconFromFluidStack(FluidStack s) {
        if (s.getFluid() instanceof GooFluid) {
            return ((GooFluid) s.getFluid()).icon();
        }
        return null;
    }

    public static void clearStacks() {
        currentStack = ItemStack.EMPTY;
        lastStack = ItemStack.EMPTY;
        lastGooEntry = new ArrayList<>();
    }
}
