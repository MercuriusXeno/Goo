package com.xeno.goo.tiles;

import com.google.common.collect.Lists;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.entities.GooBee;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

public class CrystalNestTile  extends TileEntity implements ITickableTileEntity {
    private final List<CrystalNestTile.Bee> bees = Lists.newArrayList();
    @Nullable
    private BlockPos troughPos = null;

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

    public boolean hasNoBees() {
        return this.bees.isEmpty();
    }

    public boolean isFullOfBees() {
        return this.bees.size() == 6;
    }

    public void scareBees(@Nullable PlayerEntity p_226963_1_, BlockState p_226963_2_, CrystalNestTile.State p_226963_3_) {
        List<Entity> list = this.tryReleaseBee(p_226963_2_, p_226963_3_);
        if (p_226963_1_ != null) {
            for(Entity entity : list) {
                if (entity instanceof BeeEntity) {
                    BeeEntity beeentity = (BeeEntity)entity;
                    if (p_226963_1_.getPositionVec().squareDistanceTo(entity.getPositionVec()) <= 16.0D) {
                        if (!this.isSmoked()) {
                            beeentity.setAttackTarget(p_226963_1_);
                        } else {
                            beeentity.setStayOutOfHiveCountdown(400);
                        }
                    }
                }
            }
        }

    }

    private List<Entity> tryReleaseBee(BlockState p_226965_1_, CrystalNestTile.State p_226965_2_) {
        List<Entity> list = Lists.newArrayList();
        this.bees.removeIf((p_226966_4_) -> {
            return this.spawnBee(p_226965_1_, p_226966_4_, list, p_226965_2_);
        });
        return list;
    }

    public void tryEnterHive(Entity e, boolean hasNectar) {
        this.tryEnterHive(e, hasNectar, 0);
    }

    public int getBeeCount() {
        return this.bees.size();
    }

    public static int getHoneyLevel(BlockState p_226964_0_) {
        return p_226964_0_.get(BeehiveBlock.HONEY_LEVEL);
    }

    public boolean isSmoked() {
        return CampfireBlock.isSmokingBlockAt(this.world, this.getPos());
    }

    public void tryEnterHive(Entity e, boolean hasNectar, int ticksInHive) {
        if (this.bees.size() < 3) {
            e.stopRiding();
            e.removePassengers();
            CompoundNBT compoundnbt = new CompoundNBT();
            e.writeUnlessPassenger(compoundnbt);
            this.bees.add(new CrystalNestTile.Bee(compoundnbt, ticksInHive, hasNectar ? 2400 : 600));
            if (this.world != null) {
                if (e instanceof BeeEntity) {
                    BeeEntity beeentity = (BeeEntity)e;
                    if (beeentity.hasFlower() && (!this.hasTroughPos() || this.world.rand.nextBoolean())) {
                        this.troughPos = beeentity.getFlowerPos();
                    }
                }

                BlockPos blockpos = this.getPos();
                this.world.playSound((PlayerEntity)null, (double)blockpos.getX(), (double)blockpos.getY(), (double)blockpos.getZ(), SoundEvents.BLOCK_BEEHIVE_ENTER, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }

            e.remove();
        }
    }

    private boolean spawnBee(BlockState state, CrystalNestTile.Bee bee, @Nullable List<Entity> p_235651_3_, CrystalNestTile.State p_235651_4_) {
        if (this.world.isNightTime() && p_235651_4_ != CrystalNestTile.State.PANIC) {
            return false;
        } else {
            BlockPos blockpos = this.getPos();
            CompoundNBT compoundnbt = bee.entityData;
            compoundnbt.remove("Passengers");
            compoundnbt.remove("Leash");
            compoundnbt.remove("UUID");
            Direction direction = state.get(BeehiveBlock.FACING);
            BlockPos blockpos1 = blockpos.offset(direction);
            boolean flag = !this.world.getBlockState(blockpos1).getCollisionShape(this.world, blockpos1).isEmpty();
            if (flag && p_235651_4_ != CrystalNestTile.State.PANIC) {
                return false;
            } else {
                Entity entity = EntityType.loadEntityAndExecute(compoundnbt, this.world, (p_226960_0_) -> {
                    return p_226960_0_;
                });
                if (entity != null) {
                    if (!entity.getType().equals(Registry.GOO_BEE.get())) {
                        return false;
                    } else {
                        if (entity instanceof GooBee) {
                            GooBee beeEntity = (GooBee)entity;
                            if (this.hasTroughPos() && !beeEntity.hasTrough() && this.world.rand.nextFloat() < 0.9F) {
                                beeEntity.setTroughPos(this.troughPos);
                            }

                            if (p_235651_4_ == CrystalNestTile.State.GOO_DELIVERED) {
                                if (state.getBlock().equals(BlocksRegistry.CrystalNest.get())) {
                                    int i = getHoneyLevel(state);
                                    if (i < 5) {
                                        beeEntity.onHoneyDelivered();
                                        this.world.setBlockState(this.getPos(), state.with(BeehiveBlock.HONEY_LEVEL, i + 1));
                                    }
                                }
                            }

                            this.func_235650_a_(bee.ticksInHive, beeEntity);
                            if (p_235651_3_ != null) {
                                p_235651_3_.add(beeEntity);
                            }

                            float f = entity.getWidth();
                            double d3 = flag ? 0.0D : 0.55D + (double)(f / 2.0F);
                            double d0 = (double)blockpos.getX() + 0.5D + d3 * (double)direction.getXOffset();
                            double d1 = (double)blockpos.getY() + 0.5D - (double)(entity.getHeight() / 2.0F);
                            double d2 = (double)blockpos.getZ() + 0.5D + d3 * (double)direction.getZOffset();
                            entity.setLocationAndAngles(d0, d1, d2, entity.rotationYaw, entity.rotationPitch);
                        }

                        this.world.playSound((PlayerEntity)null, blockpos, SoundEvents.BLOCK_BEEHIVE_EXIT, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        return this.world.addEntity(entity);
                    }
                } else {
                    return false;
                }
            }
        }
    }

    private void func_235650_a_(int p_235650_1_, GooBee bee) {
        int i = bee.getGrowingAge();
        if (i < 0) {
            bee.setGrowingAge(Math.min(0, i + p_235650_1_));
        } else if (i > 0) {
            bee.setGrowingAge(Math.max(0, i - p_235650_1_));
        }

        bee.setInLove(Math.max(0, bee.func_234178_eO_() - p_235650_1_));
        bee.resetTicksWithoutDrinkingGoo();
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
                CrystalNestTile.State beehivetileentity$state = beehivetileentity$bee.entityData.getBoolean("HasNectar") ? CrystalNestTile.State.GOO_DELIVERED : CrystalNestTile.State.BEE_RELEASED;
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
            CrystalNestTile.Bee beehivetileentity$bee = new CrystalNestTile.Bee(compoundnbt.getCompound("EntityData"), compoundnbt.getInt("TicksInHive"), compoundnbt.getInt("MinOccupationTicks"));
            this.bees.add(beehivetileentity$bee);
        }

        this.troughPos = null;
        if (nbt.contains("FlowerPos")) {
            this.troughPos = NBTUtil.readBlockPos(nbt.getCompound("FlowerPos"));
        }

    }

    public CompoundNBT write(CompoundNBT compound) {
        super.write(compound);
        compound.put("Bees", this.getBees());
        if (this.hasTroughPos()) {
            compound.put("FlowerPos", NBTUtil.writeBlockPos(this.troughPos));
        }

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
}