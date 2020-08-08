package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.*;

public class BlockStates extends BlockStateProvider {
    public BlockStates(DataGenerator gen, ExistingFileHelper exFileHelper) {
        super(gen, GooMod.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        registerGooBulb();
        registerGooifier();
        registerSolidifier();
    }

    private void registerGooBulb() {
        ResourceLocation end = new ResourceLocation(GooMod.MOD_ID, "block/bulb_end");
        ResourceLocation side = new ResourceLocation(GooMod.MOD_ID, "block/bulb_side");
        BlockModelBuilder model = models()
                .withExistingParent("goo_bulb", "block/block")
                .texture("particle", side)
                .element()
                .from(0, 0, 0)
                .to(16, 16, 16)
                .allFaces((t, u) -> u.texture(t == Direction.UP || t == Direction.DOWN ? "#end" : "#side"))
                .end()
                .element()
                .from(15.9f, 15.9f, 15.9f)
                .to(0.1f, 0.1f, 0.1f)
                .shade(false)
                .allFaces((t, u) -> u.texture(t == Direction.UP || t == Direction.DOWN ? "#end" : "#side"))
                .end();
        model.texture("end", end);
        model.texture("side", side);
        simpleBlock(Registry.GOO_BULB.get(), model);

        simpleBlockItem(Registry.GOO_BULB.get(), model);
    }

    private void registerGooifier() {
        ResourceLocation top = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_top");
        ResourceLocation bottom = new ResourceLocation("minecraft", "block/piston_bottom");
        ResourceLocation side = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_side");
        ResourceLocation back = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_back");
        ResourceLocation front_off = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_front_off");
        ResourceLocation front_on = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_front_on");
        BlockModelBuilder model = models()
                .cube("gooifier", bottom, top, front_off, back, side, side)
                .texture("particle", front_off);
        BlockModelBuilder modelActive = models()
                .cube("gooifier_powered", bottom, top, front_on, back, side, side)
                .texture("particle", front_on);
        horizontalBlock(Registry.GOOIFIER.get(), state -> state.get(BlockStateProperties.POWERED) ? modelActive : model);
        simpleBlockItem(Registry.GOOIFIER.get(), model);
    }

    private void registerSolidifier() {
        ResourceLocation top_off = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_top_off");
        ResourceLocation top_on = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_top_on");
        ResourceLocation bottom = new ResourceLocation("minecraft", "block/piston_bottom");
        ResourceLocation side_off = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_side_off");
        ResourceLocation side_on = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_side_on");
        ResourceLocation back_off = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_back_off");
        ResourceLocation back_on = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_back_on");
        ResourceLocation front_off = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_front_off");
        ResourceLocation front_on = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_front_on");
        BlockModelBuilder model = models()
                .cube("solidifier", bottom, top_off, front_off, back_off, side_off, side_off)
                .texture("particle", front_off);
        BlockModelBuilder modelActive = models()
                .cube("solidifier_powered", bottom, top_on, front_on, back_on, side_on, side_on)
                .texture("particle", front_on);
        horizontalBlock(Registry.SOLIDIFIER.get(), state -> state.get(BlockStateProperties.POWERED) ? modelActive : model);
        simpleBlockItem(Registry.SOLIDIFIER.get(), model);
    }
}
