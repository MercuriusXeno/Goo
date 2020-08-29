package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.setup.Resources;
import net.minecraft.block.BlockState;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.block.RotatedPillarBlock;
import net.minecraft.data.DataGenerator;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.*;

import java.util.function.Function;

public class BlockStates extends BlockStateProvider {
    public BlockStates(DataGenerator gen, ExistingFileHelper exFileHelper) {
        super(gen, GooMod.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        registerGooBulb();
        registerGooPump();
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

    private void registerGooPump() {
        ResourceLocation baseTop = new ResourceLocation(GooMod.MOD_ID, "block/pump_base_top");
        ResourceLocation baseSide = new ResourceLocation(GooMod.MOD_ID, "block/pump_base_side");
        ResourceLocation baseBottom = new ResourceLocation(GooMod.MOD_ID, "block/pump_base_bottom");
        ResourceLocation baseInner = new ResourceLocation(GooMod.MOD_ID, "block/pump_base_inner");
        ResourceLocation baseInnerBottom = new ResourceLocation("minecraft", "block/polished_basalt_top");
        ResourceLocation stemTop = new  ResourceLocation(GooMod.MOD_ID, "block/pump_stem_top");
        ResourceLocation stemSide = new  ResourceLocation(GooMod.MOD_ID, "block/pump_stem_side");
//        ResourceLocation actuatorTop = new  ResourceLocation(GooMod.MOD_ID, "block/pump_actuator_top");
//        ResourceLocation actuatorSide = new  ResourceLocation(GooMod.MOD_ID, "block/pump_actuator_side");
        ResourceLocation empty = new ResourceLocation(GooMod.MOD_ID, "block/empty");
        BlockModelBuilder model = models()
                .withExistingParent("goo_pump", "block/block")
                .texture("particle", baseBottom)
                .element()
                .from(0, 0, 0)
                .to(16, 8, 16)
                .allFaces((t, u) -> u.texture(t == Direction.DOWN ? "#base_bottom" :
                        (t == Direction.UP ? "#base_top" : "#base_side")))
                .end()
                .element()
                .from(12, 8, 12)
                .to(4, 0.1f, 4)
                .allFaces((t, u) -> u.texture(t == Direction.UP ? "#base_inner_bottom" : ( t == Direction.DOWN ? "#empty" : "#base_inner")))
                .end()
                .element()
                .from(5, 0.1f, 5)
                .to(11, 16, 11)
                .allFaces((t, u) -> u.texture(t == Direction.UP || t == Direction.DOWN ? "#stem_top" : "#stem_side"))
                .end()
                .element()
                .from(10.9f, 15.9f, 10.9f)
                .to(5.1f, 0.2f, 5.1f)
                .allFaces((t, u) -> u.texture(t == Direction.UP || t == Direction.DOWN ? "#stem_top" : "#stem_side"))
                .shade(false)
                .end();
        model.texture("base_top", baseTop);
        model.texture("base_side", baseSide);
        model.texture("base_bottom", baseBottom);
        model.texture("base_inner", baseInner);
        model.texture("base_inner_bottom", baseInnerBottom);
        model.texture("stem_top", stemTop);
        model.texture("stem_side", stemSide);
        model.texture("empty", empty);


        directionalBlock(Registry.GOO_PUMP.get(), model);

        simpleBlockItem(Registry.GOO_PUMP.get(), model);
    }

    private void registerGooifier() {
        ResourceLocation top = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_top");
        ResourceLocation bottom = new ResourceLocation("minecraft", "block/obsidian");
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
        ResourceLocation bottom = new ResourceLocation("minecraft", "block/nether_bricks");
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
