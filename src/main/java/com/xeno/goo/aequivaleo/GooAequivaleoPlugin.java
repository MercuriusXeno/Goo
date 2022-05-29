package com.xeno.goo.aequivaleo;

import com.ldtteam.aequivaleo.api.plugin.AequivaleoPlugin;
import com.ldtteam.aequivaleo.api.plugin.IAequivaleoPlugin;
import com.xeno.goo.GooMod;
import com.xeno.goo.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;

@AequivaleoPlugin
public class GooAequivaleoPlugin implements IAequivaleoPlugin {
	public static ResourceLocation location(String name) {
		return new ResourceLocation("aequivaleo", name);
	}

	public static final String ID = GooMod.location("goo_types").toString();

	@Override
	public void onConstruction()
	{
		ModList.get().getModContainerById(GooMod.MOD_ID).ifPresent(mod -> {
			Registry.TYPES.register(((FMLModContainer) mod).getEventBus());
			Registry.TYPE_GROUPS.register(((FMLModContainer) mod).getEventBus());
		});
	}

	@Override
	public String getId() {
		return "Goo";
	}
}
