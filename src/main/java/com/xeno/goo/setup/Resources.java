package com.xeno.goo.setup;

import com.xeno.goo.GooMod;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.List;

public class Resources
{
    public static class GooTextures {
        public static List<ResourceLocation> STILLS = Arrays.asList(
                Still.AQUATIC_GOO,
                Still.CHROMATIC_GOO,
                Still.CRYSTAL_GOO,
                Still.DECAY_GOO,
                Still.EARTHEN_GOO,
                Still.ENERGETIC_GOO,
                Still.FAUNAL_GOO,
                Still.FLORAL_GOO,
                Still.FUNGAL_GOO,
                Still.HONEY_GOO,
                Still.LOGIC_GOO,
                Still.METAL_GOO,
                Still.MOLTEN_GOO,
                Still.OBSIDIAN_GOO,
                Still.REGAL_GOO,
                Still.SLIME_GOO,
                Still.SNOW_GOO,
                Still.VITAL_GOO,
                Still.WEIRD_GOO);
        public static List<ResourceLocation> FLOWS = Arrays.asList(
                Flowing.AQUATIC_GOO,
                Flowing.CHROMATIC_GOO,
                Flowing.CRYSTAL_GOO,
                Flowing.DECAY_GOO,
                Flowing.EARTHEN_GOO,
                Flowing.ENERGETIC_GOO,
                Flowing.FAUNAL_GOO,
                Flowing.FLORAL_GOO,
                Flowing.FUNGAL_GOO,
                Flowing.HONEY_GOO,
                Flowing.LOGIC_GOO,
                Flowing.METAL_GOO,
                Flowing.MOLTEN_GOO,
                Flowing.OBSIDIAN_GOO,
                Flowing.REGAL_GOO,
                Flowing.SLIME_GOO,
                Flowing.SNOW_GOO,
                Flowing.VITAL_GOO,
                Flowing.WEIRD_GOO
        );
        public static class Still {
            public static final ResourceLocation AQUATIC_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/aquatic_still");
            public static final ResourceLocation CHROMATIC_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/chromatic_still");
            public static final ResourceLocation CRYSTAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/crystal_still");
            public static final ResourceLocation DECAY_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/decay_still");
            public static final ResourceLocation EARTHEN_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/earthen_still");
            public static final ResourceLocation ENERGETIC_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/energetic_still");
            public static final ResourceLocation FAUNAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/faunal_still");
            public static final ResourceLocation FLORAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/floral_still");
            public static final ResourceLocation FUNGAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/fungal_still");
            public static final ResourceLocation HONEY_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/honey_still");
            public static final ResourceLocation LOGIC_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/logic_still");
            public static final ResourceLocation METAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/metal_still");
            public static final ResourceLocation MOLTEN_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/molten_still");
            public static final ResourceLocation OBSIDIAN_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/obsidian_still");
            public static final ResourceLocation REGAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/regal_still");
            public static final ResourceLocation SLIME_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/slime_still");
            public static final ResourceLocation SNOW_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/snow_still");
            public static final ResourceLocation VITAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/vital_still");
            public static final ResourceLocation WEIRD_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/weird_still");
        }

        public static class Flowing {
            public static final ResourceLocation AQUATIC_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/aquatic_flow");
            public static final ResourceLocation CHROMATIC_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/chromatic_flow");
            public static final ResourceLocation CRYSTAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/crystal_flow");
            public static final ResourceLocation DECAY_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/decay_flow");
            public static final ResourceLocation EARTHEN_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/earthen_flow");
            public static final ResourceLocation ENERGETIC_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/energetic_flow");
            public static final ResourceLocation FAUNAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/faunal_flow");
            public static final ResourceLocation FLORAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/floral_flow");
            public static final ResourceLocation FUNGAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/fungal_flow");
            public static final ResourceLocation HONEY_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/honey_flow");
            public static final ResourceLocation LOGIC_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/logic_flow");
            public static final ResourceLocation METAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/metal_flow");
            public static final ResourceLocation MOLTEN_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/molten_flow");
            public static final ResourceLocation OBSIDIAN_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/obsidian_flow");
            public static final ResourceLocation REGAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/regal_flow");
            public static final ResourceLocation SLIME_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/slime_flow");
            public static final ResourceLocation SNOW_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/snow_flow");
            public static final ResourceLocation VITAL_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/vital_flow");
            public static final ResourceLocation WEIRD_GOO = new ResourceLocation(GooMod.MOD_ID, "block/fluid/weird_flow");
        }

