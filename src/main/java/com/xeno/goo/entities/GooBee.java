package com.xeno.goo.entities;

import com.google.common.collect.Lists;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.CrystalNest;
import com.xeno.goo.library.AudioHelper;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
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
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GooBee extends AnimalEntity implements IFlyingAnimal, IEntityAdditionalSpawnData, IFluidHandler {
    public static final int GOO_DELIVERY_AMOUNT = 64;
    private static final int TICKS_BEFORE_STOP_BEING_LAZY = 200;
    private float rollAmount;
    private float rollAmountO;
    private int ticksWithoutGooSinceExitingHive;
    private int stayOutOfHiveCountdown;
    private int remainingCooldownBeforeLocatingNewHive = 0;
    private int remainingCooldownBeforeLocatingTrough = 0;
    private BlockPos savedTroughPos = null;
    private BlockPos nestPos = null;
    private DrinkGooGoal drinkGooGoal;
    private FindCrystalNestGoal findCrystalNestGoal;
    private FluidStack goo = FluidStack.EMPTY;

    public GooBee(EntityType<GooBee> gooGooBeeType, World world) {
        super(gooGooBeeType, world);
        this.moveController = new FlyingMovementController(this, 20, true);
        this.lookController = new GooBee.BeeLookController(this);
        this.setPathPriority(PathNodeType.DANGER_FIRE, -1.0F);
        this.setPathPriority(PathNodeType.WATER, -1.0F);
        this.setPathPriority(PathNodeType.WATER_BORDER, 16.0F);
        this.setPathPriority(PathNodeType.COCOA, -1.0F);
        this.setPathPriority(PathNodeType.FENCE, -1.0F);
        setGrowingAge(-100000);
    }

    // crystal bees shatter when attacked, but are hardy/resistant otherwise
    @Override
    public boolean hitByEntity(Entity entityIn) {
        if (entityIn instanceof PlayerEntity) {
            if (world instanceof ServerWorld) {
                BlockState state = BlocksRegistry.CrystalNest.get().getDefaultState();
                ((ServerWorld)world).spawnParticle(new BlockParticleData(ParticleTypes.BLOCK, state),
                        getPosX(), getPosY(), getPosZ(), 12, 0d, 0d, 0d, 0.15d);
                AudioHelper.entityAudioEvent(this, getDeathSound(), SoundCategory.PLAYERS, 0.8f,
                        AudioHelper.PitchFormulas.HalfToOne);
                this.remove();
                return false;
            }
        }
        return super.hitByEntity(entityIn);
    }

    @Nullable
    @Override
    public AgeableEntity createChild(ServerWorld world, AgeableEntity mate) {
        return Registry.GOO_BEE.create(world);
    }

    protected void registerData() {
        super.registerData();
    }

    public float getBlockPathWeight(BlockPos pos, IWorldReader worldIn) {
        return worldIn.getBlockState(pos).isAir(worldIn, pos) ? 10.0F : 0.0F;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new EnterCrystalNestGoal());
        this.drinkGooGoal = new DrinkGooGoal();
        this.goalSelector.addGoal(1, this.drinkGooGoal);
        this.goalSelector.addGoal(2, new UpdateCrystalNestGoal());
        this.findCrystalNestGoal = new FindCrystalNestGoal();
        this.goalSelector.addGoal(2, this.findCrystalNestGoal);
        FindTroughGoal findTroughGoal = new FindTroughGoal();
        this.goalSelector.addGoal(3, findTroughGoal);
        this.goalSelector.addGoal(4, new GooBee.WanderGoal());
        this.goalSelector.addGoal(5, new SwimGoal(this));
    }

    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        if (this.hasNest()) {
            compound.put("HivePos", NBTUtil.writeBlockPos(this.getNestPos()));
        }

        if (this.hasTrough()) {
            compound.put("TroughPos", NBTUtil.writeBlockPos(this.getTroughPos()));
        }
        compound.putInt("TicksSincePollination", this.ticksWithoutGooSinceExitingHive);
        compound.putInt("CannotEnterHiveTicks", this.stayOutOfHiveCountdown);
        compound.put("goo", goo.writeToNBT(new CompoundNBT()));
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    public void readAdditional(CompoundNBT compound) {
        this.nestPos = null;
        if (compound.contains("HivePos")) {
            this.nestPos = NBTUtil.readBlockPos(compound.getCompound("HivePos"));
        }

        clearTrough();
        if (compound.contains("TroughPos")) {
            this.savedTroughPos = NBTUtil.readBlockPos(compound.getCompound("TroughPos"));
        }
        this.ticksWithoutGooSinceExitingHive = compound.getInt("TicksSincePollination");
        this.stayOutOfHiveCountdown = compound.getInt("CannotEnterHiveTicks");
        if (compound.contains("goo")) {
            this.goo = FluidStack.loadFluidStackFromNBT(compound.getCompound("goo"));
        }

        super.readAdditional(compound);
    }

    /**
     * Called to update the entity's position/logic.
     */
    public void tick() {
        super.tick();
        if (this.growingAge >= 0) {
            setGrowingAge(-10000);
        }
        if (this.hasEnoughGoo() && this.rand.nextFloat() < 0.05F) {
            this.addParticle();
        }

        this.updateBodyPitch();
    }

    private void addParticle() {
        if (!(world instanceof ServerWorld)) {
            return;
        }
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
                k, l, i, vector3d, ((float) Math.PI / 10F));
        if (vector3d1 != null) {
            this.navigator.setSearchDepthMultiplier(0.5F);
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
            boolean flag = this.failedDrinkingGooTooLong() ||this.world.isNightTime() || this.hasEnoughGoo();
            return flag && !this.isNestNearFire();
        } else {
            return false;
        }
    }

    public void setStayOutOfHiveCountdown(int countdownTimer) {
        this.stayOutOfHiveCountdown = countdownTimer;
    }
    
    public float getBodyPitch(float pitch) {
        return MathHelper.lerp(pitch, this.rollAmountO, this.rollAmount);
    }

    private void updateBodyPitch() {
        this.rollAmountO = this.rollAmount;
        this.rollAmount = Math.max(0.0F, this.rollAmount - 0.24F);
    }

    protected void updateAITasks() {
        if (!this.hasEnoughGoo()) {
            ++this.ticksWithoutGooSinceExitingHive;
        }
    }

    public boolean hasEnoughGoo() {
        return !this.goo.isEmpty() && this.goo.getAmount() >= GOO_DELIVERY_AMOUNT;
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

            if (this.ticksExisted % 20 == 0 && !this.isNestValid()) {
                this.nestPos = null;
            }
        }

    }

    private boolean isNestValid() {
        if (!this.hasNest()) {
            return false;
        } else {
            TileEntity tileentity = this.world.getTileEntity(this.nestPos);
            return tileentity instanceof CrystalNestTile;
        }
    }

    private boolean isTooFar(BlockPos pos) {
        return !this.isWithinDistance(pos, 32);
    }

    public static AttributeModifierMap.MutableAttribute setCustomAttributes() {
        return MobEntity.func_233666_p_()
                .createMutableAttribute(Attributes.MAX_HEALTH, 10D)
                .createMutableAttribute(Attributes.FLYING_SPEED, 0.6F)
                .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.3F)
                .createMutableAttribute(Attributes.ATTACK_DAMAGE, 1.0D)
                .createMutableAttribute(Attributes.FOLLOW_RANGE, 48.0D);
    }

    /**
     * Returns new PathNavigateGround instance
     */
    protected PathNavigator createNavigator(World worldIn) {
        FlyingPathNavigator flyingpathnavigator = new FlyingPathNavigator(this, worldIn) {
            public boolean canEntityStandOnPos(BlockPos pos) {
                return !worldIn.getBlockState(pos.down()).isAir(worldIn, pos);
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

    @Override
    public boolean isInRangeToRenderDist(double distance)
    {
        // 32 blocks.
        double reasonableRenderDistance = 1024;
        return distance < reasonableRenderDistance;
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

    @Override
    public void setMotion(Vector3d motionIn) {
        super.setMotion(motionIn);
    }

    @Override
    public void travel(Vector3d travelVector) {
        super.travel(travelVector);
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
        return pos.withinDistance(this.getPosition(), distance);
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
        return GOO_DELIVERY_AMOUNT;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack)
    {
        return goo.isEmpty() || stack.isFluidEqual(this.goo);
    }

    @Override
    public int fill(FluidStack resource, IFluidHandler.FluidAction action)
    {
        if (this.world.isRemote()) {
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
        }

        return transferAmount;
    }

    @Override
    public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action)
    {
        if (this.world.isRemote()) {
            return FluidStack.EMPTY;
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
        FluidStack result = new FluidStack(goo.getFluid(), Math.min(goo.getAmount(), maxDrain));
        if (action == IFluidHandler.FluidAction.EXECUTE) {
            goo.setAmount(goo.getAmount() - result.getAmount());
        }

        return result;
    }

    @Override
    public void writeSpawnData(PacketBuffer buffer) {

    }

    @Override
    public void readSpawnData(PacketBuffer additionalData) {

    }

    class BeeLookController extends LookController {
        BeeLookController(MobEntity beeIn) {
            super(beeIn);
        }

        @Override
        public void tick() {
            super.tick();
        }

        protected boolean shouldResetPitch() {
            return !GooBee.this.drinkGooGoal.isRunning();
        }
    }

    class EnterCrystalNestGoal extends GooBee.PassiveGoal {
        private EnterCrystalNestGoal() {
        }

        public boolean canBeeStart() {
            if (GooBee.this.hasNest() && GooBee.this.canEnterHive() && GooBee.this.nestPos.withinDistance(GooBee.this.getPositionVec(), 2D)) {
                TileEntity tileentity = GooBee.this.world.getTileEntity(GooBee.this.nestPos);
                if (tileentity instanceof CrystalNestTile) {
                    CrystalNestTile te = (CrystalNestTile) tileentity;
                    boolean shouldEnter = !GooBee.this.world.getBlockState(GooBee.this.nestPos).get(CrystalNest.GOO_FULL)
                            || GooBee.this.world.isNightTime();
                    if (!te.isFullOfBees() && shouldEnter) {
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
                GooBee.this.ticksWithoutGooSinceExitingHive = 0;
                CrystalNestTile te = (CrystalNestTile) tileentity;
                te.tryEnterHive(GooBee.this);
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
            return GooBee.this.nestPos != null && !GooBee.this.detachHome() && GooBee.this.canEnterHive()
                    && !this.isCloseEnough(GooBee.this.nestPos)
                    && GooBee.this.world.getBlockState(GooBee.this.nestPos)
                        .getBlock().equals(BlocksRegistry.CrystalNest.get());
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
            GooBee.this.navigator.resetSearchDepthMultiplier();
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
            GooBee.this.navigator.setSearchDepthMultiplier(10.0F);
            GooBee.this.navigator.tryMoveToXYZ(pos.getX(), pos.getY(), pos.getZ(), 1.0D);
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
            if (GooBee.this.isWithinDistance(pos, 1)) {
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
            return GooBee.this.savedTroughPos != null && !GooBee.this.detachHome()
                    && this.shouldMoveToTrough() && GooBee.this.isTrough(GooBee.this.savedTroughPos)
                    && !GooBee.this.isWithinDistance(GooBee.this.savedTroughPos, 2);
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
            GooBee.this.navigator.resetSearchDepthMultiplier();
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick() {
            if (GooBee.this.savedTroughPos != null) {
                ++this.ticks;
                if (this.ticks > 600) {
                    GooBee.this.clearTrough();
                } else if (!GooBee.this.navigator.hasPath()) {
                    if (GooBee.this.isTooFar(GooBee.this.savedTroughPos)) {
                        GooBee.this.clearTrough();
                    } else {
                        GooBee.this.startMovingTo(GooBee.this.savedTroughPos);
                    }
                }
            }
        }

        private boolean shouldMoveToTrough() {
            return GooBee.this.ticksWithoutGooSinceExitingHive > TICKS_BEFORE_STOP_BEING_LAZY;
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
        private final FluidStack DRAINED_LIQUID_PER_SIP = new FluidStack(Registry.CHROMATIC_GOO.get(), 1);
        private boolean running;
        private Vector3d nextTarget;
        private int ticks = 0;

        DrinkGooGoal() {
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canBeeStart() {
            if (GooBee.this.remainingCooldownBeforeLocatingTrough > 0) {
                return false;
            } else if (GooBee.this.hasEnoughGoo()) {
                return false;
            } else if (GooBee.this.rand.nextFloat() < 0.7F) {
                return false;
            } else {
                List<BlockPos> troughOptions = this.getTroughs();
                for(BlockPos troughOption : troughOptions) {
                    IFluidHandler troughHandler = GooBee.troughHandlerFromPosition(world, troughOption);
                    if (troughHandler == null) {
                        continue;
                    }
                    GooBee.this.savedTroughPos = troughOption;
                    GooBee.this.navigator.tryMoveToXYZ((double) GooBee.this.savedTroughPos.getX() + 0.5D,
                            (double) GooBee.this.savedTroughPos.getY() + 0.5D,
                            (double) GooBee.this.savedTroughPos.getZ() + 0.5D, 1.2D);
                    return true;
                }
            }
            return false;
        }

        public boolean canBeeContinue() {
            if (!this.running) {
                return false;
            } else if (!GooBee.this.hasTrough()) {
                return false;
            } else if (this.completedDrinkingGoo()) {
                return false;
            } else if (GooBee.this.ticksExisted % 20 == 0 && !GooBee.this.isTrough(GooBee.this.savedTroughPos)) {
                GooBee.this.clearTrough();
                return false;
            } else {
                return true;
            }
        }

        private boolean completedDrinkingGoo() {
            return !GooBee.this.goo.isEmpty() && GooBee.this.goo.getAmount() >= GOO_DELIVERY_AMOUNT;
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
            this.ticks = 0;
            this.running = true;
            GooBee.this.resetTicksWithoutDrinkingGoo();
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        public void resetTask() {
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
                GooBee.this.clearTrough();
            } else {
                Vector3d vector3d = Vector3d.copyCenteredHorizontally(GooBee.this.savedTroughPos)
                        .add(0.0D, 0.3D, 0.0D);
                if (vector3d.distanceTo(GooBee.this.getPositionVec()) > 0.7D) {
                    this.nextTarget = vector3d;
                    this.moveToNextTarget();
                } else {
                    if (this.nextTarget == null) {
                        this.nextTarget = vector3d;
                    }
                    double distanceToTarget = GooBee.this.getPositionVec().distanceTo(this.nextTarget);

                    boolean isVeryCloseToTarget = distanceToTarget <= 0.2D;
                    boolean isCloseEnoughToTarget = distanceToTarget <= 0.7D;
                    boolean isDerping = true;
                    // it seems to be taking more than 30 seconds to get to our trough.
                    if (!isVeryCloseToTarget && this.ticks > 600) {
                        GooBee.this.clearTrough();
                    } else {
                        // we're at the trough. Presuming it is valid, we drink from it.
                        if (isVeryCloseToTarget) {
                            boolean tryDerping = GooBee.this.rand.nextInt(25) == 0;
                            if (tryDerping) {
                                this.nextTarget = new Vector3d(vector3d.getX() + (double)this.getRandomOffset(), vector3d.getY(), vector3d.getZ() + (double)this.getRandomOffset());
                                GooBee.this.navigator.clearPath();
                            } else {
                                isDerping = false;
                            }
                            GooBee.this.getLookController().setLookPosition(vector3d.getX(), vector3d.getY(), vector3d.getZ());
                        }

                        if (isDerping) {
                            this.moveToNextTarget();
                        }

                        if (isCloseEnoughToTarget) {
                            IFluidHandler fh = GooBee.troughHandlerFromPosition(world, GooBee.this.savedTroughPos);
                            if (fh != null && world.rand.nextInt(6) == 0) {
                                if (fh.getFluidInTank(0).getAmount() >= DRAINED_LIQUID_PER_SIP.getAmount()) {
                                    if (!this.completedDrinkingGoo()) {
                                        GooBee.this.fill(fh.drain(DRAINED_LIQUID_PER_SIP, IFluidHandler.FluidAction.EXECUTE), FluidAction.EXECUTE);
                                    }
                                } else {
                                    // trough is empty, doesn't have the goo we want.
                                    GooBee.this.clearTrough();
                                }
                            }
                        }
                    }
                }
            }
        }

        private void moveToNextTarget() {
            GooBee.this.getMoveHelper().setMoveTo(this.nextTarget.getX(), this.nextTarget.getY(), this.nextTarget.getZ(), (double)0.7F);
        }

        private float getRandomOffset() {
            return (GooBee.this.rand.nextFloat() * 2.0F - 1.0F) * 0.33333334F;
        }

        private List<BlockPos> getTroughs() {
            return this.findTrough(32.0D);
        }

        private List<BlockPos> findTrough(double distance) {

            BlockPos blockpos = GooBee.this.getPosition();
            PointOfInterestManager pointofinterestmanager = ((ServerWorld) GooBee.this.world).getPointOfInterestManager();
            Stream<PointOfInterest> stream = pointofinterestmanager
                    .func_219146_b((poit) -> poit == Registry.GOO_TROUGH_POI.get(),
                            blockpos, 20, PointOfInterestManager.Status.ANY);
            return stream.map(PointOfInterest::getPos)
                    .filter(GooBee.this::isTrough)
                    .sorted(Comparator.comparingDouble((destBp) -> destBp.distanceSq(blockpos)))
                    .collect(Collectors.toList());
        }
    }

    private void clearTrough() {
        savedTroughPos = null;
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
            List<BlockPos> list = this.findNests();
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

        private List<BlockPos> findNests() {
            BlockPos blockpos = GooBee.this.getPosition();
            PointOfInterestManager pointofinterestmanager = ((ServerWorld) GooBee.this.world).getPointOfInterestManager();
            Stream<PointOfInterest> stream = pointofinterestmanager
                    .func_219146_b((poit) -> poit == Registry.CRYSTAL_NEST_POI.get(),
                            blockpos, 20, PointOfInterestManager.Status.ANY);
            return stream.map(PointOfInterest::getPos)
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
                GooBee.this.navigator.setPath(GooBee.this.navigator.getPathToPos(new BlockPos(vector3d), 1), 1.0D);
            }
        }

        private Vector3d getRandomLocation() {
            Vector3d vector3d;
            if (GooBee.this.isNestValid() && !GooBee.this.isWithinDistance(GooBee.this.nestPos, 22)) {
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