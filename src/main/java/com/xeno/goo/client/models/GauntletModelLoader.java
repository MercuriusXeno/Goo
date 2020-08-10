package com.xeno.goo.client.models;

public class GauntletModelLoader extends HolderModelLoader
{
    public static final GauntletModelLoader INSTANCE = new GauntletModelLoader();

    @Override
    String holderName()
    {
        return "gauntlet";
    }
}
