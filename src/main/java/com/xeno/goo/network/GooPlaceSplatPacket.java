package com.xeno.goo.network;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooContainerAbstraction;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class GooPlaceSplatPacket implements IGooModPacket {
    private BlockPos pos;
    private Vector3d hit;
    private Direction side;

    public GooPlaceSplatPacket(BlockPos pos, Vector3d hit, Direction side) {
        this.pos = pos;
        this.hit = hit;
        this.side = side;
    }

    public GooPlaceSplatPacket(PacketBuffer buf) {
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

    private void tryBlockInteraction(PlayerEntity player, IFluidHandlerItem cap)
    {
        BlockState state = player.world.getBlockState(this.pos);
        if (state.hasTileEntity()) {
            return;
        }

        if (!state.isSolidSide(player.world, this.pos, this.side)) {
            return;
        }

        // we try to get the full amount of drain but a smaller fluidstack just means a smaller, weaker projectile
        int drainAmountThrown = GooMod.config.thrownGooAmount(cap.getFluidInTank(0).getFluid());

        // -1 is disabled
        if (drainAmountThrown == -1) {
            return;
        }

        FluidStack splatStack = cap.drain(drainAmountThrown, IFluidHandler.FluidAction.EXECUTE);
        int originalAmount = splatStack.getAmount();
        splatStack.setAmount(1);
        GooSplat splat = GooSplat.createPlacedSplat(player, this.pos, this.side, this.hit, splatStack, true, 0f);
        if (originalAmount > 1) {
            FluidStack blobStack = splatStack.copy();
            blobStack.setAmount(originalAmount - 1);
            GooBlob blob = GooBlob.createSplattedBlob(player, splat, blobStack);
            player.world.addEntity(blob);
        }
        player.world.addEntity(splat);
        player.swing(Hand.MAIN_HAND, false);
    }
}
