package com.xeno.goo.fluids;

import com.xeno.goo.fluids.throwing.ThrownEffect;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;

public class AquaticThrownEffect extends ThrownEffect
{
    public AquaticThrownEffect(ServerPlayerEntity entity, ServerWorld world, GooEntityBase goo) {super();}

    public void impact() {}

    @Override
    protected void flying()
    {

    }


}
