package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.CrystalNest;
import com.xeno.goo.blocks.GooBulb;
import com.xeno.goo.blocks.GooPump;
import com.xeno.goo.client.render.PumpRenderMode;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
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
        registerGooBulbGeneric(BlocksRegistry.Bulb.get());
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
        ResourceLocation front = new ResourceLocation(GooMod.MOD_ID, "block/lobber/lobber_front");
        ResourceLocation side = new ResourceLocation(GooMod.MOD_ID, "block/lobber/lobber_side");
        ResourceLocation gasket = new ResourceLocation(GooMod.MOD_ID, "block/gasket");
        ResourceLocation empty = new ResourceLocation(GooMod.MOD_ID, "block/empty");
        float gasketThickness = 0.25f;
        BlockModelBuilder model = models()
                .withExistingParent("lobber", "block/block")
                .element()
                .from(gasketThickness, gasketThickness, gasketThickness)
                .to(16f - gasketThickness, 16f - gasketThickness, 16f - gasketThickness)
                .allFaces((t, u) ->
                        u.texture(t ==  Direction.UP ? "#front" :
                                "#side")
                        .uvs(1f, 1f, 15f, 15f))
                .end();
        addGasket(model, Direction.DOWN, gasketThickness);
        addGasket(model, Direction.EAST, gasketThickness);
        addGasket(model, Direction.WEST, gasketThickness);
        addGasket(model, Direction.SOUTH, gasketThickness);
        addGasket(model, Direction.NORTH, gasketThickness);
        model.texture("particle", front);
        model.texture("front", front);
        model.texture("side", side);
        model.texture("gasket", gasket);
        model.texture("empty", empty);
        directionalBlock(BlocksRegistry.Lobber.get(), model);
        simpleBlockItem(BlocksRegistry.Lobber.get(), model);
    }

    private void registerDegrader()
    {
        BlockModelBuilder modelInactive = models()
                .withExistingParent("degrader", new ResourceLocation(GooMod.MOD_ID, "template_degrader"));
        BlockModelBuilder modelActive = models()
                .withExistingParent("degrader_active", new ResourceLocation(GooMod.MOD_ID, "template_degrader_active"));

        getVariantBuilder(BlocksRegistry.Degrader.get())
                .forAllStates(
                        (s) -> ConfiguredModel.builder()
                                .modelFile(s.get(BlockStateProperties.POWERED) ? modelInactive : modelActive)
                                .build()
                );
    }

    private void registerPad() {
        BlockModelBuilder model = models()
                .withExistingParent("goo_pad", new ResourceLocation(GooMod.MOD_ID, "template_goo_pad"));
        BlockModelBuilder modelTriggered = models()
                .withExistingParent("goo_pad_triggered", new ResourceLocation(GooMod.MOD_ID, "template_goo_pad_triggered"));

        getVariantBuilder(BlocksRegistry.Pad.get())
                .forAllStates(s ->
                        ConfiguredModel.builder()
                        .modelFile(s.get(BlockStateProperties.TRIGGERED) ? modelTriggered : model)
                        .build()
                );
    }

    private void registerGooBulbGeneric(GooBulb base) {
        ResourceLocation end = new ResourceLocation(GooMod.MOD_ID, "block/bulb/bulb_end");
        ResourceLocation side = new ResourceLocation(GooMod.MOD_ID, "block/bulb/bulb_side");
        ResourceLocation gasket = new ResourceLocation(GooMod.MOD_ID, "block/gasket");
        ResourceLocation empty = new ResourceLocation(GooMod.MOD_ID, "block/empty");
        float gasketThickness = 0.25f;
        BlockModelBuilder model = models()
                .withExistingParent(base.getRegistryName().getPath(), "block/block")
                .texture("particle", side)
                .element()
                .from(gasketThickness, gasketThickness, gasketThickness)
                .to(16f - gasketThickness, 16f - gasketThickness, 16f - gasketThickness)
                .allFaces((t, u) -> u.texture(t == Direction.UP || t == Direction.DOWN ? "#end" : "#side")
                        .uvs(0f, 0f, 16f, 16f))
                .end()
                .element()
                .from(15.99f - gasketThickness, 15.99f - gasketThickness, 15.99f - gasketThickness)
                .to(0.01f + gasketThickness, 0.01f + gasketThickness, 0.01f + gasketThickness)
                .shade(false)
                .allFaces((t, u) -> u.texture(t == Direction.UP || t == Direction.DOWN ? "#end" : "#side")
                        .uvs(0f, 0f, 16f, 16f))
                .end()
                .texture("gasket", gasket)
                .texture("empty", empty);
        addGasket(model, Direction.UP, gasketThickness);
        addGasket(model, Direction.DOWN, gasketThickness);
        addGasket(model, Direction.EAST, gasketThickness);
        addGasket(model, Direction.WEST, gasketThickness);
        addGasket(model, Direction.SOUTH, gasketThickness);
        addGasket(model, Direction.NORTH, gasketThickness);
        model.texture("end", end);
        model.texture("side", side);
        simpleBlock(base, model);
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

    private void registerMixer()
    {
        ResourceLocation chamber_side = new ResourceLocation(GooMod.MOD_ID, "block/mixer_old/mixer_chamber_side");
        ResourceLocation chamber_inner = new ResourceLocation(GooMod.MOD_ID, "block/mixer_old/mixer_chamber_inner");
        ResourceLocation chamber_end = new ResourceLocation(GooMod.MOD_ID, "block/mixer_old/mixer_chamber_end");
        ResourceLocation chamber_bottom = new ResourceLocation(GooMod.MOD_ID, "block/mixer_old/mixer_chamber_bottom");
        ResourceLocation channel_end = new ResourceLocation(GooMod.MOD_ID, "block/mixer_old/mixer_channel_end");
        ResourceLocation merger_top = new ResourceLocation(GooMod.MOD_ID, "block/mixer_old/mixer_merger_top");
        ResourceLocation merger_bottom = new ResourceLocation(GooMod.MOD_ID, "block/mixer_old/mixer_merger_bottom");
        ResourceLocation merger_side = new ResourceLocation(GooMod.MOD_ID, "block/mixer_old/mixer_merger_side");
        BlockModelBuilder model = models()
                .withExistingParent("mixer", "block/block")
                .texture("particle", chamber_inner)
                // right chamber
                .element()
                .from(0, 4, 2)
                .to(6, 16, 14)
                .allFaces((t, u) ->
                        u.texture(t == Direction.UP || t.getAxis() == Direction.Axis.Z ? "#chamber_end" :
                                (t == Direction.DOWN ? "#chamber_bottom" :
                                        (t == Direction.WEST ? "#chamber_side" : "#chamber_inner")))
                                .uvs(t.getAxis() == Direction.Axis.Y || t.getAxis() == Direction.Axis.Z ? 5f : 2f,
                                        0f,
                                        t.getAxis() == Direction.Axis.Y || t.getAxis() == Direction.Axis.Z ? 11f : 14f,
                                        12f))
                .end()
                // right chamber innards
                .element()
                .from(5.99f, 15.99f, 13.99f)
                .to(0.01f, 4.01f, 2.01f)
                .allFaces((t, u) ->
                        u.texture(t == Direction.DOWN || t.getAxis() == Direction.Axis.Z ? "#chamber_end" :
                                (t == Direction.UP ? "#chamber_bottom" :
                                        (t == Direction.EAST ? "#chamber_side" : "#chamber_inner")))
                                .uvs(t.getAxis() == Direction.Axis.Y || t.getAxis() == Direction.Axis.Z ? 11f : 14f,
                                        12f,
                                        t.getAxis() == Direction.Axis.Y || t.getAxis() == Direction.Axis.Z ? 5f : 2f,
                                        0f))
                .end()
                // left chamber
                .element()
                .from(10, 4, 2)
                .to(16, 16, 14)
                .allFaces((t, u) ->
                        u.texture(t == Direction.UP || t.getAxis() == Direction.Axis.Z ? "#chamber_end" :
                                (t == Direction.DOWN ? "#chamber_bottom" :
                                        (t == Direction.EAST ? "#chamber_side" : "#chamber_inner")))
                                .uvs(t.getAxis() == Direction.Axis.Y || t.getAxis() == Direction.Axis.Z ? 5f : 2f,
                                        0f,
                                        t.getAxis() == Direction.Axis.Y || t.getAxis() == Direction.Axis.Z ? 11f : 14f,
                                        12f))
                .end()
                // left chamber innards
                .element()
                .from(15.99f, 15.99f, 13.99f)
                .to(10.01f, 4.01f, 2.01f)
                .allFaces((t, u) ->
                        u.texture(t == Direction.DOWN || t.getAxis() == Direction.Axis.Z ? "#chamber_end" :
                                (t == Direction.UP ? "#chamber_bottom" :
                                        (t == Direction.WEST ? "#chamber_side" : "#chamber_inner")))
                                .uvs(t.getAxis() == Direction.Axis.Y || t.getAxis() == Direction.Axis.Z ? 11f : 14f,
                                        12f,
                                        t.getAxis() == Direction.Axis.Y || t.getAxis() == Direction.Axis.Z ? 5f : 2f,
                                        0f))
                .end()
                // left channel
                .element()
                .from(1, 0, 6)
                .to(5, 4, 10)
                .allFaces((t, u) -> u.texture("#channel_end").uvs(6f, 6f, 10f, 10f))
                .end()
                .element()
                .from(4.99f, 3.99f, 9.99f)
                .to(1.01f, 0.01f, 6.01f)
                .allFaces((t, u) -> u.texture("#channel_end").uvs(10f, 10f, 6f, 6f))
                .end()
                // right channel
                .element()
                .from(11, 0, 6)
                .to(15, 4, 10)
                .allFaces((t, u) -> u.texture("#channel_end").uvs(6f, 6f, 10f, 10f))
                .end()
                .element()
                .from(14.99f, 3.99f, 9.99f)
                .to(11.01f, 0.01f, 6.01f)
                .allFaces((t, u) -> u.texture("#channel_end").uvs(10f, 10f, 6f, 6f))
                .end()
                // merger
                .element()
                .from(5, 0, 5)
                .to(11, 4, 11)
                .allFaces((t, u) -> u.texture(t == Direction.UP ? "#merger_top" :(t == Direction.DOWN ? "#merger_bottom" : "#merger_side"))
                        .uvs(
                                5f,
                                t.getAxis().isVertical() ? 5f : 6f,
                                11f,
                                t.getAxis().isVertical() ? 11f : 10f
                        ))
                .end()
                .texture("chamber_end", chamber_end)
                .texture("chamber_bottom", chamber_bottom)
                .texture("chamber_side", chamber_side)
                .texture("chamber_inner", chamber_inner)
                .texture("channel_end", channel_end)
                .texture("merger_top", merger_top)
                .texture("merger_bottom", merger_bottom)
                .texture("merger_side", merger_side);

        horizontalBlock(BlocksRegistry.Mixer.get(), model);
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

    private void registerGooPump() {
        ResourceLocation baseTop = new ResourceLocation(GooMod.MOD_ID, "block/pump/pump_base_top");
        ResourceLocation baseSide = new ResourceLocation(GooMod.MOD_ID, "block/pump/pump_base_side");
        ResourceLocation baseBottom = new ResourceLocation(GooMod.MOD_ID, "block/pump/pump_base_bottom");
        ResourceLocation baseInner = new ResourceLocation(GooMod.MOD_ID, "block/pump/pump_base_inner");
        ResourceLocation baseInnerBottom = new ResourceLocation("minecraft", "block/polished_basalt_top");
        ResourceLocation stemTop = new  ResourceLocation(GooMod.MOD_ID, "block/pump/pump_stem_top");
        ResourceLocation stemSide = new  ResourceLocation(GooMod.MOD_ID, "block/pump/pump_stem_side");
        ResourceLocation empty = new ResourceLocation(GooMod.MOD_ID, "block/empty");
        ResourceLocation actuatorTop = new  ResourceLocation(GooMod.MOD_ID, "block/pump/pump_actuator_top");
        ResourceLocation actuatorSide = new  ResourceLocation(GooMod.MOD_ID, "block/pump/pump_actuator_side");
        ResourceLocation actuatorInner = new  ResourceLocation(GooMod.MOD_ID, "block/pump/pump_actuator_inner");
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

        MultiPartBlockStateBuilder bld = getMultipartBuilder(BlocksRegistry.Pump.get());
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
                    .condition(GooPump.RENDER, PumpRenderMode.DYNAMIC);
        }

        simpleBlockItem(BlocksRegistry.Pump.get(), base);
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
        ResourceLocation baseSide = new ResourceLocation(GooMod.MOD_ID, "block/trough/trough_side_outer");
        ResourceLocation baseTop = new ResourceLocation(GooMod.MOD_ID, "block/trough/trough_top");
        ResourceLocation baseBottom = new ResourceLocation(GooMod.MOD_ID, "block/trough/trough_bottom_outer");
        ResourceLocation baseSideInner = new ResourceLocation(GooMod.MOD_ID, "block/trough/trough_side_inner");
        ResourceLocation baseBottomInner = new ResourceLocation(GooMod.MOD_ID, "block/trough/trough_bottom_inner");
        ResourceLocation empty = new ResourceLocation(GooMod.MOD_ID, "block/empty");
        ResourceLocation fixtureBottom = new ResourceLocation(GooMod.MOD_ID, "block/trough/trough_fixture_bottom");
        ResourceLocation fixtureTop = new ResourceLocation(GooMod.MOD_ID, "block/trough/trough_fixture_top");
        ResourceLocation fixtureSide = new ResourceLocation(GooMod.MOD_ID, "block/trough/trough_fixture_side");
        ResourceLocation fixtureFace = new ResourceLocation(GooMod.MOD_ID, "block/trough/trough_fixture_face");
        BlockModelBuilder model = models()
                .withExistingParent("goo_trough", "block/block")
                .element()
                .from(1f, 0f, 1f).to(15f, 4f, 15f)
                .allFaces((t, u) ->
                        u.texture(t == Direction.DOWN ? "#bottom" :
                                (t == Direction.UP ? "#top" :
                                        "#side_outer"))
                ).end()
                .element()
                .from(14f, 4f, 14f).to(2f, 1f, 2f)
                .allFaces((t, u) ->
                        u.texture(t == Direction.DOWN ? "#empty" :
                                (t == Direction.UP ? "#bottom_inner" :
                                        "#side_inner"))
                ).end()
                .element()
                .from(4f, 0.01f, 0f).to(12f, 12f, 4f)
                .allFaces((t, u) ->
                        u.texture(t == Direction.DOWN ? "#fixture_bottom" :
                                (t == Direction.UP ? "#fixture_top" :
                                        t.getAxis() == Direction.Axis.X ? "#fixture_side" :
                                                "#fixture_face"))
                                .uvs(
                                        t.getAxis() == Direction.Axis.X ? 6f : 4f,
                                        t.getAxis() == Direction.Axis.Y ? 6f : 2f,
                                        t.getAxis() == Direction.Axis.X ? 10f : 12f,
                                        t.getAxis() == Direction.Axis.Y ? 10f : 14f
                                )
                ).end();
        model.texture("bottom", baseBottom);
        model.texture("top", baseTop);
        model.texture("side_outer", baseSide);
        model.texture("empty", empty);
        model.texture("bottom_inner", baseBottomInner);
        model.texture("side_inner", baseSideInner);
        model.texture("fixture_bottom", fixtureBottom);
        model.texture("fixture_top", fixtureTop);
        model.texture("fixture_side", fixtureSide);
        model.texture("fixture_face", fixtureFace);
        model.texture("particle", baseBottom);
        horizontalBlock(BlocksRegistry.Trough.get(), state -> model);
    }
}
