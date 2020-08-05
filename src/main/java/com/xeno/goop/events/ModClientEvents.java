package com.xeno.goop.events;

import com.xeno.goop.GoopMod;
import com.xeno.goop.client.render.GoopBulbTileRenderer;
import com.xeno.goop.client.render.SolidifierTileRenderer;
import com.xeno.goop.setup.Registry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = GoopMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModClientEvents
{
}
