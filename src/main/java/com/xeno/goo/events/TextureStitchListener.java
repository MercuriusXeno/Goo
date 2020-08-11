package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class TextureStitchListener
{
    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event)
    {
        if (!event.getMap().getTextureLocation().equals(PlayerContainer.LOCATION_BLOCKS_TEXTURE)) {
            return;
        }
        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/crucible_fluid"));
        // event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/mobius_crucible_fluid"));
        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/gauntlet_fluid"));
        // event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/combo_gauntlet_fluid"));
    }
}