        public static class OldEntities
        {
            public static final ResourceLocation AQUATIC = new ResourceLocation(GooMod.MOD_ID, "textures/entity/aquatic.png");
            public static final ResourceLocation CHROMATIC = new ResourceLocation(GooMod.MOD_ID, "textures/entity/chromatic.png");
            public static final ResourceLocation CRYSTAL = new ResourceLocation(GooMod.MOD_ID, "textures/entity/crystal.png");
            public static final ResourceLocation DECAY = new ResourceLocation(GooMod.MOD_ID, "textures/entity/decay.png");
            public static final ResourceLocation EARTHEN = new ResourceLocation(GooMod.MOD_ID, "textures/entity/earthen.png");
            public static final ResourceLocation ENERGETIC = new ResourceLocation(GooMod.MOD_ID, "textures/entity/energetic.png");
            public static final ResourceLocation FAUNAL = new ResourceLocation(GooMod.MOD_ID, "textures/entity/faunal.png");
            public static final ResourceLocation FLORAL = new ResourceLocation(GooMod.MOD_ID, "textures/entity/floral.png");
            public static final ResourceLocation FUNGAL = new ResourceLocation(GooMod.MOD_ID, "textures/entity/fungal.png");
            public static final ResourceLocation HONEY = new ResourceLocation(GooMod.MOD_ID, "textures/entity/honey.png");
            public static final ResourceLocation LOGIC = new ResourceLocation(GooMod.MOD_ID, "textures/entity/logic.png");
            public static final ResourceLocation METAL = new ResourceLocation(GooMod.MOD_ID, "textures/entity/metal.png");
            public static final ResourceLocation MOLTEN = new ResourceLocation(GooMod.MOD_ID, "textures/entity/molten.png");
            public static final ResourceLocation OBSIDIAN = new ResourceLocation(GooMod.MOD_ID, "textures/entity/obsidian.png");
            public static final ResourceLocation REGAL = new ResourceLocation(GooMod.MOD_ID, "textures/entity/regal.png");
            public static final ResourceLocation SLIME = new ResourceLocation(GooMod.MOD_ID, "textures/entity/slime.png");
            public static final ResourceLocation SNOW = new ResourceLocation(GooMod.MOD_ID, "textures/entity/snow.png");
            public static final ResourceLocation VITAL = new ResourceLocation(GooMod.MOD_ID, "textures/entity/vital.png");
            public static final ResourceLocation WEIRD = new ResourceLocation(GooMod.MOD_ID, "textures/entity/weird.png");
        }

        public static class Entities
        {
            public static final ResourceLocation AQUATIC = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/aquatic_still.png");
            public static final ResourceLocation CHROMATIC = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/chromatic_still.png");
            public static final ResourceLocation CRYSTAL = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/crystal_still.png");
            public static final ResourceLocation DECAY = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/decay_still.png");
            public static final ResourceLocation EARTHEN = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/earthen_still.png");
            public static final ResourceLocation ENERGETIC = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/energetic_still.png");
            public static final ResourceLocation FAUNAL = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/faunal_still.png");
            public static final ResourceLocation FLORAL = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/floral_still.png");
            public static final ResourceLocation FUNGAL = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/fungal_still.png");
            public static final ResourceLocation HONEY = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/honey_still.png");
            public static final ResourceLocation LOGIC = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/logic_still.png");
            public static final ResourceLocation METAL = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/metal_still.png");
            public static final ResourceLocation MOLTEN = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/molten_still.png");
            public static final ResourceLocation OBSIDIAN = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/obsidian_still.png");
            public static final ResourceLocation REGAL = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/regal_still.png");
            public static final ResourceLocation SLIME = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/slime_still.png");
            public static final ResourceLocation SNOW = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/snow_still.png");
            public static final ResourceLocation VITAL = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/vital_still.png");
            public static final ResourceLocation WEIRD = new ResourceLocation(GooMod.MOD_ID, "textures/block/fluid/weird_still.png");
        }
    }
}
