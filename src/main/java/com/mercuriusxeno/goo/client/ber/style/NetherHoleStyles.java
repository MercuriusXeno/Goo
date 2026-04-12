package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.ber.NetherBlackHoleRenderer;

/**
 * Registry of available {@link NetherHoleStyle} implementations and the
 * one-field swap point for selecting which is currently active on the
 * client. Flip {@link #ACTIVE} to the instance you want and rerun; both
 * implementations live side by side, neither is deleted, and the BER
 * dispatch reads from here so no other code has to change.
 *
 * <p>This is a dev-side experimentation seam - there is no config, no
 * keybind, no runtime UI. If a style is worth promoting out of the
 * experiment branch, wire it behind a proper config entry then.
 */
public final class NetherHoleStyles {

    /** Canonical sphere implementation - the shipped black-hole visual
     * with a UV sphere occluder, ray-sphere fresnel corona, and a flat
     * accretion disc. Delegates entirely to the existing
     * {@link NetherBlackHoleRenderer}. */
    public static final NetherHoleStyle SPHERE = new SphereHoleStyle();

    /** Cube experiment - a cube occluder with a cube-edge glow shader
     * standing in for the corona, reusing the existing flat accretion
     * disc. See {@link CubeHoleStyle} for the geometry and shader. */
    public static final NetherHoleStyle CUBE = new CubeHoleStyle();

    /** The style currently in use. Flip this field and rerun the client
     * to switch implementations. Defaults to {@link #CUBE} on this
     * experiment branch so "just run the client" shows the cube; flip
     * to {@link #SPHERE} for an A/B comparison. */
    public static final NetherHoleStyle ACTIVE = SPHERE;

    /** Kill switch for the screen-space {@code NetherLensEffect}
     * post-process. When {@code false}, {@code applyPerFrame}
     * short-circuits and deactivates the post effect; both styles
     * continue to call {@code markHoleActive} (cheap static-field
     * writes) but nothing drains the state into a uniform upload, so
     * the lens never runs. Flip to {@code false} to kill the lens
     * for debugging without touching the markers. The extract and
     * apply methods in
     * {@link com.mercuriusxeno.goo.client.lens.NetherLensEffect} are
     * intentionally left in place so toggling this flag is a clean
     * on/off. */
    public static final boolean LENS_ENABLED = true;

    private NetherHoleStyles() {}
}
