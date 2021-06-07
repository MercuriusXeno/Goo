package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.fertilize.FertilizeImpl;
import com.xeno.goo.shrink.ShrinkImpl;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModCommonEvents {
	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		ShrinkImpl.init();
		FertilizeImpl.init();
	}
}
