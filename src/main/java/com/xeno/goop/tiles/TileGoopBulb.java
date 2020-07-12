package com.xeno.goop.tiles;

import com.xeno.goop.setup.Registration;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileGoopBulb extends TileEntity implements ITickableTileEntity {
    public TileGoopBulb() {
        super(Registration.GOOP_BULB_TILE.get());
    }

    @Override
    public void tick() {

    }
}
