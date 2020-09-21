package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.GooChopEffects;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GooBlob extends Entity implements IEntityAdditionalSpawnData, IFluidHandler
{
    private static final DataParameter<Integer> GOO_SIZE = EntityDataManager.createKey(GooBlob.class, DataSerializers.VARINT);
    private static final double GENERAL_FRICTION = 0.98d;
    private static final int QUIVER_TIMER_INITIALIZED_VALUE = 100;
    private static final int QUIVER_TIMER_ONE_CYCLE_DOWN = 75;
    private static final double GOO_GRAVITY = 0.06d;
    private FluidStack goo;
    private int quiverTimer;
    private Entity owner;
    private float cubicSize;
    private EntitySize size;
    private Direction sideWeLiveOn;
    private BlockPos blockAttached = null;
    private boolean isAttachedToBlock;

    public GooBlob(EntityType<GooBlob> type, World worldIn) {
        super(type, worldIn);
    }

    public GooBlob(EntityType<GooBlob> type, World worldIn, Entity sender, FluidStack stack) {
        super(type, worldIn);
        goo = stack;
        isAttachedToBlock = false;
        if (!(stack.getFluid() instanceof GooFluid)) {
            this.setDead();
            this.remove();
        } else {
            Vector3d pos = initialPosition(sender);
            this.setPositionAndRotation(pos.x, pos.y, pos.z, sender.rotationYaw, sender.rotationPitch);
            this.owner = sender;
            this.setSize();
            this.shoot();
        }
    }

    public GooBlob(EntityType<GooBlob> type, World worldIn, Entity sender, FluidStack stack, Vector3d pos) {
        super(type, worldIn);
        goo = stack;
        this.setPositionAndRotation(pos.x, pos.y, pos.z, sender.rotationYaw, sender.rotationPitch);
        this.owner = sender;
        this.setSize();
    }

    private Vector3d initialPosition(Entity sender)
    {
        return sender.getPositionVec().add(0d, sender.getEyeHeight(), 0d).add(sender.getLookVec());
    }

    private void shoot()
    {
        if (owner == null) {
            return;
        }
        if (owner instanceof PlayerEntity) {
            world.playSound((PlayerEntity) owner, owner.getPosX(), owner.getPosY(), owner.getPosZ(), Registry.GOO_LOB_SOUND.get(),
                    SoundCategory.PLAYERS, 1.0f, world.rand.nextFloat() * 0.5f + 0.5f);
        }
        startQuivering();
        Vector3d velVec = owner.getLookVec();
        velVec = (velVec).normalize().scale(1.0f);
        this.setMotion(velVec);
    }

    public AxisAlignedBB getCollisionBoundingBox() {
        return this.getBoundingBox();
    }

    @Override
    public void recalculateSize() {
        double d0 = this.getPosX();
        double d1 = this.getPosY();
        double d2 = this.getPosZ();
        super.recalculateSize();
        this.setPosition(d0, d1, d2);
    }

    @Override
    public void tick()
    {
        super.tick();

        // quiver timer is really just a client side thing
        if (this.quiverTimer > 0) {
            quiverTimer--;
        }

        if (isAttachedToBlock) {
            approachAttachmentPoint();
        } else {
            if (handleMovement()) {
                return; // stop moving
            }
        }
        doFreeMovement();
        this.isCollidingEntity = this.checkForEntityCollision();
    }

    private void approachAttachmentPoint()
    {
        Vector3d attachmentPoint = attachmentPoint();
        Vector3d approachVector = attachmentPoint.subtract(this.getPositionVec());
        if (approachVector.length() <= 0.1d) {
            this.setMotion(approachVector);
        } else {
            this.setMotion(approachVector.normalize().scale(0.05d));
        }
    }

    private Vector3d attachmentPoint()
    {
        Vector3d attachmentPoint = new Vector3d(
                blockAttached.getX(),
                blockAttached.getY(),
                blockAttached.getZ())
                .add(0.5d, 0.5d, 0.5d)
                .add(0.5d * sideWeLiveOn.getXOffset(),
                        0.5d * sideWeLiveOn.getYOffset(),
                        0.5d * sideWeLiveOn.getZOffset())
                .add((this.cubicSize / 2d) * sideWeLiveOn.getXOffset(),
                        (this.cubicSize / 2d) * sideWeLiveOn.getYOffset(),
                        (this.cubicSize / 2d) * sideWeLiveOn.getZOffset());
        return attachmentPoint;
    }

    private boolean handleMovement() {
        handleGravity();
        handleFriction();
        return handleMaterialCollisionChecks();
    }

    private boolean handleMaterialCollisionChecks()
    {
        Vector3d motion = this.getMotion();
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);

        BlockRayTraceResult blockResult = this.world.rayTraceBlocks(new RayTraceContext(position, projection, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.ANY, this));
        if (blockResult.getType() != RayTraceResult.Type.MISS) {
            // try colliding with the block for some tank interaction, this entity
            // wants to enter receptacles first if it can, but splat otherwise
            collideBlockMaybe(blockResult);
            if (goo.getAmount() > 0) {
                splat(blockResult.getPos(), blockResult.getFace(), blockResult.getHitVec());
                return true;
            }
            return false;
        }

        EntityRayTraceResult entityResult = this.rayTraceEntities(position, projection);
        if (entityResult != null && entityResult.getType() != RayTraceResult.Type.MISS) {
            onImpact(entityResult);
            this.isAirBorne = true;
        }

        handleLiquidCollisions(motion);

        this.doBlockCollisions();
        return false;
    }

    private void handleFriction()
    {
        this.setMotion(this.getMotion().scale(GENERAL_FRICTION));
    }

    private void handleGravity()
    {
        this.setMotion(this.getMotion().add(0d, -GOO_GRAVITY, 0d));
    }

    protected void splat(BlockPos pos, Direction face, Vector3d hitVec) {
        // the state we're interested in observing is the state of the hit block, not the offset.
        BlockState state = world.getBlockState(pos);
        GooSplatEffects.spawnParticles(this);
        attachToBlock(pos, face);
        // create a goo splat
        GooSplat splat = new GooSplat(Registry.GOO_SPLAT.get(), world, this, hitVec);
        world.addEntity(splat);
        splat.fill(this.drain(1, FluidAction.EXECUTE), FluidAction.EXECUTE);
    }

    private void attachToBlock(BlockPos pos, Direction face)
    {
        this.blockAttached = pos;
        this.sideWeLiveOn = face;
        this.isAttachedToBlock = true;
    }

    protected void doFreeMovement() {
        Vector3d motion = this.getMotion();
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);

        this.setPositionAndRotation(projection.x, projection.y, projection.z, this.rotationYaw, this.rotationPitch);
    }

    private void handleLiquidCollisions(Vector3d motion)
    {
        double xVel = motion.x;
        double yVel = motion.y;
        double zVel = motion.z;

        double xProj = this.getPosX() + xVel;
        double yProj = this.getPosY() + yVel;
        double zProj = this.getPosZ() + zVel;

        if (this.isInWater()) {
            for(int j = 0; j < 4; ++j) {
                double f4 = 0.25F;
                this.world.addParticle(ParticleTypes.BUBBLE, xProj - xVel * f4, yProj - yVel * f4, zProj - zVel * f4, xVel, yVel, zVel);
            }
        } else if (this.isInLava()) {
            for(int j = 0; j < 4; ++j) {
                double f4 = 0.25F;
                this.world.addParticle(ParticleTypes.FLAME, xProj - xVel * f4, yProj - yVel * f4, zProj - zVel * f4, xVel, yVel, zVel);
            }
        }
    }

    /**
     * Gets the EntityRayTraceResult representing the entity hit
     */
    protected EntityRayTraceResult rayTraceEntities(Vector3d startVec, Vector3d endVec) {
        return ProjectileHelper.rayTraceEntities(this.world, this, startVec, endVec, this.getBoundingBox().expand(this.getMotion()).grow(1.0D), this::canHitEntityAndNotAlready);
    }

    protected boolean canHitEntityAndNotAlready(Entity hitEntity) {
        return canHitEntity(hitEntity);
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
        deserializeAttachment(tag);
        setSize();
        if (tag.hasUniqueId("owner")) {
            this.owner = world.getPlayerByUuid(tag.getUniqueId("owner"));
        }
        this.isCollidingEntity = tag.getBoolean("LeftOwner");
    }

    private void deserializeAttachment(CompoundNBT tag)
    {
        if (!tag.contains("attachment")) {
            this.isAttachedToBlock = false;
            return;
        }
        CompoundNBT at = tag.getCompound("attachment");
        this.blockAttached =
                new BlockPos(at.getInt("x"), at.getInt("y"), at.getInt("z"));
        this.sideWeLiveOn = Direction.byIndex(at.getInt("side"));
        this.isAttachedToBlock = true;
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT tag = super.serializeNBT();
        goo.writeToNBT(tag);
        serializeAttachment(tag);
        tag.putFloat("cubicSize", cubicSize);
        if (this.owner != null) { tag.putUniqueId("owner", owner.getUniqueID()); }
        if (this.isCollidingEntity) { tag.putBoolean("isDepartedOwner", true); }
        return tag;
    }

    private void serializeAttachment(CompoundNBT tag)
    {
        if (!isAttachedToBlock) {
            return;
        }
        CompoundNBT at = new CompoundNBT();
        at.putInt("x", blockAttached.getX());
        at.putInt("y", blockAttached.getY());
        at.putInt("z", blockAttached.getZ());
        at.putInt("side", sideWeLiveOn.getIndex());
        tag.put("attachment", at);
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

    public void tryFluidHandlerInteraction(BlockPos blockPos, Direction sideHit)
    {
        if (world.isRemote()) {
            return;
        }

        LazyOptional<IFluidHandler> fh = FluidHandlerHelper.capability(this.world, blockPos, sideHit);
        fh.ifPresent(this::enterTank);
    }

    private void enterTank(IFluidHandler fh)
    {
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
        this.cubicSize = (float)Math.cbrt(goo.getAmount() / 1000f);
        this.size = new EntitySize(cubicSize, cubicSize, false);
        this.dataManager.set(GOO_SIZE, goo.getAmount());
        this.recalculateSize();
    }

    @Override
    protected void registerData() {
        this.dataManager.register(GOO_SIZE, 1);
    }

    public void notifyDataManagerChange(DataParameter<?> key) {
        if (GOO_SIZE.equals(key)) {
            this.recalculateSize();
        }

        super.notifyDataManagerChange(key);
    }

    private boolean isCollidingEntity;

    private boolean checkForEntityCollision() {
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

    protected void onImpact(RayTraceResult rayTraceResult) {
        RayTraceResult.Type resultType = rayTraceResult.getType();
        if (resultType == RayTraceResult.Type.ENTITY) {
            collideWithEntity(((EntityRayTraceResult)rayTraceResult).getEntity());
        }
    }

    protected void collideWithEntity(Entity entityHit)
    {
        if (entityHit == owner) {
            return;
        }
        if (entityHit instanceof LivingEntity && this.owner instanceof LivingEntity) {
            int intensity = Math.max(1, (int)Math.ceil(Math.sqrt(this.goo.getAmount()) - 1));
            GooChopEffects.doChopEffect(this.goo, intensity, (LivingEntity)this.owner, (LivingEntity)entityHit);
            // dissipate
            this.remove();
        }
    }

    protected void collideBlockMaybe(BlockRayTraceResult rayTraceResult) {
        doChangeBlockState(rayTraceResult.getPos(), rayTraceResult.getFace());
    }

    protected void doChangeBlockState(BlockPos blockPos, Direction sideHit) {
        tryFluidHandlerInteraction(blockPos, sideHit);
    }

    public float cubicSize()
    {
        return this.cubicSize;
    }

    public int quiverTimer()
    {
        return quiverTimer;
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
    public int fill(FluidStack resource, IFluidHandler.FluidAction action)
    {
        int spaceRemaining = getTankCapacity(1) - goo.getAmount();
        int transferAmount = Math.min(resource.getAmount(), spaceRemaining);
        if (action == IFluidHandler.FluidAction.EXECUTE && transferAmount > 0) {
            goo.setAmount(goo.getAmount() + transferAmount);
            setSize();
        }

        return transferAmount;
    }

    @Override
    public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action)
    {
        FluidStack result = new FluidStack(goo.getFluid(), Math.min(goo.getAmount(), resource.getAmount()));
        if (action == IFluidHandler.FluidAction.EXECUTE) {
            goo.setAmount(goo.getAmount() - result.getAmount());
            if (goo.isEmpty()) {
                this.remove();
            } else {
                setSize();
            }
        }

        return result;
    }

    @Override
    public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action)
    {
        FluidStack result = new FluidStack(goo.getFluid(), Math.min(goo.getAmount(), maxDrain));
        if (action == IFluidHandler.FluidAction.EXECUTE) {
            goo.setAmount(goo.getAmount() - result.getAmount());
            if (goo.isEmpty()) {
                this.remove();
            } else {
                setSize();
            }
        }

        return result;
    }

    public Entity owner()
    {
        return this.owner;
    }

    public FluidStack goo() {
        return this.goo;
    }

    public Direction sideWeLiveOn()
    {
        return sideWeLiveOn;
    }

    public BlockPos blockAttached()
    {
        return this.blockAttached;
    }
}
