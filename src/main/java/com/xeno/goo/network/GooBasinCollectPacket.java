package com.xeno.goo.network;

import com.xeno.goo.items.BasinAbstractionCapability;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooContainerAbstraction;
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

public class GooBasinCollectPacket implements IGooModPacket {
    private BlockPos pos;
    private Vector3d hit;
    private Direction side;
    private Hand hand;

    public GooBasinCollectPacket(BlockPos pos, Vector3d hit, Direction side, Hand hand) {
        this.pos = pos;
        this.hit = hit;
        this.side = side;
        this.hand = hand;
    }

    public GooBasinCollectPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void toBytes(PacketBuffer buf) {
        buf.writeBlockPos(pos);
        buf.writeDouble(hit.x);
        buf.writeDouble(hit.y);
        buf.writeDouble(hit.z);
        buf.writeInt(side.getIndex());
        buf.writeEnumValue(hand);
    }

    @Override
    public void read(PacketBuffer buf) {

        this.pos = buf.readBlockPos();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        this.hit =  new Vector3d(x, y, z);
        this.side = Direction.byIndex(buf.readInt());
        this.hand = buf.readEnumValue(Hand.class);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.SERVER) {
                ServerPlayerEntity player = supplier.get().getSender();
                if (player == null) {
                    return;
                }

                ItemStack heldItem = player.getHeldItem(hand);
                LazyOptional<IFluidHandlerItem> lazyCap = heldItem.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
                if (lazyCap.isPresent()) {
                    lazyCap.ifPresent((c) -> tryBlockInteraction(player, (BasinAbstractionCapability)c));
                    player.swing(hand, true);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }

    private boolean tryBlockInteraction(PlayerEntity player, BasinAbstractionCapability cap)
    {
        TileEntity t = player.world.getTileEntity(this.pos);
        if (!(t instanceof GooContainerAbstraction)) {
            return false;
        }

        // special caller for getting the "right" capability, this is mainly for *mixers* having two caps
        IFluidHandler tileCap = ((GooContainerAbstraction)t).getCapabilityFromRayTraceResult(this.hit, this.side, RayTraceTargetSource.BASIN);

        FluidStack hitFluid = ((GooContainerAbstraction) t).getGooFromTargetRayTraceResult(this.hit, this.side, RayTraceTargetSource.BASIN);
        // if cap is empty try a drain.
        if (cap.getFluidInTank(0).isEmpty()) {
            return tryFillingEmptyBasin(player.world, this.hit, player, cap, tileCap, hitFluid);
        }

        boolean isAltBehavior = player.isSneaking();

        // ordinarily the basin just empties and fills exclusively in a toggle state.
        // holding [sneak] changes the behavior to try to fill the basin first.
        // the fluid we contain isn't the type hit or it is, but our receptacle is full so the intent is inverted.
        if (!isAltBehavior || !cap.getFluidInTank(0).isFluidEqual(hitFluid) || cap.totalFluid() == cap.capacity()) {
            return tryFillingGooContainer(player.world, player, cap, tileCap);
        }

        return tryFillingBasinWithSameFluid(player.world, player, cap, tileCap, hitFluid);
    }

    private boolean tryFillingGooContainer(World world, PlayerEntity player,
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

    private boolean tryFillingBasinWithSameFluid(World world, PlayerEntity player,
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

    private boolean tryFillingEmptyBasin(World world, Vector3d pos, PlayerEntity player,
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
