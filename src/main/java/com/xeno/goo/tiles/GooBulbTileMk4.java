package com.xeno.goo.tiles;

import com.xeno.goo.setup.Registry;

public class GooBulbTileMk4 extends GooBulbTileAbstraction
{
    public GooBulbTileMk4() {
        super(Registry.GOO_BULB_TILE_MK4.get());
    }

    public int getStorageMultiplier()
    {
        return 512;
    }
}
