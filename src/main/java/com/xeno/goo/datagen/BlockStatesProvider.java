package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.CrystalNest;
import com.xeno.goo.blocks.GooPump;
import com.xeno.goo.client.render.block.DynamicRenderMode;
import com.xeno.goo.client.render.block.DynamicRenderMode.DynamicRenderTypes;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.Direction.AxisDirection;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.client.model.generators.*;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.function.Supplier;

public class BlockStatesProvider extends BlockStateProvider {
    private static final float GASKET_U = 4f;
    private static final float GASKET_V = 4f;
    private static final float GASKET_U2 = 12f;
    private static final float GASKET_V2 = 12f;

    public BlockStatesProvider(DataGenerator gen, ExistingFileHelper exFileHelper) {
        super(gen, GooMod.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        registerGooBulb();
        registerGooPump();
        registerGooifier();
        registerSolidifier();
        registerMixer();
        registerDegrader();
        registerLobber();
        registerDrain();
        registerRadiantLight();
        registerTrough();
        registerCrystalNest();
        registerDecorativeBlocks();
        registerPad();
        registerPassivatedBlock();
    }

    private void registerPassivatedBlock() {
        ResourceLocation texture = new ResourceLocation(GooMod.MOD_ID, "block/passivated_block");
        BlockModelBuilder model = models().cubeAll("passivated_block", texture);
        simpleBlock(BlocksRegistry.PassivatedBlock.get(), model);
        simpleBlockItem(BlocksRegistry.PassivatedBlock.get(), model);
    }

    private void registerGooBulb() {
        BlockModelBuilder model = models().withExistingParent("bulb", new ResourceLocation(GooMod.MOD_ID, "prefab_bulb"));

        simpleBlock(BlocksRegistry.Bulb.get(), model);

        simpleBlockItem(BlocksRegistry.Bulb.get(), model);
    }

    private void registerGooPump() {
        BlockModelBuilder base = models().withExistingParent("pump", new ResourceLocation(GooMod.MOD_ID, "prefab_pump"));
        BlockModelBuilder actuator = models().withExistingParent("pump_actuator", new ResourceLocation(GooMod.MOD_ID, "prefab_pump_actuator"));
        MultiPartBlockStateBuilder bld = getMultipartBuilder(BlocksRegistry.Pump.get());
        for (Direction d : BlockStateProperties.FACING.getAllowedValues()) {
            int rotationX = getRotationXFromDirection(d);
            int rotationY = getRotationYFromDirection(d);
            bld.part().modelFile(base)
                    .rotationX(rotationX).rotationY(rotationY)
                    .addModel()
                    .condition(BlockStateProperties.FACING, d)
                    .condition(DynamicRenderMode.RENDER, DynamicRenderTypes.STATIC);

            bld.part().modelFile(actuator)
                    .rotationX(rotationX).rotationY(rotationY)
                    .addModel()
                    .condition(BlockStateProperties.FACING, d)
                    .condition(DynamicRenderMode.RENDER, DynamicRenderTypes.DYNAMIC);
        }

        simpleBlockItem(BlocksRegistry.Pump.get(), base);
    }

    private void registerMixer()
    {
        BlockModelBuilder model = models()
                .withExistingParent("mixer", new ResourceLocation(GooMod.MOD_ID, "prefab_mixer"));
        BlockModelBuilder spinner = models()
                .withExistingParent("mixer_spinner", new ResourceLocation(GooMod.MOD_ID, "prefab_mixer_spinner"));

        MultiPartBlockStateBuilder bld = getMultipartBuilder(BlocksRegistry.Mixer.get());
        for(Direction d : BlockStateProperties.HORIZONTAL_FACING.getAllowedValues()) {
            int rotationY = getRotationYFromDirection(d);
            bld.part().modelFile(model)
                    .rotationY(rotationY)
                    .addModel()
                    .condition(BlockStateProperties.HORIZONTAL_FACING, d)
                    .condition(BlockStateProperties.POWERED, true, false)
                    .condition(DynamicRenderMode.RENDER, DynamicRenderTypes.STATIC);

            bld.part().modelFile(spinner)
                    .rotationY(rotationY)
                    .addModel()
                    .condition(BlockStateProperties.HORIZONTAL_FACING, d)
                    .condition(BlockStateProperties.POWERED, true, false)
                    .condition(DynamicRenderMode.RENDER, DynamicRenderTypes.DYNAMIC);
        }

        simpleBlockItem(BlocksRegistry.Mixer.get(), model);
    }

    private void registerDecorativeBlocks() {

        // loop over fluid variants and generate a variant for each fluid + variant composite (and two for the pillars, they are special)
        Registry.FluidSuppliers.forEach(this::proceduralGooBlockVariant);
    }

    private void proceduralGooBlockVariant(ResourceLocation k, Supplier<GooFluid> v) {
        String prefix = k.getPath();
        String textureDir = "block/crystal_blocks/";
        for(String variant : BlocksRegistry.CRYSTAL_BLOCK_VARIANTS) {
            String fullPath = prefix + "_" + variant;
            ResourceLocation loc = new ResourceLocation(GooMod.MOD_ID, textureDir + fullPath);
            ResourceLocation block = new ResourceLocation(GooMod.MOD_ID, fullPath);
            BlockModelBuilder model = models().cubeAll(prefix +  "_" + variant, loc);
            simpleBlock(BlocksRegistry.CrystalBlocks.get(block).get(), model);
            simpleBlockItem(BlocksRegistry.CrystalBlocks.get(block).get(), model);
        }

        for(String variant : BlocksRegistry.PILLAR_CRYSTAL_BLOCK_VARIANTS) {
            String topPath = prefix +  "_" + variant + "_top";
            String sidePath = prefix +  "_" + variant + "_side";
            ResourceLocation topLoc = new ResourceLocation(GooMod.MOD_ID, textureDir + topPath);
            ResourceLocation sideLoc = new ResourceLocation(GooMod.MOD_ID, textureDir + sidePath);
            ResourceLocation block = new ResourceLocation(GooMod.MOD_ID, prefix +  "_" + variant);
            BlockModelBuilder model = models().cubeColumn(prefix +  "_" + variant, sideLoc, topLoc);
            simpleBlock(BlocksRegistry.CrystalBlocks.get(block).get(), model);
            simpleBlockItem(BlocksRegistry.CrystalBlocks.get(block).get(), model);
        }
    }

    private void registerDrain() {
        ResourceLocation top = new ResourceLocation(GooMod.MOD_ID, "block/drain/drain_top");
        ResourceLocation side = new ResourceLocation(GooMod.MOD_ID, "block/drain/drain_side");
        BlockModelBuilder model = models()
                .withExistingParent("drain", "block/block")
                .texture("particle", side)
                .element()
                .from(0, 12, 0)
                .to(16, 16, 16)
                .allFaces((t, u) ->
                        u.texture(t == Direction.UP || t == Direction.DOWN ? "#top" : "#side")
                                .uvs(0f,
                                        t.getAxis() == Direction.Axis.Y ? 0f : 6f,
                                        16f,
                                        t.getAxis() == Direction.Axis.Y ? 16f : 10f))
                .end();

        model.texture("top", top);
        model.texture("side", side);
        simpleBlock(BlocksRegistry.Drain.get(), model);
        simpleBlockItem(BlocksRegistry.Drain.get(), model);
    }



    private void addGasket(BlockModelBuilder builder, Direction d, float thickness) {
        Vector3f from, to;
        switch (d) {
            case UP:
                from = new Vector3f(6f, 16f - thickness, 6f);
                to = new Vector3f(10f, 16f, 10f);
                break;
            case DOWN:
                from = new Vector3f(6f, 0f, 6f);
                to = new Vector3f(10f, thickness, 10f);
                break;
            case EAST:
                from = new Vector3f(16f - thickness, 6f, 6f);
                to = new Vector3f(16f, 10f, 10f);
                break;
            case WEST:
                from = new Vector3f(0f, 6f, 6f);
                to = new Vector3f(thickness, 10f, 10f);
                break;
            case SOUTH:
                from = new Vector3f(6f, 6f, 16f - thickness);
                to = new Vector3f(10f, 10f, 16f);
                break;
            case NORTH:
                from = new Vector3f(6f, 6f, 0f);
                to = new Vector3f(10f, 10f, thickness);
                break;
            default:
                from = new Vector3f(0f, 0f, 0f);
                to = new Vector3f(0f, 0f, 0f);
                break;
        }
        addGasket(builder, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), d,
                GASKET_U, GASKET_V, GASKET_U2, GASKET_V2);
    }

