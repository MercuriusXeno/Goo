package com.xeno.goo.entities;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooBase;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GooEntity extends Entity implements IEntityAdditionalSpawnData
{
    public Vector3d decayingVector;
    private List<AxisAlignedBB> proportions;
    private List<Vector3d> collisions;
    public double decayingSpeed;
    private boolean isHeld;
    private Entity holder;
    public FluidStack goo;

    public GooEntity(World worldIn) {
        super(Registry.GOO.get(), worldIn);
    }
//
//    public GooEntity(World worldIn, CompoundNBT tag) {
//        super(Registry.GOO.get(), worldIn);
//        read(tag);
//    }

    public GooEntity(World worldIn, Entity sender, FluidStack stack, double enchantedSpeed)
    {
        super(Registry.GOO.get(), worldIn);
        decayingVector = sender.getLookVec().normalize();
        holder = sender;
        Vector3d location = sender.getEyePosition(0f).add(decayingVector.mul(0.125d, 0.125d, 0.125d));
        decayingSpeed = 0.2d;// enchantedSpeed;
        goo = stack;
        if (!(stack.getFluid() instanceof GooBase)) {
            this.setDead();
        }

        this.setLocationAndAngles(location.x, location.y, location.z, holder.getYaw(0f), holder.getPitch(0f));
        this.setMotion(decayingVector.mul(decayingSpeed, decayingSpeed, decayingSpeed));
        this.proportions = new ArrayList<>();
        this.collisions = new ArrayList<>();
    }

    public GooEntity(EntityType<GooEntity> entityEntityType, World world)
    {
        super(entityEntityType, world);
    }

    int DEBUG_TICKS_ALLOWED = 100;
    int ticks = 0;
    @Override
    public void tick()
    {
        super.tick();
        ticks++;

        if (world.isRemote()) {
            // GooMod.debug("Things are happening in the goo you threw, client side.");
        } else {
            // GooMod.debug("Things are happening in the goo you threw.");
        }
//        if (goo.getAmount() > 0) {
//            goo.setAmount(goo.getAmount() - 1);
//        } else {
//            this.setDead();
//        }
        if (ticks > DEBUG_TICKS_ALLOWED) {
            this.setDead();
        }
    }

    @Override
    protected void registerData()
    {

    }

    @Override
    public void read(CompoundNBT tag)
    {
        super.read(tag);
        decayingVector = new Vector3d(tag.getDouble("vx"), tag.getDouble("vy"), tag.getDouble("vz"));
        Vector3d location = new Vector3d(tag.getDouble("lx"), tag.getDouble("ly"), tag.getDouble("lz"));
        this.setPosition(location.x, location.y, location.z);
        decayingSpeed = tag.getDouble("speed");
        isHeld = tag.getBoolean("held");
        if (tag.contains("holder")) {
            UUID playerId = tag.getUniqueId("holder");

            holder = world.getPlayerByUuid(playerId);

        }

        goo = FluidStack.loadFluidStackFromNBT(tag);
        proportions = new ArrayList<>();
        CompoundNBT p = tag.getCompound("proportions");
        for(int i = 0; i < p.getInt("boxes"); i++) {
            proportions.set(i, new AxisAlignedBB(
            new Vector3d(p.getDouble("lx"), p.getDouble("ly"), p.getDouble("lz")),
            new Vector3d(p.getDouble("hx"), p.getDouble("hy"), p.getDouble("hz"))));
        }
    }

    @Override
    protected void readAdditional(CompoundNBT compound)
    {

    }

    @Override
    protected void writeAdditional(CompoundNBT compound)
    {

    }

    @Override
    public IPacket<?> createSpawnPacket()
    {
        return NetworkHooks.getEntitySpawningPacket(this);
        // return new PacketSpawn(this.world.func_234923_W_(), serializeNBT(), Registry.GOO.getId(), this.getPosition());
    }

    @Override
    public CompoundNBT serializeNBT()
    {
        CompoundNBT e = new CompoundNBT();
        e.putDouble("vx", decayingVector.x);
        e.putDouble("vy", decayingVector.y);
        e.putDouble("vz", decayingVector.z);
        e.putDouble("lx", getPosX());
        e.putDouble("ly", getPosY());
        e.putDouble("lz", getPosZ());
        e.putDouble("speed", decayingSpeed);
        e.putBoolean("held", isHeld);
        e.putUniqueId("holder", holder.getUniqueID());
        goo.writeToNBT(e);
        CompoundNBT p = new CompoundNBT();
        p.putInt("boxes", proportions.size());
        for(int i = 0; i < proportions.size(); i++) {
            p.putDouble("lx" + i, proportions.get(i).minX);
            p.putDouble("ly" + i, proportions.get(i).minY);
            p.putDouble("lz" + i, proportions.get(i).minZ);
            p.putDouble("hx" + i, proportions.get(i).maxX);
            p.putDouble("hy" + i, proportions.get(i).maxY);
            p.putDouble("hz" + i, proportions.get(i).maxZ);
        }
        e.put("proportions", p);
        return e;
    }

    @Override
    public void writeSpawnData(PacketBuffer buffer)
    {
        buffer.writeCompoundTag(serializeNBT());
    }

    @Override
    public void readSpawnData(PacketBuffer additionalData)
    {
        CompoundNBT tag = additionalData.readCompoundTag();
        deserializeNBT(tag);
    }
}
