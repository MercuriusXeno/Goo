package com.xeno.goo.items;

import com.xeno.goo.GooMod;

public class Basin extends BasinAbstraction
{
    public Basin()
    {
        super(GooMod.config.basinCapacity());
    }
}