    private void addGasket(BlockModelBuilder builder, float fromX, float fromY, float fromZ,
                                        float toX, float toY, float toZ,
                                        Direction d, float u1, float v1, float u2, float v2) {
        // normal gasket
        builder.element()
                .from(fromX, fromY, fromZ)
                .to(toX, toY, toZ)
                .allFaces((t, u) -> u.texture("#gasket").uvs(u1, v1, u2, t.getAxis() == d.getAxis() ? v2 : v1 + 1f))
                .end();
        // inverse (inside of gasket)
        builder.element()
                .from(toX - (d.getAxis() == Direction.Axis.X ? 0f : 1f),
                        toY - (d.getAxis() == Direction.Axis.Y ? 0f : 1f),
                        toZ - (d.getAxis() == Direction.Axis.Z ? 0f : 1f))
                .to(fromX + (d.getAxis() == Direction.Axis.X ? 0f : 1f),
                        fromY + (d.getAxis() == Direction.Axis.Y ? 0f : 1f),
                        fromZ + (d.getAxis() == Direction.Axis.Z ? 0f : 1f))
                .allFaces((t, u) ->
                        u.texture(t.getAxis() == d.getAxis() ? "#empty" : "#gasket")
                                .uvs(u1 + 1f,
                                        v1 + 1f,
                                        u2 - 1f,
                                        v1 + 2f))
                .end();
    }


