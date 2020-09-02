package com.xeno.goo.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.fonts.Font;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.gui.ForgeIngameGui;
import net.minecraftforge.common.util.LazyOptional;

import java.util.function.Function;

public class GooGuiFactory extends ForgeIngameGui
{
    public GooGuiFactory(Minecraft mc)
    {
        super(mc);
    }
}