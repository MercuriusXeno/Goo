package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.evaluations.GooEntry;
import com.xeno.goo.items.GooHolder;
import com.xeno.goo.network.Networking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeClientEvents
{
    @SubscribeEvent
    public static void tooltipEvent(ItemTooltipEvent e) {
        if (e.getItemStack().isEmpty()) {
            return;
        }
        if (!Screen.hasShiftDown()) {
            return;
        }
        String registryName = Objects.requireNonNull(e.getItemStack().getItem().getRegistryName()).toString();

        GooEntry mapping = GooMod.handler.get(registryName);
        if (mapping.isUnknown()) {
            return;
        }
        mapping.translateToTooltip(e.getToolTip());
    }
}