    private void registerLobber() {
        BlockModelBuilder model = models()
                .withExistingParent("lobber", new ResourceLocation(GooMod.MOD_ID, "prefab_lobber"));

        getVariantBuilder(BlocksRegistry.Lobber.get())
                .forAllStates(
                        (s) -> ConfiguredModel.builder()
                                .modelFile(model)
                                .rotationY(s.get(BlockStateProperties.FACING).getAxis() == Axis.Y ? 0 : (180 + s.get(BlockStateProperties.FACING).getHorizontalIndex() * 90))
                                .rotationX(s.get(BlockStateProperties.FACING).getAxis() != Axis.Y ? 0 :
                                        (s.get(BlockStateProperties.FACING).getAxisDirection() == AxisDirection.NEGATIVE ? 90 : 270)
                                    )
                                .build()
                );
        simpleBlockItem(BlocksRegistry.Lobber.get(), model);
    }

    private void registerDegrader()
    {
        BlockModelBuilder modelInactive = models()
                .withExistingParent("degrader_inactive", new ResourceLocation(GooMod.MOD_ID, "prefab_degrader_inactive"));
        BlockModelBuilder modelActive = models()
                .withExistingParent("degrader_active", new ResourceLocation(GooMod.MOD_ID, "prefab_degrader_active"));

        getVariantBuilder(BlocksRegistry.Degrader.get())
                .forAllStates(
                        (s) -> ConfiguredModel.builder()
                                .modelFile(s.get(BlockStateProperties.POWERED) ? modelInactive : modelActive)
                                .rotationY(180 - s.get(BlockStateProperties.HORIZONTAL_FACING).getHorizontalIndex() * 90)
                                .build()
                );

        simpleBlockItem(BlocksRegistry.Degrader.get(), modelInactive);
    }

    private void registerPad() {
        BlockModelBuilder model = models()
                .withExistingParent("pad", new ResourceLocation(GooMod.MOD_ID, "prefab_pad"));
        BlockModelBuilder modelTriggered = models()
                .withExistingParent("pad_triggered", new ResourceLocation(GooMod.MOD_ID, "prefab_pad_triggered"));

        getVariantBuilder(BlocksRegistry.Pad.get())
                .forAllStates(s ->
                        ConfiguredModel.builder()
                        .modelFile(s.get(BlockStateProperties.TRIGGERED) ? modelTriggered : model)
                        .build()
                );

        simpleBlockItem(BlocksRegistry.Pad.get(), model);
    }

