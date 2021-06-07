package com.xeno.goo.fertilize;

import com.xeno.goo.shrink.api.IShrinkProvider;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;

public class FertilizeCapability {
	@CapabilityInject(IFertilizeProvider.class)
	public static Capability<IFertilizeProvider> FERTILIZE_CAPABILITY = null;
}
