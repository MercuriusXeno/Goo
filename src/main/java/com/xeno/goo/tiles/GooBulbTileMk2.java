package com.xeno.goo.tiles;

import com.xeno.goo.setup.Registry;

public class GooBulbTileMk2 extends GooBulbTileAbstraction
{
    public GooBulbTileMk2() {
        super(Registry.GOO_BULB_TILE_MK2.get());
    }

    public int getStorageMultiplier()
    {
        return 8;
    }
}
