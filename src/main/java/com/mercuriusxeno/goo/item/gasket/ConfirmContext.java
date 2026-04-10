package com.mercuriusxeno.goo.item.gasket;

import com.mercuriusxeno.goo.item.ConfirmAction;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Bundles the pending confirmation state for tuner link resolution.
 *
 * @param action the confirmation action type
 * @param target the block position awaiting confirmation, or null if none
 * @param slot the slot index associated with the confirmation
 */
public record ConfirmContext(ConfirmAction action, @Nullable BlockPos target, int slot) {}
