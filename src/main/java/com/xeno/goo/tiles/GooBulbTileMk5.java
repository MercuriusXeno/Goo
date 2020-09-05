package com.xeno.goo.tiles;

import com.xeno.goo.setup.Registry;

public class GooBulbTileMk5 extends GooBulbTileAbstraction
{
    public GooBulbTileMk5() {
        super(Registry.GOO_BULB_TILE_MK5.get());
    }

    public int storageMultiplier()
    {
        return 4096;
    }
}
