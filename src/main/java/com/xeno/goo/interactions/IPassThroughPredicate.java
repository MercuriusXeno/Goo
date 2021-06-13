package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockRayTraceResult;

public interface IPassThroughPredicate {
    Boolean blobPassThroughPredicate(BlockState state, GooBlob gooBlob);
}
