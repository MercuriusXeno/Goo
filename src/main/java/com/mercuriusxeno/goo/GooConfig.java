package com.mercuriusxeno.goo;

import net.neoforged.neoforge.common.ModConfigSpec;

public class GooConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue BASE_VALUES_OVERRIDE_RECIPES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Goo Value Derivation Settings");
        builder.push("derivation");

        BASE_VALUES_OVERRIDE_RECIPES = builder
            .comment(
                "When true, hand-keyed base values always win over recipe-derived values.",
                "When false (default), the lowest total blob count wins (LCD rule).",
                "Set to true if you are a modpack author who wants full control over base values.")
            .define("baseValuesOverrideRecipes", false);

        builder.pop();

        SPEC = builder.build();
    }
}
