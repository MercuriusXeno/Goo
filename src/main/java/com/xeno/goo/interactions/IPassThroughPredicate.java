package com.xeno.goo.interactions;

import com.xeno.goo.entities.HexController;
import net.minecraft.block.BlockState;

public interface IPassThroughPredicate {
    Boolean blobPassThroughPredicate(BlockState state, HexController gooBlob);
}
