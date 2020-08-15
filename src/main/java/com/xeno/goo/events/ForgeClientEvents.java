package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.evaluations.GooEntry;
import com.xeno.goo.items.GooHolder;
import com.xeno.goo.network.MouseRightHeldPacket;
import com.xeno.goo.network.Networking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

        GooEntry mapping = GooMod.handler.get(registryName);
        if (mapping.isUnknown()) {
            return;
        }
        mapping.translateToTooltip(e.getToolTip());
    }


    private static final int MOUSE1 = GLFW.GLFW_MOUSE_BUTTON_1;
    private static final int MOUSE2 = GLFW.GLFW_MOUSE_BUTTON_2;
    private static final int PRESS = GLFW.GLFW_PRESS;
    private static final int RELEASE = GLFW.GLFW_RELEASE;
    private static Map<Integer, Integer> lastActions = new HashMap<>();
    @SubscribeEvent
    public static void onEvent(InputEvent.MouseInputEvent event) {
        if (Minecraft.getInstance().world == null || Minecraft.getInstance().player == null) {
            return;
        }
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player.getHeldItemMainhand().isEmpty() || player.getHeldItemMainhand().getItem() instanceof GooHolder) {
            return;
        }
        int button = event.getButton();
        int action = event.getAction();
        if (button == MOUSE1) {
            if (action == RELEASE) {
            } else {
                if (getLast(button) == PRESS) {
                }
            }
        } else if (button == MOUSE2) {
            if (action == RELEASE) {
                Networking.sendToServer(new MouseRightHeldPacket(false), player);
            } else {
                if (getLast(button) == PRESS) {
                    Networking.sendToServer(new MouseRightHeldPacket(true), player);
                }
            }
        }
        lastActions.put(button, action);
    }

    private static int getLast(int button)
    {
        if (!lastActions.containsKey(button)) {
            return 0;
        }
        return lastActions.get(button);
    }
}
