package com.xeno.goo.client.models;

public class ComboGauntletModelLoader extends HolderModelLoader
{
    public static final ComboGauntletModelLoader INSTANCE = new ComboGauntletModelLoader();

    @Override
    String holderName()
    {
        return "combo_gauntlet";
    }
}
