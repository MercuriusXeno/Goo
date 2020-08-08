package com.xeno.goo.fluids.throwing;

public abstract class ThrownEffect
{
    public static final ThrownEffect EMPTY = new ThrownEffect()
    {
        @Override
        protected void impact()
        {

        }

        @Override
        protected void flying()
        {

        }
    };

    protected abstract void impact();

    protected abstract void flying();
}
