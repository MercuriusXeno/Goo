package com.mercuriusxeno.goo.client.hud;

/**
 * Axis-aligned rectangle for HUD panel layout (position + size).
 *
 * @param x left edge
 * @param y top edge
 * @param w width
 * @param h height
 */
public record PanelRect(float x, float y, float w, float h) {}
