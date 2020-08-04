package com.xeno.goop.network;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class ChangeSolidifierTargetPacket
{
    private final DimensionType type;
    private final BlockPos pos;
    private final ItemStack target;
    private final ItemStack newTarget;
    private final int changeTargetTimer;

    public ChangeSolidifierTargetPacket(PacketBuffer buf) {
        type = DimensionType.getById(buf.readInt());
        pos = buf.readBlockPos();
        target = buf.readItemStack();
        newTarget = buf.readItemStack();
        changeTargetTimer = buf.readInt();
    }

    public ChangeSolidifierTargetPacket(DimensionType type, BlockPos pos, ItemStack target, ItemStack newTarget, int changeTargetTimer) {
        this.type = type;
        this.pos = pos;
        this.target = target;
        this.newTarget = newTarget;
        this.changeTargetTimer = changeTargetTimer;
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeInt(type.getId());
        buf.writeBlockPos(pos);
        buf.writeItemStack(target);
        buf.writeItemStack(newTarget);
        buf.writeInt(changeTargetTimer);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                if (Minecraft.getInstance().world == null) {
                    return;
                }
                if (Minecraft.getInstance().world.dimension.getType() != type) {
                    return;
                }
                TileEntity te = Minecraft.getInstance().world.getTileEntity(pos);
                if (te instanceof ChangeSolidifierTargetPacket.IChangeSolidifierTargetReceiver) {
                    ((ChangeSolidifierTargetPacket.IChangeSolidifierTargetReceiver) te).updateSolidifierTarget(target, newTarget, changeTargetTimer);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }

    public interface IChangeSolidifierTargetReceiver {

        /**
         * @param target the actual target of the solidifier, whatever it is currently or was before the change event
         * @param newTarget the target we'll change to if the change is confirmed within the time limit
         * @param changeTargetTimer the time left to confirm change, this is actually not important, just needs nonzero
         */
        void updateSolidifierTarget(ItemStack target, ItemStack newTarget, int changeTargetTimer);
    }
}
