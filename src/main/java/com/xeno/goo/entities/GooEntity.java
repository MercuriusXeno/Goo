package com.xeno.goo.entities;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.LooseMaterialTypes;
import com.xeno.goo.fluids.GooBase;
import com.xeno.goo.items.GooHolder;
import com.xeno.goo.tiles.BulbFluidHandler;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapeSpliterator;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public abstract class GooEntity extends Entity implements IEntityAdditionalSpawnData
{
    private static final DataParameter<Integer> GOO_SIZE = EntityDataManager.createKey(GooEntity.class, DataSerializers.VARINT);
    private double enchantedSpeed;
    public FluidStack goo;
    private boolean isInGround;
    private int quiverTimer;
    private BlockState inBlockState;
    private List<Integer> touchingEntityIds;
    private boolean isHeld;
    private Entity owner;


    protected GooEntity(EntityType<? extends GooEntity> entityType, World worldIn, Entity sender, FluidStack stack) {
        super(entityType, worldIn);
        goo = stack;
        if (!(stack.getFluid() instanceof GooBase)) {
            this.setDead();
        } else {
            this.setSize();
        }
        this.setInvulnerable(true);
//        this.setNoGravity(true);
        this.attachGooToSender(sender);
    }

    @Nullable
    public AxisAlignedBB getCollisionBoundingBox() {
        return this.getBoundingBox();
    }

//    @Override
//    public void checkDespawn()
//    {
//        super.checkDespawn();
//    }

    public GooEntity(EntityType<CrystalEntity> type, World world)
    {
        super(type, world);
        this.setInvulnerable(true);
    }

    public void recalculateSize() {
        double d0 = this.getPosX();
        double d1 = this.getPosY();
        double d2 = this.getPosZ();
        super.recalculateSize();
        this.setPosition(d0, d1, d2);
    }

    @Override
    public EntitySize getSize(Pose poseIn) {
        return new EntitySize(sizeRatio(), sizeRatio(), false);
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

    @Override
    protected void onInsideBlock(BlockState state)
    {
        super.onInsideBlock(state);
        if (state.getMaterial() == Material.LAVA) {
            interactWithLava();
        } else if (state.getMaterial() == Material.WATER) {
            interactWithWater();
        } else if (state.isSolid()) {
            interactWithSolid(); // ideally I'd do more than this I think
        }
    }

    protected abstract void interactWithWater();

    protected abstract void interactWithSolid();

    protected abstract void interactWithLava();

    @Override
    public void tick()
    {
        if (this.world.isRemote()) {
            return;
        }

        if (owner == null) {
            detachFromHolder(false);
        }
        
        handleDecay();

        if (!this.isHeld) {
            handleMovement();
            this.isCollidingEntity = this.checkForEntityCollision();
        } else {
            handleKeepingSteady();
        }

        super.tick();
    }

    protected void handleDecay() {
        // goo doesn't decay as long as you're holding it.
        if (this.isHeld) {
            return;
        }
        if (goo.getAmount() < 1) {
            this.setDead();
        } else {
            goo.setAmount(goo.getAmount() - gooBase().decayRate());
            setSize();
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

    private void detachFromHolder(boolean isShooting)
    {
        if (isShooting) {
            shoot();
        }
        this.isHeld = false;
    }

    protected Vector3d getSenderHoldPosition() {
        return new Vector3d(owner.getPosX(), owner.getPosYEye(), owner.getPosZ()).add(owner.getLookVec().normalize().mul(2 * this.sizeRatio(), 2 * this.sizeRatio(), 2 * this.sizeRatio()));
    }

    protected void handleKeepingSteady() {
        Vector3d vec = getSenderHoldPosition();
        this.setRotation(owner.getYaw(0f), owner.getPitch(0f));
        setPositionAndUpdate(vec.x, vec.y, vec.z);
    }

    private boolean shouldRelease() {
        if (this.getCollisionBoundingBox() == null) {
            return false;
        }
        return this.isInGround && this.hasNoCollisions(this, this.getCollisionBoundingBox().grow(0.01D));
    }

    boolean hasNoCollisions(Entity entity, AxisAlignedBB aabb) {
        return this.allVoxelCollisionIsEmpty(entity, aabb, (checkEntity) -> {
            return true;
        });
    }

    boolean allVoxelCollisionIsEmpty(@Nullable Entity e, AxisAlignedBB b, Predicate<Entity> p) {
        return this.getVoxelShapesColliding(e, b, p).allMatch(VoxelShape::isEmpty);
    }

    Stream<VoxelShape> getVoxelShapesColliding(@Nullable Entity e, AxisAlignedBB b, Predicate<Entity> p) {
        List<VoxelShape> listVoxes = this.getCollisionShapes(e, b).collect(Collectors.toList());
        List<VoxelShape> listWorldVoxes = this.world.func_230318_c_(e, b, p).collect(Collectors.toList());
        return Stream.concat(this.getCollisionShapes(e, b), this.world.func_230318_c_(e, b, p));
    }

    Stream<VoxelShape> getCollisionShapes(@Nullable Entity e, AxisAlignedBB b) {
        return StreamSupport.stream(new VoxelShapeSpliterator(world, e, b), false);
    }

    private void releaseFromGround() {
        this.isInGround = false;
        Vector3d vector3d = this.getMotion();
        this.setMotion(vector3d.mul((this.rand.nextFloat() * 0.2F), (this.rand.nextFloat() * 0.2F), (this.rand.nextFloat() * 0.2F)));
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

        if (this.quiverTimer > 0) {
            --this.quiverTimer;
        }

        if (this.isInGround) {
            if (this.inBlockState != blockstate && this.shouldRelease()) {
                this.releaseFromGround();
            } else {
                doCollidedMovement(motion);
            }
        } else {
            doFreeMovement(motion);
        }
    }

    protected void doCollidedMovement(Vector3d motion) {
        LooseMaterialTypes materialIn = LooseMaterialTypes.ANY;
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);
        RayTraceResult rayTraceResult = this.world.rayTraceBlocks(new RayTraceContext(position, projection, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.ANY, this));
        if (rayTraceResult.getType() != RayTraceResult.Type.MISS) {
            projection = rayTraceResult.getHitVec();
        }

        EntityRayTraceResult entityResult = this.rayTraceEntities(position, projection);
        if (entityResult != null) {
            rayTraceResult = entityResult;
        }

        if (rayTraceResult.getType() != RayTraceResult.Type.MISS) {
            motion.add(onImpact(rayTraceResult));

            // I'm not sure how this means airborne. Seems inverted.
            this.isAirBorne = true;
        }

        double d3 = motion.x;
        double d4 = motion.y;
        double d0 = motion.z;

        double d5 = this.getPosX() + d3;
        double d1 = this.getPosY() + d4;
        double d2 = this.getPosZ() + d0;

        if (this.isInWater()) {
            materialIn = LooseMaterialTypes.WATER;
            for(int j = 0; j < 4; ++j) {
                double f4 = 0.25F;
                this.world.addParticle(ParticleTypes.BUBBLE, d5 - d3 * f4, d1 - d4 * f4, d2 - d0 * f4, d3, d4, d0);
            }
        } else if (this.isInLava()) {
            materialIn = LooseMaterialTypes.LAVA;
            for(int j = 0; j < 4; ++j) {
                double f4 = 0.25F;
                this.world.addParticle(ParticleTypes.FLAME, d5 - d3 * f4, d1 - d4 * f4, d2 - d0 * f4, d3, d4, d0);
            }
        }

        double drag = gooBase().stickiness(materialIn);
        //double buoyancy = gooBase().buoyancy(materialIn);
        this.setMotion(motion.scale(1f - drag));
        //this.applyBuoyancyMotionY(buoyancy);

        this.doBlockCollisions();
        this.setPositionAndUpdate(projection.x, projection.y, projection.z);
    }

    protected void doFreeMovement(Vector3d motion) {
        LooseMaterialTypes materialIn = LooseMaterialTypes.AIR;
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);
        GooMod.debug("proj x " + projection.x + " y " + projection.y + " z " + projection.z);
        RayTraceResult rayTraceResult = this.world.rayTraceBlocks(new RayTraceContext(position, projection, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.ANY, this));
        if (rayTraceResult.getType() != RayTraceResult.Type.MISS) {
            projection = rayTraceResult.getHitVec();
            GooMod.debug("rt proj x " + projection.x + " y " + projection.y + " z " + projection.z);
            materialIn = LooseMaterialTypes.ANY;
        }

        EntityRayTraceResult entityResult = this.rayTraceEntities(position, projection);
        if (entityResult != null) {
            rayTraceResult = entityResult;
        }

        if (rayTraceResult.getType() != RayTraceResult.Type.MISS) {
            motion.add(onImpact(rayTraceResult));

            // I'm not sure how this means airborne. Seems inverted.
            this.isAirBorne = true;
        }

        double d3 = motion.x;
        double d4 = motion.y;
        double d0 = motion.z;

        double d5 = this.getPosX() + d3;
        double d1 = this.getPosY() + d4;
        double d2 = this.getPosZ() + d0;

        if (this.isInWater()) {
            materialIn = LooseMaterialTypes.WATER;
            for(int j = 0; j < 4; ++j) {
                double f4 = 0.25F;
                this.world.addParticle(ParticleTypes.BUBBLE, d5 - d3 * f4, d1 - d4 * f4, d2 - d0 * f4, d3, d4, d0);
            }
        } else if (this.isInLava()) {
            materialIn = LooseMaterialTypes.LAVA;
            for(int j = 0; j < 4; ++j) {
                double f4 = 0.25F;
                this.world.addParticle(ParticleTypes.FLAME, d5 - d3 * f4, d1 - d4 * f4, d2 - d0 * f4, d3, d4, d0);
            }
        }

        double drag = gooBase().stickiness(materialIn);
//        double buoyancy = gooBase().buoyancy(materialIn);
        this.setMotion(motion.scale(1f - drag));
//        this.applyBuoyancyMotionY(buoyancy);

        this.doBlockCollisions();
        this.setPositionAndUpdate(projection.x, projection.y, projection.z);
    }

    @Override
    public ActionResultType applyPlayerInteraction(PlayerEntity player, Vector3d vec, Hand hand)
    {
        return super.applyPlayerInteraction(player, vec, hand);
    }

    @Override
    public ActionResultType processInitialInteract(PlayerEntity player, Hand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (heldItem.isEmpty()) {
            return ActionResultType.PASS;
        }
        if (heldItem.getItem() instanceof GooHolder) {
            this.attachGooToSender(player);
            return ActionResultType.SUCCESS;
        }
        return ActionResultType.PASS;
    }


    protected void applyBuoyancyMotionY(double buoyancy) {
        if (testBuoyancyEvenNecessary(buoyancy)) {
            this.setMotion(this.getMotion().add(0d, buoyancy, 0d));
        }
    }

    protected boolean testBuoyancyEvenNecessary(double buoyancy) {
        return true;
    }

    /**
     * Gets the EntityRayTraceResult representing the entity hit
     */
    @Nullable
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
        setSize();
        if (tag.hasUniqueId("Owner")) {
            this.owner = world.getPlayerByUuid(tag.getUniqueId("Owner"));
        }
        this.isCollidingEntity = tag.getBoolean("LeftOwner");
        this.enchantedSpeed = tag.getFloat("enchantedSpeed");
        this.isHeld = tag.getBoolean("isHeld");
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT tag = super.serializeNBT();
        goo.writeToNBT(tag);
        if (this.owner != null) { tag.putUniqueId("owner", owner.getUniqueID()); }
        if (this.isCollidingEntity) { tag.putBoolean("isDepartedOwner", true); }
        return tag;
    }

    public void shoot() {
        if (owner == null) {
            return;
        }
        Vector3d velVec = owner.getLookVec();
        double velocity = this.enchantedSpeed;
        velVec = (velVec).normalize().scale(velocity);
        this.setMotion(velVec);
        this.owner = null;
        this.isHeld = false;
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

        GooBulbTile b = (GooBulbTile) tile;

        BulbFluidHandler bfh = (BulbFluidHandler) BulbFluidHandler.bulbCapability(b, Direction.UP);
        int attemptTransfer = bfh.fill(goo, IFluidHandler.FluidAction.SIMULATE);
        if (attemptTransfer >= goo.getAmount()) {
            bfh.fill(goo, IFluidHandler.FluidAction.EXECUTE);
            goo.setAmount(0);
        } else {
            bfh.fill(new FluidStack(goo.getFluid(), attemptTransfer), IFluidHandler.FluidAction.EXECUTE);
            goo.setAmount(goo.getAmount() - attemptTransfer);
        }
    }

    protected void setSize() {
        this.dataManager.set(GOO_SIZE, goo.getAmount());
        this.setBoundingBox(getSizeBasedBoundingBox());
        this.recalculateSize();
    }

    protected AxisAlignedBB getSizeBasedBoundingBox() {
        Vector3d minPosVec = this.getPositionVec().add(-this.sizeRatio() / 2d, 0d, -this.sizeRatio() / 2d);
        Vector3d maxPosVec = this.getPositionVec().add(this.sizeRatio() / 2d, this.sizeRatio(), this.sizeRatio() / 2d);
        return new AxisAlignedBB(minPosVec, maxPosVec);
    }

    public float sizeRatio() { return (float)Math.cbrt(this.getDataManager().get(GOO_SIZE) / 1000f); } // 1000 is the cubic scale we're using via cube root to obtain 1000 mB = 1 Cube, ish.

    public abstract ResourceLocation texture();

    public abstract GooBase gooBase();
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
            GooBase collidingGoo = ((GooEntity) entityHit).gooBase();
            result.add(doGooCollision(entityHit, collidingGoo));
        } else if (entityHit instanceof ServerPlayerEntity) {
            result.add(doPlayerCollision(((ServerPlayerEntity) entityHit)));
        } else {
            result.add(doEverythingElseCollision(entityHit));
        }

        return result;
    }

    protected abstract Vector3d doEverythingElseCollision(Entity entityHit);

    protected abstract Vector3d doPlayerCollision(ServerPlayerEntity entityHit);

    protected abstract Vector3d doGooCollision(Entity entityHit, GooBase collidingGoo);

    protected Vector3d collideBlockMaybe(BlockRayTraceResult rayTraceResult) {
        BlockState blockstate = this.world.getBlockState(rayTraceResult.getPos());
        if (this.inBlockState != blockstate) {
            doChangeBlockState(blockstate, rayTraceResult.getPos(), this.getPositionUnderneath());
        }
        return Vector3d.ZERO;
    }

    protected void doChangeBlockState(BlockState blockstate, BlockPos blockPos, BlockPos positionUnderneath) {
        TileEntity te = world.getTileEntity(blockPos);
        if (te instanceof GooBulbTile) {
            tryEnteringTank(blockPos);
        }
    }

    public void attachGooToSender(Entity entity)
    {
        this.isHeld = true;
        this.owner = entity;
        this.enchantedSpeed = getArmstrongSpeed(entity);

        handleKeepingSteady();
    }

    protected double getArmstrongSpeed(Entity entity) {
        if (!(entity instanceof PlayerEntity)) {
            return 0d;
        }

        PlayerEntity player = (PlayerEntity)entity;
        ItemStack holder = player.getHeldItemMainhand();
        if (holder.isEmpty() || !(holder.getItem() instanceof GooHolder)) {
            return 0d;
        }

        return ((GooHolder)holder.getItem()).data(holder).thrownSpeed(holder);
    }

    public void detachGooFromSender(boolean isShooting) {
        if (isShooting) { shoot(); } else {
            this.owner = null;
            this.isHeld = false;
        }
    }

    public Entity owner()
    {
        return owner;
    }

    public boolean isHeld() {
        return isHeld;
    }
}