    private void registerGooifier() {
        ResourceLocation top = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_old/gooifier_top");
        ResourceLocation bottom = new ResourceLocation("minecraft", "block/polished_blackstone");
        ResourceLocation side = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_old/gooifier_side");
        ResourceLocation front_off = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_old/gooifier_front_off");
        ResourceLocation front_on = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_old/gooifier_front_on");
        ResourceLocation hatch = new ResourceLocation(GooMod.MOD_ID, "block/gooifier_old/hatch");
        ResourceLocation gasket = new ResourceLocation(GooMod.MOD_ID, "block/gasket_filled");
        ResourceLocation empty = new ResourceLocation(GooMod.MOD_ID, "block/empty");
        float gasketThickness = 0.25f;
        BlockModelBuilder modelInactive = models()
                .withExistingParent("gooifier", "block/block")
                .element()
                .from(gasketThickness, 0, gasketThickness)
                .to(16f - gasketThickness, 16f - gasketThickness, 16f - gasketThickness)
                .allFaces((t, u) -> u.texture(
                        t == Direction.NORTH ? "#front_off" :
                                (t == Direction.UP ? "#top" :
                                        (t == Direction.DOWN ? "#bottom" : "#side")))
                        .uvs(0f, 0f, 16f, 16f)
                )
                .end()
                // south hatch
                .element()
                .from(5f, 3f, 15.75f).to(11f, 9f, 16f)
                .allFaces((t, u) -> u.texture("#hatch")
                        .uvs(5f,
                                5f,
                                t.getAxis() == Direction.Axis.X ? 6f : 11f,
                                t.getAxis() == Direction.Axis.Z  ? 11f : 6f)
                )
                .end()
                .texture("hatch", hatch)
                .texture("gasket", gasket)
                .texture("empty", empty)
                .texture("particle", front_off)
                .texture("front_off", front_off)
                .texture("side", side)
                .texture("top", top)
                .texture("bottom", bottom);
        addGasket(modelInactive, Direction.UP, gasketThickness);
        addGasket(modelInactive, Direction.EAST, gasketThickness);
        addGasket(modelInactive, Direction.WEST, gasketThickness);

        BlockModelBuilder modelActive = models()
                .withExistingParent("gooifier_powered", "block/block")
                .element()
                .from(gasketThickness, 0, gasketThickness)
                .to(16f - gasketThickness, 16f - gasketThickness, 16f - gasketThickness)
                .allFaces((t, u) -> u.texture(
                        t == Direction.NORTH ? "#front_on" :
                                    (t == Direction.UP ? "#top" :
                                            (t == Direction.DOWN ? "#bottom" : "#side")))
                        .uvs(0f, 0f, 16f, 16f)
                )
                .end()
                // south hatch
                .element()
                .from(5f, 3f, 15.75f).to(11f, 9f, 16f)
                .allFaces((t, u) -> u.texture("#hatch")
                        .uvs(5f,
                                5f,
                                t.getAxis() == Direction.Axis.X ? 6f : 11f,
                                t.getAxis() == Direction.Axis.Z  ? 11f : 6f)
                )
                .end()
                .texture("hatch", hatch)
                .texture("gasket", gasket)
                .texture("empty", empty)
                .texture("particle", front_on)
                .texture("front_on", front_on)
                .texture("side", side)
                .texture("top", top)
                .texture("bottom", bottom);
        addGasket(modelActive, Direction.UP, gasketThickness);
        addGasket(modelActive, Direction.EAST, gasketThickness);
        addGasket(modelActive, Direction.WEST, gasketThickness);
        horizontalBlock(BlocksRegistry.Gooifier.get(), state -> !state.get(BlockStateProperties.POWERED) ? modelActive : modelInactive);
        simpleBlockItem(BlocksRegistry.Gooifier.get(), modelInactive);
    }

    private void registerSolidifier() {
        ResourceLocation top_off = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_old/solidifier_top_off");
        ResourceLocation top_on = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_old/solidifier_top_on");
        ResourceLocation bottom = new ResourceLocation("minecraft", "block/nether_bricks");
        ResourceLocation side_off = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_old/solidifier_side_off");
        ResourceLocation side_on = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_old/solidifier_side_on");
        ResourceLocation front_off = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_old/solidifier_front_off");
        ResourceLocation front_on = new ResourceLocation(GooMod.MOD_ID, "block/solidifier_old/solidifier_front_on");
        ResourceLocation gasket = new ResourceLocation(GooMod.MOD_ID, "block/gasket_filled");
        ResourceLocation empty = new ResourceLocation(GooMod.MOD_ID, "block/empty");
        float gasketThickness = 0.25f;
        BlockModelBuilder modelInactive = models()
                .withExistingParent("solidifier", "block/block")
                .element()
                .from(gasketThickness, 0, gasketThickness)
                .to(16f - gasketThickness, 16f - gasketThickness, 16f - gasketThickness)
                .allFaces((t, u) ->
                        u.texture(t == Direction.DOWN ? "#bottom" :
                                (t == Direction.UP ? "#top_off" :
                                        (t == Direction.NORTH ? "#front_off" : "#side_off"
                                                        )))
                                .uvs(0f, 0f, 16f, 16f))
                .end()
                .texture("gasket", gasket)
                .texture("empty", empty);
        addGasket(modelInactive, Direction.UP, gasketThickness);
        addGasket(modelInactive, Direction.EAST, gasketThickness);
        addGasket(modelInactive, Direction.WEST, gasketThickness);
        modelInactive.texture("particle", front_off);
        modelInactive.texture("bottom", bottom);
        modelInactive.texture("top_off", top_off);
        modelInactive.texture("side_off", side_off);
        modelInactive.texture("front_off", front_off);

        BlockModelBuilder modelActive = models()
                .withExistingParent("solidifier_powered", "block/block")
                .element()
                .from(gasketThickness, 0, gasketThickness)
                .to(16f - gasketThickness, 16f - gasketThickness, 16f - gasketThickness)
                .allFaces((t, u) ->
                        u.texture(t == Direction.DOWN ? "#bottom" :
                                (t == Direction.UP ? "#top_on" :
                                        (t == Direction.NORTH ? "#front_on" : "#side_on"
                                        )))
                                .uvs(0f, 0f, 16f, 16f))
                .end()
                .texture("gasket", gasket)
                .texture("empty", empty);
        modelActive.texture("particle", front_on);
        modelActive.texture("bottom", bottom);
        modelActive.texture("top_on", top_on);
        modelActive.texture("side_on", side_on);
        modelActive.texture("front_on", front_on);
        addGasket(modelActive, Direction.UP, gasketThickness);
        addGasket(modelActive, Direction.EAST, gasketThickness);
        addGasket(modelActive, Direction.WEST, gasketThickness);
        horizontalBlock(BlocksRegistry.Solidifier.get(), state -> !state.get(BlockStateProperties.POWERED) ? modelActive : modelInactive);
        simpleBlockItem(BlocksRegistry.Solidifier.get(), modelInactive);
    }

