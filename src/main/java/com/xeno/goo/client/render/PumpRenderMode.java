package com.xeno.goo.client.render;

import net.minecraft.util.IStringSerializable;

public enum PumpRenderMode implements IStringSerializable {
    STATIC,
    DYNAMIC;

    @Override
    public String getString() {
        return this.name().toLowerCase();
    }
}