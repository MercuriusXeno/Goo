package com.xeno.goo.interactions;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.fluid.Fluid;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

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

    public static final Map<Fluid, Map<Tuple<Integer, String>, ISplatInteraction>> splatRegistry = new HashMap<>();
    public static void registerSplat(Fluid fluid, String key, ISplatInteraction interaction) {
        ensureSplatMapContainsFluid(fluid);
        registerSplat(fluid, key, splatRegistry.get(fluid).size(), interaction);
    }

    private static void registerSplat(Fluid fluid, String key, int rank, ISplatInteraction interaction) {
        splatRegistry.get(fluid).put(new Tuple<>(rank, key), interaction);
    }

    private static void ensureSplatMapContainsFluid(Fluid fluid) {
        if (!splatRegistry.containsKey(fluid)) {
            splatRegistry.put(fluid, new TreeMap<>(Comparator.comparing(Tuple::getA))); // sort by rank
        }
    }

    public static void registerBlob(Fluid f, String key, IBlobInteraction i) {
        ensureBlobMapContainsFluid(f);
        registerBlob(f, key, blobRegistry.get(f).size(), i);
    }

    public static final Map<Fluid, Map<Tuple<Integer, String>, IBlobInteraction>> blobRegistry = new HashMap<>();
    private static void registerBlob(Fluid fluid, String key, int rank, IBlobInteraction interaction) {
        blobRegistry.get(fluid).put(new Tuple<>(rank, key), interaction);
    }

    private static void ensureBlobMapContainsFluid(Fluid f) {
        if (!blobRegistry.containsKey(f)) {
            blobRegistry.put(f, new TreeMap<>(Comparator.comparing(Tuple::getA))); // sort by rank
        }
    }

    public static final Map<Fluid, IPassThroughPredicate> materialPassThroughPredicateRegistry = new HashMap<>();
    public static void registerPassThroughPredicate(Fluid fluid, IPassThroughPredicate p) {
        materialPassThroughPredicateRegistry.put(fluid, p);
    }

    public static void initialize()
    {
        Aquatic.registerInteractions();
        Chromatic.registerInteractions();
        Crystal.registerInteractions();
        Decay.registerInteractions();
        Earthen.registerInteractions();
        Energetic.registerInteractions();
        Faunal.registerInteractions();
        Floral.registerInteractions();
        Fungal.registerInteractions();
        Honey.registerInteractions();
        Logic.registerInteractions();
        Metal.registerInteractions();
        Molten.registerInteractions();
        Obsidian.registerInteractions();
        Regal.registerInteractions();
        Slime.registerInteractions();
        Snow.registerInteractions();
        Vital.registerInteractions();
        Weird.registerInteractions();
    }

    public static void tryResolving(BlockRayTraceResult blockResult, GooBlob gooBlob)
    {
        // no interactions registered, we don't want to crash.
        if (!blobRegistry.containsKey(gooBlob.goo().getFluid())) {
            return;
        }
        BlobContext context = new BlobContext(blockResult, gooBlob, gooBlob.goo().getFluid());
        // cycle over resolvers in rank order and drain/apply when possible.
        Map<Tuple<Integer, String>, IBlobInteraction> map = blobRegistry.get(gooBlob.goo().getFluid());
        map.forEach((k, v) -> tryResolving(gooBlob.goo().getFluid(), k, v, context.withKey(k.getB())));
    }

    private static void tryResolving(Fluid fluid, Tuple<Integer, String> interactionKey, IBlobInteraction iBlobInteraction, BlobContext context)
    {
        int keyCost = GooMod.config.costOfBlobInteraction(fluid, interactionKey.getB());
        if (keyCost == -1) {
            // interaction is disabled, abort
            return;
        }
        FluidStack drained = context.fluidHandler().drain(keyCost, IFluidHandler.FluidAction.SIMULATE);
        if (drained.getAmount() < keyCost) {
            return;
        }
        if (iBlobInteraction.resolve(context)) {
            context.fluidHandler().drain(keyCost, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    public static void tryResolving(GooSplat gooSplat)
    {
        // no interactions registered, we don't want to crash.
        if (!splatRegistry.containsKey(gooSplat.goo().getFluid())) {
            return;
        }
        SplatContext context = new SplatContext(gooSplat, gooSplat.goo().getFluid());
        // cycle over resolvers in rank order and drain/apply when possible.
        Map<Tuple<Integer, String>, ISplatInteraction> map = splatRegistry.get(gooSplat.goo().getFluid());
        map.forEach((k, v) -> tryResolving(gooSplat.goo().getFluid(), k, v, context.withKey(k.getB())));
    }

    private static void tryResolving(Fluid fluid, Tuple<Integer, String> interactionKey, ISplatInteraction iSplatInteraction, SplatContext context)
    {
        int keyCost = GooMod.config.costOfSplatInteraction(fluid, interactionKey.getB());
        if (keyCost == -1) {
            // interaction is disabled, abort
            return;
        }
        FluidStack drained = context.fluidHandler().drain(keyCost, IFluidHandler.FluidAction.SIMULATE);
        if (drained.getAmount() < keyCost) {
            return;
        }
        if (iSplatInteraction.resolve(context)) {
            context.fluidHandler().drain(keyCost, IFluidHandler.FluidAction.EXECUTE);
        }
    }
}
