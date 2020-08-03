package com.xeno.goop.setup;

import com.xeno.goop.GoopMod;
import com.xeno.goop.client.render.GoopBulbTileRenderer;
import com.xeno.goop.library.GoopMapping;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;
import java.util.Objects;

@Mod.EventBusSubscriber(modid = GoopMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    public static void init(final FMLClientSetupEvent event)
    {
        setGoopBulbTransparencyRenderLayer();
        GoopBulbTileRenderer.register();
    }

    private static void setGoopBulbTransparencyRenderLayer() {
        RenderTypeLookup.setRenderLayer(Registry.GOOP_BULB.get(), RenderType.getCutout());
    }
}
