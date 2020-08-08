package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.GooEntry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

        GooEntry mapping = GooMod.mappingHandler.get(registryName);
        if (mapping.isUnknown()) {
            return;
        }
        mapping.translateToTooltip(e.getToolTip());
    }
}
