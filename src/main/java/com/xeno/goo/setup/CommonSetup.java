package com.xeno.goo.setup;

import com.xeno.goo.network.Networking;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public class CommonSetup
{
    public static void init(final FMLCommonSetupEvent event)
    {
        registerEntityAttributes(event);
        Networking.registerNetworkMessages();
    }

    private static void registerEntityAttributes(FMLCommonSetupEvent event)
    {
    }
}
