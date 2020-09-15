package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.network.GooLobConfirmationPacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.tiles.BulbFluidHandler;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooBulbTile;
import com.xeno.goo.tiles.GooBulbTileAbstraction;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.*;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.wrappers.BlockWrapper;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GooEntity extends Entity implements IEntityAdditionalSpawnData, IFluidHandler
{
    private static final DataParameter<Integer> GOO_SIZE = EntityDataManager.createKey(GooEntity.class, DataSerializers.VARINT);
    private static final double GENERAL_FRICTION = 0.98d;
    private static final int QUIVER_TIMER_INITIALIZED_VALUE = 100;
    private static final int QUIVER_TIMER_ONE_CYCLE_DOWN = 75;
    private float enchantedSpeed;
    public FluidStack goo;
    private boolean isInGround;
    private int quiverTimer;
    private BlockState inBlockState;
    private List<Entity> touchingEntityIds = new ArrayList<>();
    private boolean isHeld;
    private Entity owner;
    private float cubicSize;
    private EntitySize size;
    private boolean isLaunched;


    public GooEntity(EntityType<GooEntity> type, World worldIn) {
        super(type, worldIn);
    }

    public GooEntity(EntityType<GooEntity> type, World worldIn, Entity sender, FluidStack stack) {
        super(type, worldIn);
        goo = stack;
        if (!(stack.getFluid() instanceof GooFluid)) {
            this.setDead();
            this.remove();
        } else {
            this.setSize();
            this.setInvulnerable(true);
            this.attachGooToSender(sender);
        }
    }

    public AxisAlignedBB getCollisionBoundingBox() {
        return this.getBoundingBox();
    }

    public void recalculateSize() {
        double d0 = this.getPosX();
        double d1 = this.getPosY();
        double d2 = this.getPosZ();
        super.recalculateSize();
        this.setPosition(d0, d1, d2);
    }

    @Override
    public EntitySize getSize(Pose poseIn)
    {
        return this.size;
    }

    public void notifyDataManagerChange(DataParameter<?> key) {
        if (GOO_SIZE.equals(key)) {
            this.recalculateSize();
        }

        super.notifyDataManagerChange(key);
    }

    @Override
    protected void registerData() {
        this.dataManager.register(GOO_SIZE, 1);
    }

    protected void interactWithSolid(BlockPos pos) {
        // TODO
    }

    protected void interactWithGoo(BlockPos pos) {
        // TODO

    }

    protected void interactWithWater(BlockPos pos) {
        // TODO

    }

    protected void interactWithLava(BlockPos pos) {
        // TODO
    }

    @Override
    public void tick()
    {
        // quiver timer is really just a client side thing
        if (this.quiverTimer > 0) {
            quiverTimer--;
        }

        if (this.isHeld) {
            if (owner == null) {
                // orphaned, just drop to the ground
                detachGooFromSender(false);
            } else {
                // track owner motion!
                this.setMotion(owner.getMotion());
                handleKeepingSteady();
            }
        } else {

            handleMovement();
            this.isCollidingEntity = this.checkForEntityCollision();

            handleDecay();
            super.tick();
        }
    }

    protected void handleDecay() {
        // goo doesn't decay as long as you're holding it.
        if (this.isHeld) {
            return;
        }
        if (goo.getAmount() < 1) {
            this.setDead();
            this.remove();
        } else {
            if (ticksExisted >= 20) {
                goo.setAmount(goo.getAmount() - 1); // decay system needs work TODO
                setSize();
            }
        }
    }

    @Override
    protected void setDead()
    {
        super.setDead();
    }

    @Override
    public void remove()
    {
        super.remove();
    }

    public Vector3d getSenderHoldPosition() {
        return new Vector3d(owner.getPosX(), owner.getPosYEye(), owner.getPosZ()).add(owner.getLookVec().normalize().scale(1f + (this.cubicSize / 10f)));
    }

    protected void handleKeepingSteady() {
        Vector3d vec = getSenderHoldPosition();
        if (getPositionVec().subtract(vec).length() > 0.1F) {
            startQuivering();
        }
        this.setLocationAndAngles(vec.x, vec.y, vec.z, owner.rotationYaw, owner.rotationPitch);
    }

    private void handleMovement() {
        Vector3d motion = this.getMotion();
        BlockPos blockpos = this.getPosition();
        BlockState blockstate = this.world.getBlockState(blockpos);
        if (!blockstate.isAir(this.world, blockpos)) {
            VoxelShape voxelshape = blockstate.getCollisionShape(this.world, blockpos);
            if (!voxelshape.isEmpty()) {
                Vector3d vector3d1 = this.getPositionVec();

                for(AxisAlignedBB axisalignedbb : voxelshape.toBoundingBoxList()) {
                    if (axisalignedbb.offset(blockpos).contains(vector3d1)) {
                        this.isInGround = true;
                        break;
                    }
                }
            }
        }

        if (this.isInGround) {
            startQuivering();
            this.setMotion(Vector3d.ZERO);
        } else {
            doFreeMovement(motion);
        }
        this.inBlockState = blockstate;
    }

    protected void splat(BlockPos pos, Direction face) {
        // the state we're interested in observing is the state of the hit block, not the offset.
        BlockState state = world.getBlockState(pos);
        // the result position is, if needed, the offset position based on hit face.
        pos = pos.offset(face);
        if (state.isSolid()) {
            interactWithSolid(pos); // ideally I'd do more than this I think
        } else if (state.getMaterial() == Material.WATER) {
            if (state.getFluidState().getFluid() instanceof GooFluid) {
                if (state.getFluidState().getFluid().isEquivalentTo(goo.getFluid())) {
                    // same fluid, treat it like it's a solid.
                    interactWithSolid(pos);
                } else {
                    // different goo, maybe a reaction, but maybe it just stacks.
                    interactWithGoo(pos);
                }
            } else {
                interactWithWater(pos);
            }
        } else if (state.getMaterial() == Material.LAVA) {
            interactWithLava(pos);
        }
    }

    protected void doFreeMovement(Vector3d motion) {
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);
        BlockRayTraceResult blockResult = this.world.rayTraceBlocks(new RayTraceContext(position, projection, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.ANY, this));
        if (blockResult.getType() != RayTraceResult.Type.MISS) {
            splat(blockResult.getPos(), blockResult.getFace());
            return;
        }

        EntityRayTraceResult entityResult = this.rayTraceEntities(position, projection);
        if (entityResult != null && entityResult.getType() != RayTraceResult.Type.MISS) {
            motion.add(onImpact(entityResult));
            this.isAirBorne = true;
        }

        double d3 = motion.x;
        double d4 = motion.y;
        double d0 = motion.z;

        double d5 = this.getPosX() + d3;
        double d1 = this.getPosY() + d4;
        double d2 = this.getPosZ() + d0;

        if (this.isInWater()) {
            for(int j = 0; j < 4; ++j) {
                double f4 = 0.25F;
                this.world.addParticle(ParticleTypes.BUBBLE, d5 - d3 * f4, d1 - d4 * f4, d2 - d0 * f4, d3, d4, d0);
            }
        } else if (this.isInLava()) {
            for(int j = 0; j < 4; ++j) {
                double f4 = 0.25F;
                this.world.addParticle(ParticleTypes.FLAME, d5 - d3 * f4, d1 - d4 * f4, d2 - d0 * f4, d3, d4, d0);
            }
        }

        this.setMotion(motion.scale(GENERAL_FRICTION));

        this.doBlockCollisions();
        this.setPositionAndUpdate(projection.x, projection.y, projection.z);
    }

    /**
     * Gets the EntityRayTraceResult representing the entity hit
     */
    protected EntityRayTraceResult rayTraceEntities(Vector3d startVec, Vector3d endVec) {
        return ProjectileHelper.rayTraceEntities(this.world, this, startVec, endVec, this.getBoundingBox().expand(this.getMotion()).grow(1.0D), this::canHitEntityAndNotAlready);
    }

    protected boolean canHitEntityAndNotAlready(Entity hitEntity) {
        return canHitEntity(hitEntity) && (this.touchingEntityIds == null || !this.touchingEntityIds.contains(hitEntity.getEntityId()));
    }

    protected boolean canHitEntity(Entity hitEntity) {
        if (!hitEntity.isSpectator() && hitEntity.isAlive() && hitEntity.canBeCollidedWith()) {
            return owner == null || this.isCollidingEntity || !owner.isRidingSameEntity(hitEntity);
        } else {
            return false;
        }
    }

    @Override
    public void read(CompoundNBT tag)
    {
        super.read(tag);
        goo = FluidStack.loadFluidStackFromNBT(tag);
        cubicSize = tag.getFloat("cubicSize");
        setSize();
        if (tag.hasUniqueId("owner")) {
            this.owner = world.getPlayerByUuid(tag.getUniqueId("owner"));
        }
        this.isCollidingEntity = tag.getBoolean("LeftOwner");
        this.enchantedSpeed = tag.getFloat("enchantedSpeed");
        this.isHeld = tag.getBoolean("isHeld");
        this.isLaunched = tag.getBoolean("isLaunched");
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT tag = super.serializeNBT();
        goo.writeToNBT(tag);
        tag.putFloat("cubicSize", cubicSize);
        if (this.owner != null) { tag.putUniqueId("owner", owner.getUniqueID()); }
        if (this.isCollidingEntity) { tag.putBoolean("isDepartedOwner", true); }
        tag.putFloat("enchantedSpeed", this.enchantedSpeed);
        tag.putBoolean("isHeld", isHeld);
        tag.putBoolean("isLaunched", isLaunched);
        return tag;
    }

    public void shoot() {
        if (owner == null) {
            return;
        }
        this.isLaunched = true;
        startQuivering();
        Vector3d velVec = owner.getLookVec();
        double velocity = this.enchantedSpeed;
        velVec = (velVec).normalize().scale(velocity);
        this.setMotion(velVec);
    }

    public void startQuivering()
    {
        if (this.quiverTimer < QUIVER_TIMER_ONE_CYCLE_DOWN) {
            this.quiverTimer = QUIVER_TIMER_INITIALIZED_VALUE;
        }
    }

    @Override
    public IPacket<?> createSpawnPacket()
    {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void readAdditional(CompoundNBT tag) {
    }

    @Override
    public void writeAdditional(CompoundNBT tag) {
    }

    @Override
    public boolean onLivingFall(float distance, float damageMultiplier)
    {
        return super.onLivingFall(distance, damageMultiplier);
    }

    @Override
    public boolean canRenderOnFire()
    {
        return false;
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return false;
    }

    @Override
    public void writeSpawnData(PacketBuffer buffer)
    {
        CompoundNBT tag = serializeNBT();
        writeAdditional(tag);
        buffer.writeCompoundTag(tag);
    }

    @Override
    public void readSpawnData(PacketBuffer additionalData)
    {
        CompoundNBT tag = additionalData.readCompoundTag();
        deserializeNBT(tag);
        readAdditional(tag);
    }

    public void tryEnteringTank(BlockPos blockPos)
    {
        if (world.isRemote()) {
            return;
        }
        TileEntity tile = world.getTileEntity(blockPos);
        if (!(tile instanceof GooBulbTile)) {
            return;
        }

        IFluidHandler fh = FluidHandlerHelper.capability(tile, Direction.UP);
        int attemptTransfer = fh.fill(goo, IFluidHandler.FluidAction.SIMULATE);
        if (attemptTransfer >= goo.getAmount()) {
            fh.fill(goo, IFluidHandler.FluidAction.EXECUTE);
            goo.setAmount(0);
        } else {
            fh.fill(new FluidStack(goo.getFluid(), attemptTransfer), IFluidHandler.FluidAction.EXECUTE);
            goo.setAmount(goo.getAmount() - attemptTransfer);
        }
    }

    protected void setSize() {
        this.cubicSize = (float)Math.cbrt(goo.getAmount()) / 10f;
        this.size = new EntitySize(cubicSize, cubicSize, false);
        this.dataManager.set(GOO_SIZE, goo.getAmount());
        this.recalculateSize();
    }

    public GooFluid gooBase() {
        return (GooFluid)this.goo.getFluid();
    }
    private boolean isCollidingEntity;

    private boolean checkForEntityCollision() {
        if (this.isHeld) {
            return false;
        }
        if (owner != null) {
            Collection<Entity> collidedEntities =
                    this.world.getEntitiesInAABBexcluding(this,
                            // grow bb
                            this.getBoundingBox().expand(this.getMotion()).grow(1.0D),
                            // filter
                            (eInBB) -> !eInBB.isSpectator() && eInBB.canBeCollidedWith());
            for(Entity entity1 : collidedEntities) {
                // skip riders unless we're hitting the lowest
                if (entity1.getLowestRidingEntity() == owner.getLowestRidingEntity()) {
                    return false;
                }
            }
        }

        return true;
    }

    protected Vector3d onImpact(RayTraceResult rayTraceResult) {
        RayTraceResult.Type resultType = rayTraceResult.getType();
        Vector3d result = Vector3d.ZERO;
        if (resultType == RayTraceResult.Type.ENTITY) {
            result = onEntityHit((EntityRayTraceResult)rayTraceResult);
        } else if (resultType == RayTraceResult.Type.BLOCK) {
            result = collideBlockMaybe((BlockRayTraceResult)rayTraceResult);
        }

        return result;
    }

    /**
     * Complex/annoying: returns the buoyancy and drag of the material entity collided with (accounting for
     * things like relative buoyancy or overall stickiness.
     * @param entityTraceResult
     * @return
     */
    protected Vector3d onEntityHit(EntityRayTraceResult entityTraceResult) {
        Entity entityHit = entityTraceResult.getEntity();
        Vector3d result = this.getMotion();
        if (entityHit instanceof GooEntity) {
            GooFluid collidingGoo = ((GooEntity) entityHit).gooBase();
            result.add(doGooCollision(entityHit, collidingGoo));
        } else if (entityHit instanceof ServerPlayerEntity) {
            result.add(doPlayerCollision(((ServerPlayerEntity) entityHit)));
        } else {
            result.add(doEverythingElseCollision(entityHit));
        }

        return result;
    }

    protected Vector3d doEverythingElseCollision(Entity entityHit)
    {
        // TODO
        return this.getMotion();
    }

    protected Vector3d doPlayerCollision(ServerPlayerEntity entityHit)
    {
        // TODO
        return this.getMotion();
    }

    protected Vector3d doGooCollision(Entity entityHit, GooFluid collidingGoo)
    {
        // TODO
        return this.getMotion();
    }

    protected Vector3d collideBlockMaybe(BlockRayTraceResult rayTraceResult) {
        BlockState blockstate = this.world.getBlockState(rayTraceResult.getPos());
        if (this.inBlockState != blockstate) {
            doChangeBlockState(blockstate, rayTraceResult.getPos(), this.getPositionUnderneath());
        }
        return Vector3d.ZERO;
    }

    protected void doChangeBlockState(BlockState blockstate, BlockPos blockPos, BlockPos positionUnderneath) {
        TileEntity te = world.getTileEntity(blockPos);
        if (te instanceof GooBulbTileAbstraction) {
            tryEnteringTank(blockPos);
        }
    }

    public void attachGooToSender(Entity entity)
    {
        this.isHeld = true;
        this.isLaunched = false;
        this.owner = entity;
        this.enchantedSpeed = 3f; //getArmstrongSpeed(entity);

        handleKeepingSteady();
    }

//    protected float getArmstrongSpeed(Entity entity) {
//        if (!(entity instanceof PlayerEntity)) {
//            return 0f;
//        }
//
//        PlayerEntity player = (PlayerEntity)entity;
//        ItemStack holder = player.getHeldItemMainhand();
//        if (holder.isEmpty() || !(holder.getItem() instanceof GooHolder)) {
//            return 0f;
//        }
//
//        return ((GooHolder)holder.getItem()).data(holder).thrownSpeed(holder);
//    }

    public void detachGooFromSender(boolean isShooting) {
        if (isShooting) {
            shoot();
            if (this.world instanceof ServerWorld && this.owner instanceof PlayerEntity) {
                Networking.sendToClientsAround(new GooLobConfirmationPacket(this, (PlayerEntity)owner), (ServerWorld)world, this.getPosition());
            }
        }
        clearHolder();
    }

    public void clearHolder()
    {
        // this.owner = null;
        this.isHeld = false;
    }

    public Entity owner()
    {
        return owner;
    }

    public boolean isHeld() {
        return isHeld;
    }

    public float cubicSize()
    {
        return this.cubicSize;
    }

    public int quiverTimer()
    {
        return quiverTimer;
    }

    public boolean isLaunched()
    {
        return isLaunched;
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
        return 1000;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack)
    {
        return stack.isFluidEqual(this.goo);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action)
    {
        int spaceRemaining = getTankCapacity(1) - goo.getAmount();
        int transferAmount = Math.min(resource.getAmount(), spaceRemaining);
        if (action == FluidAction.EXECUTE && transferAmount > 0) {
            goo.setAmount(goo.getAmount() + transferAmount);
            setSize();
        }

        return transferAmount;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action)
    {
        FluidStack result = new FluidStack(goo.getFluid(), Math.min(goo.getAmount(), resource.getAmount()));
        if (action == FluidAction.EXECUTE) {
            goo.setAmount(goo.getAmount() - result.getAmount());
            setSize();
        }

        return result;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action)
    {
        FluidStack result = new FluidStack(goo.getFluid(), Math.min(goo.getAmount(), maxDrain));
        if (action == FluidAction.EXECUTE) {
            goo.setAmount(goo.getAmount() - result.getAmount());
            setSize();
        }

        return result;
    }
}
