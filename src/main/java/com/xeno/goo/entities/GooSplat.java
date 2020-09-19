package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.GooChopEffects;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
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
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.Collection;

import static net.minecraft.util.Direction.Axis.X;

public class GooSplat extends Entity implements IEntityAdditionalSpawnData, IFluidHandler
{
    private static final DataParameter<Integer> GOO_SIZE = EntityDataManager.createKey(GooSplat.class, DataSerializers.VARINT);
    private static final int INITIAL_GRACE_TICKS = 60;
    private static final int DECAY_TICKS_PER_UNIT = 10;
    private static final double GRAVITY = 0.012d;
    private static final double GENERAL_FRICTION = 0.98d;
    public FluidStack goo;
    private Entity owner;
    private Vector3d shape;
    private AxisAlignedBB box;
    private int graceTicks;
    private Direction sideWeLiveOn;
    private double depth;
    private int decayTicks;

    public Vector3d shape()
    {
        return this.shape;
    }

    public Direction.Axis depthAxis() {
        return sideWeLiveOn.getAxis();
    }

    public GooSplat(EntityType<GooSplat> type, World worldIn) {
        super(type, worldIn);
    }

    public GooSplat(EntityType<GooSplat> type, World worldIn, Entity sender, Direction sideWeLiveOn, Vector3d hitVec, FluidStack stack) {
        super(type, worldIn);
        this.goo = stack;
        this.owner = sender;
        resetGraceTicks();
        this.decayTicks = 0;
        this.sideWeLiveOn = sideWeLiveOn;
        updateSplatState(true, false, 0d);
        if (!(stack.getFluid() instanceof GooFluid) || stack.isEmpty()) {
            this.setDead();
            this.remove();
        } else {
            Vector3d findCenter = findCenter(hitVec);
            this.setPosition(findCenter.x, findCenter.y, findCenter.z);
            this.setSize();
        }
        if (sender instanceof PlayerEntity) {
            world.playSound((PlayerEntity)sender, sender.getPositionVec().x, sender.getPositionVec().y,
                    sender.getPositionVec().z, Registry.GOO_SPLAT_SOUND.get(), SoundCategory.PLAYERS,
                    1.0f, world.rand.nextFloat() * 0.5f + 0.5f);
        } else {
            world.playSound(hitVec.x, hitVec.y, hitVec.z, Registry.GOO_SPLAT_SOUND.get(), SoundCategory.AMBIENT,
                    1.0f, world.rand.nextFloat() * 0.5f + 0.5f, false);
        }
    }

    private void resetGraceTicks()
    {
        this.graceTicks = INITIAL_GRACE_TICKS;
    }

    private static final double LIQUID_CUBIC_RATIO = 1000d;
    private void setShape()
    {
        double amountWithDecay = goo.getAmount() - (decayTicks / (double)DECAY_TICKS_PER_UNIT);
        double cubicArea = amountWithDecay / LIQUID_CUBIC_RATIO;
        double targetSurfaceArea = cubicArea / depth;
        double sideLength = Math.sqrt(targetSurfaceArea);
        // for visuals only
        if (amountWithDecay < 1.0d) {
            sideLength *= amountWithDecay;
        }
        switch (depthAxis()) {
            case X:
                shape =  new Vector3d(depth, sideLength, sideLength);
                break;
            case Y:
                shape =  new Vector3d(sideLength, depth, sideLength);
                break;
            case Z:
                shape =  new Vector3d(sideLength, sideLength, depth);
                break;
            default:
                shape = Vector3d.ZERO;
                break;
        }

        makeBox();
    }

    private void decay()
    {
        if (goo.getAmount() - 1 <= 0) {
            this.remove();
            return;
        }
        goo.setAmount(goo.getAmount() - 1);
        updateSplatState(false, false, goo.getAmount() / (double)(goo.getAmount() + 1));
    }

    private void updateSplatState(boolean isInitializing, boolean isDripping, double ratio)
    {
        if (isInitializing) {
            setDepth(defaultDepth());
        } else {
            if (ratio < 1.0d || isDripping) {
                // ratio is actually a cubic reduction, we have to settle that here
                setDepth(this.depth * Math.cbrt(ratio));
            } else {
                setDepth(defaultDepth());
            }
        }
        setShape();
        makeBox();
    }

    private double defaultDepth()
    {
        return Math.cbrt(goo.getAmount() / LIQUID_CUBIC_RATIO) * 0.1d;
    }

