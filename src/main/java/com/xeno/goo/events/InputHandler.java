package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.client.gui.GooRadial;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.annotation.Target;

public class InputHandler {
    private static int radialTicksHeld = 0;
    private static boolean radialHeld = false;
    public static final Minecraft instance = Minecraft.getInstance();

    public static void handleRadialInvocation(int action) {
        if (instance.world == null) {
            return;
        }
        if (instance.player == null) {
            return;
        }

        // experimental
        if (instance.currentScreen instanceof GooRadial) {
            instance.currentScreen.closeScreen();
            return;
        }

        if (instance.currentScreen != null) {
            return;
        }

        ItemStack mainHand = instance.player.getHeldItem(Hand.MAIN_HAND);
        if (!(mainHand.getItem() instanceof Gauntlet)) {
            radialHeld = false;
            return;
        }

        // press or held
        if (action == GLFW.GLFW_PRESS) {
            radialHeld = true;
            return;
        }

        if (action == GLFW.GLFW_RELEASE && radialHeld) {
            radialHeld = false;
            if (radialTicksHeld < GooMod.config.radialMenuThreshold()) {
                tryUsingGauntlet(instance.player);
            }
        }
    }

    private static void tryOpeningGauntletRadial(ClientPlayerEntity player, ItemStack mainHand) {
        // open the radial locally
        instance.displayGuiScreen(new GooRadial(ForgeClientEvents.USE_ITEM_BINDING.get()));
    }

    private static void tryUsingGauntlet(ClientPlayerEntity player) {
        if (player.isSwingInProgress) {
            return;
        }
        if (TargetingHandler.lastTargetedEntity instanceof GooSplat && ((GooSplat) TargetingHandler.lastTargetedEntity).isAtRest()) {
                // refer to the targeting handler to figure out if we are looking at a goo entity
                Networking.sendToServer(new GooGrabPacket(TargetingHandler.lastTargetedEntity), player);
        } else if (TargetingHandler.lastTargetedBlock != null) {
            if (TargetingHandler.lastHitIsGooContainer) {
                // refer to the targeting handler to figure out if we are looking at a goo container
                Networking.sendToServer(new GooCollectPacket(TargetingHandler.lastTargetedBlock, TargetingHandler.lastHitVector, TargetingHandler.lastHitSide), player);
            } else {
                // try placing a splat at the block if it's a valid location. Let the server handle the check.
                Networking.sendToServer(new GooPlaceSplatPacket(TargetingHandler.lastTargetedBlock, TargetingHandler.lastHitVector, TargetingHandler.lastHitSide), player);
            }
        } else {
            // packet to server to request a throw event in lieu of grabbing anything
            Networking.sendToServer(new GooLobPacket(), player);
        }
    }

    public static void handleEventTicking(TickEvent.ClientTickEvent event) {
        if (instance.world == null) {
            return;
        }

        if (instance.player == null) {
            return;
        }

        if (radialHeld) {
            radialTicksHeld++;
        } else {
            radialTicksHeld = 0;
        }

        ItemStack mainHand = instance.player.getHeldItem(Hand.MAIN_HAND);

        if (radialTicksHeld >= GooMod.config.radialMenuThreshold()) {
            tryOpeningGauntletRadial(instance.player, mainHand);
            radialHeld = false;
        }
    }
}
