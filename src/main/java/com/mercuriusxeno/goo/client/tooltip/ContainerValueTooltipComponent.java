package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import java.util.List;

/**
 * Two-column tooltip for fluid containers: left shows fluid contents,
 * right shows the container's own goo decomposition value, separated
 * by a "+" sign.
 *
 * @param contents  the fluid contents (left column)
 * @param container the container's goo value (right column)
 */
public record ContainerValueTooltipComponent(
        List<Entry> contents,
        List<Entry> container
) implements TooltipComponent {

    /**
     * A single goo type + amount entry for one column row.
     *
     * @param type   the goo type
     * @param amount the volume in microblobs
     */
    public record Entry(GooType type, int amount) {
    }
}
