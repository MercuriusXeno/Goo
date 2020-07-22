package com.xeno.goop.tiles;

import com.xeno.goop.setup.Registry;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;

public class GoopifierTile extends TileEntity implements ITickableTileEntity {
    public GoopifierTile() {
        super(Registry.GOOPIFIER_TILE.get());
    }

    @Override
    public void tick() {

    }
}
