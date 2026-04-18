package com.mercuriusxeno.goo.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.material.Fluid;

/**
 * Data model for a vanilla fluid tooltip line: bucket icon + mB amount.
 *
 * @param fluid the vanilla fluid (water, lava, etc.)
 * @param amount the volume in microblobs
 */
public record VanillaFluidTooltipComponent(Fluid fluid, int amount) implements TooltipComponent {
}
