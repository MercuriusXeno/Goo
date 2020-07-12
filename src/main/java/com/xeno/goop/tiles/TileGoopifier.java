package com.xeno.goop.tiles;

import com.xeno.goop.setup.Registration;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileGoopifier extends TileEntity implements ITickableTileEntity {
    public TileGoopifier() {
        super(Registration.GOOPIFIER_TILE.get());
    }

    @Override
    public void tick() {

    }
}
