package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import java.util.List;

/**
 * Rendering utilities for the ability radial menu. Abilities are
 * arranged as icon+label slots in a ring around the center.
 * Icons resolve by convention (textures/goo/ability/{path}.png)
 * with an optional per-ability override from the JSON spec.
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
    /** Divisor for computing wedge midpoint angle. */
    private static final double ANGLE_HALF = 2.0;
    /** Label radius as fraction between inner and outer. */
    private static final double LABEL_FRAC = 0.65;
    /** Icon size in pixels. */
    private static final int ICON_SIZE = 11;
    /** Half-icon offset for centering. */
    private static final int ICON_OFFSET = 5;
    /** Convention path prefix for ability icons. */
    private static final String ABILITY_ICON_PREFIX = "textures/goo/ability/";
    /** Convention path suffix for ability icons. */
    private static final String ABILITY_ICON_SUFFIX = ".png";
    /** Vertical gap between icon and label text. */
    private static final int LABEL_GAP = 1;

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

    /** Color computation for synced client abilities.
     *
     * @param colors       the output color array
     * @param abilities    the synced ability list
     * @param type         the goo type for base color
     * @param hoveredIndex the currently hovered wedge
     */
    static void computeWedgeColorsFromSync(int[] colors, List<ClientAbility> abilities,
            GooType type, int hoveredIndex) {
        int baseColor = GooColors.wheel(type);
        for (int i = 0; i < abilities.size(); i++) {
            int alpha = (i == hoveredIndex) ? HOVER_ALPHA : NORMAL_ALPHA;
            colors[i] = ARGB.color(alpha, baseColor);
        }
    }

    /** Renders icon slots and labels for synced client abilities.
     *
     * @param graphics     the GUI graphics extractor
     * @param centerX      the screen center x
     * @param centerY      the screen center y
     * @param colors       the per-wedge ARGB colors
     * @param hoveredIndex the hovered wedge index
     * @param abilities    the synced ability list
     * @param font         the font renderer
     */
    static void renderSlotsFromSync(GuiGraphicsExtractor graphics, int centerX, int centerY,
            int[] colors, int hoveredIndex, List<ClientAbility> abilities, Font font) {
        if (abilities.isEmpty()) { return; }
        double wedgeArc = TWO_PI / abilities.size();
        double slotRadius = INNER_RADIUS + (OUTER_RADIUS - INNER_RADIUS) * LABEL_FRAC;
        for (int i = 0; i < abilities.size(); i++) {
            double midAngle = i * wedgeArc + wedgeArc / ANGLE_HALF + ANGLE_OFFSET;
            int sx = centerX + (int) (Math.cos(midAngle) * slotRadius);
            int sy = centerY + (int) (Math.sin(midAngle) * slotRadius);
            renderSlotIcon(graphics, abilities.get(i), sx, sy, colors[i]);
            int textColor = (i == hoveredIndex) ? HOVER_TEXT_COLOR : TEXT_COLOR;
            Component label = Component.translatable(abilities.get(i).displayName());
            graphics.centeredText(font, label, sx, sy + ICON_OFFSET + LABEL_GAP, textColor);
        }
    }

    /** Renders a single ability icon at the slot center, tinted with the wedge color.
     *
     * @param graphics the GUI graphics extractor
     * @param ability  the client ability descriptor
     * @param cx       the slot center x
     * @param cy       the slot center y
     * @param color    the ARGB tint color
     */
    private static void renderSlotIcon(GuiGraphicsExtractor graphics, ClientAbility ability,
            int cx, int cy, int color) {
        Identifier tex = resolveAbilityIcon(ability);
        graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                cx - ICON_OFFSET, cy - ICON_OFFSET,
                0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, color);
    }

    /** Resolves the icon texture for an ability. Uses the explicit override
     * if provided, otherwise falls back to convention path.
     *
     * @param ability the client ability descriptor
     * @return the icon texture identifier
     */
    static Identifier resolveAbilityIcon(ClientAbility ability) {
        if (!ability.icon().isEmpty()) {
            return Identifier.fromNamespaceAndPath(Goo.MODID, ability.icon());
        }
        String path = ability.id().getPath();
        return Identifier.fromNamespaceAndPath(Goo.MODID,
                ABILITY_ICON_PREFIX + path + ABILITY_ICON_SUFFIX);
    }
}
