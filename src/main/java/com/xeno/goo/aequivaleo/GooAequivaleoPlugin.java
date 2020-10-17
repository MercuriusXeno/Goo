package com.xeno.goo.aequivaleo;

import com.ldtteam.aequivaleo.api.plugin.AequivaleoPlugin;
import com.ldtteam.aequivaleo.api.plugin.IAequivaleoPlugin;
import net.minecraft.world.server.ServerWorld;

@AequivaleoPlugin
public class GooAequivaleoPlugin implements IAequivaleoPlugin
{
    @Override
    public String getId()
    {
        return "Goo";
    }

    @Override
    public void onReloadStartedFor(final ServerWorld world)
    {
        Equivalencies.resetFurnaceProducts(world);
    }
}
