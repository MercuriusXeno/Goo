package com.xeno.goop.tiles;

import com.xeno.goop.setup.Registration;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileSolidifier extends TileEntity implements ITickableTileEntity {
    public TileSolidifier() {
        super(Registration.SOLIDIFIER_TILE.get());
    }

    @Override
    public void tick() {

    }
}
