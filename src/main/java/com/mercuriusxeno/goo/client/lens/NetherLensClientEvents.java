package com.mercuriusxeno.goo.client.lens;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-side event handler that drives the nether black-hole lens
 * post-process. Hooks {@link RenderLevelStageEvent.AfterLevel}, which
 * fires inside {@code GameRenderer.renderLevel} after
 * {@code LevelRenderer.renderLevel} returns (so all BERs have already
 * run their extract paths) and before the post-effect chain executes.
 * That timing is the only window where:
 * <ul>
 *   <li>The BER has had a chance to call
 *       {@link NetherLensEffect#markHoleActive} for the frame.</li>
 *   <li>The projection/model-view matrices are still the ones used
 *       for level rendering, so projecting the hole's world position
 *       to a UV matches what the main framebuffer sees.</li>
 *   <li>The post-effect pass has not yet consumed the uniform buffer,
 *       so a fresh {@code writeToBuffer} is picked up this same
 *       frame.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class NetherLensClientEvents {

    private NetherLensClientEvents() {}

    /** Drives the lens post-effect once per frame at the end of the
     * level pass.
     *
     * @param event the render-level stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        NetherLensEffect.applyPerFrame(Minecraft.getInstance());
    }
}