    private void makeBox()
    {
        this.box = new AxisAlignedBB(shape.scale(-0.5d), shape.scale(0.5d));
    }

    /**
     * Sets the x,y,z of the entity from the given parameters. Also seems to set up a bounding box.
     */
    public void setPosition(double x, double y, double z) {
        this.setRawPosition(x, y, z);
        if (this.isAddedToWorld() && !this.world.isRemote && world instanceof ServerWorld)
            ((ServerWorld)this.world).chunkCheck(this); // Forge - Process chunk registration after moving.
        if (box == null) {
            Vector3d bsBox = Vector3d.ZERO.add(0.1d, 0.1d, 0.1d);
            this.setBoundingBox(new AxisAlignedBB(getPositionVec().subtract(bsBox), getPositionVec().add(bsBox)));
        } else {
            this.setBoundingBox(box.offset(this.getPositionVec()));
        }
    }

    private Vector3d findCenter(Vector3d hitVec)
    {
        switch(sideWeLiveOn) {
            case SOUTH:
                return new Vector3d(hitVec.x - (0.01d + depth / 2d), hitVec.y, hitVec.z);
            case NORTH:
                return new Vector3d(hitVec.x + (0.01d + depth / 2d), hitVec.y, hitVec.z);
            case DOWN:
                return new Vector3d(hitVec.x, hitVec.y + (0.01d + depth / 2d), hitVec.z);
            case UP:
                return new Vector3d(hitVec.x, hitVec.y - (0.01d + depth / 2d), hitVec.z);
            case WEST:
                return new Vector3d(hitVec.x, hitVec.y, hitVec.z + (0.01d + depth / 2d));
            case EAST:
                return new Vector3d(hitVec.x, hitVec.y, hitVec.z - (0.01d + depth / 2d));
        }
        // something weird happened that wasn't supposed to.
        return hitVec;
    }

    @Override
    public void remove()
    {
        super.remove();
    }

    @Override
    protected void setDead()
    {
        super.setDead();
    }

    private void setDepth(double depth)
    {
        this.depth = depth;
    }

    public AxisAlignedBB getCollisionBoundingBox() {
        return null;
    }

    public void recalculateSize() {
        double d0 = this.getPosX();
        double d1 = this.getPosY();
        double d2 = this.getPosZ();
        super.recalculateSize();
        this.setPosition(d0, d1, d2);
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
    public void tick()
    {
        super.tick();
        handleMaterialCollisionChecks();
        handleGravity();
        handleFriction();
        handleDecay();
        doFreeMovement();
        this.isCollidingEntity = this.checkForEntityCollision();
    }

    private void handleFriction()
    {
        this.setMotion(this.getMotion().scale(GENERAL_FRICTION));
    }

    protected void doFreeMovement() {
        Vector3d motion = this.getMotion();
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);

        this.setPositionAndRotation(projection.x, projection.y, projection.z, this.rotationYaw, this.rotationPitch);
    }

    private void handleMaterialCollisionChecks()
    {
        Vector3d motion = this.getMotion();
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);

        BlockRayTraceResult blockResult = this.world.rayTraceBlocks(new RayTraceContext(position, projection, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.ANY, this));
        if (blockResult.getType() != RayTraceResult.Type.MISS) {
            // try colliding with the block for some tank interaction, this entity
            collideBlockMaybe(blockResult);
        }

        EntityRayTraceResult entityResult = this.rayTraceEntities(position, projection);
        if (entityResult != null && entityResult.getType() != RayTraceResult.Type.MISS) {
            onImpact(entityResult);
            this.isAirBorne = true;
        }

        handleLiquidCollisions(motion);

        this.doBlockCollisions();
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

    private void handleGravity()
    {
        // if we're clinging to a ceiling or floor, we don't move.
        if (depthAxis() == Direction.Axis.Y) {
            // counterintuitively, this means we're on the "top" of a block
            // (we're on the "bottom" of the block we're inside)
            if (sideWeLiveOn == Direction.DOWN) {
                return;
            } else {
                // we're dripping!
                updateSplatState(false, true, 1.1d);
                if (Math.pow(this.depth, 3) > goo.getAmount() / LIQUID_CUBIC_RATIO) {
                    detachFromHangingAndFormBlob();
                }
                return;
            }

        }
        this.setMotion(0d, -GRAVITY / 2d, 0d);
    }

    private void detachFromHangingAndFormBlob()
    {
        this.remove();
        world.addEntity(new GooBlob(Registry.GOO_BLOB.get(), world, owner, goo, this.getPositionVec()));
    }

