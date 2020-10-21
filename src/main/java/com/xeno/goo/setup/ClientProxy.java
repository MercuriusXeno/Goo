package com.xeno.goo.setup;

import com.xeno.goo.client.gui.GooRadial;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;

public class ClientProxy extends CommonProxy {
    @Override
    public void openRadialMenu(LivingEntity player) {
        Minecraft.getInstance().displayGuiScreen(new GooRadial(player.getActiveHand()));
    }
}
