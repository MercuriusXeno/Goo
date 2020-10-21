package com.xeno.goo.network;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.items.GauntletAbstraction;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Hand;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class GooGrabPacket implements IGooModPacket {
    private int gooEntityId;
    private Hand hand;

    public GooGrabPacket (Entity entity, Hand hand) {
        this.gooEntityId = entity.getEntityId();
        this.hand = hand;
    }

    public GooGrabPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void toBytes(PacketBuffer buf) {
        buf.writeInt(gooEntityId);
        buf.writeEnumValue(hand);
    }

    @Override
    public void read(PacketBuffer buf) {
        this.gooEntityId = buf.readInt();
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
                Entity e = player.world.getEntityByID(this.gooEntityId);
                if (e instanceof GooSplat && ((GooSplat)e).isAtRest()) {
                    tryPlayerInteraction(player, (IFluidHandler)e, hand);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }

    private boolean tryPlayerInteraction(PlayerEntity player, IFluidHandler handler, Hand hand)
    {
        boolean[] didStuff = {false};
        LazyOptional<IFluidHandlerItem> cap = player.getHeldItem(hand).getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
        cap.ifPresent((c) -> didStuff[0] = tryExtractingGooFromEntity(c, handler));
        if (didStuff[0]) {
            AudioHelper.playerAudioEvent(player, Registry.GOO_WITHDRAW_SOUND.get(), 1.0f);
        }
        return didStuff[0];
    }

    private boolean tryExtractingGooFromEntity(IFluidHandlerItem item, IFluidHandler entity)
    {
        FluidStack heldGoo = item.getFluidInTank(0);
        if (!item.getFluidInTank(0).isEmpty()) {
            if (!heldGoo.isFluidEqual(entity.getFluidInTank(0)) || entity.getFluidInTank(0).isEmpty()) {
                return false;
            }
        }

        int spaceRemaining = item.getTankCapacity(0) - item.getFluidInTank(0).getAmount();
        FluidStack tryDrain = entity.drain(spaceRemaining, IFluidHandler.FluidAction.SIMULATE);
        if (tryDrain.isEmpty()) {
            return false;
        }

        item.fill(entity.drain(tryDrain, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }
}
