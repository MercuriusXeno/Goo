package com.xeno.goo.client;
import com.xeno.goo.client.render.blockitem.*;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;

import java.util.concurrent.Callable;

public class ISTERProvider
{
    public static Callable<ItemStackTileEntityRenderer> gooBulb() {
        return BulbItemRenderer::new;
    }

    public static Callable<ItemStackTileEntityRenderer> mixer() {
        return MixerItemRenderer::new;
    }

    public static Callable<ItemStackTileEntityRenderer> degrader() {
        return DegraderItemRenderer::new;
    }

    public static Callable<ItemStackTileEntityRenderer> trough() {
        return TroughItemRenderer::new;
    }

    public static Callable<ItemStackTileEntityRenderer> crucible() {
        return CrucibleItemRenderer::new;
    }

    public static Callable<ItemStackTileEntityRenderer> pad() {
        return PadItemRenderer::new;
    }

    public static Callable<ItemStackTileEntityRenderer> pump() { return PumpItemRenderer::new; }

    public static Callable<ItemStackTileEntityRenderer> solidifier() { return SolidifierItemRenderer::new; }
}
