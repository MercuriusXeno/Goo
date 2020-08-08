package com.xeno.goo.fluids;

import com.xeno.goo.fluids.throwing.ThrownEffect;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidAttributes;

import java.util.function.Supplier;

import static com.xeno.goo.library.GooEntry.EMPTY;

public class AquaticGoo extends GooBase
{
    public AquaticGoo(Supplier<? extends Item> bucket, FluidAttributes.Builder builder) {
        super(bucket, builder);
    }

    public ThrownEffect thrownEffect(ServerPlayerEntity entity, ServerWorld world, GooEntityBase goo) {
        return new AquaticThrownEffect(entity, world, goo);
    }
}
