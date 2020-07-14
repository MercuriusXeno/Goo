package com.xeno.goop.tiles;

import com.xeno.goop.setup.Registration;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;

public class GoopifierTile extends TileEntity implements ITickableTileEntity {
    public GoopifierTile() {
        super(Registration.GOOPIFIER_TILE.get());
    }

    @Override
    public void tick() {

    }
}
