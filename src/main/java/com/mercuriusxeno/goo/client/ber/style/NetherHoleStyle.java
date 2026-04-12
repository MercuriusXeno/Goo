package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.ber.ChainMarkerBlockEntityRenderer;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Swap-in strategy for the nether black-hole visual. Each implementation
 * is free to own its geometry, shaders, lens-markup behavior, and any
 * style-specific per-frame state it stashes on {@link ChainMarkerRenderState}.
 * {@link ChainMarkerBlockEntityRenderer} dispatches extract
 * and submit through {@link NetherHoleStyles#active()} so flipping the
 * active style is a one-field change with no touching of either
 * implementation.
 *
 * <p>Conventions: implementations must be stateless except for cached
 * meshes / constants - one instance is reused for every active nether
 * hole on the client. Per-hole state lives on the render state passed
 * to extract and submit.
 */
public interface NetherHoleStyle {

    /** Populates {@code state} with whatever the submit pass needs.
     * Called once per BER per frame during render state extraction.
     * Implementations should no-op (or clear {@code state.netherActive})
     * when the BE has no active nether behavior, so the BER's dispatch
     * check in submit can short-circuit cleanly.
     *
     * @param be    the chain marker block entity
     * @param state the render state to populate
     */
    void extract(ChainMarkerBlockEntity be, ChainMarkerRenderState state);

    /** Emits the style's geometry passes to the node collector. Called
     * once per BER per frame during submit, only when
     * {@code state.netherActive} is true.
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     */
    void submit(ChainMarkerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector);
}
