package com.xeno.goo.entities;

import com.xeno.goo.GooMod;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.AudioHelper.PitchFormulas;
import com.xeno.goo.setup.EntitySpawnConditions;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.AgeableEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ItemParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

public class GooSnail extends AnimalEntity implements IEntityAdditionalSpawnData {
    private static final DataParameter<Integer> TICKS_MOVING = EntityDataManager.createKey(GooSnail.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> TICKS_TO_MOVE = EntityDataManager.createKey(GooSnail.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> TICKS_SPOOKED = EntityDataManager.createKey(GooSnail.class, DataSerializers.VARINT);
    private static final int MAX_DURATION_OF_MOVEMENT = 20;
    private static final float SNAIL_SQUIDGE_RATIO = 0.1f;
    private static final int SPOOK_DURATION = 100;
    private int ticksMoving;
    private int ticksToMove;
    private int ticksSpooked;
    private ItemEntity combToSeek = null;
    private GooSnail.EatCombGoal eatCombGoal;
    private FluidStack goo = FluidStack.EMPTY;
    private BlockPos homePos = null;
    private boolean isSpawnedByPlayerPlacement;
    public GooSnail(EntityType<? extends AnimalEntity> type, World worldIn) {
        super(type, worldIn);
        ticksMoving = 0;
        ticksToMove = 0;
        this.moveController = new MovementController(this);
        this.lookController = new GooSnail.SnailLookController(this);
        this.setPathPriority(PathNodeType.DANGER_FIRE, -100);
        this.setPathPriority(PathNodeType.DAMAGE_FIRE, -1000);
        this.setPathPriority(PathNodeType.LAVA, -1000);
        this.setPathPriority(PathNodeType.WATER, -1.0F);
        this.setPathPriority(PathNodeType.WATER_BORDER, 16.0F);
        this.setPathPriority(PathNodeType.COCOA, -1.0F);
        this.setPathPriority(PathNodeType.FENCE, -1.0F);
        setGrowingAge(1);
    }

    public void setSpawnedByPlayerPlacement(boolean spawnedByPlayerPlacement) {
        isSpawnedByPlayerPlacement = spawnedByPlayerPlacement;
    }

    @Override
    public int getMaxSpawnedInChunk() {
        return 1;
    }

    @Override
    public boolean canSpawn(IWorld worldIn, SpawnReason spawnReasonIn) {
        return EntitySpawnConditions.snailSpawnConditions(worldIn, spawnReasonIn, this.getPosition(), this.rand);
    }

    public static boolean nearbyLiquidConditionsMet(IWorld world, BlockPos pos) {
        Stream<BlockPos> positions = BlockPos.getAllInBox(pos.getX() - 7, pos.getY() - 1, pos.getZ() - 7,
                pos.getX() + 7, pos.getY() + 1, pos.getZ() + 7);
        Iterable<BlockPos> iterable = positions::iterator;
        boolean nearLava = false;
        boolean nearWater = false;
        for(BlockPos p : iterable) {
            BlockState state = world.getBlockState(p);
            if (state.matchesBlock(Blocks.LAVA)) {
                nearLava = true;
            }
            if (state.matchesBlock(Blocks.WATER)) {
                nearWater = true;
            }
        }
        return !nearLava && nearWater;
    }

    public static boolean nearbySnail(IWorld world, BlockPos pos) {
        AxisAlignedBB box = new AxisAlignedBB(pos.getX() - 16, pos.getY() - 16, pos.getZ() - 16, pos.getX() + 16, pos.getY() + 16, pos.getZ() + 16);
        return !world.getEntitiesWithinAABB(GooSnail.class, box).isEmpty();
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
            UpdateNavigatorAndSlinkingAnimations();

            if (this.ticksSpooked > 0) {
                this.ticksSpooked--;
                dataManager.set(TICKS_SPOOKED, this.ticksSpooked);
            }
        }
        super.tick();
    }

    private void UpdateNavigatorAndSlinkingAnimations() {
        if (this.navigator.hasPath()) {
            if (this.ticksToMove == 0) {
                ticksToMove = MAX_DURATION_OF_MOVEMENT;
            }
            if (this.ticksToMove > 0) {
                ticksMoving++;
            }
        // here we want to ensure that our CURRENT movement cycle is finished before resetting the vars, otherwise weird snapping occurs.
        } else if ((this.ticksMoving % MAX_DURATION_OF_MOVEMENT) < this.ticksToMove) {
            // we haven't finished our movement animation, which makes the snail sproingy and weird.
            // finish animating before resetting.
            this.ticksMoving++;
        } else {
            ticksMoving = 0;
            ticksToMove = 0;
        }
        dataManager.set(TICKS_TO_MOVE, ticksToMove);
        dataManager.set(TICKS_MOVING, ticksMoving);
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

    @Override
    public ActionResultType applyPlayerInteraction(PlayerEntity player, Vector3d vec, Hand hand) {
        if (player.getHeldItem(hand).isEmpty()) {
            player.setHeldItem(hand, new ItemStack(ItemsRegistry.SNAIL.get()));
            this.remove();
            return ActionResultType.CONSUME;
        }
        return super.applyPlayerInteraction(player, vec, hand);
    }

    private void spook() {
        // cancel goals, and clear navigator
        if (this.eatCombGoal.isRunning()) {
            eatCombGoal.cancel();
        }

        this.navigator.clearPath();

        this.ticksSpooked = SPOOK_DURATION;
        if (!world.isRemote()) {
            dataManager.set(TICKS_SPOOKED, ticksSpooked);
        }
    }

    // stolen from LivingEntity, but it's private.
    // TODO this sometimes doesn't put the particles in the snail's face where I'd like them to kinda be. Needs tuning.
    private void spawnEatingParticles(ItemStack stack, int count) {
        for(int i = 0; i < count; ++i) {
            Vector3d offset = new Vector3d(((double)this.rand.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, 0.0D);
            offset = offset.rotatePitch(-this.rotationPitch * ((float)Math.PI / 180F));
            offset = offset.rotateYaw(-this.rotationYaw * ((float)Math.PI / 180F));
            double d0 = (double)(-this.rand.nextFloat()) * 0.6D - 0.3D;
            Vector3d spawnPos = new Vector3d(((double)this.rand.nextFloat() - 0.5D) * 0.3D, d0, 0.6D);
            spawnPos = spawnPos.rotatePitch(-this.rotationPitch * ((float)Math.PI / 180F));
            spawnPos = spawnPos.rotateYaw(-this.rotationYaw * ((float)Math.PI / 180F));
            spawnPos = spawnPos.add(this.getPosX(), this.getPosYEye(), this.getPosZ());
            if (this.world instanceof ServerWorld)
                ((ServerWorld)this.world).spawnParticle(new ItemParticleData(ParticleTypes.ITEM, stack), spawnPos.x, spawnPos.y, spawnPos.z, 1, offset.x, offset.y + 0.05D, offset.z, 0.0D);
            else
                this.world.addParticle(new ItemParticleData(ParticleTypes.ITEM, stack), spawnPos.x, spawnPos.y, spawnPos.z, offset.x, offset.y + 0.05D, offset.z);
        }
    }

    public boolean canDespawn(double distanceToClosestPlayer) {
        return !this.isBondedToPlayers() && this.ticksExisted > 2400;
    }

    private boolean isBondedToPlayers() {
        return this.isSpawnedByPlayerPlacement;
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
        this.goalSelector.addGoal(1, new PoopGoal());
        this.goalSelector.addGoal(2, new GooSnail.WanderGoal());
        this.goalSelector.addGoal(3, new SwimGoal(this));
    }

    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        compound.put("HomePos", NBTUtil.writeBlockPos(this.homePos()));
        compound.putInt("SpookedTicks", this.ticksSpooked);
        compound.putInt("TicksToMove", this.ticksToMove);
        compound.putInt("TicksMoving", this.ticksMoving);
        compound.put("goo", goo.writeToNBT(new CompoundNBT()));
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
            return true;
            // return !GooSnail.this.eatCombGoal.isRunning();
        }
    }

    @Override
    public AgeableEntity createChild(ServerWorld p_241840_1_, AgeableEntity p_241840_2_) {
        return Registry.GOO_SNAIL.create(world);
    }

    public float getBodyStretch() {
        if (ticksMoving > 0 || ticksToMove > 0) {
            int halfStep = (this.ticksToMove / 2);
            int stepProgress = ticksMoving % ticksToMove;
            int actualProgress = stepProgress <= halfStep ? stepProgress : halfStep - (stepProgress - halfStep);
            float progressPercent = actualProgress / (float)halfStep;
            float stretch = 1f + SNAIL_SQUIDGE_RATIO * progressPercent;
            return Math.max(1f, Math.min(1f + SNAIL_SQUIDGE_RATIO, stretch));
        }
        return 1f;
    }

    public static AttributeModifierMap.MutableAttribute setCustomAttributes() {
        return MobEntity.func_233666_p_()
                .createMutableAttribute(Attributes.MAX_HEALTH, 10D)
                .createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.09f)
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
        private int ticks = 0;
        private int ticksEating = 0;
        private final int EAT_COMB_DURATION = 20;

        EatCombGoal() {
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canSnailStart() {
            if (GooSnail.this.isSpooked()) {
                return false;
            } else if (GooSnail.this.hasGoo() && GooSnail.this.goo.getAmount() >= 10) {
                return false;
            } else {
                ItemEntity comb = this.getComb();
                if (comb == null) {
                    return false;
                }
                GooSnail.this.combToSeek = comb;
                return true;
            }
        }

        @Override
        public boolean canSnailContinue() {
            if (!this.running || GooSnail.this.isSpooked() || GooSnail.this.combToSeek == null) {
                return false;
            } else if (GooSnail.this.hasGoo() && GooSnail.this.goo.getAmount() >= 10) {
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
            this.ticksEating = 0;
            this.running = true;
            GooSnail.this.getLookController().setLookPosition(GooSnail.this.combToSeek.getPosX(),
                    GooSnail.this.combToSeek.getPosY(),
                    GooSnail.this.combToSeek.getPosZ());
            GooSnail.this.navigator.tryMoveToXYZ(GooSnail.this.combToSeek.getPosX(),
                    GooSnail.this.combToSeek.getPosY(),
                    GooSnail.this.combToSeek.getPosZ(), 1.5f);
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        public void resetTask() {
            this.running = false;
            GooSnail.this.navigator.clearPath();
        }

        private int EAT_COMB_TIMEOUT = 120;
        private double DISTANCE_THAT_IS_VERY_CLOSE = 0.2D;
        private double DISTANCE_TO_EAT = 0.7D;
        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick() {
            ++this.ticks;
            if (this.ticks > EAT_COMB_TIMEOUT) {
                GooSnail.this.clearComb();
                resetTask();
            } else {
                if (GooSnail.this.combToSeek == null || !GooSnail.this.combToSeek.isAlive()) {
                    resetTask();
                    return;
                }
                double distanceToTarget = GooSnail.this.getPositionVec().distanceTo(GooSnail.this.combToSeek.getPositionVec());
                if (distanceToTarget <= DISTANCE_TO_EAT) {
                    if (distanceToTarget > DISTANCE_THAT_IS_VERY_CLOSE) {
                        GooSnail.this.getLookController().setLookPosition(GooSnail.this.combToSeek.getPosX(),
                                GooSnail.this.combToSeek.getPosY(),
                                GooSnail.this.combToSeek.getPosZ());
                    }
                    ++this.ticksEating;
                    GooSnail.this.spawnEatingParticles(combToSeek.getItem(), 2);
                    AudioHelper.entityAudioEvent(GooSnail.this, Registry.SNAIL_EAT_SOUND.get(), SoundCategory.NEUTRAL, 0.7f, PitchFormulas.HalfToOne);
                    if (this.ticksEating > EAT_COMB_DURATION) {
                        eatComb(GooSnail.this.combToSeek);
                        GooSnail.this.clearComb();
                    }
                } else {
                    // we're stalling out trying to get to the thing, try squidging closer
                    GooSnail.this.getMoveHelper().setMoveTo(GooSnail.this.combToSeek.getPosX(),
                            GooSnail.this.combToSeek.getPosY(),
                            GooSnail.this.combToSeek.getPosZ(), 1.5f);
                }
            }
        }

        private void eatComb(ItemEntity comb) {
            comb.remove();
            if (GooSnail.this.goo.isEmpty()) {
                GooSnail.this.goo = new FluidStack(Registry.PRIMORDIAL_GOO.get(), GooMod.config.snailProductionAmount());
            } else {
                GooSnail.this.goo.setAmount(GooSnail.this.goo.getAmount() + GooMod.config.snailProductionAmount());
            }
        }

        private ItemEntity getComb() {
            return this.findComb(7.0D);
        }

        private ItemEntity findComb(double distance) {
            List<ItemEntity> combs = world.getEntitiesWithinAABB(ItemEntity.class, GooSnail.this.getBoundingBox().grow(distance), e -> e.getItem().getItem().equals(ItemsRegistry.CRYSTAL_COMB
					.get()));
            ItemEntity closestComb = null;
            double d = Double.MAX_VALUE;
            for(ItemEntity e : combs) {
                if (e.getMotion().lengthSquared() > 0.0001d) {
                    continue;
                }
                double toEntity = e.getDistance(GooSnail.this);
                if (toEntity < d) {
                    d = toEntity;
                    closestComb = e;
                }
            }
            return closestComb;
        }
    }

    class PoopGoal extends GooSnail.PassiveGoal {
        private boolean running;
        private int ticks = 0;
        private int TICKS_TO_POOP = 120;
        // PoopGoal() { }

        @Override
        public boolean canSnailStart() {
            if (this.running || GooSnail.this.isSpooked()) {
                return false;
            }
            return GooSnail.this.hasGoo() && GooSnail.this.goo.getAmount() >= 10;
        }

        @Override
        public boolean canSnailContinue() {
            if (!this.running || GooSnail.this.isSpooked()) {
                return false;
            }
            return GooSnail.this.hasGoo() && GooSnail.this.goo.getAmount() >= 10;
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void startExecuting() {
            this.running = true;
            this.ticks = 0;
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        public void resetTask() {
            this.running = false;
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick() {
            ++this.ticks;
            if (this.ticks >= TICKS_TO_POOP) {
                DoPoop();
                resetTask();
            }
        }

        private void DoPoop() {
            AudioHelper.entityAudioEvent(GooSnail.this, Registry.SNAIL_POOP_SOUND.get(), SoundCategory.NEUTRAL, 1.0f, PitchFormulas.HalfToOne);
            if (world.isRemote()) {
                return;
            }
            GooSnail.this.goo.setAmount(GooSnail.this.goo.getAmount() - 10);
            if (GooSnail.this.goo.getAmount() == 0) {
                GooSnail.this.goo =  FluidStack.EMPTY;
            }
            ItemStack primordialShard = new ItemStack(ItemsRegistry.gooCrystalByFluidAndType(Registry.PRIMORDIAL_GOO.get(), "sliver").get());
            Vector3d pos = new Vector3d(GooSnail.this.getPosX(), GooSnail.this.getPosY(), GooSnail.this.getPosZ());
            world.addEntity(new ItemEntity(world, pos.x, pos.y, pos.z, primordialShard));
        }
    }


    private boolean hasGoo() {
        return !this.goo.isEmpty();
    }

    private void clearComb() {
        this.combToSeek = null;
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
                GooSnail.this.navigator.setPath(GooSnail.this.navigator.getPathToPos(new BlockPos(vector3d), 1), 1f);
                GooSnail.this.getLookController().setLookPosition(vector3d.getX(), vector3d.getY(), vector3d.getZ());
            }
        }

        private Vector3d getRandomLocation() {
            Vector3d bearing;
            if (!GooSnail.this.isWithinDistance(GooSnail.this.homePos(), 5)) {
                Vector3d home = Vector3d.copyCentered(GooSnail.this.homePos());
                bearing = home.subtract(GooSnail.this.getPositionVec()).normalize();
            } else {
                bearing = GooSnail.this.getLook(0.0F);
            }
            return generateRandomDest(6, 0, 0, bearing, ((float) Math.PI / 2F));
        }

        private Vector3d generateRandomDest(int range, int verticality, int verticalOffset, Vector3d bearing, float radians) {
            return RandomPositionGenerator.findGroundTarget(GooSnail.this, range, verticality, verticalOffset, bearing, radians);
        }
    }
}
