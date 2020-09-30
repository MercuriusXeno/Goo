package com.xeno.goo.interactions;

import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.IFluidHandler;

public interface IGooInteraction
{
    boolean resolve(InteractionContext context);
}
