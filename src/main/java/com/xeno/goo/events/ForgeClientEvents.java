package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.GooEntry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event){
        if (!event.getMap().getTextureLocation().equals(PlayerContainer.LOCATION_BLOCKS_TEXTURE)) {
            return;
        }
        registerMaskingSprites(event);
    }

    private static void registerMaskingSprites(TextureStitchEvent.Pre event)
    {
        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/crucible_fluid"));
        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/crucible_cover"));

        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/mobius_crucible_fluid"));
        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/mobius_crucible_cover"));

        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/gauntlet_fluid"));
        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/gauntlet_cover"));

        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/combo_gauntlet_fluid"));
        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/combo_gauntlet_cover"));
    }
}
