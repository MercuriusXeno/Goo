package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.events.TargetingHandler;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.network.*;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.overlay.RayTracing;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooContainerAbstraction;
import net.minecraft.block.BlockState;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GooHandlingHelper {
    public static void tryUsingGauntletOrBasin(ServerPlayerEntity player, Hand hand) {
        if (player.isSwingInProgress) {
            return;
        }

        // lobbing is something only gauntlets can do
        if (player.getHeldItem(hand).getItem() instanceof Gauntlet) {
            GauntletAbstraction.tryLobbingGoo(player, hand);
        }
    }

    public static boolean tryBlockInteraction(ItemUseContext context) {

        PlayerEntity player = context.getPlayer();
        if (player == null || context.getWorld().isRemote()) {
            return false;
        }

        Hand hand = context.getHand();
        BlockState state = context.getWorld().getBlockState(context.getPos());
        boolean didStuff = false;
        if (state.getBlock().hasTileEntity(state)) {
            TileEntity e = context.getWorld().getTileEntity(context.getPos());
            if (!(e instanceof GooContainerAbstraction)) {
                return false;
            }
            if (player.getHeldItem(hand).getItem() instanceof Gauntlet) {
                // refer to the targeting handler to figure out if we are looking at a goo container
                didStuff = tryTakingFluidFromContainerWithGauntlet(context, player, hand);
            } else if (player.getHeldItem(hand).getItem() instanceof Basin) {
                didStuff = tryTakingFluidFromContainerWithBasin(context, player, hand);
            }
        }

        if (didStuff) {
            return true;
        }

        // placing a single splat is a gauntlet function
        if (player.getHeldItem(hand).getItem() instanceof Gauntlet) {
            // try placing a splat at the block if it's a valid location. Let the server handle the check.
            didStuff = tryPlacingSplatWithGauntlet(player.getHeldItem(hand), player, context);
        } else if (player.getHeldItem(hand).getItem() instanceof Basin) {
            // basins instead place as many as 9, utilizing the overlay to indicate where they will be placed.
            didStuff = tryPlacingSplatAreaWithBasin(context.getPlayer(), hand, context.getFace(), context.getPos(), context.getHitVec());
        }
        if (didStuff) {
            return true;
        }
        return false;
    }

    private static boolean tryTakingFluidFromContainerWithGauntlet(ItemUseContext context, PlayerEntity player, Hand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        LazyOptional<IFluidHandlerItem> lazyCap = heldItem.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
        boolean[] didStuff = {false};
        lazyCap.ifPresent((c) -> didStuff[0] = tryTakingFluidFromContainerWithGauntlet(player, c, context.getHitVec(),
                context.getPos(), context.getFace()));
        player.swing(hand, true);
        return didStuff[0];
    }

    private static boolean tryTakingFluidFromContainerWithGauntlet(PlayerEntity player, IFluidHandlerItem cap,
                                                                   Vector3d hit, BlockPos pos, Direction side)
    {
        TileEntity t = player.world.getTileEntity(pos);
        if (!(t instanceof GooContainerAbstraction)) {
            return false;
        }

        // special caller for getting the "right" capability, this is mainly for *mixers* having two caps
        IFluidHandler tileCap = ((GooContainerAbstraction)t).getCapabilityFromRayTraceResult(hit, side, RayTraceTargetSource.GAUNTLET);

        FluidStack hitFluid = ((GooContainerAbstraction) t).getGooFromTargetRayTraceResult(hit, side, RayTraceTargetSource.GAUNTLET);
        // if cap is empty try a drain.
        if (cap.getFluidInTank(0).isEmpty()) {
            return tryCoatingBareGauntlet(player.world, hit, player, cap, tileCap, hitFluid);
        }

        boolean isAltBehavior = player.isSneaking();

        // the fluid we contain isn't the type hit or it is, but our receptacle is full so the intent is inverted.
        if (!isAltBehavior || !cap.getFluidInTank(0).isFluidEqual(hitFluid) || cap.getFluidInTank(0).getAmount() == cap.getTankCapacity(0)) {
            return tryFillingGooContainerWithGauntlet(player.world, player, cap, tileCap, hitFluid);
        }

        return tryCoatingGauntletWithSameFluid(player.world, player, cap, tileCap, hitFluid);
    }

    private static boolean tryFillingGooContainerWithGauntlet(World world, PlayerEntity player,
                                                       IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
    {
        FluidStack sendingFluid = cap.getFluidInTank(0).copy();
        int amountSent = tileCap.fill(sendingFluid, IFluidHandler.FluidAction.SIMULATE);
        if (amountSent == 0) {
            return false;
        }
        if (amountSent < sendingFluid.getAmount()) {
            sendingFluid.setAmount(amountSent);
        }
        FluidStack drainResult = cap.drain(sendingFluid, IFluidHandler.FluidAction.SIMULATE);
        if (drainResult.isEmpty()) {
            return false;
        }

        if (!world.isRemote()) {
            tileCap.fill(cap.drain(sendingFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        AudioHelper.playerAudioEvent(player, Registry.GOO_DEPOSIT_SOUND.get(), 1.0f);
        return true;
    }

    private static boolean tryCoatingGauntletWithSameFluid(World world, PlayerEntity player,
                                                    IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
    {
        int amountRequested = cap.getTankCapacity(0) - cap.getFluidInTank(0).getAmount();
        FluidStack requestFluid = hitFluid.copy();
        requestFluid.setAmount(Math.min(requestFluid.getAmount(), amountRequested));
        FluidStack drainResult = tileCap.drain(requestFluid, IFluidHandler.FluidAction.SIMULATE);
        if (drainResult.isEmpty()) {
            return false;
        }
        int fillResult = cap.fill(drainResult, IFluidHandler.FluidAction.SIMULATE);
        if (fillResult == 0) {
            return false;
        }

        if (!world.isRemote()) {
            cap.fill(tileCap.drain(requestFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        AudioHelper.playerAudioEvent(player, Registry.GOO_WITHDRAW_SOUND.get(), 1.0f);
        return true;
    }

    private static boolean tryCoatingBareGauntlet(World world, Vector3d pos,  PlayerEntity player,
                                           IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
    {
        FluidStack requestFluid = hitFluid.copy();
        if (requestFluid.getAmount() > cap.getTankCapacity(0)) {
            requestFluid.setAmount(cap.getTankCapacity(0));
        }
        FluidStack result = tileCap.drain(requestFluid, IFluidHandler.FluidAction.SIMULATE);
        if (result.isEmpty()) {
            return false;
        }
        int fillResult = cap.fill(result, IFluidHandler.FluidAction.SIMULATE);
        if (fillResult == 0) {
            return false;
        }

        if (!world.isRemote()) {
            cap.fill(tileCap.drain(requestFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        AudioHelper.playerAudioEvent(player, Registry.GOO_WITHDRAW_SOUND.get(), 1.0f);
        return true;
    }

    public static final Map<Direction.Axis, List<Vector3i>> OFFSET_BY_SIDE_HIT_PATTERNS = new HashMap<>();
    static {
        for(Direction.Axis a : Direction.Axis.values()) {
            OFFSET_BY_SIDE_HIT_PATTERNS.put(a, offsetBySideHitPattern(a));
        }
    }

    private static List<Vector3i> offsetBySideHitPattern(Direction.Axis a) {
        switch (a) {
            case Y:
                return Arrays.asList(
                        new Vector3i(0, 0, 0),
                        new Vector3i(1, 0, 0),
                        new Vector3i(0, 0, 1),
                        new Vector3i(-1, 0, 0),
                        new Vector3i(0, 0, -1),
                        new Vector3i(1, 0, 1),
                        new Vector3i(1, 0, -1),
                        new Vector3i(-1, 0, 1),
                        new Vector3i(-1, 0, -1)
                );
            case Z:
                return Arrays.asList(
                        new Vector3i(0, 0, 0),
                        new Vector3i(1, 0, 0),
                        new Vector3i(0, 1, 0),
                        new Vector3i(-1, 0, 0),
                        new Vector3i(0, -1, 0),
                        new Vector3i(1, 1, 0),
                        new Vector3i(1, -1, 0),
                        new Vector3i(-1, 1, 0),
                        new Vector3i(-1, -1, 0)
                );
            case X:
                return Arrays.asList(
                        new Vector3i(0,0,  0),
                        new Vector3i(0,1,  0),
                        new Vector3i(0,0,  1),
                        new Vector3i(0,-1,  0),
                        new Vector3i(0,0,  -1),
                        new Vector3i(0,1,  1),
                        new Vector3i(0,1,  -1),
                        new Vector3i(0,-1,  1),
                        new Vector3i(0,-1,  -1)
                );
        }
        // something went wrong.
        return Arrays.asList(new Vector3i(0, 0, 0));
    }

    private static final float IMPERCEPTIBLE_OFFSET = 0.0001f;
    private static boolean tryPlacingSplatAreaWithBasin(PlayerEntity player, Hand hand,
                                                        Direction side, BlockPos pos, Vector3d hit)
    {
        ItemStack heldItem = player.getHeldItem(hand);
        LazyOptional<IFluidHandlerItem> lazyCap = heldItem.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
        if (!lazyCap.isPresent()) {
            return false;
        }
        boolean[] didStuff = {false};
        lazyCap.ifPresent((c) -> didStuff[0] = tryPlacingSplatAreaWithBasin(player, hand, side, pos, hit, c));
        return didStuff[0];
    }

    private static boolean tryPlacingSplatAreaWithBasin(PlayerEntity player, Hand hand, Direction side, BlockPos pos,
                                                        Vector3d hit, IFluidHandlerItem cap) {
        // unlike gauntlet splat placement, we have to iterate over a list of block positions.
        // our convention for this is to start at 0, 0 and then move in a cross pattern (up left right down)
        // and then in a diagonal X pattern (up left, to up right, down left, to down right)
        boolean didStuff = false;
        Direction.Axis a = side.getAxis();
        List<Vector3i> offsets = OFFSET_BY_SIDE_HIT_PATTERNS.get(a);
        // to combat z fighting we apply a very slight offset to the spawn location of each splat, sequentially
        float imperceptibleOffset = IMPERCEPTIBLE_OFFSET;
        boolean playedSoundYet = false;
        for (Vector3i offset : offsets) {
            // incrementally applying the offset to avoid z-fighting here
            imperceptibleOffset += IMPERCEPTIBLE_OFFSET;
            BlockPos actualPos = new BlockPos(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());
            BlockState state = player.world.getBlockState(actualPos);
            if (state.hasTileEntity()) {
                continue;
            }

            if (!state.isSolidSide(player.world, actualPos, side)) {
                continue;
            }
            // we try to get the full amount of drain but a smaller fluidstack just means a smaller, weaker projectile
            int drainAmountPlaced = GooMod.config.thrownGooAmount(cap.getFluidInTank(0).getFluid());

            // -1 is disabled
            if (drainAmountPlaced == -1) {
                continue;
            }

            // basins have slightly more strict placement guidelines than gauntlets because they will
            // try to place splats in places the player can't see by design. Don't allow non-air block placements
            BlockState splatZoneState = player.world.getBlockState(actualPos.offset(side));
            boolean isAir = splatZoneState.isAir(player.world, actualPos.offset(side));
            boolean isReplaceable = splatZoneState.getMaterial().isReplaceable();
            if (!isAir && !isReplaceable) {
                continue;
            }

            didStuff = true;
            FluidStack splatStack = cap.drain(drainAmountPlaced, IFluidHandler.FluidAction.EXECUTE);
            int originalAmount = splatStack.getAmount();
            splatStack.setAmount(1);
            GooSplat splat = GooSplat.createPlacedSplat(player, actualPos, side, hit, splatStack, !playedSoundYet, imperceptibleOffset);
            playedSoundYet = true;
            if (originalAmount > 1) {
                FluidStack blobStack = splatStack.copy();
                blobStack.setAmount(originalAmount - 1);
                GooBlob blob = GooBlob.createSplattedBlob(player, splat, blobStack);
                player.world.addEntity(blob);
            }
            player.world.addEntity(splat);
        }
        if (didStuff) {
            player.swing(hand, true);
        }
        return didStuff;
    }

    private static boolean tryPlacingSplatWithGauntlet(ItemStack heldItem, PlayerEntity player, ItemUseContext context) {
        LazyOptional<IFluidHandlerItem> lazyCap = heldItem.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
        // if we can't place a single splat, we throw one instead.
        boolean[] tryThrowingInstead = {false};
        lazyCap.ifPresent((c) -> tryThrowingInstead[0] = tryPlacingSplatWithGauntlet(player, c, context.getPos(),
                context.getFace(), context.getHitVec(), context.getHand()));
        if (tryThrowingInstead[0]) {
            return GauntletAbstraction.tryLobbingGoo(player, context.getHand());
        }
        return true;
    }

    private static boolean tryPlacingSplatWithGauntlet(PlayerEntity player, IFluidHandlerItem cap, BlockPos pos,
                                                       Direction side, Vector3d hit, Hand hand)
    {
        BlockState state = player.world.getBlockState(pos);
        if (state.hasTileEntity()) {
            return true;
        }

        boolean solidSideHit = state.isSolidSide(player.world, pos, side);
        boolean sourceLiquidHit = !state.getFluidState().getFluid().isEquivalentTo(Fluids.EMPTY) && state.getFluidState().isSource();
        if (!solidSideHit && !sourceLiquidHit) {
            return true;
        }

        // we try to get the full amount of drain but a smaller fluidstack just means a smaller, weaker projectile
        int drainAmountThrown = GooMod.config.thrownGooAmount(cap.getFluidInTank(0).getFluid());

        // -1 is disabled
        if (drainAmountThrown == -1) {
            return false;
        }

        FluidStack splatStack = cap.drain(drainAmountThrown, IFluidHandler.FluidAction.EXECUTE);
        int originalAmount = splatStack.getAmount();
        splatStack.setAmount(1);
        GooSplat splat = GooSplat.createPlacedSplat(player, pos, side, hit, splatStack, true, 0f);
        if (originalAmount > 1) {
            FluidStack blobStack = splatStack.copy();
            blobStack.setAmount(originalAmount - 1);
            GooBlob blob = GooBlob.createSplattedBlob(player, splat, blobStack);
            player.world.addEntity(blob);
        }
        player.world.addEntity(splat);
        player.swing(hand, true);
        return false;
    }

    private static boolean tryTakingFluidFromContainerWithBasin(ItemUseContext context, PlayerEntity player, Hand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        LazyOptional<IFluidHandlerItem> lazyCap = heldItem.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
        if (lazyCap.isPresent()) {
            boolean[] didStuff = {false};
            lazyCap.ifPresent((c) ->
                    didStuff[0] = tryTakingFluidFromContainerWithBasin(context.getWorld(), context.getPos(),
                            context.getHitVec(), context.getFace(), context.getPlayer(), (BasinAbstractionCapability)c));
            player.swing(hand, true);
            return didStuff[0];
        }
        return false;
    }

    private static boolean tryTakingFluidFromContainerWithBasin(World world, BlockPos pos, Vector3d hit, Direction side, PlayerEntity player,
                                                                BasinAbstractionCapability cap)
    {
        TileEntity t = world.getTileEntity(pos);
        if (!(t instanceof GooContainerAbstraction)) {
            return false;
        }

        // special caller for getting the "right" capability, this is mainly for *mixers* having two caps
        IFluidHandler tileCap = ((GooContainerAbstraction)t).getCapabilityFromRayTraceResult(hit, side, RayTraceTargetSource.BASIN);

        FluidStack hitFluid = ((GooContainerAbstraction) t).getGooFromTargetRayTraceResult(hit, side, RayTraceTargetSource.BASIN);
        // if cap is empty try a drain.
        if (cap.getFluidInTank(0).isEmpty()) {
            return tryFillingEmptyBasin(world, player, cap, tileCap, hitFluid);
        }

        boolean isAltBehavior = player.isSneaking();

        // ordinarily the basin just empties and fills exclusively in a toggle state.
        // holding [sneak] changes the behavior to try to fill the basin first.
        // the fluid we contain isn't the type hit or it is, but our receptacle is full so the intent is inverted.
        if (!isAltBehavior || !cap.getFluidInTank(0).isFluidEqual(hitFluid) || cap.totalFluid() == cap.capacity()) {
            return tryFillingGooContainerFromBasin(world, player, cap, tileCap);
        }

        return tryFillingBasinWithSameFluid(world, player, cap, tileCap, hitFluid);
    }

    private static boolean tryFillingGooContainerFromBasin(World world, PlayerEntity player,
                                                    BasinAbstractionCapability cap, IFluidHandler tileCap)
    {
        FluidStack sendingFluid = cap.getFluidInTank(0).copy();
        int amountSent = tileCap.fill(sendingFluid, IFluidHandler.FluidAction.SIMULATE);
        if (amountSent == 0) {
            return false;
        }
        if (amountSent < sendingFluid.getAmount()) {
            sendingFluid.setAmount(amountSent);
        }
        FluidStack drainResult = cap.drain(sendingFluid, IFluidHandler.FluidAction.SIMULATE);
        if (drainResult.isEmpty()) {
            return false;
        }
        if (!world.isRemote()) {
            tileCap.fill(cap.drain(sendingFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        AudioHelper.playerAudioEvent(player, Registry.GOO_DEPOSIT_SOUND.get(), 1.0f);
        return true;
    }

    private static boolean tryFillingBasinWithSameFluid(World world, PlayerEntity player,
                                                 BasinAbstractionCapability cap, IFluidHandler tileCap, FluidStack hitFluid)
    {
        int amountRequested = cap.capacity() - cap.totalFluid();
        FluidStack requestFluid = hitFluid.copy();
        requestFluid.setAmount(Math.min(requestFluid.getAmount(), amountRequested));
        FluidStack drainResult = tileCap.drain(requestFluid, IFluidHandler.FluidAction.SIMULATE);
        if (drainResult.isEmpty()) {
            return false;
        }
        int fillResult = cap.fill(drainResult, IFluidHandler.FluidAction.SIMULATE);
        if (fillResult == 0) {
            return false;
        }
        if (!world.isRemote()) {
            cap.fill(tileCap.drain(requestFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        AudioHelper.playerAudioEvent(player, Registry.GOO_WITHDRAW_SOUND.get(), 1.0f);
        return true;
    }

    private static boolean tryFillingEmptyBasin(World world, PlayerEntity player,
                                         BasinAbstractionCapability cap, IFluidHandler tileCap, FluidStack hitFluid)
    {
        FluidStack requestFluid = hitFluid.copy();
        if (requestFluid.getAmount() > cap.capacity() - cap.totalFluid()) {
            requestFluid.setAmount(cap.capacity() - cap.totalFluid());
        }
        FluidStack result = tileCap.drain(requestFluid, IFluidHandler.FluidAction.SIMULATE);
        if (result.isEmpty()) {
            return false;
        }
        int fillResult = cap.fill(result, IFluidHandler.FluidAction.SIMULATE);
        if (fillResult == 0) {
            return false;
        }

        if (!world.isRemote()) {
            cap.fill(tileCap.drain(requestFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        AudioHelper.playerAudioEvent(player, Registry.GOO_WITHDRAW_SOUND.get(), 1.0f);
        return true;
    }
}
