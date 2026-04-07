package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Data model carrying a single goo type and its amount for tooltip rendering.
 * One component per goo line in the tooltip. Amount is in microblobs (long).
 *
 * @param type   the goo type for this tooltip line
 * @param amount the volume in microblobs
 */
public record GooValueTooltipComponent(GooType type, long amount) implements TooltipComponent {
}
