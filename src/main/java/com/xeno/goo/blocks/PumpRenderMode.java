package com.xeno.goo.blocks;

import net.minecraft.util.IStringSerializable;

public enum PumpRenderMode implements IStringSerializable {
    STATIC,
    DYNAMIC;

    @Override
    public String getString() {
        return this.name().toLowerCase();
    }
}