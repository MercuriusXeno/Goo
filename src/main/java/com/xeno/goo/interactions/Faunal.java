package com.xeno.goo.interactions;

import com.xeno.goo.setup.Registry;

public class Faunal
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.FAUNAL_GOO.get(), "twitterpate_animals", Faunal::makeAnimalsTwitterpated);
    }

    private static boolean makeAnimalsTwitterpated(SplatContext splatContext) {
        return false;
    }
}
