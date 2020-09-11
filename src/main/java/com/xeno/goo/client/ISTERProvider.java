package com.xeno.goo.client;
import com.xeno.goo.client.render.CrucibleItemRenderer;
import com.xeno.goo.client.render.GooBulbItemRenderer;
import com.xeno.goo.client.render.MixerItemRenderer;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;

import java.util.concurrent.Callable;

public class ISTERProvider
{
    public static Callable<ItemStackTileEntityRenderer> gooBulb() {
        return GooBulbItemRenderer::new;
    }

    public static Callable<ItemStackTileEntityRenderer> mixer() {
        return MixerItemRenderer::new;
    }

    public static Callable<ItemStackTileEntityRenderer> crucible() {
        return CrucibleItemRenderer::new;
    }
}
