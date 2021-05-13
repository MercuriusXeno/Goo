package com.xeno.goo.tiles;

import com.google.common.collect.Lists;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.CrystalNest;
import com.xeno.goo.entities.GooBee;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.BeehiveTileEntity;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

public class CrystalNestTile  extends TileEntity implements ITickableTileEntity, IFluidHandler {
    private static final int READY_FOR_HARVEST_GOO_AMOUNT = 960;
    private final List<CrystalNestTile.Bee> bees = Lists.newArrayList();
    @Nullable
    private BlockPos troughPos = null;
    private FluidStack goo = FluidStack.EMPTY;

    public CrystalNestTile() {
        super(Registry.CRYSTAL_NEST_TILE.get());
    }

    /**
     * For tile entities, ensures the chunk containing the tile entity is saved to disk later - the game won't think it
     * hasn't changed and skip it.
     */
    public void markDirty() {
        if (this.isNearFire()) {
            this.scareBees(null, this.world.getBlockState(this.getPos()), CrystalNestTile.State.PANIC);
        }

        super.markDirty();
    }

    public boolean isNearFire() {
        if (this.world == null) {
            return false;
        } else {
            for(BlockPos blockpos : BlockPos.getAllInBoxMutable(this.pos.add(-1, -1, -1), this.pos.add(1, 1, 1))) {
                if (this.world.getBlockState(blockpos).getBlock() instanceof FireBlock) {
                    return true;
                }
            }

            return false;
        }
    }

    public boolean isFullOfBees() {
        return this.bees.size() == 6;
    }

    public void scareBees(@Nullable PlayerEntity player, BlockState state, CrystalNestTile.State nestState) {
        List<Entity> list = this.tryReleaseBee(state, nestState);
        if (player != null) {
            for(Entity entity : list) {
                if (entity instanceof GooBee) {
                    GooBee bee = (GooBee)entity;
                    if (player.getPositionVec().squareDistanceTo(entity.getPositionVec()) <= 16.0D) {
                        bee.setStayOutOfHiveCountdown(400);
                    }
                }
            }
        }

    }

    private List<Entity> tryReleaseBee(BlockState state, CrystalNestTile.State nestState) {
        List<Entity> list = Lists.newArrayList();
        this.bees.removeIf((e) -> {
            return this.spawnBee(state, e, list, nestState);
        });
        return list;
    }

    public int getBeeCount() {
        return this.bees.size();
    }

