package com.xeno.goo.entities;

import com.xeno.goo.GooMod;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

public class GooSnail extends AnimalEntity implements IEntityAdditionalSpawnData {
    private static final DataParameter<Integer> TICKS_MOVING = EntityDataManager.createKey(GooSnail.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> TICKS_TO_MOVE = EntityDataManager.createKey(GooSnail.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> TICKS_SPOOKED = EntityDataManager.createKey(GooSnail.class, DataSerializers.VARINT);
    private static float SNAIL_SPEED = 0.06f;
    private static final int SPOOK_DURATION = 100;
    private int ticksMoving;
    private int ticksToMove;
    // determines the snail's speed indirectly.
    private int ticksWithoutComb;
    private int ticksSpooked;
    private int remainingCooldownBeforeLocatingComb = 0;
    private BlockPos savedCombPos = null;
    private GooSnail.EatCombGoal eatCombGoal;
    private GooSnail.FindCombGoal findCombGoal;
    private FluidStack goo = FluidStack.EMPTY;
    private BlockPos homePos = null;

    public GooSnail(EntityType<? extends AnimalEntity> type, World worldIn) {
        super(type, worldIn);
        ticksMoving = 0;
        ticksToMove = 0;
        this.moveController = new MovementController(this);
        this.lookController = new GooSnail.SnailLookController(this);
        this.setPathPriority(PathNodeType.DANGER_FIRE, -1.0F);
        this.setPathPriority(PathNodeType.WATER, -1.0F);
        this.setPathPriority(PathNodeType.WATER_BORDER, 16.0F);
        this.setPathPriority(PathNodeType.COCOA, -1.0F);
        this.setPathPriority(PathNodeType.FENCE, -1.0F);
        setGrowingAge(1);
    }

    @Override
    public void tick() {
        // snails are tough, and regen slowly.
        if (this.ticksExisted % 200 == 0) {
            this.heal(0.5f);
        }

        if (world.isRemote()) {
            this.ticksMoving = this.dataManager.get(TICKS_MOVING);
            this.ticksToMove = this.dataManager.get(TICKS_TO_MOVE);
            this.ticksSpooked = this.dataManager.get(TICKS_SPOOKED);
        } else {
            if (this.navigator.hasPath()) {
                if (this.ticksToMove == 0) {
                    double distance = this.navigator.getTargetPos().distanceSq(getPosX(), getPosY(), getPosZ(), true);
                    int steps = (int)Math.ceil(distance / SNAIL_SPEED);
                    ticksToMove = steps;
                    ticksMoving = 0;
                    dataManager.set(TICKS_TO_MOVE, ticksToMove);
                } else {
                    ticksMoving++;
                    dataManager.set(TICKS_MOVING, ticksMoving);
                }
            } else {
                ticksMoving = 0;
                dataManager.set(TICKS_MOVING, ticksMoving);
                ticksToMove = 0;
                dataManager.set(TICKS_TO_MOVE, ticksToMove);
            }

            if (this.ticksSpooked > 0) {
                this.ticksSpooked--;
                dataManager.set(TICKS_SPOOKED, this.ticksSpooked);
            }
        }
        super.tick();
    }

    @Override
    protected void damageEntity(DamageSource damageSrc, float damageAmount) {
        // immune when spooked
        if (this.isSpooked()) {
            return;
        }
        super.damageEntity(damageSrc, damageAmount);

        this.spook();
    }

    private void spook() {
        // cancel goals, and clear navigator
        if (this.eatCombGoal.isRunning()) {
            eatCombGoal.cancel();
        }

        if (this.findCombGoal.isRunning()) {
            findCombGoal.cancel();
        }

        this.navigator.clearPath();

        this.ticksSpooked = SPOOK_DURATION;
        if (!world.isRemote()) {
            dataManager.set(TICKS_SPOOKED, ticksSpooked);
        }
    }

    public boolean isSpooked() {
        return ticksSpooked > 0;
    }

    public void setHome(BlockPos pos) {
        this.homePos = pos;
    }

    @Override
    protected void registerGoals() {
        this.eatCombGoal = new GooSnail.EatCombGoal();
        this.goalSelector.addGoal(0, this.eatCombGoal);
        this.findCombGoal = new GooSnail.FindCombGoal();
        this.goalSelector.addGoal(1, this.findCombGoal);
        this.goalSelector.addGoal(2, new GooSnail.WanderGoal());
        this.goalSelector.addGoal(3, new SwimGoal(this));
    }


    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        compound.put("HomePos", NBTUtil.writeBlockPos(this.homePos()));

        if (this.hasComb()) {
            compound.put("CombPos", NBTUtil.writeBlockPos(this.getCombPos()));
        }
        compound.putInt("TicksSinceEatingComb", this.ticksWithoutComb);
        compound.putInt("SpookedTicks", this.ticksSpooked);
        compound.putInt("TicksToMove", this.ticksToMove);
        compound.putInt("TicksMoving", this.ticksMoving);
        compound.put("goo", goo.writeToNBT(new CompoundNBT()));
    }

    private boolean hasComb() {
        return savedCombPos != null;
    }

    private BlockPos getCombPos() {
        return savedCombPos;
    }

    private BlockPos homePos() {
        if (homePos == null) {
            setHome(this.getPosition());
        }
        return homePos;
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    public void readAdditional(CompoundNBT compound) {
        setHome(null);
        if (compound.contains("HomePos")) {
            setHome(NBTUtil.readBlockPos(compound.getCompound("HomePos")));
        }

        clearComb();
        if (compound.contains("CombPos")) {
            this.savedCombPos = NBTUtil.readBlockPos(compound.getCompound("CombPos"));
        }
        this.ticksWithoutComb = compound.getInt("TicksSinceEatingComb");
        this.ticksSpooked = compound.getInt("SpookedTicks");
        this.ticksMoving = compound.getInt("TicksMoving");
        this.ticksToMove = compound.getInt("TicksToMove");
        if (compound.contains("goo")) {
            this.goo = FluidStack.loadFluidStackFromNBT(compound.getCompound("goo"));
        }

        super.readAdditional(compound);
    }

    class SnailLookController extends LookController {
        SnailLookController(MobEntity snailIn) {
            super(snailIn);
        }

        @Override
        public void tick() {
            super.tick();
        }

        protected boolean shouldResetPitch() {
            return !GooSnail.this.eatCombGoal.isRunning();
        }
    }

    @Nullable
    @Override
    public AgeableEntity func_241840_a(ServerWorld p_241840_1_, AgeableEntity p_241840_2_) {
        return Registry.GOO_SNAIL.get().create(world);
    }

    public float getBodyStretch() {
        if (this.isInMotion() && this.ticksToMove > 0) {
            int halfStep = (this.ticksToMove / 2);
            int stepProgress = ticksMoving % halfStep;
            float stretch = 1f + (0.6f * (ticksMoving < halfStep ? stepProgress : halfStep - stepProgress) / (float)halfStep);
            return Math.min(1f, Math.max(1.6f, stretch));
        }
        return 1f;
    }

    private boolean isInMotion() {
        return ticksMoving > 0;
    }

    public static AttributeModifierMap.MutableAttribute setCustomAttributes() {
        return MobEntity.func_233666_p_()
                .createMutableAttribute(Attributes.MAX_HEALTH, 10D)
                .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.06F)
                .createMutableAttribute(Attributes.ATTACK_DAMAGE, 1.0D)
                .createMutableAttribute(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    public void writeSpawnData(PacketBuffer buffer) {

    }

    @Override
    public void readSpawnData(PacketBuffer additionalData) {

    }

    protected void registerData() {
        super.registerData();
        this.dataManager.register(TICKS_MOVING, 0);
        this.dataManager.register(TICKS_TO_MOVE, 0);
        this.dataManager.register(TICKS_SPOOKED, 0);
    }

    private boolean isWithinDistance(BlockPos pos, int distance) {
        return pos.withinDistance(this.getPosition(), distance);
    }

    private boolean isTooFar(BlockPos pos) {
        return !this.isWithinDistance(pos, 32);
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
            this.navigator.setRangeMultiplier(0.5F);
            this.navigator.tryMoveToXYZ(vector3d1.x, vector3d1.y, vector3d1.z, 0.1D);
        }
    }

    // goal stuff

    abstract class PassiveGoal extends Goal {
        private PassiveGoal() {
        }

        public abstract boolean canSnailStart();

        public abstract boolean canSnailContinue();

        /**
         * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
         * method as well.
         */
        public boolean shouldExecute() {
            return this.canSnailStart();
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        public boolean shouldContinueExecuting() {
            return this.canSnailContinue();
        }
    }


    class EatCombGoal extends GooSnail.PassiveGoal {
        private boolean running;
        private Vector3d nextTarget;
        private int ticks = 0;

        EatCombGoal() {
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canSnailStart() {
            if (GooSnail.this.isSpooked()) {
                return false;
            } else if (GooSnail.this.remainingCooldownBeforeLocatingComb > 0) {
                return false;
            } else if (GooSnail.this.hasGoo()) {
                return false;
            } else if (GooSnail.this.rand.nextFloat() < 0.7F) {
                return false;
            } else {
                ItemEntity comb = this.getComb();
                if (comb == null) {
                    return false;
                }
                GooSnail.this.savedCombPos = comb.getPosition();
                GooSnail.this.navigator.tryMoveToXYZ((double) GooSnail.this.savedCombPos.getX() + 0.5D,
                        (double) GooSnail.this.savedCombPos.getY() + 0.5D,
                        (double) GooSnail.this.savedCombPos.getZ() + 0.5D, 0.06D);
                return true;
            }
        }

        @Override
        public boolean canSnailContinue() {
            if (!this.running || GooSnail.this.isSpooked()) {
                return false;
            } else if (GooSnail.this.hasGoo()) {
                return false;
            } else {
                return true;
            }
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
            GooSnail.this.resetTicksWithoutEatingComb();
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        public void resetTask() {
            this.running = false;
            GooSnail.this.navigator.clearPath();
            GooSnail.this.remainingCooldownBeforeLocatingComb = 200;
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick() {
            ++this.ticks;
            if (this.ticks > 600) {
                GooSnail.this.clearComb();
            } else {
                Vector3d vector3d = Vector3d.copyCenteredHorizontally(GooSnail.this.savedCombPos)
                        .add(0.0D, 0.3D, 0.0D);
                if (vector3d.distanceTo(GooSnail.this.getPositionVec()) > 0.7D) {
                    this.nextTarget = vector3d;
                    this.moveToNextTarget();
                } else {
                    if (this.nextTarget == null) {
                        this.nextTarget = vector3d;
                    }
                    double distanceToTarget = GooSnail.this.getPositionVec().distanceTo(this.nextTarget);

                    boolean isVeryCloseToTarget = distanceToTarget <= 0.2D;
                    boolean isCloseEnoughToTarget = distanceToTarget <= 0.7D;
                    boolean isDerping = true;
                    // it seems to be taking more than 30 seconds to get to our trough.
                    if (!isVeryCloseToTarget && this.ticks > 600) {
                        GooSnail.this.clearComb();
                    } else {
                        // we're at the trough. Presuming it is valid, we drink from it.
                        if (isVeryCloseToTarget) {
                            boolean tryDerping = GooSnail.this.rand.nextInt(25) == 0;
                            if (tryDerping) {
                                this.nextTarget = new Vector3d(vector3d.getX() + (double)this.getRandomOffset(), vector3d.getY(), vector3d.getZ() + (double)this.getRandomOffset());
                                GooSnail.this.navigator.clearPath();
                            } else {
                                isDerping = false;
                            }
                            GooSnail.this.getLookController().setLookPosition(vector3d.getX(), vector3d.getY(), vector3d.getZ());
                        }

                        if (isDerping) {
                            this.moveToNextTarget();
                        }

                        if (isCloseEnoughToTarget) {
                            ItemEntity comb = getComb();
                            if (comb != null) {
                                eatComb(comb);
                            }
                            GooSnail.this.clearComb();
                        }
                    }
                }
            }
        }

        private void eatComb(ItemEntity comb) {
            comb.remove();
            GooSnail.this.goo = new FluidStack(Registry.PRIMORDIAL_GOO.get(), GooMod.config.snailProductionAmount());
        }

        private void moveToNextTarget() {
            GooSnail.this.getMoveHelper().setMoveTo(this.nextTarget.getX(), this.nextTarget.getY(), this.nextTarget.getZ(), (double)0.06F);
        }

        private float getRandomOffset() {
            return (GooSnail.this.rand.nextFloat() * 2.0F - 1.0F) * 0.33333334F;
        }

        private ItemEntity getComb() {
            return this.findComb(7.0D);
        }

        private ItemEntity findComb(double distance) {
            List<ItemEntity> combs = world.getEntitiesWithinAABB(ItemEntity.class, GooSnail.this.getBoundingBox().grow(distance), e -> e.getItem().getItem().equals(ItemsRegistry.CrystalComb.get()));
            ItemEntity closestComb = null;
            double d = Double.MAX_VALUE;
            for(ItemEntity e : combs) {
                if (e.getDistance(GooSnail.this) < d) {
                    d = e.getDistance(GooSnail.this);
                    closestComb = e;
                }
            }
            return closestComb;
        }
    }

    private void resetTicksWithoutEatingComb() {
        ticksWithoutComb = 0;
    }

    private boolean hasGoo() {
        return !this.goo.isEmpty();
    }

    private void clearComb() {
        this.savedCombPos = null;
    }

    public class FindCombGoal extends GooSnail.PassiveGoal {
        private int ticks = GooSnail.this.world.rand.nextInt(10);
        private boolean running;

        FindCombGoal() {
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canSnailStart() {
            return GooSnail.this.savedCombPos != null && !GooSnail.this.detachHome() && !GooSnail.this.isSpooked()
                    && this.shouldMoveToComb() && !GooSnail.this.isWithinDistance(GooSnail.this.savedCombPos, 2);
        }

        public boolean canSnailContinue() {
            return this.canSnailStart();
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
            super.startExecuting();
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        public void resetTask() {
            this.ticks = 0;
            this.running = false;
            GooSnail.this.navigator.clearPath();
            GooSnail.this.navigator.resetRangeMultiplier();
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick() {
            if (GooSnail.this.savedCombPos != null) {
                ++this.ticks;
                if (this.ticks > 600) {
                    GooSnail.this.clearComb();
                } else if (!GooSnail.this.navigator.hasPath()) {
                    if (GooSnail.this.isTooFar(GooSnail.this.savedCombPos)) {
                        GooSnail.this.clearComb();
                    } else {
                        GooSnail.this.startMovingTo(GooSnail.this.savedCombPos);
                    }
                }
            }
        }

        private boolean shouldMoveToComb() {
            return true;
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
            return GooSnail.this.navigator.noPath() && GooSnail.this.rand.nextInt(10) == 0 && !GooSnail.this.isSpooked();
        }

        /**
         * Returns whether an in-progress EntityAIBase should continue executing
         */
        public boolean shouldContinueExecuting() {
            return GooSnail.this.navigator.hasPath() && !GooSnail.this.isSpooked();
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void startExecuting() {
            Vector3d vector3d = this.getRandomLocation();
            if (vector3d != null) {
                GooSnail.this.navigator.setPath(GooSnail.this.navigator.getPathToPos(new BlockPos(vector3d), 1), 1.0D);
                GooSnail.this.getLookController().setLookPosition(vector3d.getX(), vector3d.getY(), vector3d.getZ());
            }
        }

        private Vector3d getRandomLocation() {
            Vector3d bearing;
            if (!GooSnail.this.isWithinDistance(GooSnail.this.homePos(), 1)) {
                Vector3d vector3d1 = Vector3d.copyCentered(GooSnail.this.homePos());
                bearing = vector3d1.subtract(GooSnail.this.getPositionVec()).normalize();
            } else {
                bearing = GooSnail.this.getLook(0.0F);
            }
            return generateRandomDest(1, 0, 0, bearing, ((float) Math.PI / 2F));
        }

        private Vector3d generateRandomDest(int range, int verticality, int verticalOffset, Vector3d bearing, float radians) {
            return RandomPositionGenerator.findGroundTarget(GooSnail.this, range, verticality, verticalOffset, bearing, radians);
        }
    }
}
