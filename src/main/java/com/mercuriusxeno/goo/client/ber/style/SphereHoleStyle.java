package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.ability.NetherSphereVisual;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Sphere style adapter - thin delegator that forwards every call to the
 * existing {@link NetherSphereVisual} static methods. This class adds
 * no behavior; its only job is to fit the canonical implementation into
 * the {@link NetherHoleStyle} seam so the BER can dispatch through
 * {@link NetherHoleStyles#ACTIVE} uniformly.
 *
 * <p>NetherBlackHoleRender itself is intentionally untouched on this
 * experiment branch - flipping back to sphere should land at pixel
 * parity with the shipped build.
 */
final class SphereHoleStyle implements NetherHoleStyle {

    @Override
    public void extract(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
        NetherSphereVisual.extract(be, state);
    }

    @Override
    public void submit(ChainMarkerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector) {
        NetherSphereVisual.submit(state, poseStack, nodeCollector);
    }
}
