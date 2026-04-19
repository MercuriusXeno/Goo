package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import java.util.List;

/**
 * Rendering utilities for the ability radial menu. Uses a simpler
 * layout than the type radial: abilities are arranged as labeled
 * slots in a ring around the center.
 */
final class AbilityRadialRenderer {

    private static final int INNER_RADIUS = 30;
    private static final int OUTER_RADIUS = 100;
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final int NO_SELECTION = -1;
    private static final int NORMAL_ALPHA = 0xAA;
    private static final int HOVER_ALPHA = 0xEE;
    private static final double ANGLE_OFFSET = -Math.PI / 2;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HOVER_TEXT_COLOR = 0xFFFFFF00;
    /** Size of each ability slot indicator in pixels. */
    private static final int SLOT_SIZE = 20;
    /** Half of slot size for centering. */
    private static final int SLOT_HALF = 10;
    /** Divisor for computing wedge midpoint angle. */
    private static final double ANGLE_HALF = 2.0;
    /** Label radius as fraction between inner and outer. */
    private static final double LABEL_FRAC = 0.65;

    private AbilityRadialRenderer() {}

    /**
     * Determines which wedge the mouse hovers for a variable wedge count.
     *
     * @param mouseX     the current mouse x
     * @param mouseY     the current mouse y
     * @param centerX    the screen center x
     * @param centerY    the screen center y
     * @param wedgeCount the number of ability wedges
     * @return the hovered wedge index, or -1
     */
    static int computeHoveredIndex(int mouseX, int mouseY, int centerX, int centerY,
            int wedgeCount) {
        if (wedgeCount <= 0) { return NO_SELECTION; }
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < INNER_RADIUS || dist > OUTER_RADIUS) { return NO_SELECTION; }
        double angle = Math.atan2(dy, dx) - ANGLE_OFFSET;
        if (angle < 0) { angle += TWO_PI; }
        double wedgeArc = TWO_PI / wedgeCount;
        return (int) (angle / wedgeArc) % wedgeCount;
    }

    /**
     * Fills the wedge color array.
     *
     * @param colors       the output color array
     * @param abilities    the ability list
     * @param type         the goo type for base color
     * @param hoveredIndex the currently hovered wedge
     */
    static void computeWedgeColors(int[] colors, List<AbilityDefinition> abilities,
            GooType type, int hoveredIndex) {
        int baseColor = GooColors.wheel(type);
        for (int i = 0; i < abilities.size(); i++) {
            int alpha = (i == hoveredIndex) ? HOVER_ALPHA : NORMAL_ALPHA;
            colors[i] = ARGB.color(alpha, baseColor);
        }
    }

    /**
     * Renders colored slot indicators and labels for each ability.
     *
     * @param graphics     the GUI graphics extractor
     * @param centerX      the screen center x
     * @param centerY      the screen center y
     * @param colors       the per-wedge ARGB colors
     * @param hoveredIndex the hovered wedge index
     * @param abilities    the ability list
     * @param font         the font renderer
     */
    static void renderSlots(GuiGraphicsExtractor graphics, int centerX, int centerY,
            int[] colors, int hoveredIndex, List<AbilityDefinition> abilities, Font font) {
        if (abilities.isEmpty()) { return; }
        double wedgeArc = TWO_PI / abilities.size();
        double slotRadius = INNER_RADIUS + (OUTER_RADIUS - INNER_RADIUS) * LABEL_FRAC;
        for (int i = 0; i < abilities.size(); i++) {
            double midAngle = i * wedgeArc + wedgeArc / ANGLE_HALF + ANGLE_OFFSET;
            int sx = centerX + (int) (Math.cos(midAngle) * slotRadius);
            int sy = centerY + (int) (Math.sin(midAngle) * slotRadius);
            graphics.fill(RenderPipelines.GUI, sx - SLOT_HALF, sy - SLOT_HALF,
                    sx + SLOT_HALF, sy + SLOT_HALF, colors[i]);
            int textColor = (i == hoveredIndex) ? HOVER_TEXT_COLOR : TEXT_COLOR;
            Component label = Component.translatable(abilities.get(i).displayName());
            graphics.centeredText(font, label, sx, sy + SLOT_HALF + 1, textColor);
        }
    }
}
