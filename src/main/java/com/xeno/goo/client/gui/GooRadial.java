package com.xeno.goo.client.gui;


import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.GooBulbItem;
import com.xeno.goo.events.TargetingHandler;
import com.xeno.goo.items.Basin;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.network.GooGauntletSwapPacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistryEntry;
import org.lwjgl.opengl.GL11;

import java.util.*;

@Mod.EventBusSubscriber(Dist.CLIENT)
public class GooRadial extends Screen {
    private static final int ICON_WIDTH = 18;
    private static final int ICON_HEIGHT = 27;
    private static final float PRECISION = 2.5f / 360.0f;

    private KeyBinding keybinding;

    private boolean closing;

    private double startAnimation;

    private int selectedItem;

    private FluidStack lastFluidStackTarget;

    public FluidStack target() {
        return lastFluidStackTarget;
    }

    public GooRadial(KeyBinding keybinding) {
        super(new StringTextComponent(""));
        this.keybinding = keybinding;
        this.closing = false;

        Minecraft mc = Minecraft.getInstance();
        this.startAnimation = mc.world.getGameTime() + (double) mc.getRenderPartialTicks();

        this.selectedItem = -1;
        this.lastFluidStackTarget = FluidStack.EMPTY;
    }

    @SubscribeEvent
    public static void overlayEvent(RenderGameOverlayEvent.Pre event) {
        if (Minecraft.getInstance().currentScreen instanceof GooRadial) {
            if (event.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
                event.setCanceled(true);
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        super.render(matrices, mouseX, mouseY, partialTicks);

        final float OPEN_ANIMATION_LENGTH = 2.5f;
        long worldTime = Minecraft.getInstance().world.getGameTime();
        float animationTime = (float) (worldTime + partialTicks - startAnimation);
        float openAnimation = closing ? 1.0f - animationTime / OPEN_ANIMATION_LENGTH : animationTime / OPEN_ANIMATION_LENGTH;

        float animProgress = MathHelper.clamp(openAnimation, 0, 1);
        float radiusIn = Math.max(0.1f, 45 * animProgress);
        float radiusOut = radiusIn * 2;
        float itemRadius = (radiusIn + radiusOut) * 0.5f;
        float animTop = (1 - animProgress) * height / 2.0f;
        int x = width / 2;
        int y = height / 2;

        List<FluidStack> availableGooTypes = availableGooTypes(Minecraft.getInstance().player);
        int numberOfSlices = availableGooTypes.size();

        double a = Math.toDegrees(Math.atan2(mouseY - y, mouseX - x));
        double d = Math.sqrt(Math.pow(mouseX - x, 2) + Math.pow(mouseY - y, 2));
        float s0 = (((0 - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
        if (a < s0) {
            a += 360;
        }

        RenderSystem.pushMatrix();
        RenderSystem.disableAlphaTest();
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        RenderSystem.translated(0, animTop, 0);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        String hovering = "";
        if (!closing) {
            selectedItem = -1;
            for (int i = 0; i < numberOfSlices; i++) {
                float s = (((i - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
                float e = (((i + 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
                if (a >= s && a < e && d >= radiusIn && d < radiusOut) {
                    hovering = "" + i;
                    selectedItem = i;
                    break;
                }
            }
        }

        for (int i = 0; i < numberOfSlices; i++) {
            float s = (((i - 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            float e = (((i + 0.5f) / (float) numberOfSlices) + 0.25f) * 360;
            if (selectedItem == i) {
                drawSlice(buffer, x, y, 10, radiusIn, radiusOut, s, e, 63, 161, 191, 60);
            }
            else {
                drawSlice(buffer, x, y, 10, radiusIn, radiusOut, s, e, 0, 0, 0, 64);
            }
        }

        tessellator.draw();
        RenderSystem.enableTexture();

        RenderHelper.enableStandardItemLighting();
        RenderSystem.popMatrix();
        for(int i = 0; i < numberOfSlices; i++){
            FluidStack s = availableGooTypes.get(i);
            float angle1 = (((i / (float) numberOfSlices) - 0.25f) * 2 * (float) Math.PI) + (float)Math.PI;
            float posX = x + itemRadius * (float) Math.cos(angle1);
            float posY = y + itemRadius * (float) Math.sin(angle1);

            ResourceLocation resourceIcon = TargetingHandler.iconFromFluidStack(s);
            RenderSystem.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
            RenderSystem.disableLighting();
            RenderSystem.disableDepthTest();
            if(resourceIcon != null) {
                TargetingHandler.renderGooIcon(matrices, resourceIcon, (int)posX - ICON_WIDTH / 2, (int)posY - ICON_HEIGHT / 2, s.getAmount());
            }

            if (i == selectedItem) {
                lastFluidStackTarget = s;
            }
        }
    }

    private List<FluidStack> availableGooTypes(ClientPlayerEntity player) {
        Map<Fluid, FluidStack> result = new TreeMap<>(Comparator.comparing(ForgeRegistryEntry::getRegistryName));
        if (!isGauntletEmpty(player)) {
            result.put(Fluids.EMPTY, FluidStack.EMPTY);
        }
        for (ItemStack i : player.inventory.mainInventory) {
            if (i.getItem() instanceof GooBulbItem) {
                CompoundNBT bulbTag = FluidHandlerHelper.getOrCreateTileTag(i, Objects.requireNonNull(Registry.GOO_BULB_TILE.get().getRegistryName()).toString());
                CompoundNBT gooTag = bulbTag.getCompound("goo");
                List<FluidStack> bulbStacks = GooBulbTile.deserializeGooForDisplay(gooTag);
                bulbStacks.forEach((s) -> pushToMap(result, s));
            }
        }
        List<FluidStack> listResult = new ArrayList<>();
        result.forEach((k, v) -> listResult.add(v));
        return listResult;
    }

    private boolean isGauntletEmpty(ClientPlayerEntity player) {
        ItemStack stack = player.getHeldItem(Hand.MAIN_HAND);
        if (stack.getItem() instanceof Gauntlet) {
            LazyOptional<IFluidHandlerItem> lazyCap = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
            boolean[] isEmpty = {false};
            lazyCap.ifPresent(c -> isEmpty[0] = c.getFluidInTank(0).isEmpty());
            return isEmpty[0];
        }
        return false;
    }

    private void pushToMap(Map<Fluid, FluidStack> result, FluidStack s) {
        if (s.isEmpty() || s.getAmount() == 0) {
            return;
        }
        if (result.containsKey(s.getFluid())) {
            FluidStack newStack = result.get(s.getFluid()).copy();
            newStack.setAmount(newStack.getAmount() + s.getAmount());
            result.put(s.getFluid(), newStack);
        } else {
            result.put(s.getFluid(), s);
        }
    }

    @Override
    public void closeScreen() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (this.selectedItem != -1) {
            trySwitchingGooTypes(minecraft.player, target());
        }
        super.closeScreen();
    }

    private void trySwitchingGooTypes(ClientPlayerEntity player, FluidStack target) {
        Networking.sendToServer(new GooGauntletSwapPacket(target), player);
    }

    @Override
    public boolean mouseClicked(double p_mouseClicked_1_, double p_mouseClicked_3_, int p_mouseClicked_5_) {
        if (minecraft == null || minecraft.player == null) {
            return true;
        }

        if(this.selectedItem != -1){
            minecraft.player.closeScreen();
        }
        return true;
    }

    private void drawSlice(
            BufferBuilder buffer, float x, float y, float z, float radiusIn, float radiusOut, float startAngle, float endAngle, int r, int g, int b, int a) {
        float angle = endAngle - startAngle;
        int sections = Math.max(1, MathHelper.ceil(angle / PRECISION));

        startAngle = (float) Math.toRadians(startAngle);
        endAngle = (float) Math.toRadians(endAngle);
        angle = endAngle - startAngle;

        for (int i = 0; i < sections; i++)
        {
            float angle1 = startAngle + (i / (float) sections) * angle;
            float angle2 = startAngle + ((i + 1) / (float) sections) * angle;

            float pos1InX = x + radiusIn * (float) Math.cos(angle1);
            float pos1InY = y + radiusIn * (float) Math.sin(angle1);
            float pos1OutX = x + radiusOut * (float) Math.cos(angle1);
            float pos1OutY = y + radiusOut * (float) Math.sin(angle1);
            float pos2OutX = x + radiusOut * (float) Math.cos(angle2);
            float pos2OutY = y + radiusOut * (float) Math.sin(angle2);
            float pos2InX = x + radiusIn * (float) Math.cos(angle2);
            float pos2InY = y + radiusIn * (float) Math.sin(angle2);

            buffer.pos(pos1OutX, pos1OutY, z).color(r, g, b, a).endVertex();
            buffer.pos(pos1InX, pos1InY, z).color(r, g, b, a).endVertex();
            buffer.pos(pos2InX, pos2InY, z).color(r, g, b, a).endVertex();
            buffer.pos(pos2OutX, pos2OutY, z).color(r, g, b, a).endVertex();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

/*
Note: This code has been modified from David Quintana's solution.
Below is the required copyright notice.
Copyright (c) 2015, David Quintana <gigaherz@gmail.com>
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the author nor the
      names of the contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
