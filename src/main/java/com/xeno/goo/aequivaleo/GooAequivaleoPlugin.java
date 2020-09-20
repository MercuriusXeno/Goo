package com.xeno.goo.aequivaleo;

import com.google.common.collect.Sets;
import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.information.locked.ILockedCompoundInformationRegistry;
import com.ldtteam.aequivaleo.api.plugin.AequivaleoPlugin;
import com.ldtteam.aequivaleo.api.plugin.IAequivaleoPlugin;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.Items;
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
