package com.xeno.goo.client.render.block;

import net.minecraft.state.EnumProperty;
import net.minecraft.util.IStringSerializable;

import java.util.Locale;

public class HatchOpeningState {
    public static final EnumProperty<HatchOpeningStates> OPENING_STATE = EnumProperty.create("hatch_opening", HatchOpeningStates.class);

    public enum HatchOpeningStates implements IStringSerializable {
        OPEN,
        WANING,
        WAXING,
        CLOSED;

        @Override
        public String getString() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}