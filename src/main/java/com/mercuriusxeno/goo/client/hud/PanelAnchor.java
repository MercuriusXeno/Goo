package com.mercuriusxeno.goo.client.hud;

import net.minecraft.core.Direction;

/**
 * Camera-relative positioning data for an in-world HUD panel.
 *
 * @param cx tracked center X in world space
 * @param lift vertical offset above the block
 * @param cz tracked center Z in world space
 * @param face the block face the panel is anchored to
 * @param blockAbove whether a solid block exists above the anchor
 * @param pitch the camera pitch for billboard orientation
 */
public record PanelAnchor(double cx, double lift, double cz, Direction face, boolean blockAbove, float pitch) {}
