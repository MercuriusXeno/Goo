package com.xeno.goo.events;

import com.ldtteam.aequivaleo.api.IAequivaleoAPI;
import com.ldtteam.aequivaleo.api.compound.ICompoundInstance;
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
        String registryName = Objects.requireNonNull(e.getItemStack().getItem().getRegistryName()).toString();

        if (e.getPlayer() == null) {
            return;
        }
        World world = e.getPlayer().getEntityWorld();
        Set<ICompoundInstance> compounds = Equivalencies.cache(world).getFor(new ItemStack(e.getItemStack().getItem(), 1));
        GooEntry mapping = new GooEntry(compounds);
        mapping.translateToTooltip(e.getToolTip());
    }
}
