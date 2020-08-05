package com.xeno.goop.setup;

import com.xeno.goop.network.Networking;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public class CommonSetup
{
    public static void init(final FMLCommonSetupEvent event)
    {
        Networking.registerNetworkMessages();
    }
}
