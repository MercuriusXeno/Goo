package com.xeno.goo.interactions;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.HashMap;
import java.util.Map;

public class GooInteractions
{
    private static BasicParticleType particleTypeFromGoo(FluidStack fluidInTank)
    {
        return particleTypeFromGoo(fluidInTank.getFluid());
    }

    private static BasicParticleType particleTypeFromGoo(Fluid f)
    {
        return Registry.fallingParticleFromFluid(f);
    }

    public static void spawnParticles(GooBlob e)
    {
        if (!(e.getEntityWorld() instanceof ServerWorld)) {
            return;
        }
        // we should be able to guarantee the fluid has goo particles, so spawn a mess of them
        if (e.goo().getFluid() instanceof GooFluid) {
            spawnParticles(e, (GooFluid) e.goo().getFluid());
        }
    }

    // the difference here is that we can call this one during events where
    // the blob is being "emptied" and hang onto its fluid type.
    public static void spawnParticles(GooBlob e, GooFluid f)
    {
        if (!(e.getEntityWorld() instanceof ServerWorld)) {
            return;
        }
        // we should be able to guarantee the fluid has goo particles, so spawn a mess of them
        BasicParticleType type = particleTypeFromGoo(f);
        if (type == null) {
            return;
        }
        Vector3d spawnVec = e.getPositionVec();
        // give it a bit of randomness around the hit location
        double offX = (e.cubicSize() / 2d) * (e.getEntityWorld().rand.nextFloat() - 0.5f);
        double offZ = (e.cubicSize() / 2d) * (e.getEntityWorld().rand.nextFloat() - 0.5f);

        ((ServerWorld)e.getEntityWorld()).spawnParticle(type, spawnVec.x, spawnVec.y, spawnVec.z, e.goo().getAmount(),
                offX, e.cubicSize(), offZ, 0.2d);
    }

    public static final Map<Fluid, Map<Tuple<Integer, String>, IGooInteraction>> registry = new HashMap<>();
    public static void register(Fluid fluid, String key, int rank, IGooInteraction interaction) {
        Tuple<Integer, String> compositeKey = new Tuple<>(rank, key);
        if (!registry.containsKey(fluid)) {
            registry.put(fluid, new HashMap<>());
        }

        registry.get(fluid).put(compositeKey, interaction);
    }

    public static void initialize()
    {
        Aquatic.registerInteractions();

        Energetic.registerInteractions();

        Molten.registerInteractions();

        Snow.registerInteractions();
    }

    public static void tryResolving(GooSplat gooSplat)
    {
        // no interactions registered, we don't want to crash.
        if (!registry.containsKey(gooSplat.goo().getFluid())) {
            return;
        }
        InteractionContext context = new InteractionContext(gooSplat);
        // cycle over resolvers in rank order and drain/apply when possible.
        Map<Tuple<Integer, String>, IGooInteraction> map = registry.get(gooSplat.goo().getFluid());
        map.forEach((k, v) -> tryResolving(gooSplat.goo().getFluid(), k, v, context));
    }

    private static void tryResolving(Fluid fluid, Tuple<Integer, String> interactionKey, IGooInteraction iGooInteraction, InteractionContext context)
    {
        int keyCost = GooMod.config.costOfInteraction(fluid, interactionKey.getB());
        FluidStack drained = context.fluidHandler().drain(keyCost, IFluidHandler.FluidAction.SIMULATE);
        if (drained.getAmount() < keyCost) {
            return;
        }
        if (iGooInteraction.resolve(context)) {
            context.fluidHandler().drain(keyCost, IFluidHandler.FluidAction.EXECUTE);
        }
    }
}