    private void handleDecay()
    {
        if (this.graceTicks > 0) {
            graceTicks--;
            return;
        }
        this.decayTicks++;
        if (this.decayTicks >= DECAY_TICKS_PER_UNIT - 1) {
            this.decayTicks = 0;
            decay();
        } else {
            setShape();
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
        sideWeLiveOn = Direction.byIndex(tag.getInt("side_we_live_on"));
        graceTicks = tag.getInt("grace_ticks");
        decayTicks = tag.getInt("decay_ticks");
        depth = tag.getDouble("depth");
        deserializeShape(tag);
        setSize();
        if (tag.hasUniqueId("owner")) {
            this.owner = world.getPlayerByUuid(tag.getUniqueId("owner"));
        }
        this.isCollidingEntity = tag.getBoolean("LeftOwner");
    }

    private void deserializeShape(CompoundNBT tag)
    {
        this.shape = new Vector3d(
                tag.getDouble("shape_x"),
                tag.getDouble("shape_y"),
                tag.getDouble("shape_z")
        );
        makeBox();
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT tag = super.serializeNBT();
        goo.writeToNBT(tag);
        tag.putInt("side_we_live_on", sideWeLiveOn.getIndex());
        tag.putInt("grace_ticks", graceTicks);
        tag.putInt("decay_ticks", decayTicks);
        tag.putDouble("depth", depth);
        serializeShape(tag);
        if (this.owner != null) { tag.putUniqueId("owner", owner.getUniqueID()); }
        if (this.isCollidingEntity) { tag.putBoolean("isDepartedOwner", true); }
        return tag;
    }

    private void serializeShape(CompoundNBT tag)
    {
        tag.putDouble("shape_x", shape.x);
        tag.putDouble("shape_y", shape.y);
        tag.putDouble("shape_z", shape.z);
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
        int attemptTransfer = fh.fill(goo, FluidAction.SIMULATE);
        if (attemptTransfer >= goo.getAmount()) {
            fh.fill(goo, FluidAction.EXECUTE);
            goo.setAmount(0);
        } else {
            fh.fill(new FluidStack(goo.getFluid(), attemptTransfer), FluidAction.EXECUTE);
            goo.setAmount(goo.getAmount() - attemptTransfer);
        }
    }

    protected void setSize() {
        this.dataManager.set(GOO_SIZE, goo.getAmount());
        this.recalculateSize();
    }

    private boolean isCollidingEntity;

    private boolean checkForEntityCollision() {
        if (!this.isAlive()) {
            return false;
        }
        Collection<Entity> collidedEntities =
                this.world.getEntitiesInAABBexcluding(this,
                        // grow bb
                        this.getBoundingBox().expand(this.getMotion()).grow(1.0D),
                        // filter
                        (eInBB) -> isGooThing(eInBB) || isValidCollisionEntity(eInBB));
        for(Entity e : collidedEntities) {
            if (e instanceof GooSplat) {
                combineSplats((GooSplat)e);
            } else if (e instanceof GooBlob) {
                absorbBlob((GooBlob)e);
            } else {
                // some other entity, do nasty stuff to it. Or good stuff, you know, whatever.
            }
        }

        return false;
    }

    private boolean isValidCollisionEntity(Entity eInBB)
    {
        return !eInBB.isSpectator() && eInBB.canBeCollidedWith();
    }

    private boolean isGooThing(Entity eInBB)
    {
        return eInBB instanceof GooSplat || eInBB instanceof GooBlob;
    }

    private void combineSplats(GooSplat e)
    {
        // side hits different? don't combine them, they're not the same splat.
        if (e.sideWeLiveOn != this.sideWeLiveOn) {
            return;
        }
        // perform a weighted average of locations
        int volumeWeight = goo.getAmount() + e.goo.getAmount();
        double ratio = volumeWeight / (double)goo.getAmount();
        this.goo.setAmount(volumeWeight);
        updateSplatState(false, false, ratio);
        resetGraceTicks();
        e.remove();
    }

    private void absorbBlob(GooBlob e)
    {
        // perform a weighted average of locations
        int volumeWeight = goo.getAmount() + e.goo.getAmount();
        double ratio = volumeWeight / (double)goo.getAmount();
        this.goo.setAmount(volumeWeight);
        updateSplatState(false, false, ratio);
        resetGraceTicks();
        e.remove();
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
