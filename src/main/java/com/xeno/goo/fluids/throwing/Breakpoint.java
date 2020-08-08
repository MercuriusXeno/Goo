package com.xeno.goo.fluids.throwing;

import com.xeno.goo.library.GooEntry;

public class Breakpoint
{
    public GooEntry goo;
    public ThrownEffect effect;

    public Breakpoint(GooEntry goo, ThrownEffect effect) {
        this.goo = goo;
        this.effect = effect;
    }
}
