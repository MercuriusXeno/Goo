package com.xeno.goo.entities;

import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.FlyingMovementController;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.IFlyingAnimal;
import net.minecraft.fluid.Fluid;
import net.minecraft.network.PacketBuffer;
import net.minecraft.pathfinding.FlyingPathNavigator;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tags.ITag;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class LightingBug extends AnimalEntity implements IFlyingAnimal, IEntityAdditionalSpawnData {

	public static AttributeModifierMap.MutableAttribute setCustomAttributes() {

		return MobEntity.func_233666_p_()
				.createMutableAttribute(Attributes.MAX_HEALTH, 10D)
				.createMutableAttribute(Attributes.FLYING_SPEED, 1.2F)
				.createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.5F)
				.createMutableAttribute(Attributes.ATTACK_DAMAGE, 1.0D)
				.createMutableAttribute(Attributes.FOLLOW_RANGE, 48.0D);
	}

	private BlockPos darkPosition;

	public LightingBug(EntityType<LightingBug> type, World worldIn) {

		super(type, worldIn);
		this.moveController = new FlyingMovementController(this, 20, true);
		this.setPathPriority(PathNodeType.DANGER_CACTUS, -1);
		this.setPathPriority(PathNodeType.DANGER_FIRE, -1);
		this.setPathPriority(PathNodeType.DANGER_OTHER, -1);
		this.setPathPriority(PathNodeType.UNPASSABLE_RAIL, 0);
		this.setPathPriority(PathNodeType.WATER, -1);
		this.setPathPriority(PathNodeType.WATER_BORDER, 16);
		this.setPathPriority(PathNodeType.COCOA, -1);
		this.setPathPriority(PathNodeType.FENCE, -1);
		setGrowingAge(-100000);
	}

	@Override
	protected PathNavigator createNavigator(World worldIn) {

		FlyingPathNavigator flyingpathnavigator = new FlyingPathNavigator(this, worldIn) {

			@Override
			public boolean canEntityStandOnPos(BlockPos pos) {

				return this.world.getBlockState(pos).isTopSolid(this.world, pos, this.entity, Direction.UP);
			}
		};
		flyingpathnavigator.setCanOpenDoors(false);
		flyingpathnavigator.setCanSwim(false);
		flyingpathnavigator.setCanEnterDoors(true);
		return flyingpathnavigator;
	}

	@Override
	protected void registerGoals() {

		//this.goalSelector.addGoal(0, new EnterNestGoal());
		this.goalSelector.addGoal(1, new LightDarkBlockGoal());
		//this.drinkGooGoal = new DrinkGooGoal();
		//this.goalSelector.addGoal(10, this.drinkGooGoal);
		this.goalSelector.addGoal(21, new MoveToDarkBlockGoal());
		this.goalSelector.addGoal(40, new WanderGoal());
		this.goalSelector.addGoal(45, new FindDarkBlockGoal());
		this.goalSelector.addGoal(50, new SwimGoal(this));
	}

	@Override
	public void writeSpawnData(PacketBuffer buffer) {

	}

	@Override
	public void readSpawnData(PacketBuffer additionalData) {

	}

	@Override
	public AgeableEntity createChild(ServerWorld world, AgeableEntity mate) {

		LightingBug childEntity = Registry.LIGHTING_BUG.get().create(world);
		//		if (mate instanceof LightingBug) {
		//			if (this.rand.nextBoolean()) {
		//				childEntity.setType(this.getType());
		//			} else {
		//				childEntity.setType(((LightingBug)mate).getType());
		//			}

		//			if (this.isTamed()) {
		//				childEntity.setOwnerId(this.getOwnerId());
		//				childEntity.setTamed(true);
		//			}
		//		}

		return childEntity;
	}

	public float getBodyPitch(float pct) {

		return MathHelper.lerp(pct, 0, 0);
	}

	@Override
	public CreatureAttribute getCreatureAttribute() {

		return CreatureAttribute.ARTHROPOD;
	}

	@Override
	protected void handleFluidJump(ITag<Fluid> fluidTag) {

		this.setMotion(this.getMotion().add(0.0D, 0.01D, 0.0D));
	}

	@Override
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

	class MoveToDarkBlockGoal extends WanderGoal {

		MoveToDarkBlockGoal() {

			this.setMutexFlags(EnumSet.of(Flag.MOVE, Flag.TARGET));
		}

		@Override
		public boolean shouldExecute() {

			return darkPosition != null && super.shouldExecute();
		}

		@Override
		public void startExecuting() {

			LightingBug.this.navigator.setPath(LightingBug.this.navigator.getPathToPos(new BlockPos(darkPosition), 0), 1.0D);
		}

		@Override
		public void resetTask() {

			darkPosition = null;
		}
	}

	class LightDarkBlockGoal extends Goal {

		private LightDarkBlockGoal() {

			this.setMutexFlags(EnumSet.of(Flag.TARGET, Flag.MOVE));
		}

		@Override
		public boolean shouldExecute() {

			return darkPosition != null && LightingBug.this.getPosition().equals(darkPosition);
		}

		@Override
		public boolean shouldContinueExecuting() {

			return false;
		}

		@Override
		public void startExecuting() {

			BlockPos start = LightingBug.this.getPosition();
			World world = LightingBug.this.getEntityWorld();
			for (Direction dir : Direction.getFacingDirections(LightingBug.this)) {
				BlockPos pos = start.offset(dir);
				BlockState state = world.getBlockState(pos);
				if (state.isSolidSide(world, pos, dir.getOpposite())) {
					world.setBlockState(start, BlocksRegistry.RadiantLight.get().getDefaultState().with(BlockStateProperties.FACING, dir.getOpposite()));
					return;
				}
			}
		}
	}

	class FindDarkBlockGoal extends Goal {

		private FindDarkBlockGoal() {

			this.setMutexFlags(EnumSet.of(Flag.TARGET));
		}

		@Override
		public boolean shouldExecute() {

			return darkPosition == null && LightingBug.this.rand.nextInt(10) == 0;
		}

		@Override
		public boolean shouldContinueExecuting() {

			return false;
		}

		@Override
		public void tick() {

		}

		@Override
		public void startExecuting() {

			Vector3d position;
			//			if (LightingBug.this.isNestValid() && !LightingBug.this.isWithinDistance(LightingBug.this.nestPos, 22)) {
			//				Vector3d vector3d1 = Vector3d.copyCentered(LightingBug.this.nestPos);
			//				position = vector3d1.subtract(LightingBug.this.getPositionVec()).normalize();
			//			} else
			{
				position = LightingBug.this.getLook(0.0F);
			}

			Vector3d airTarget = RandomPositionGenerator.findAirTarget(LightingBug.this,
					8, 7, position, ((float) Math.PI / 2F), 2, 1);
			Vector3d target = airTarget != null ? airTarget : RandomPositionGenerator.findGroundTarget(LightingBug.this,
					8, 4, -2, position, (double) ((float) Math.PI / 2F));
			if (target != null) {
				BlockPos start = new BlockPos(target);
				World world = LightingBug.this.getEntityWorld();
				for (Direction dir : Direction.getFacingDirections(LightingBug.this)) {
					BlockPos pos = start.offset(dir);
					BlockState state = world.getBlockState(pos);
					if (state.isSolidSide(world, pos, dir.getOpposite())) {
						darkPosition = start;
						return;
					}
				}
			}
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
		@Override
		public boolean shouldExecute() {

			return LightingBug.this.navigator.noPath() && LightingBug.this.rand.nextInt(10) == 0;
		}

		/**
		 * Returns whether an in-progress EntityAIBase should continue executing
		 */
		@Override
		public boolean shouldContinueExecuting() {

			return LightingBug.this.navigator.hasPath();
		}

		/**
		 * Execute a one shot task or start executing a continuous task
		 */
		@Override
		public void startExecuting() {

			Vector3d vector3d = this.getRandomLocation();
			if (vector3d != null) {
				LightingBug.this.navigator.setPath(LightingBug.this.navigator.getPathToPos(new BlockPos(vector3d), 2), 0.75D);
			}
		}

		@Nullable
		private Vector3d getRandomLocation() {

			Vector3d position;
			//			if (LightingBug.this.isNestValid() && !LightingBug.this.isWithinDistance(LightingBug.this.nestPos, 22)) {
			//				Vector3d vector3d1 = Vector3d.copyCentered(LightingBug.this.nestPos);
			//				position = vector3d1.subtract(LightingBug.this.getPositionVec()).normalize();
			//			} else
			{
				position = LightingBug.this.getLook(0.0F);
			}

			Vector3d airTarget = RandomPositionGenerator.findAirTarget(LightingBug.this,
					8, 7, position, ((float) Math.PI / 2F), 2, 1);
			return airTarget != null ? airTarget : RandomPositionGenerator.findGroundTarget(LightingBug.this,
					8, 4, -2, position, (double) ((float) Math.PI / 2F));
		}
	}
}
