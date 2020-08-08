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
        registerGoopBulb();
        registerGoopifier();
        registerSolidifier();
    }

    private void registerGoopBulb() {
        ResourceLocation end = new ResourceLocation(GooMod.MOD_ID, "block/bulb_end");
        ResourceLocation side = new ResourceLocation(GooMod.MOD_ID, "block/bulb_side");
        BlockModelBuilder model = models()
                .withExistingParent("goop_bulb", "block/block")
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
        simpleBlock(Registry.GOOP_BULB.get(), model);
        simpleBlockItem(Registry.GOOP_BULB.get(), model);
    }

    private void registerGoopifier() {
        ResourceLocation top = new ResourceLocation(GooMod.MOD_ID, "block/goopifier_top");
        ResourceLocation bottom = new ResourceLocation("minecraft", "block/piston_bottom");
        ResourceLocation side = new ResourceLocation(GooMod.MOD_ID, "block/goopifier_side");
        ResourceLocation back = new ResourceLocation(GooMod.MOD_ID, "block/goopifier_back");
        ResourceLocation front_off = new ResourceLocation(GooMod.MOD_ID, "block/goopifier_front_off");
        ResourceLocation front_on = new ResourceLocation(GooMod.MOD_ID, "block/goopifier_front_on");
        BlockModelBuilder model = models()
                .cube("goopifier", bottom, top, front_off, back, side, side)
                .texture("particle", front_off);
        BlockModelBuilder modelActive = models()
                .cube("goopifier_powered", bottom, top, front_on, back, side, side)
                .texture("particle", front_on);
        horizontalBlock(Registry.GOOPIFIER.get(), state -> state.get(BlockStateProperties.POWERED) ? modelActive : model);
        simpleBlockItem(Registry.GOOPIFIER.get(), model);
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
