package com.xeno.goo.client.models;

public class MobiusCrucibleModelLoader extends HolderModelLoader
{
    public static final MobiusCrucibleModelLoader INSTANCE = new MobiusCrucibleModelLoader();

    @Override
    String holderName()
    {
        return "mobius_crucible";
    }
}
