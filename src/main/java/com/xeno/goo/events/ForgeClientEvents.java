package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeClientEvents
{
    private static final String EVENT_PHASE_FOR_OVERLAY_INJECTION = "ALL";

    @SubscribeEvent
    public static void onDrawTooltip(ItemTooltipEvent event) {
        TargetingHandler.onDraw(event);
    }

    @SubscribeEvent
    public static void onPostTextTooltip(RenderTooltipEvent.PostText event) {
        TargetingHandler.tryDraw(event);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        TargetingHandler.clearStacks();
    }

    @SubscribeEvent
    public static void onGameOverlay(RenderGameOverlayEvent.Post event) {
        if (!event.getType().name().equals(EVENT_PHASE_FOR_OVERLAY_INJECTION)) {
            return;
        }
        TargetingHandler.onGameOverlay(event);
    }

    public static final Supplier<KeyBinding> USE_ITEM_BINDING = () -> Minecraft.getInstance().gameSettings.keyBindUseItem;
    @SubscribeEvent
    public static void onInput(InputEvent event) {
        if (event instanceof InputEvent.MouseInputEvent) {
            onMouseInput((InputEvent.MouseInputEvent) event);
        }
        if (event instanceof InputEvent.KeyInputEvent) {
            onKeyInput((InputEvent.KeyInputEvent) event);
        }
    }

    private static void onKeyInput(InputEvent.KeyInputEvent event) {
        if (event.getKey() == USE_ITEM_BINDING.get().getKey().getKeyCode()) {
            InputHandler.handleRadialInvocation(event.getAction());
        }

    }

    private static void onMouseInput(InputEvent.MouseInputEvent event) {
        if (event.getButton() == USE_ITEM_BINDING.get().getKey().getKeyCode()) {
            InputHandler.handleRadialInvocation(event.getAction());
        }
    }
}
