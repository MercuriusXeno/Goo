package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.Goo;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Manages a scratch GPU texture that holds a copy of the main framebuffer
 * color for crystal shard refraction sampling. The copy is taken once per
 * frame before crystal geometry renders, so shards can read the scene
 * behind them via a sampler without read-write hazards.
 */
public final class CrystalSceneCopy extends AbstractTexture {

    /** Texture manager identifier for the scene copy. */
    public static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(
            Goo.MODID, "crystal_scene_copy");

    /** Usage flags: copy destination + texture sampling. */
    private static final int USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
    /** Debug label for the GPU texture. */
    private static final String TEXTURE_LABEL = "Crystal scene copy";

    private int lastWidth;
    private int lastHeight;

    /**
     * Copies the current main framebuffer color to this texture. Handles
     * resize if the window dimensions changed since the last copy.
     */
    public void copyFromMainTarget() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        ensureSize(w, h);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(main.getColorTexture(), texture, 0, 0, 0, 0, 0, w, h);
    }

    /** Creates or recreates the scratch texture if the size changed.
     *
     * @param w the required width
     * @param h the required height
     */
    private void ensureSize(int w, int h) {
        if (texture != null && lastWidth == w && lastHeight == h) { return; }
        close();
        texture = RenderSystem.getDevice().createTexture(
                () -> TEXTURE_LABEL, USAGE, TextureFormat.RGBA8, w, h, 1, 1);
        textureView = RenderSystem.getDevice().createTextureView(texture);
        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        lastWidth = w;
        lastHeight = h;
    }

    /**
     * Registers this texture with the texture manager so RenderSetup can
     * resolve it by identifier.
     */
    public static void register() {
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, new CrystalSceneCopy());
    }

    /**
     * Returns the registered instance from the texture manager.
     *
     * @return the scene copy texture, or null if not registered
     */
    public static CrystalSceneCopy getInstance() {
        return (CrystalSceneCopy) Minecraft.getInstance().getTextureManager().getTexture(TEXTURE_ID);
    }
}
