package com.xeno.goop.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goop.setup.Registration;
import com.xeno.goop.tiles.GoopBulbTile;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class GoopBulbItemRenderer extends ItemStackTileEntityRenderer {
    private GoopBulbTileRenderer proxyRenderer = createProxyRenderer();

    private GoopBulbTileRenderer createProxyRenderer() {
        return new GoopBulbTileRenderer();
    }

    private LazyOptional<GoopBulbTileRenderer> lazyProxy = LazyOptional.of(() -> proxyRenderer);
    @Override
    public void render(ItemStack itemStack, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn) {
        if (!itemStack.hasTag()) {
            return;
        }
        GoopBulbTile te = (GoopBulbTile)GoopBulbTile.create(itemStack.getTag().getCompound("BlockEntityTag"));
        proxyRenderer.render(te, 1f, matrixStack, buffer, combinedLightIn, combinedOverlayIn);
    }
}