    private void registerRadiantLight() {
        ResourceLocation lightTop = new ResourceLocation(GooMod.MOD_ID, "block/radiant_light/radiant_top");
        ResourceLocation lightSide = new ResourceLocation(GooMod.MOD_ID, "block/radiant_light/radiant_side");
        BlockModelBuilder light = models()
                .withExistingParent("radiant_light", "block/block")
                .texture("particle", lightTop)
                .element()
                .from(3, 0, 3)
                .to(13, 2, 13)
                .allFaces((t, u) -> u.texture(t == Direction.DOWN ? "#light_top" :
                        (t == Direction.UP ? "#light_top" : "#light_side"))
                        .uvs(
                                3f,
                                t.getAxis().isVertical() ? 3f : 7f,
                                13f,
                                t.getAxis().isVertical() ? 13f : 9f
                        ))
                .end();
        light.texture("light_top", lightTop);
        light.texture("light_side", lightSide);

        MultiPartBlockStateBuilder bld = getMultipartBuilder(BlocksRegistry.RadiantLight.get());
        for (Direction d : BlockStateProperties.FACING.getAllowedValues()) {
            int rotationX = getRotationXFromDirection(d);
            int rotationY = getRotationYFromDirection(d);
            bld.part().modelFile(light)
                    .rotationX(rotationX).rotationY(rotationY)
                    .addModel()
                    .condition(BlockStateProperties.FACING, d);
        }
        simpleBlockItem(BlocksRegistry.RadiantLight.get(), light);
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

    private void registerCrystalNest() {
        ResourceLocation top = new ResourceLocation(GooMod.MOD_ID, "block/bee_nest/bee_nest_top");
        ResourceLocation bottom = new ResourceLocation(GooMod.MOD_ID, "block/bee_nest/bee_nest_bottom");
        ResourceLocation side = new ResourceLocation(GooMod.MOD_ID, "block/bee_nest/bee_nest_side");
        ResourceLocation front = new ResourceLocation(GooMod.MOD_ID, "block/bee_nest/bee_nest_front");
        ResourceLocation frontFull = new ResourceLocation(GooMod.MOD_ID, "block/bee_nest/bee_nest_front_honey");
        BlockModelBuilder model = models()
                .cube("crystal_nest", bottom, top, front, side, side, side)
                .texture("particle", side);
        BlockModelBuilder modelFull = models()
                .cube("crystal_nest_full", bottom, top, frontFull, side, side, side)
                .texture("particle", side);
        horizontalBlock(BlocksRegistry.CrystalNest.get(), state -> state.get(CrystalNest.GOO_FULL) ? modelFull : model);
        simpleBlockItem(BlocksRegistry.CrystalNest.get(), model);
    }

    private void registerTrough() {
        BlockModelBuilder model = models().withExistingParent("trough", new ResourceLocation(GooMod.MOD_ID, "prefab_trough"));

        horizontalBlock(BlocksRegistry.Trough.get(), state -> model);

        simpleBlockItem(BlocksRegistry.Trough.get(), model);
    }
}
