package com.xeno.goop.datagen;

import com.xeno.goop.GoopMod;
import com.xeno.goop.setup.Registration;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.data.DataGenerator;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.*;
import java.util.function.Function;

public class BlockStates extends BlockStateProvider {
    public BlockStates(DataGenerator gen, ExistingFileHelper exFileHelper) {
        super(gen, GoopMod.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        registerGoopBulb();
        registerGoopifier();
        registerSolidifier();
    }

    private void registerGoopBulb() {
        ResourceLocation end = new ResourceLocation(GoopMod.MOD_ID, "block/bulb_end");
        ResourceLocation side = new ResourceLocation(GoopMod.MOD_ID, "block/bulb_side");
        BlockModelBuilder model = models().cube("goop_bulb", end, end, side, side, side, side);
        orientedBlock(Registration.GOOP_BULB.get(), state -> model);

    }

    private void registerGoopifier() {
        ResourceLocation top = new ResourceLocation(GoopMod.MOD_ID, "block/goopifier_top");
        ResourceLocation bottom = new ResourceLocation("minecraft", "block/piston_bottom");
        ResourceLocation side = new ResourceLocation(GoopMod.MOD_ID, "block/goopifier_side");
        ResourceLocation back = new ResourceLocation(GoopMod.MOD_ID, "block/goopifier_back");
        ResourceLocation front_off = new ResourceLocation(GoopMod.MOD_ID, "block/goopifier_front_off");
        ResourceLocation front_on = new ResourceLocation(GoopMod.MOD_ID, "block/goopifier_front_on");
        BlockModelBuilder model = models().cube("goopifier", bottom, top, back, front_off, side, side);
        BlockModelBuilder modelActive = models().cube("goopifier_powered", bottom, top, back, front_on, side, side);
        orientedBlock(Registration.GOOPIFIER.get(), state -> state.get(BlockStateProperties.POWERED) ? modelActive : model);
    }

    private void registerSolidifier() {
        ResourceLocation top_off = new ResourceLocation(GoopMod.MOD_ID, "block/solidifier_top_off");
        ResourceLocation top_on = new ResourceLocation(GoopMod.MOD_ID, "block/solidifier_top_on");
        ResourceLocation bottom = new ResourceLocation("minecraft", "block/piston_bottom");
        ResourceLocation side_off = new ResourceLocation(GoopMod.MOD_ID, "block/solidifier_side_off");
        ResourceLocation side_on = new ResourceLocation(GoopMod.MOD_ID, "block/solidifier_side_on");
        ResourceLocation back_off = new ResourceLocation(GoopMod.MOD_ID, "block/solidifier_back_off");
        ResourceLocation back_on = new ResourceLocation(GoopMod.MOD_ID, "block/solidifier_back_on");
        ResourceLocation front_off = new ResourceLocation(GoopMod.MOD_ID, "block/solidifier_front_off");
        ResourceLocation front_on = new ResourceLocation(GoopMod.MOD_ID, "block/solidifier_front_on");
        BlockModelBuilder model = models().cube("solidifier", bottom, top_off, back_off, front_off, side_off, side_off);
        BlockModelBuilder modelActive = models().cube("solidifier_powered", bottom, top_on, back_on, front_on, side_on, side_on);
        orientedBlock(Registration.SOLIDIFIER.get(), state -> state.get(BlockStateProperties.POWERED) ? modelActive : model);
    }

    private void orientedBlock(Block block, Function<BlockState, ModelFile> modelFunc) {
        getVariantBuilder(block)
                .forAllStates(state -> {
                    Direction dir = state.get(BlockStateProperties.FACING);
                    return ConfiguredModel.builder()
                            .modelFile(modelFunc.apply(state))
                            .rotationX(dir.getAxis() == Direction.Axis.Y ?  dir.getAxisDirection().getOffset() * -90 : 0)
                            .rotationY(dir.getAxis() != Direction.Axis.Y ? ((dir.getHorizontalIndex() + 2) % 4) * 90 : 0)
                            .build();
                });
    }
}
