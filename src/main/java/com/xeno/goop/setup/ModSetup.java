package com.xeno.goop.setup;

import com.xeno.goop.GoopMod;
import com.xeno.goop.commands.ModCommands;
import com.xeno.goop.network.Networking;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

@Mod.EventBusSubscriber(modid = GoopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModSetup {
    public static final ItemGroup ITEM_GROUP = new ItemGroup("goop") {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(Registration.SOLIDIFIER.get());
        }
    };

    public static void init(final FMLCommonSetupEvent event) {
        Networking.registerMessages();
    }

    @SubscribeEvent
    public static void serverLoad(FMLServerStartingEvent event) {
        ModCommands.register(event.getCommandDispatcher());
    }
}
