package com.xeno.goo.events;

import com.ldtteam.aequivaleo.api.IAequivaleoAPI;
import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.results.IResultsInformationCache;
import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;
import java.util.Set;

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

        if (e.getPlayer() == null) {
            return;
        }
        GooEntry mapping = Equivalencies.getEntry(e.getPlayer().getEntityWorld(), e.getItemStack().getItem());
        mapping.translateToTooltip(e.getToolTip());
    }
}