    public void tryEnterHive(Entity e) {
        if (getBeeCount() < 6) {
            if (e instanceof GooBee) {
                GooBee bee = (GooBee)e;
                int restTime = 300;
                if (bee.hasEnoughGoo()) {
                    restTime = 600;
                    // the flag is all we need; we "take" the bee's goo here, it becomes part of the hive.
                    int space = this.getTankCapacity(0) - this.getFluidInTank(0).getAmount();
                    if (space > 0) {
                        int amountToDrain = Math.min(space, READY_FOR_HARVEST_GOO_AMOUNT);
                        FluidStack drainedFluid = bee.drain(amountToDrain, FluidAction.EXECUTE);
                        this.fill(drainedFluid, FluidAction.EXECUTE);
                    }
                }
                e.stopRiding();
                e.removePassengers();
                CompoundNBT compoundnbt = new CompoundNBT();
                e.writeUnlessPassenger(compoundnbt);
                this.bees.add(new CrystalNestTile.Bee(compoundnbt, 0, restTime));
                if (this.world != null) {
                    if (bee.hasTrough() && (!this.hasTroughPos() || this.world.rand.nextBoolean())) {
                        this.troughPos = bee.getTroughPos();
                    }
                }

                BlockPos blockpos = this.getPos();
                this.world.playSound(null, blockpos.getX(), blockpos.getY(), blockpos.getZ(),
                        SoundEvents.BLOCK_BEEHIVE_ENTER, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }

            e.remove();
        }
    }

    private boolean spawnBee(BlockState state, CrystalNestTile.Bee bee, @Nullable List<Entity> bees, CrystalNestTile.State nestState) {
        if (this.world.isNightTime() && nestState != CrystalNestTile.State.PANIC) {
            return false;
        } else {
            BlockPos blockpos = this.getPos();
            CompoundNBT compoundnbt = bee.entityData;
            compoundnbt.remove("Passengers");
            compoundnbt.remove("Leash");
            compoundnbt.remove("UUID");
            Direction direction = state.get(CrystalNest.FACING);
            BlockPos blockpos1 = blockpos.offset(direction);
            boolean flag = !this.world.getBlockState(blockpos1).getCollisionShape(this.world, blockpos1).isEmpty();
            if (flag && nestState != CrystalNestTile.State.PANIC) {
                return false;
            } else {
                Entity entity = EntityType.loadEntityAndExecute(compoundnbt, this.world, (e) -> e);
                if (entity != null) {
                    if (!entity.getType().equals(Registry.GOO_BEE)) {
                        return false;
                    } else {
                        if (entity instanceof GooBee) {
                            GooBee beeEntity = (GooBee)entity;
                            if (this.hasTroughPos() && !beeEntity.hasTrough() && this.world.rand.nextFloat() < 0.9F) {
                                beeEntity.setTroughPos(this.troughPos);
                            }

                            if (bees != null) {
                                bees.add(beeEntity);
                            }

                            float f = entity.getWidth();
                            double d3 = flag ? 0.0D : 0.55D + (double)(f / 2.0F);
                            double d0 = (double)blockpos.getX() + 0.5D + d3 * (double)direction.getXOffset();
                            double d1 = (double)blockpos.getY() + 0.5D - (double)(entity.getHeight() / 2.0F);
                            double d2 = (double)blockpos.getZ() + 0.5D + d3 * (double)direction.getZOffset();
                            entity.setLocationAndAngles(d0, d1, d2, entity.rotationYaw, entity.rotationPitch);
                        }

                        this.world.playSound(null, blockpos, SoundEvents.BLOCK_BEEHIVE_EXIT, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        return this.world.addEntity(entity);
                    }
                } else {
                    return false;
                }
            }
        }
    }

    private boolean hasTroughPos() {
        return this.troughPos != null;
    }

    private void tickBees() {
        Iterator<CrystalNestTile.Bee> iterator = this.bees.iterator();

        CrystalNestTile.Bee beehivetileentity$bee;
        for(BlockState blockstate = this.getBlockState(); iterator.hasNext(); beehivetileentity$bee.ticksInHive++) {
            beehivetileentity$bee = iterator.next();
            if (beehivetileentity$bee.ticksInHive > beehivetileentity$bee.minOccupationTicks) {
                CrystalNestTile.State beehivetileentity$state = beehivetileentity$bee.entityData.getBoolean("HasGoo") ? CrystalNestTile.State.GOO_DELIVERED : CrystalNestTile.State.BEE_RELEASED;
                if (this.spawnBee(blockstate, beehivetileentity$bee, (List<Entity>)null, beehivetileentity$state)) {
                    iterator.remove();
                }
            }
        }

    }

    public void tick() {
        if (!this.world.isRemote) {
            this.tickBees();
            BlockPos blockpos = this.getPos();
            if (this.bees.size() > 0 && this.world.getRandom().nextDouble() < 0.005D) {
                double d0 = (double)blockpos.getX() + 0.5D;
                double d1 = (double)blockpos.getY();
                double d2 = (double)blockpos.getZ() + 0.5D;
                this.world.playSound((PlayerEntity)null, d0, d1, d2, SoundEvents.BLOCK_BEEHIVE_WORK, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    public void read(BlockState state, CompoundNBT nbt) {
        super.read(state, nbt);
        this.bees.clear();
        ListNBT listnbt = nbt.getList("Bees", 10);

        for(int i = 0; i < listnbt.size(); ++i) {
            CompoundNBT compoundnbt = listnbt.getCompound(i);
            CrystalNestTile.Bee te$bee = new CrystalNestTile.Bee(compoundnbt.getCompound("EntityData"), compoundnbt.getInt("TicksInHive"), compoundnbt.getInt("MinOccupationTicks"));
            this.bees.add(te$bee);
        }

        this.troughPos = null;
        if (nbt.contains("TroughPos")) {
            this.troughPos = NBTUtil.readBlockPos(nbt.getCompound("TroughPos"));
        }

        if (nbt.contains("goo")) {
            this.goo = FluidStack.loadFluidStackFromNBT(nbt.getCompound("goo"));
        }
    }

    public CompoundNBT write(CompoundNBT compound) {
        super.write(compound);
        compound.put("Bees", this.getBees());
        if (this.hasTroughPos()) {
            compound.put("TroughPos", NBTUtil.writeBlockPos(this.troughPos));
        }
        compound.put("goo", this.goo.writeToNBT(new CompoundNBT()));

        return compound;
    }

    public ListNBT getBees() {
        ListNBT listnbt = new ListNBT();

        for(CrystalNestTile.Bee beehivetileentity$bee : this.bees) {
            beehivetileentity$bee.entityData.remove("UUID");
            CompoundNBT compoundnbt = new CompoundNBT();
            compoundnbt.put("EntityData", beehivetileentity$bee.entityData);
            compoundnbt.putInt("TicksInHive", beehivetileentity$bee.ticksInHive);
            compoundnbt.putInt("MinOccupationTicks", beehivetileentity$bee.minOccupationTicks);
            listnbt.add(compoundnbt);
        }

        return listnbt;
    }

    static class Bee {
        private final CompoundNBT entityData;
        private int ticksInHive;
        private final int minOccupationTicks;

        private Bee(CompoundNBT nbt, int ticksInHive, int minOccupationTicks) {
            nbt.remove("UUID");
            this.entityData = nbt;
            this.ticksInHive = ticksInHive;
            this.minOccupationTicks = minOccupationTicks;
        }
    }

    public static enum State {
        GOO_DELIVERED,
        BEE_RELEASED,
        PANIC;
    }


    @Override
    public int getTanks()
    {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank)
    {
        return goo;
    }

    @Override
    public int getTankCapacity(int tank)
    {
        return READY_FOR_HARVEST_GOO_AMOUNT;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack)
    {
        return stack.isFluidEqual(this.goo);
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action)
    {
        if (this.world.isRemote()) {
            return 0;
        }
        if (resource.isEmpty()) {
            return 0;
        }
        int spaceRemaining = getTankCapacity(1) - goo.getAmount();
        int transferAmount = Math.min(resource.getAmount(), spaceRemaining);
        if (action == IFluidHandler.FluidAction.EXECUTE && transferAmount > 0) {
            if (goo.isEmpty()) {
                goo = resource;
            } else {
                goo.setAmount(goo.getAmount() + transferAmount);
            }
            if (this.goo.getAmount() >= READY_FOR_HARVEST_GOO_AMOUNT) {
                BlockState state = this.world.getBlockState(this.getPos());
                this.world.setBlockState(this.getPos(), state.with(CrystalNest.GOO_FULL, true));
            }
        }

        return transferAmount;
    }

    public void resetGooAmount() {
        this.goo = FluidStack.EMPTY;
        BlockState state = this.world.getBlockState(this.getPos());
        if (state.get(CrystalNest.GOO_FULL)) {
            this.world.setBlockState(this.getPos(), state.with(CrystalNest.GOO_FULL, false));
        }

    }

    @Override
    public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action)
    {
        if (this.world.isRemote()) {
            return FluidStack.EMPTY;
        }
        if (this.goo.isEmpty()) {
            return goo;
        }
        FluidStack result = new FluidStack(goo.getFluid(), Math.min(goo.getAmount(), resource.getAmount()));
        if (action == IFluidHandler.FluidAction.EXECUTE) {
            goo.setAmount(goo.getAmount() - result.getAmount());
        }

        return result;
    }

    @Override
    public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action)
    {
        if (this.world.isRemote()) {
            return FluidStack.EMPTY;
        }
        if (this.goo.isEmpty()) {
            return goo;
        }
        FluidStack result = new FluidStack(goo.getFluid(), Math.min(goo.getAmount(), maxDrain));
        if (action == IFluidHandler.FluidAction.EXECUTE) {
            goo.setAmount(goo.getAmount() - result.getAmount());
        }

        return result;
    }
}