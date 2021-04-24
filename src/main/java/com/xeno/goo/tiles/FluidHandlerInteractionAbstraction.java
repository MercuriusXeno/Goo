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

public abstract class FluidHandlerInteractionAbstraction extends TileEntity {

    /**
     * Lambda to call when a lazy optional is invalidated.
     */
    protected final NonNullConsumer<LazyOptional<IFluidHandler>> directionalInvalidator =
            WeakConsumerWrapper.of(this, FluidHandlerInteractionAbstraction::clearCachedReference);

    protected Map<Direction, LazyOptional<IFluidHandler>> directionalHandlers = new HashMap<>();

	public FluidHandlerInteractionAbstraction(TileEntityType<?> tileEntityTypeIn) {

		super(tileEntityTypeIn);
	}

	public void clearCachedReference(Direction d) {

		directionalHandlers.remove(d);
	}

	public void clearCachedReference(LazyOptional<IFluidHandler> handler) {

		for (Direction d : Direction.values())
			if (directionalHandlers.containsKey(d) && directionalHandlers.get(d) == handler)
				directionalHandlers.remove(d);
	}

	protected LazyOptional<IFluidHandler> fluidHandlerInDirection(Direction d) {

		LazyOptional<IFluidHandler> ret = directionalHandlers.computeIfAbsent(d, k -> {

			LazyOptional<IFluidHandler> handler = FluidHandlerHelper.capabilityOfNeighbor(this, k);
			if (!handler.isPresent()) return null;

			handler.addListener(directionalInvalidator);
			return handler;
		});
		return ret == null ? LazyOptional.empty() : ret;
	}
}
