package com.xeno.goo.client.render.block;

import net.minecraft.state.EnumProperty;
import net.minecraft.util.IStringSerializable;

import java.util.Locale;

public class DynamicRenderMode {
    public static final EnumProperty<DynamicRenderTypes> RENDER = EnumProperty.create("render", DynamicRenderTypes.class);

    public enum DynamicRenderTypes implements IStringSerializable {
        STATIC,
        DYNAMIC,
        ITEM;

        @Override
        public String getString() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}