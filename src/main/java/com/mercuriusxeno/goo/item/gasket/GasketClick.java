package com.mercuriusxeno.goo.item.gasket;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Identifies a specific gasket face the player targeted.
 *
 * @param role      the gasket role (intake or output)
 * @param gasketId  the UUID of the gasket
 * @param pos       the block position of the gasket holder
 * @param slot      the slot index within the holder
 * @param faceLabel the face label, or null for single-face gaskets
 */
public record GasketClick(GasketRole role, UUID gasketId, BlockPos pos, int slot, @Nullable String faceLabel) {
}
