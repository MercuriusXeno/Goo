package com.xeno.goo.tiles;

import com.xeno.goo.library.WeakConsumerWrapper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.HashMap;
import java.util.Map;

public abstract class FluidHandlerInteractionAbstraction extends TileEntity
{
    public FluidHandlerInteractionAbstraction(TileEntityType<?> tileEntityTypeIn)
    {
        super(tileEntityTypeIn);
    }

    /** Lambda to call when a lazy optional is invalidated. Final variable to reduce memory usage */
    protected final NonNullConsumer<LazyOptional<IFluidHandler>> directionalInvalidator =
            new WeakConsumerWrapper<>(this, (te, handler) -> {
                for (Direction d : Direction.values())
                    if (te.directionalHandlers.containsKey(d) && te.directionalHandlers.get(d) == handler) {
                        te.clearCachedReference(d);
                    }
            });

    public void clearCachedReference(Direction d)
    {
        directionalHandlers.put(d, null);
    }

    Map<Direction, LazyOptional<IFluidHandler>> directionalHandlers = new HashMap<>();
    protected LazyOptional<IFluidHandler> fluidHandlerInDirection(Direction d)
    {
        if (directionalHandlers.containsKey(d) && directionalHandlers.get(d) != null) {
            return directionalHandlers.get(d);
        }

        LazyOptional<IFluidHandler> handler = FluidHandlerHelper.capabilityOfNeighbor(this, d);
        if (handler.isPresent()) {
            handler.addListener(directionalInvalidator);
            directionalHandlers.put(d, handler);
            return directionalHandlers.get(d);
        }

        directionalHandlers.put(d, LazyOptional.empty());
        return directionalHandlers.get(d);
    }
}
