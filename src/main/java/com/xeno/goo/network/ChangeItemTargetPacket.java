package com.xeno.goo.network;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class ChangeItemTargetPacket implements IGooModPacket
{
    private RegistryKey<World> worldRegistryKey;
    private BlockPos pos;
    private ItemStack target;
    private ItemStack newTarget;
    private int changeTargetTimer;

    public ChangeItemTargetPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void read(PacketBuffer buf)
    {
        worldRegistryKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, buf.readResourceLocation());
        pos = buf.readBlockPos();
        target = buf.readItemStack();
        newTarget = buf.readItemStack();
        changeTargetTimer = buf.readInt();
    }

    public ChangeItemTargetPacket(RegistryKey<World> registryKey, BlockPos pos, ItemStack target, ItemStack newTarget, int changeTargetTimer) {
        this.worldRegistryKey = registryKey;
        this.pos = pos;
        this.target = target;
        this.newTarget = newTarget;
        this.changeTargetTimer = changeTargetTimer;
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeResourceLocation(worldRegistryKey.getLocation());
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
                if (Minecraft.getInstance().world.getDimensionKey() != worldRegistryKey) {
                    return;
                }
                TileEntity te = Minecraft.getInstance().world.getTileEntity(pos);
                if (te instanceof IChangeItemTargetReceiver) {
                    ((IChangeItemTargetReceiver) te).updateItemTarget(target, newTarget, changeTargetTimer);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }

    public interface IChangeItemTargetReceiver
    {

        /**
         * @param target the actual target of the solidifier, whatever it is currently or was before the change event
         * @param newTarget the target we'll change to if the change is confirmed within the time limit
         * @param changeTargetTimer the time left to confirm change, this is actually not important, just needs nonzero
         */
        void updateItemTarget(ItemStack target, ItemStack newTarget, int changeTargetTimer);
    }
}
