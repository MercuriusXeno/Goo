package com.xeno.goop.tiles;

import com.xeno.goop.setup.Registry;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;

public class SolidifierTile extends TileEntity implements ITickableTileEntity {
    public SolidifierTile() {
        super(Registry.SOLIDIFIER_TILE.get());
    }

    @Override
    public void tick() {

    }
}
