package com.xeno.goo.network;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.entities.GooSplat;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class GooPlaceSplatAreaPacket implements IGooModPacket {
    private static final float IMPERCEPTIBLE_OFFSET = 0.0001f;
    private BlockPos pos;
    private Vector3d hit;
    private Direction side;

    public GooPlaceSplatAreaPacket(BlockPos pos, Vector3d hit, Direction side) {
        this.pos = pos;
        this.hit = hit;
        this.side = side;
    }

    public GooPlaceSplatAreaPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void toBytes(PacketBuffer buf) {
        buf.writeBlockPos(pos);
        buf.writeDouble(hit.x);
        buf.writeDouble(hit.y);
        buf.writeDouble(hit.z);
        buf.writeInt(side.getIndex());
    }

    @Override
    public void read(PacketBuffer buf) {

        this.pos = buf.readBlockPos();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        this.hit =  new Vector3d(x, y, z);
        this.side = Direction.byIndex(buf.readInt());
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.SERVER) {
                ServerPlayerEntity player = supplier.get().getSender();
                if (player == null) {
                    return;
                }

                ItemStack heldItem = player.getHeldItem(Hand.MAIN_HAND);
                LazyOptional<IFluidHandlerItem> lazyCap = heldItem.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
                lazyCap.ifPresent((c) -> tryBlockInteraction(player, c));
            }
        });

        supplier.get().setPacketHandled(true);
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


    private void tryBlockInteraction(PlayerEntity player, IFluidHandlerItem cap)
    {
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
            player.swing(Hand.MAIN_HAND, false);
        }
    }
}
