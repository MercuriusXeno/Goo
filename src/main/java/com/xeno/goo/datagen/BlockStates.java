package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.GooBulbAbstraction;
import com.xeno.goo.blocks.GooPump;
import com.xeno.goo.blocks.Mixer;
import com.xeno.goo.blocks.PumpRenderMode;
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
        registerGooBulbGeneric(Registry.GOO_BULB.get());
        registerGooBulbGeneric(Registry.GOO_BULB_MK2.get());
        registerGooBulbGeneric(Registry.GOO_BULB_MK3.get());
        registerGooBulbGeneric(Registry.GOO_BULB_MK4.get());
        registerGooBulbGeneric(Registry.GOO_BULB_MK5.get());
        registerGooPump();
        registerGooifier();
        registerSolidifier();
        registerMixer();
    }

    private void registerMixer()
    {
        // empty
        ResourceLocation empty = new ResourceLocation(GooMod.MOD_ID, "block/empty");

        // base
        ResourceLocation base_side = new ResourceLocation(GooMod.MOD_ID, "block/mixer_base_side");
        ResourceLocation base_top = new ResourceLocation(GooMod.MOD_ID, "block/mixer_base_top");
        ResourceLocation base_bottom = new ResourceLocation("block/piston_bottom");

        // L and R bulbs
        ResourceLocation bulb_end = new ResourceLocation(GooMod.MOD_ID, "block/mixer_bulb_front");
        ResourceLocation bulb_outer = new ResourceLocation(GooMod.MOD_ID, "block/mixer_bulb_outer");
        ResourceLocation bulb_inner = new ResourceLocation(GooMod.MOD_ID, "block/mixer_bulb_inner");
        ResourceLocation bulb_bottom = new ResourceLocation(GooMod.MOD_ID, "block/mixer_bulb_bottom");
        // there's no top texture, just the top bulb

        // Top bulb
        ResourceLocation receptacle_bottom = new ResourceLocation(GooMod.MOD_ID, "block/mixer_receptacle_bottom");
        ResourceLocation receptacle_side = new ResourceLocation(GooMod.MOD_ID, "block/mixer_receptacle_side");
        ResourceLocation receptacle_front = new ResourceLocation(GooMod.MOD_ID, "block/mixer_receptacle_front");
        ResourceLocation receptacle_top = new ResourceLocation(GooMod.MOD_ID, "block/bulb_end");

        // L and R pistons
        ResourceLocation rod_side = new ResourceLocation(GooMod.MOD_ID, "block/mixer_rod_side");
        ResourceLocation piston_side = new ResourceLocation(GooMod.MOD_ID, "block/mixer_bulb_piston_side");
        ResourceLocation piston_face = new ResourceLocation(GooMod.MOD_ID, "block/mixer_bulb_piston");
        ResourceLocation piston_end = new ResourceLocation(GooMod.MOD_ID, "block/mixer_bulb_piston_end");

        // spinny wheezlebritches
        // already have the rod end, just need the "brick"
        ResourceLocation agitator_end = new ResourceLocation(GooMod.MOD_ID, "block/mixer_agitator_end");
        ResourceLocation agitator_side = new ResourceLocation(GooMod.MOD_ID, "block/mixer_agitator_side");
        ResourceLocation agitator_top = new ResourceLocation(GooMod.MOD_ID, "block/mixer_agitator_top");

        // base first
        BlockModelBuilder base = models()
                .withExistingParent("mixer_base", "block/block")
                .texture("particle", bulb_end)
                .element()
                .from(0, 0, 0)
                .to(16, 4, 16)
                .allFaces((t, u) -> u.texture(t == Direction.UP ? "#base_top" :
                        (t == Direction.DOWN ? "#base_bottom": "#base_side")))
                .end()
                .texture("base_top", base_top)
                .texture("base_bottom", base_bottom)
                .texture("base_side", base_side)
                // left
                .element()
                .from(0, 4, 0)
                .to(6, 10, 16)
                .allFaces((t, u) -> u.texture(t == Direction.UP ? "#empty" :
                        (t == Direction.DOWN ? "#empty" :
                                (t == Direction.WEST ? "#bulb_outer":
                                        (t == Direction.EAST ? "#bulb_inner": "#bulb_end"))))
                        .uvs(t.getAxis() == Direction.Axis.Z ? 5f : 0f,
                                t.getAxis() == Direction.Axis.Z || t.getAxis() == Direction.Axis.X ?  6f : 0f,
                                t.getAxis() == Direction.Axis.Z ? 11f : 16f,
                                t.getAxis() == Direction.Axis.Z || t.getAxis() == Direction.Axis.X ?  12f : 16f))
                .end()
                .element()
                .from(5.99f, 9.99f, 15.99f)
                .to(0.01f, 4.01f, 0.01f)
                .allFaces((t, u) -> u.texture(t == Direction.UP ? "#bulb_bottom" :
                        (t == Direction.DOWN ? "#empty" :
                                (t == Direction.WEST ? "#bulb_inner":
                                        (t == Direction.EAST ? "#bulb_outer" : "#bulb_end"))))
                        .uvs(t == Direction.UP || t.getAxis() == Direction.Axis.Z ? 11f : 16f,
                                t.getAxis() == Direction.Axis.Z || t.getAxis() == Direction.Axis.X ?  12f : 16f,
                                t == Direction.UP || t.getAxis() == Direction.Axis.Z ? 5f : 0f,
                                t.getAxis() == Direction.Axis.Z || t.getAxis() == Direction.Axis.X ?  6f : 0f))
                .end()

                // right
                .element()
                .from(10, 4, 0)
                .to(16, 10, 16)
                .allFaces((t, u) -> u.texture(t == Direction.UP ? "#empty" :
                        (t == Direction.DOWN ? "#empty" :
                                (t == Direction.WEST ? "#bulb_inner":
                                        (t == Direction.EAST ? "#bulb_outer": "#bulb_end"))))
                        .uvs(t.getAxis() == Direction.Axis.Z ? 5f : 0f,
                                t.getAxis() == Direction.Axis.Z || t.getAxis() == Direction.Axis.X ?  6f : 0f,
                                t.getAxis() == Direction.Axis.Z ? 11f : 16f,
                                t.getAxis() == Direction.Axis.Z || t.getAxis() == Direction.Axis.X ?  12f : 16f))
                .end()
                .element()
                .from(15.99f, 9.99f, 15.99f)
                .to(10.01f, 4.01f, 0.01f)
                .allFaces((t, u) -> u.texture(t == Direction.UP ? "#bulb_bottom" :
                        (t == Direction.DOWN ? "#empty" :
                                (t == Direction.WEST ? "#bulb_outer" :
                                        (t == Direction.EAST ? "#bulb_inner": "#bulb_end"))))
                        .uvs(t == Direction.UP || t.getAxis() == Direction.Axis.Z ? 11f : 16f,
                                t.getAxis() == Direction.Axis.Z || t.getAxis() == Direction.Axis.X ?  12f : 16f,
                            t == Direction.UP || t.getAxis() == Direction.Axis.Z ? 5f : 0f,
                                t.getAxis() == Direction.Axis.Z || t.getAxis() == Direction.Axis.X ?  6f : 0f))
                .end()
                .texture("empty", empty)
                .texture("bulb_bottom", bulb_bottom)
                .texture("bulb_outer", bulb_outer)
                .texture("bulb_inner", bulb_inner)
                .texture("bulb_end", bulb_end)
                // receptacle
                .element()
                .from(0, 10, 0)
                .to(16, 16, 16)
                .allFaces((t, u) -> u.texture(t == Direction.UP ? "#receptacle_top" :
                        (t == Direction.DOWN ? "#receptacle_bottom" :
                                (t.getAxis() == Direction.Axis.Z ? "#receptacle_front" : "#receptacle_side")))
                        .uvs( 0f, t.getAxis().isVertical() ? 0f : 5f, 16f,  t.getAxis().isVertical() ? 16f : 11f))
                .end()
                .element()
                .from(15.99f, 15.99f, 15.99f)
                .to(0.01f, 10.01f, 0.01f)
                .allFaces((t, u) -> u.texture(t == Direction.DOWN ? "#receptacle_top" :
                        (t == Direction.UP ? "#receptacle_bottom" :
                                (t.getAxis() == Direction.Axis.Z ? "#receptacle_front" : "#receptacle_side")))
                    .uvs( 0f, t.getAxis().isVertical() ? 0f : 5f, 16f,  t.getAxis().isVertical() ? 16f : 11f))
                .end()
                .texture("receptacle_bottom", receptacle_bottom)
                .texture("receptacle_top", receptacle_top)
                .texture("receptacle_side", receptacle_side)
                .texture("receptacle_front", receptacle_front);

        // "left" (east) piston shaft
        BlockModelBuilder leftShaft = models()
                .withExistingParent("left_shaft", "block/block")
                .element()
                .from(7, 0, 2)
                .to(9, 4.02f, 4)
                .allFaces((t, u) -> u.texture(t.getAxis().isVertical() ? "#empty" : "#rod_side"))
                .end()
                .texture("empty", empty)
                .texture("rod_side", rod_side)
                .element()
                .from(0.02f, 4.02f, 0.02f)
                .to(5.98f, 4.98f, 15.98f)
                .allFaces((t, u) -> u.texture(t.getAxis().isVertical() ? "#piston_face" :
                        t.getAxis() == Direction.Axis.X ? "#piston_end" : "#piston_side"))
                .end()
                .texture("piston_face", piston_face)
                .texture("piston_end", piston_end)
                .texture("piston_side", piston_side);

        // "right" (west) piston shaft
        BlockModelBuilder rightShaft = models()
                .withExistingParent("right_shaft", "block/block")
                .element()
                .from(7, 0, 12)
                .to(9, 4.02f, 14)
                .allFaces((t, u) -> u.texture(t.getAxis().isVertical() ? "#empty" : "#rod_side"))
                .end()
                .texture("empty", empty)
                .texture("rod_side", rod_side)
                .element()
                .from(10.02f, 4.02f, 0.02f)
                .to(15.98f, 4.98f, 15.98f)
                .allFaces((t, u) -> u.texture(t.getAxis().isVertical() ? "#piston_face" :
                        t.getAxis() == Direction.Axis.X ? "#piston_end" : "#piston_side"))
                .end()
                .texture("piston_face", piston_face)
                .texture("piston_end", piston_end)
                .texture("piston_side", piston_side);

        // mixer shaft
        BlockModelBuilder middleShaft = models()
                .withExistingParent("agitator", "block/block")
                .element()
                .from(7, 4, 7)
                .to(9, 10.02f, 9)
                .allFaces((t, u) -> u.texture(t.getAxis().isVertical() ? "#empty" : "#rod_side"))
                .end()
                .texture("empty", empty)
                .texture("rod_side", rod_side)
                .element()
                .from(4, 10.02f, 7)
                .to(12, 12.02f, 9)
                .allFaces((t, u) -> u.texture(t.getAxis().isVertical() ? "#agitator_top" :
                        (t.getAxis() == Direction.Axis.X ? "#agitator_end" : "#agitator_side")))
                .end()
                .texture("agitator_top", agitator_top)
                .texture("agitator_side", agitator_side)
                .texture("agitator_end", agitator_end);

        MultiPartBlockStateBuilder bld = getMultipartBuilder(Registry.MIXER.get());
        for (Direction d : BlockStateProperties.HORIZONTAL_FACING.getAllowedValues()) {
            int rotationY = getRotationYFromDirection(d);
            bld.part().modelFile(base)
                    .rotationY(rotationY)
                    .addModel()
                    .condition(BlockStateProperties.HORIZONTAL_FACING, d)
                    .condition(GooPump.RENDER, PumpRenderMode.STATIC);

            bld.part().modelFile(leftShaft)
                    .rotationY(rotationY)
                    .addModel()
                    .condition(BlockStateProperties.HORIZONTAL_FACING, d)
                    .condition(GooPump.RENDER, PumpRenderMode.DYNAMIC);

            bld.part().modelFile(rightShaft)
                    .rotationY(rotationY)
                    .addModel()
                    .condition(BlockStateProperties.HORIZONTAL_FACING, d)
                    .condition(GooPump.RENDER, PumpRenderMode.DYNAMIC);

            bld.part().modelFile(middleShaft)
                    .rotationY(rotationY)
                    .addModel()
                    .condition(BlockStateProperties.HORIZONTAL_FACING, d)
                    .condition(GooPump.RENDER, PumpRenderMode.DYNAMIC);
        }

        // simpleBlockItem(Registry.MIXER.get(), base);
    }

    private void registerGooBulbGeneric(GooBulbAbstraction base) {
        ResourceLocation end = new ResourceLocation(GooMod.MOD_ID, "block/bulb_end");
        ResourceLocation side = new ResourceLocation(GooMod.MOD_ID, "block/bulb_side");
        BlockModelBuilder model = models()
                .withExistingParent(base.getRegistryName().getPath(), "block/block")
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
        simpleBlock(base, model);
    }

    private void registerGooPump() {
        ResourceLocation baseTop = new ResourceLocation(GooMod.MOD_ID, "block/pump_base_top");
        ResourceLocation baseSide = new ResourceLocation(GooMod.MOD_ID, "block/pump_base_side");
        ResourceLocation baseBottom = new ResourceLocation(GooMod.MOD_ID, "block/pump_base_bottom");
        ResourceLocation baseInner = new ResourceLocation(GooMod.MOD_ID, "block/pump_base_inner");
        ResourceLocation baseInnerBottom = new ResourceLocation("minecraft", "block/polished_basalt_top");
        ResourceLocation stemTop = new  ResourceLocation(GooMod.MOD_ID, "block/pump_stem_top");
        ResourceLocation stemSide = new  ResourceLocation(GooMod.MOD_ID, "block/pump_stem_side");
        ResourceLocation empty = new ResourceLocation(GooMod.MOD_ID, "block/empty");
        ResourceLocation actuatorTop = new  ResourceLocation(GooMod.MOD_ID, "block/pump_actuator_top");
        ResourceLocation actuatorSide = new  ResourceLocation(GooMod.MOD_ID, "block/pump_actuator_side");
        ResourceLocation actuatorInner = new  ResourceLocation(GooMod.MOD_ID, "block/pump_actuator_inner");
        BlockModelBuilder base = models()
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
                .allFaces((t, u) -> u.texture(t.getAxis().isVertical() ? "#stem_top" : "#stem_side"))
                .end()
                .element()
                .from(10.9f, 15.9f, 10.9f)
                .to(5.1f, 0.2f, 5.1f)
                .allFaces((t, u) -> u.texture(t.getAxis().isVertical() ? "#stem_top" : "#stem_side"))
                .shade(false)
                .end();
        base.texture("base_top", baseTop);
        base.texture("base_side", baseSide);
        base.texture("base_bottom", baseBottom);
        base.texture("base_inner", baseInner);
        base.texture("base_inner_bottom", baseInnerBottom);
        base.texture("stem_top", stemTop);
        base.texture("stem_side", stemSide);
        base.texture("empty", empty);

        // actuator model
        BlockModelBuilder actuator = models()
                .withExistingParent("goo_pump_actuator", "block/block")
                .element()
                .from(4.01f, 4.1f, 4.01f).to(11.99f, 8.1f, 11.99f)
                .allFaces((t, u) -> u.texture(t == Direction.UP || t == Direction.DOWN ? "#actuator_top" : "#actuator_side")
                        .uvs(4f,
                                t == Direction.UP || t == Direction.DOWN ? 4f : 6f,
                                12f,
                                t == Direction.UP || t == Direction.DOWN ? 12f : 10f))
                .end()
                .element()
                .from(11.99f, 8.1f, 11.99f).to(4.01f, 4.1f, 4.01f)
                .allFaces((t, u) -> u.texture(t == Direction.UP || t == Direction.DOWN ? "#empty" : "#actuator_inner")
                        .uvs(5f,
                                t == Direction.UP || t == Direction.DOWN ? 4f : 6f,
                                11f,
                                t == Direction.UP || t == Direction.DOWN ? 12f : 10f))
                .end();
        actuator.texture("actuator_top", actuatorTop);
        actuator.texture("actuator_side", actuatorSide);
        actuator.texture("actuator_inner", actuatorInner);
        actuator.texture("empty", empty);

        MultiPartBlockStateBuilder bld = getMultipartBuilder(Registry.GOO_PUMP.get());
        for (Direction d : BlockStateProperties.FACING.getAllowedValues()) {
            int rotationX = getRotationXFromDirection(d);
            int rotationY = getRotationYFromDirection(d);
            bld.part().modelFile(base)
                    .rotationX(rotationX).rotationY(rotationY)
                    .addModel()
                    .condition(BlockStateProperties.FACING, d)
                    .condition(GooPump.RENDER, PumpRenderMode.STATIC);

            bld.part().modelFile(actuator)
                    .rotationX(rotationX).rotationY(rotationY)
                    .addModel()
                    .condition(BlockStateProperties.FACING, d)
                    .condition(GooPump.RENDER, PumpRenderMode.DYNAMIC);;
        }

        simpleBlockItem(Registry.GOO_PUMP.get(), base);
    }

    private int getRotationYFromDirection(Direction d)
    {
        switch (d) {
            case EAST:
                return 90;
            case WEST:
                return 270;
        }
        return 180;
    }

    private int getRotationXFromDirection(Direction d)
    {
        switch(d) {
            case DOWN:
                return 180;
            case SOUTH:
            case WEST:
            case EAST:
                return 90;
            case NORTH:
                return 270;
            case UP:
                return 0;
        }
        return 0;
    }

    private void registerGooifier() {
        ResourceLocation top = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_top");
        ResourceLocation bottom = new ResourceLocation("minecraft", "block/obsidian");
        ResourceLocation side = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_side");
        ResourceLocation back = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_back");
        ResourceLocation front_off = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_front_off");
        ResourceLocation front_on = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_front_on");
        BlockModelBuilder modelInactive = models()
                .cube("gooifier", bottom, top, front_off, back, side, side)
                .texture("particle", front_off);
        BlockModelBuilder modelActive = models()
                .cube("gooifier_powered", bottom, top, front_on, back, side, side)
                .texture("particle", front_on);
        horizontalBlock(Registry.GOOIFIER.get(), state -> !state.get(BlockStateProperties.POWERED) ? modelActive : modelInactive);
        simpleBlockItem(Registry.GOOIFIER.get(), modelInactive);
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
        BlockModelBuilder modelInactive = models()
                .withExistingParent("solidifier", "block/block")
                .element()
                .from(0f, 0, 0f).to(16f, 16, 16f)
                .allFaces((t, u) ->
                        u.texture(t == Direction.DOWN ? "#bottom" :
                                (t == Direction.UP ? "#top_off" :
                                        (t == Direction.EAST || t == Direction.WEST ? "#side_off" :
                                                (t == Direction.SOUTH ? "#back_off" :
                                                        "#front_off")))))
                .end();
        modelInactive.texture("particle", front_off);
        modelInactive.texture("bottom", bottom);
        modelInactive.texture("top_off", top_off);
        modelInactive.texture("side_off", side_off);
        modelInactive.texture("front_off", front_off);
        modelInactive.texture("back_off", back_off);

        BlockModelBuilder modelActive = models()
                .withExistingParent("solidifier_powered", "block/block")
                .element()
                .from(0f, 0, 0f).to(16f, 16, 16f)
                .allFaces((t, u) ->
                        u.texture(t == Direction.DOWN ? "#bottom" :
                                (t == Direction.UP ? "#top_on" :
                                        (t == Direction.EAST || t == Direction.WEST ? "#side_on" :
                                                (t == Direction.SOUTH ? "#back_on" :
                                                        "#front_on")))))
                .end();
        modelActive.texture("particle", front_on);
        modelActive.texture("bottom", bottom);
        modelActive.texture("top_on", top_on);
        modelActive.texture("side_on", side_on);
        modelActive.texture("front_on", front_on);
        modelActive.texture("back_on", back_on);
        horizontalBlock(Registry.SOLIDIFIER.get(), state -> !state.get(BlockStateProperties.POWERED) ? modelActive : modelInactive);
        simpleBlockItem(Registry.SOLIDIFIER.get(), modelInactive);
    }
}
