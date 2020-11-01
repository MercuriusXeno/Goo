package com.xeno.goo.entities;

import com.google.common.collect.Lists;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.CrystalNestTile;
import com.xeno.goo.tiles.TroughTile;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.FlyingMovementController;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.IFlyingAnimal;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.IParticleData;
import net.minecraft.pathfinding.FlyingPathNavigator;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.tags.ITag;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.village.PointOfInterest;
import net.minecraft.village.PointOfInterestManager;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GooBee extends AnimalEntity implements IFlyingAnimal {
    private static final DataParameter<Byte> DATA_FLAGS_ID = EntityDataManager.createKey(GooBee.class, DataSerializers.BYTE);
    private float rollAmount;
    private float rollAmountO;
    private int ticksWithoutGooSinceExitingHive;
    private int stayOutOfHiveCountdown;
    private int numCropsGrownSincePollination;
    private int remainingCooldownBeforeLocatingNewHive = 0;
    private int remainingCooldownBeforeLocatingTrough = 0;
    private BlockPos savedTroughPos = null;
    private BlockPos nestPos = null;
    private DrinkGooGoal drinkGooGoal;
    private FindCrystalNestGoal findCrystalNestGoal;

    public GooBee(EntityType<GooBee> gooGooBeeType, World world) {
        super(gooGooBeeType, world);
        this.moveController = new FlyingMovementController(this, 20, true);
        this.lookController = new GooBee.BeeLookController(this);
        this.setPathPriority(PathNodeType.DANGER_FIRE, -1.0F);
        this.setPathPriority(PathNodeType.WATER, -1.0F);
        this.setPathPriority(PathNodeType.WATER_BORDER, -1.0F);
        this.setPathPriority(PathNodeType.COCOA, -1.0F);
        this.setPathPriority(PathNodeType.FENCE, -1.0F);
        setGrowingAge(-1);
    }

    public static AttributeModifierMap.MutableAttribute setCustomAttributes() {
        return MobEntity.func_233666_p_()
                .createMutableAttribute(Attributes.MAX_HEALTH, 1D)
                .createMutableAttribute(Attributes.FLYING_SPEED, (double) 1.6F)
                .createMutableAttribute(Attributes.MOVEMENT_SPEED, (double) 1.2F)
                .createMutableAttribute(Attributes.ATTACK_DAMAGE, 1.0D)
                .createMutableAttribute(Attributes.FOLLOW_RANGE, 16.0D);
    }

    protected void registerData() {
        super.registerData();
        this.dataManager.register(DATA_FLAGS_ID, (byte) 0);
    }

    public float getBlockPathWeight(BlockPos pos, IWorldReader worldIn) {
        return worldIn.getBlockState(pos).isAir() ? 10.0F : 0.0F;
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new EnterCrystalNestGoal());
        this.findCrystalNestGoal = new FindCrystalNestGoal();
        this.goalSelector.addGoal(1, this.findCrystalNestGoal);
        this.goalSelector.addGoal(2, new UpdateCrystalNestGoal());
        this.drinkGooGoal = new DrinkGooGoal();
        this.goalSelector.addGoal(3, this.drinkGooGoal);
        FindTroughGoal findTroughGoal = new FindTroughGoal();
        this.goalSelector.addGoal(4, findTroughGoal);
        this.goalSelector.addGoal(5, new GooBee.WanderGoal());
        this.goalSelector.addGoal(6, new SwimGoal(this));
    }

    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        if (this.hasNest()) {
            compound.put("HivePos", NBTUtil.writeBlockPos(this.getNestPos()));
        }

        if (this.hasTrough()) {
            compound.put("TroughPos", NBTUtil.writeBlockPos(this.getTroughPos()));
        }

        compound.putBoolean("HasGoo", this.hasGoo());
        compound.putInt("TicksSincePollination", this.ticksWithoutGooSinceExitingHive);
        compound.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        compound.putInt("CropsGrownSincePollination", this.numCropsGrownSincePollination);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    public void readAdditional(CompoundNBT compound) {
        this.nestPos = null;
        if (compound.contains("HivePos")) {
            this.nestPos = NBTUtil.readBlockPos(compound.getCompound("HivePos"));
        }

        this.savedTroughPos = null;
        if (compound.contains("TroughPos")) {
            this.savedTroughPos = NBTUtil.readBlockPos(compound.getCompound("TroughPos"));
        }

        super.readAdditional(compound);
        this.setHasGoo(compound.getBoolean("HasGoo"));
        this.ticksWithoutGooSinceExitingHive = compound.getInt("TicksSincePollination");
        this.stayOutOfHiveCountdown = compound.getInt("CannotEnterHiveTicks");
        this.numCropsGrownSincePollination = compound.getInt("CropsGrownSincePollination");
    }

    /**
     * Called to update the entity's position/logic.
     */
    public void tick() {
        super.tick();
        if (this.growingAge >= 0) {
            setGrowingAge(-1);
        }
        if (this.hasGoo() && this.rand.nextFloat() < 0.05F) {
            this.addParticle();
        }

        this.updateBodyPitch();
    }

    private void addParticle() {

        BasicParticleType type = Registry.vaporParticleFromFluid(Registry.CHROMATIC_GOO.get());
        if (type == null) {
            return;
        }
        AxisAlignedBB box = getBoundingBox();
        for (int i = 0; i < 4; i++) {
            float scale = world.rand.nextFloat() / 6f + 0.25f;
            Vector3d lowerBounds = new Vector3d(box.minX, box.minY, box.minZ);
            Vector3d upperBounds = new Vector3d(box.maxX, box.maxY, box.maxZ);
            Vector3d threshHoldMax = upperBounds.subtract(lowerBounds);
            Vector3d centeredBounds = threshHoldMax.scale(0.5f);
            Vector3d center = lowerBounds.add(centeredBounds);
            Vector3d randomOffset = threshHoldMax.mul(world.rand.nextFloat() - 0.5f,
                    world.rand.nextFloat() - 0.5f,
                    world.rand.nextFloat() - 0.5f).scale(scale * 2f);
            Vector3d spawnVec = center.add(randomOffset);
            // make sure the spawn area is offset in a way that puts the particle outside of the block side we live on
            // Vector3d offsetVec = Vector3d.copy(sideWeLiveOn().getDirectionVec()).mul(threshHoldMax.x, threshHoldMax.y, threshHoldMax.z);
            ((ServerWorld) world).spawnParticle(type, spawnVec.x, spawnVec.y, spawnVec.z,
                    1, 0d, 0d, 0d, scale);
        }
    }

    private void startMovingTo(BlockPos pos) {
        Vector3d vector3d = Vector3d.copyCenteredHorizontally(pos);
        int i = 0;
        BlockPos blockpos = this.getPosition();
        int j = (int) vector3d.y - blockpos.getY();
        if (j > 2) {
            i = 4;
        } else if (j < -2) {
            i = -4;
        }

        int k = 6;
        int l = 8;
        int i1 = blockpos.manhattanDistance(pos);
        if (i1 < 15) {
            k = i1 / 2;
            l = i1 / 2;
        }

        Vector3d vector3d1 = RandomPositionGenerator.func_226344_b_(this, 
                k, l, i, vector3d, (double) ((float) Math.PI / 10F));
        if (vector3d1 != null) {
            this.navigator.setRangeMultiplier(0.5F);
            this.navigator.tryMoveToXYZ(vector3d1.x, vector3d1.y, vector3d1.z, 1.0D);
        }
    }
    
    public BlockPos getTroughPos() {
        return this.savedTroughPos;
    }

    public boolean hasTrough() {
        return this.savedTroughPos != null;
    }

    public void setTroughPos(BlockPos pos) {
        this.savedTroughPos = pos;
    }

    private boolean failedDrinkingGooTooLong() {
        return this.ticksWithoutGooSinceExitingHive > 3600;
    }

    private boolean canEnterHive() {
        if (this.stayOutOfHiveCountdown <= 0 && !this.drinkGooGoal.isRunning()) {
            boolean flag = this.failedDrinkingGooTooLong() ||this.world.isNightTime() || this.hasGoo();
            return flag && !this.isNestNearFire();
        } else {
            return false;
        }
    }

    public void setStayOutOfHiveCountdown(int p_226450_1_) {
        this.stayOutOfHiveCountdown = p_226450_1_;
    }
    
    public float getBodyPitch(float p_226455_1_) {
        return MathHelper.lerp(p_226455_1_, this.rollAmountO, this.rollAmount);
    }

    private void updateBodyPitch() {
        this.rollAmountO = this.rollAmount;
        if (this.isNearTarget()) {
            this.rollAmount = Math.min(1.0F, this.rollAmount + 0.2F);
        } else {
            this.rollAmount = Math.max(0.0F, this.rollAmount - 0.24F);
        }

    }

    protected void updateAITasks() {
        if (this.isInWaterOrBubbleColumn()) {
            ++this.underWaterTicks;
        } else {
            this.underWaterTicks = 0;
        }

        if (!this.hasGoo()) {
            ++this.ticksWithoutGooSinceExitingHive;
        }
    }

    public void resetTicksWithoutDrinkingGoo() {
        this.ticksWithoutGooSinceExitingHive = 0;
    }

    private boolean isNestNearFire() {
        if (this.nestPos == null) {
            return false;
        } else {
            TileEntity tileentity = this.world.getTileEntity(this.nestPos);
            return tileentity instanceof CrystalNestTile && ((CrystalNestTile) tileentity).isNearFire();
        }
    }

    private boolean doesNestHaveSpace(BlockPos pos) {
        TileEntity tileentity = this.world.getTileEntity(pos);
        if (tileentity instanceof CrystalNestTile) {
            return !((CrystalNestTile) tileentity).isFullOfBees();
        } else {
            return false;
        }
    }

    public boolean hasNest() {
        return this.nestPos != null;
    }

    public BlockPos getNestPos() {
        return this.nestPos;
    }

    /**
     * Called frequently so the entity can update its state every tick as required. For example, zombies and skeletons
     * use this to react to sunlight and start to burn.
     */
    public void livingTick() {
        super.livingTick();
        if (!this.world.isRemote) {
            if (this.stayOutOfHiveCountdown > 0) {
                --this.stayOutOfHiveCountdown;
            }

            if (this.remainingCooldownBeforeLocatingNewHive > 0) {
                --this.remainingCooldownBeforeLocatingNewHive;
            }

            if (this.remainingCooldownBeforeLocatingTrough > 0) {
                --this.remainingCooldownBeforeLocatingTrough;
            }

            if (this.ticksExisted % 20 == 0 && !this.isHiveValid()) {
                this.nestPos = null;
            }
        }

    }

    private boolean isHiveValid() {
        if (!this.hasNest()) {
            return false;
        } else {
            TileEntity tileentity = this.world.getTileEntity(this.nestPos);
            return tileentity instanceof CrystalNestTile;
        }
    }

    public boolean hasGoo() {
        return this.getBeeFlag(8);
    }

    private void setHasGoo(boolean hasGoo) {
        if (hasGoo) {
            this.resetTicksWithoutDrinkingGoo();
        }

        this.setBeeFlag(8, hasGoo);
    }

    private boolean isNearTarget() {
        return this.getBeeFlag(2);
    }

    private boolean isTooFar(BlockPos pos) {
        return !this.isWithinDistance(pos, 32);
    }

    private void setBeeFlag(int flagId, boolean p_226404_2_) {
        if (p_226404_2_) {
            this.dataManager.set(DATA_FLAGS_ID, (byte) (this.dataManager.get(DATA_FLAGS_ID) | flagId));
        } else {
            this.dataManager.set(DATA_FLAGS_ID, (byte) (this.dataManager.get(DATA_FLAGS_ID) & ~flagId));
        }
    }

    private boolean getBeeFlag(int flagId) {
        return (this.dataManager.get(DATA_FLAGS_ID) & flagId) != 0;
    }

    /**
     * Returns new PathNavigateGround instance
     */
    protected PathNavigator createNavigator(World worldIn) {
        FlyingPathNavigator flyingpathnavigator = new FlyingPathNavigator(this, worldIn) {
            public boolean canEntityStandOnPos(BlockPos pos) {
                return !this.world.getBlockState(pos.down()).isAir();
            }

            public void tick() {
                if (!GooBee.this.drinkGooGoal.isRunning()) {
                    super.tick();
                }
            }
        };
        flyingpathnavigator.setCanOpenDoors(false);
        flyingpathnavigator.setCanSwim(false);
        flyingpathnavigator.setCanEnterDoors(true);
        return flyingpathnavigator;
    }

    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }

    private boolean isTrough(BlockPos pos) {
        return this.world.isBlockPresent(pos) && this.world.getBlockState(pos).getBlock().equals(BlocksRegistry.Trough.get());
    }

    // 32 blocks.
    private double A_REASONABLE_RENDER_DISTANCE_SQUARED = 1024;
    @Override
    public boolean isInRangeToRenderDist(double distance)
    {
        return distance < A_REASONABLE_RENDER_DISTANCE_SQUARED;
    }

    protected void playStepSound(BlockPos pos, BlockState blockIn) {
    }

    protected SoundEvent getAmbientSound() {
        return null;
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return Registry.GOO_BEE_SHATTER_SOUND.get();
    }

    protected SoundEvent getDeathSound() {
        return Registry.GOO_BEE_SHATTER_SOUND.get();
    }

    /**
     * Returns the volume for the sounds this mob makes.
     */
    protected float getSoundVolume() {
        return 0.4F;
    }

    public GooBee func_241840_a(ServerWorld world, AgeableEntity p_241840_2_) {
        return Registry.GOO_BEE.get().create(world);
    }

    protected float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn) {
        return this.isChild() ? sizeIn.height * 0.5F : sizeIn.height * 0.5F;
    }

    public boolean onLivingFall(float distance, float damageMultiplier) {
        return false;
    }

    protected void updateFallState(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    protected boolean makeFlySound() {
        return true;
    }

    public void onHoneyDelivered() {
        this.setHasGoo(false);
    }

    /**
     * Called when the entity is attacked.
     */
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            Entity entity = source.getTrueSource();
            if (!this.world.isRemote) {
                this.drinkGooGoal.cancel();
            }

            return super.attackEntityFrom(source, amount);
        }
    }

    public CreatureAttribute getCreatureAttribute() {
        return CreatureAttribute.ARTHROPOD;
    }

    protected void handleFluidJump(ITag<Fluid> fluidTag) {
        this.setMotion(this.getMotion().add(0.0D, 0.01D, 0.0D));
    }

    public Vector3d func_241205_ce_() {
        return new Vector3d(0.0D, (double) (0.5F * this.getEyeHeight()), (double) (this.getWidth() * 0.2F));
    }

    private boolean isWithinDistance(BlockPos pos, int distance) {
        return pos.withinDistance(this.getPosition(), (double) distance);
    }

    class BeeLookController extends LookController {
        BeeLookController(MobEntity beeIn) {
            super(beeIn);
        }

        protected boolean shouldResetPitch() {
            return !GooBee.this.drinkGooGoal.isRunning();
        }
    }

    class EnterCrystalNestGoal extends GooBee.PassiveGoal {
        private EnterCrystalNestGoal() {
        }

        public boolean canBeeStart() {
            if (GooBee.this.hasNest() && GooBee.this.canEnterHive() && GooBee.this.nestPos.withinDistance(GooBee.this.getPositionVec(), 2.0D)) {
                TileEntity tileentity = GooBee.this.world.getTileEntity(GooBee.this.nestPos);
                if (tileentity instanceof CrystalNestTile) {
                    CrystalNestTile te = (CrystalNestTile) tileentity;
                    if (!te.isFullOfBees()) {
                        return true;
                    }

                    GooBee.this.nestPos = null;
                }
            }

            return false;
        }

        public boolean canBeeContinue() {
            return false;
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void startExecuting() {
            TileEntity tileentity = GooBee.this.world.getTileEntity(GooBee.this.nestPos);
            if (tileentity instanceof CrystalNestTile) {
                CrystalNestTile te = (CrystalNestTile) tileentity;
                te.tryEnterHive(GooBee.this, GooBee.this.hasGoo());
            }

        }
    }

    public class FindCrystalNestGoal extends GooBee.PassiveGoal {
        private int ticks = GooBee.this.world.rand.nextInt(10);
        private final List<BlockPos> possibleNests = Lists.newArrayList();
        private Path path = null;
        private int ticksTried;

        FindCrystalNestGoal() {
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canBeeStart() {
            return GooBee.this.nestPos != null && !GooBee.this.detachHome() && GooBee.this.canEnterHive() && !this.isCloseEnough(GooBee.this.nestPos) && GooBee.this.world.getBlockState(GooBee.this.nestPos).getBlock().equals(BlocksRegistry.CrystalNest.get());
        }

        public boolean canBeeContinue() {
            return this.canBeeStart();
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void startExecuting() {
            this.ticks = 0;
            this.ticksTried = 0;
            super.startExecuting();
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        public void resetTask() {
            this.ticks = 0;
            this.ticksTried = 0;
            GooBee.this.navigator.clearPath();
            GooBee.this.navigator.resetRangeMultiplier();
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick() {
            if (GooBee.this.nestPos != null) {
                ++this.ticks;
                if (this.ticks > 600) {
                    this.makeChosenHivePossibleHive();
                } else if (!GooBee.this.navigator.hasPath()) {
                    if (!GooBee.this.isWithinDistance(GooBee.this.nestPos, 16)) {
                        if (GooBee.this.isTooFar(GooBee.this.nestPos)) {
                            this.reset();
                        } else {
                            GooBee.this.startMovingTo(GooBee.this.nestPos);
                        }
                    } else {
                        boolean flag = this.startMovingToFar(GooBee.this.nestPos);
                        if (!flag) {
                            this.makeChosenHivePossibleHive();
                        } else if (this.path != null && GooBee.this.navigator.getPath().isSamePath(this.path)) {
                            ++this.ticksTried;
                            if (this.ticksTried > 60) {
                                this.reset();
                                this.ticksTried = 0;
                            }
                        } else {
                            this.path = GooBee.this.navigator.getPath();
                        }

                    }
                }
            }
        }

        private boolean startMovingToFar(BlockPos pos) {
            GooBee.this.navigator.setRangeMultiplier(10.0F);
            GooBee.this.navigator.tryMoveToXYZ((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), 1.0D);
            return GooBee.this.navigator.getPath() != null && GooBee.this.navigator.getPath().reachesTarget();
        }

        private boolean isPossibleHive(BlockPos pos) {
            return this.possibleNests.contains(pos);
        }

        private void addPossibleHives(BlockPos pos) {
            this.possibleNests.add(pos);

            while (this.possibleNests.size() > 3) {
                this.possibleNests.remove(0);
            }

        }

        private void clearPossibleHives() {
            this.possibleNests.clear();
        }

        private void makeChosenHivePossibleHive() {
            if (GooBee.this.nestPos != null) {
                this.addPossibleHives(GooBee.this.nestPos);
            }

            this.reset();
        }

        private void reset() {
            GooBee.this.nestPos = null;
            GooBee.this.remainingCooldownBeforeLocatingNewHive = 200;
        }

        private boolean isCloseEnough(BlockPos pos) {
            if (GooBee.this.isWithinDistance(pos, 2)) {
                return true;
            } else {
                Path path = GooBee.this.navigator.getPath();
                return path != null && path.getTarget().equals(pos) && path.reachesTarget() && path.isFinished();
            }
        }
    }

    public class FindTroughGoal extends GooBee.PassiveGoal {
        private int ticks = GooBee.this.world.rand.nextInt(10);

        FindTroughGoal() {
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canBeeStart() {
            return GooBee.this.savedTroughPos != null && !GooBee.this.detachHome() && this.shouldMoveToTrough() && GooBee.this.isTrough(GooBee.this.savedTroughPos) && !GooBee.this.isWithinDistance(GooBee.this.savedTroughPos, 2);
        }

        public boolean canBeeContinue() {
            return this.canBeeStart();
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void startExecuting() {
            this.ticks = 0;
            super.startExecuting();
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        public void resetTask() {
            this.ticks = 0;
            GooBee.this.navigator.clearPath();
            GooBee.this.navigator.resetRangeMultiplier();
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick() {
            if (GooBee.this.savedTroughPos != null) {
                ++this.ticks;
                if (this.ticks > 600) {
                    GooBee.this.savedTroughPos = null;
                } else if (!GooBee.this.navigator.hasPath()) {
                    if (GooBee.this.isTooFar(GooBee.this.savedTroughPos)) {
                        GooBee.this.savedTroughPos = null;
                    } else {
                        GooBee.this.startMovingTo(GooBee.this.savedTroughPos);
                    }
                }
            }
        }

        private boolean shouldMoveToTrough() {
            return GooBee.this.ticksWithoutGooSinceExitingHive > 2400;
        }
    }

    abstract class PassiveGoal extends Goal {
        private PassiveGoal() {
        }

        public abstract boolean canBeeStart();

        public abstract boolean canBeeContinue();

        /**
         * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
         * method as well.
         */
        public boolean shouldExecute() {
            return this.canBeeStart();
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        public boolean shouldContinueExecuting() {
            return this.canBeeContinue();
        }
    }

    class DrinkGooGoal extends GooBee.PassiveGoal {
        private final FluidStack DRAIN_AMOUNT = new FluidStack(Registry.CHROMATIC_GOO.get(), 1);
        private final Predicate<BlockState> troughPredicate = (state) -> state.getBlock().equals(BlocksRegistry.Trough.get());
        private int drankGooTicks = 0;
        private int lastDrankGooTick = 0;
        private boolean running;
        private Vector3d nextTarget;
        private int ticks = 0;

        DrinkGooGoal() {
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canBeeStart() {
            if (GooBee.this.remainingCooldownBeforeLocatingTrough > 0) {
                return false;
            } else if (GooBee.this.hasGoo()) {
                return false;
            } else {
                Optional<BlockPos> optional = this.getTrough();
                if (optional.isPresent()) {
                    IFluidHandler troughHandler = GooBee.troughHandlerFromPosition(world, optional.get());
                    if (troughHandler == null) {
                        return false;
                    }
                    GooBee.this.savedTroughPos = optional.get();
                    GooBee.this.navigator.tryMoveToXYZ((double) GooBee.this.savedTroughPos.getX() + 0.5D,
                            (double) GooBee.this.savedTroughPos.getY() + 0.25D,
                            (double) GooBee.this.savedTroughPos.getZ() + 0.5D, (double) 0.35F);
                    return true;
                } else {
                    return false;
                }
            }
        }

        public boolean canBeeContinue() {
            if (!this.running) {
                return false;
            } else if (!GooBee.this.hasTrough()) {
                return false;
            } else if (this.completedDrinkingGoo()) {
                return GooBee.this.rand.nextFloat() < 0.2F;
            } else if (GooBee.this.ticksExisted % 20 == 0 && !GooBee.this.isTrough(GooBee.this.savedTroughPos)) {
                GooBee.this.savedTroughPos = null;
                return false;
            } else {
                return true;
            }
        }

        private boolean completedDrinkingGoo() {
            return this.drankGooTicks > 100;
        }

        private boolean isRunning() {
            return this.running;
        }

        private void cancel() {
            this.running = false;
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void startExecuting() {
            this.drankGooTicks = 0;
            this.ticks = 0;
            this.lastDrankGooTick = 0;
            this.running = true;
            GooBee.this.resetTicksWithoutDrinkingGoo();
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        public void resetTask() {
            if (this.completedDrinkingGoo()) {
                GooBee.this.setHasGoo(true);
            }

            this.running = false;
            GooBee.this.navigator.clearPath();
            GooBee.this.remainingCooldownBeforeLocatingTrough = 200;
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick() {
            ++this.ticks;
            if (this.ticks > 600) {
                GooBee.this.savedTroughPos = null;
            } else {
                Vector3d vector3d = Vector3d.copyCenteredHorizontally(GooBee.this.savedTroughPos).add(0.0D, (double) 0.25F, 0.0D);
                if (vector3d.distanceTo(GooBee.this.getPositionVec()) > 1D) {
                    this.nextTarget = vector3d;
                    this.moveToNextTarget();
                } else {
                    if (this.nextTarget == null) {
                        this.nextTarget = vector3d;
                    }

                    boolean isVeryCloseToTarget = GooBee.this.getPositionVec().distanceTo(this.nextTarget) <= 0.1D;
                    // it seems to be taking more than 30 seconds to get to our trough.
                    if (!isVeryCloseToTarget && this.ticks > 600) {
                        GooBee.this.savedTroughPos = null;
                    } else {
                        // we're at the trough. Presuming it is valid, we drink from it.
                        if (isVeryCloseToTarget) {
                            GooBee.this.getLookController().setLookPosition(vector3d.getX(), vector3d.getY(), vector3d.getZ());
                        }

                        IFluidHandler fh = GooBee.troughHandlerFromPosition(world, GooBee.this.savedTroughPos);
                        if (fh != null) {
                            if (fh.getFluidInTank(0).getAmount() > 0) {
                                if (!this.completedDrinkingGoo()) {
                                    ++this.drankGooTicks;
                                    fh.drain(DRAIN_AMOUNT, IFluidHandler.FluidAction.EXECUTE);
                                    if (GooBee.this.rand.nextFloat() < 0.05F && this.drankGooTicks > this.lastDrankGooTick + 60) {
                                        this.lastDrankGooTick = this.drankGooTicks;
                                    }
                                }
                            } else {
                                // trough is empty, doesn't have the goo we want.
                                GooBee.this.savedTroughPos = null;
                            }
                        }
                    }
                }
            }
        }

        private void moveToNextTarget() {
            GooBee.this.getMoveHelper().setMoveTo(this.nextTarget.getX(), this.nextTarget.getY(), this.nextTarget.getZ(), (double) 1.35F);
        }

        private Optional<BlockPos> getTrough() {
            return this.findTrough(this.troughPredicate, 32.0D);
        }

        private Optional<BlockPos> findTrough(Predicate<BlockState> statePredicate, double distance) {
            BlockPos blockpos = GooBee.this.getPosition();
            BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

            for (int i = 0; (double) i <= distance; i = i > 0 ? -i : 1 - i) {
                for (int j = 0; (double) j < distance; ++j) {
                    for (int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                        for (int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                            blockpos$mutable.setAndOffset(blockpos, k, i - 1, l);
                            if (blockpos.withinDistance(blockpos$mutable, distance) && statePredicate.test(GooBee.this.world.getBlockState(blockpos$mutable))) {
                                return Optional.of(blockpos$mutable);
                            }
                        }
                    }
                }
            }

            return Optional.empty();
        }
    }

    private static IFluidHandler troughHandlerFromPosition(World world, BlockPos blockPos) {
        TileEntity te = world.getTileEntity(blockPos);
        if (!(te instanceof TroughTile)) {
            return null;
        }
        LazyOptional<IFluidHandler> fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
        if (!fh.isPresent()) {
            return null;
        }
        if (!fh.resolve().isPresent()) {
            return null;
        }
        if (!fh.resolve().get().getFluidInTank(0).getFluid().equals(Registry.CHROMATIC_GOO.get())) {
            return null;
        }
        return fh.resolve().get();
    }

    class UpdateCrystalNestGoal extends GooBee.PassiveGoal {
        private UpdateCrystalNestGoal() {
        }

        public boolean canBeeStart() {
            return GooBee.this.remainingCooldownBeforeLocatingNewHive == 0 && !GooBee.this.hasNest() && GooBee.this.canEnterHive();
        }

        public boolean canBeeContinue() {
            return false;
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void startExecuting() {
            GooBee.this.remainingCooldownBeforeLocatingNewHive = 200;
            List<BlockPos> list = this.getNearbyFreeNests();
            if (!list.isEmpty()) {
                for (BlockPos blockpos : list) {
                    if (!GooBee.this.findCrystalNestGoal.isPossibleHive(blockpos)) {
                        GooBee.this.nestPos = blockpos;
                        return;
                    }
                }

                GooBee.this.findCrystalNestGoal.clearPossibleHives();
                GooBee.this.nestPos = list.get(0);
            }
        }


        private List<BlockPos> getNearbyFreeNests() {
//            BlockPos blockpos = GooBee.this.getPosition();
//            PointOfInterestManager pointofinterestmanager = ((ServerWorld) GooBee.this.world).getPointOfInterestManager();
//            Stream<PointOfInterest> stream = pointofinterestmanager
//                    .func_219146_b((poit) -> true, //poit == Registry.CRYSTAL_NEST_POI.get(),
//                            blockpos, 20, PointOfInterestManager.Status.ANY);
//            return stream.map(PointOfInterest::getPos)
//                    .filter(GooBee.this::doesNestHaveSpace)
//                    .sorted(Comparator.comparingDouble((destBp) -> destBp.distanceSq(blockpos)))
//                    .collect(Collectors.toList());
            BlockPos blockpos = GooBee.this.getPosition();
            PointOfInterestManager pointofinterestmanager = ((ServerWorld) GooBee.this.world).getPointOfInterestManager();
            List<PointOfInterest> stream = pointofinterestmanager
                    .func_219146_b((poit) -> poit == Registry.CRYSTAL_NEST_POI.get(),
                            blockpos, 20, PointOfInterestManager.Status.ANY).collect(Collectors.toList());
            return stream.stream().map(PointOfInterest::getPos)
                    .filter(GooBee.this::doesNestHaveSpace)
                    .sorted(Comparator.comparingDouble((destBp) -> destBp.distanceSq(blockpos)))
                    .collect(Collectors.toList());
        }
    }

    class WanderGoal extends Goal {
        WanderGoal() {
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
         * method as well.
         */
        public boolean shouldExecute() {
            return GooBee.this.navigator.noPath() && GooBee.this.rand.nextInt(10) == 0;
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        public boolean shouldContinueExecuting() {
            return GooBee.this.navigator.hasPath();
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void startExecuting() {
            Vector3d vector3d = this.getRandomLocation();
            if (vector3d != null) {
                GooBee.this.navigator.setPath(GooBee.this.navigator.getPathToPos(new BlockPos(vector3d), 1), 0.35D);
            }
        }

        private Vector3d getRandomLocation() {
            Vector3d vector3d;
            if (GooBee.this.isHiveValid() && !GooBee.this.isWithinDistance(GooBee.this.nestPos, 7)) {
                Vector3d vector3d1 = Vector3d.copyCentered(GooBee.this.nestPos);
                vector3d = vector3d1.subtract(GooBee.this.getPositionVec()).normalize();
            } else {
                vector3d = GooBee.this.getLook(0.0F);
            }

            Vector3d vector3d2 = RandomPositionGenerator.findAirTarget(GooBee.this,
                    8, 7, vector3d, ((float) Math.PI / 2F), 2, 1);
            return vector3d2 != null ? vector3d2 : RandomPositionGenerator.findGroundTarget(GooBee.this,
                    8, 4, -2, vector3d, (double) ((float) Math.PI / 2F));
        }
    }
}