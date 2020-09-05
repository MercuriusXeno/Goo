package com.xeno.goo.tiles;

import com.xeno.goo.setup.Registry;

public class GooBulbTileMk3 extends GooBulbTileAbstraction
{
    public GooBulbTileMk3() {
        super(Registry.GOO_BULB_TILE_MK3.get());
    }

    public int storageMultiplier()
    {
        return 64;
    }
}
