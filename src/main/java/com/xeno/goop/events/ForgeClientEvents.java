package com.xeno.goop.events;

import com.xeno.goop.GoopMod;
import com.xeno.goop.library.GoopMapping;
import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;

@Mod.EventBusSubscriber(modid = GoopMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

        GoopMapping mapping = GoopMod.mappingHandler.get(registryName);
        if (mapping.isUnknown()) {
            return;
        }
        mapping.translateToTooltip(e.getToolTip());
    }
}
