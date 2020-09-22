package com.xeno.goo.entities;

import com.xeno.goo.GooMod;
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
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
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

import javax.annotation.Nullable;
import java.util.Collection;

public class GooSplat extends Entity implements IEntityAdditionalSpawnData, IFluidHandler
{
    private static final DataParameter<Integer> GOO_AMOUNT = EntityDataManager.createKey(GooSplat.class, DataSerializers.VARINT);
    private static final double PUDDLE_DEPTH = 0.1d;
    private static final double SINGLE_TILE_LIQUID_COVERING_RATIO = 16d; // 16 units is expected to cover 1x1
    private static final double LIQUID_CUBIC_RATIO = 1000d;
    private static final double LIQUID_CUBIC_TILE_COVERAGE_VOLUME = SINGLE_TILE_LIQUID_COVERING_RATIO / LIQUID_CUBIC_RATIO;
    private static final double LIQUID_CUBIC_SIDE_LENGTH_DERIVED = Math.sqrt(LIQUID_CUBIC_TILE_COVERAGE_VOLUME / PUDDLE_DEPTH);
    private static final double PUDDLE_EXPANSION_RATIO = 1d / LIQUID_CUBIC_SIDE_LENGTH_DERIVED;
    private FluidStack goo;
    private Entity owner;
    private Vector3d shape;
    private AxisAlignedBB box;
    private float cubicSize;
    private EntitySize size;
    private Direction sideWeLiveOn;
    private BlockPos blockAttached = null;

    public Vector3d shape()
    {
        return this.shape;
    }

    public Direction.Axis depthAxis() {
        return sideWeLiveOn.getAxis();
    }

    public GooSplat(EntityType<GooSplat> type, World world) {
        super(type, world);
    }

    public GooSplat(EntityType<GooSplat> type, Entity sender, World world, FluidStack traceGoo, Vector3d hitVec, BlockPos pos, Direction face) {
        super(type, world);
        this.goo = traceGoo;
        this.owner = sender;
        this.sideWeLiveOn = face;
        this.blockAttached = pos;
        updateSplatState();
        Vector3d findCenter = findCenter(face, pos, hitVec);
        this.setPosition(findCenter.x, findCenter.y, findCenter.z);
        this.setSize();
        world.playSound(hitVec.x, hitVec.y, hitVec.z, Registry.GOO_SPLAT_SOUND.get(), SoundCategory.AMBIENT,
                1.0f, world.rand.nextFloat() * 0.5f + 0.5f, false);
    }

    private void updateSplatState()
    {
        double cubicArea = goo.getAmount() / LIQUID_CUBIC_RATIO;
        double targetSurfaceArea = cubicArea / PUDDLE_DEPTH;
        double sideLength = Math.sqrt(targetSurfaceArea) * PUDDLE_EXPANSION_RATIO;
        switch (depthAxis()) {
            case X:
                shape =  new Vector3d(PUDDLE_DEPTH, sideLength, sideLength);
                break;
            case Y:
                shape =  new Vector3d(sideLength, PUDDLE_DEPTH, sideLength);
                break;
            case Z:
                shape =  new Vector3d(sideLength, sideLength, PUDDLE_DEPTH);
                break;
            default:
                shape = Vector3d.ZERO;
                break;
        }

        makeBox();
    }

    private void makeBox()
    {
        this.box = new AxisAlignedBB(shape.scale(-0.5d), shape.scale(0.5d));
        this.setBoundingBox(box.offset(this.getPositionVec()));
    }

    /**
     * Sets the x,y,z of the entity from the given parameters. Also seems to set up a bounding box.
     */
    public void setPosition(double x, double y, double z) {
        this.setRawPosition(x, y, z);
        if (this.isAddedToWorld() && !this.world.isRemote && world instanceof ServerWorld)
            ((ServerWorld)this.world).chunkCheck(this); // Forge - Process chunk registration after moving.
    }

    private Vector3d findCenter(Direction face, BlockPos pos, Vector3d hitVec)
    {
        switch(sideWeLiveOn) {
            case NORTH:
                return new Vector3d(hitVec.x - (0.01d + PUDDLE_DEPTH / 2d), hitVec.y, hitVec.z);
            case SOUTH:
                return new Vector3d(hitVec.x + (0.01d + PUDDLE_DEPTH / 2d), hitVec.y, hitVec.z);
            case UP:
                return new Vector3d(hitVec.x, hitVec.y + (0.01d + PUDDLE_DEPTH / 2d), hitVec.z);
            case DOWN:
                return new Vector3d(hitVec.x, hitVec.y - (0.01d + PUDDLE_DEPTH / 2d), hitVec.z);
            case EAST:
                return new Vector3d(hitVec.x, hitVec.y, hitVec.z + (0.01d + PUDDLE_DEPTH / 2d));
            case WEST:
                return new Vector3d(hitVec.x, hitVec.y, hitVec.z - (0.01d + PUDDLE_DEPTH / 2d));
        }
        // something weird happened that wasn't supposed to.
        return hitVec;
    }

    public AxisAlignedBB getCollisionBoundingBox() {
        return null;
    }

    public void recalculateSize() {
        this.cubicSize = (float)Math.cbrt(goo.getAmount() / 1000f);
        this.size = new EntitySize(cubicSize, cubicSize, false);
        this.updateSplatState();
    }

    @Override
    protected void registerData() {
        this.dataManager.register(GOO_AMOUNT, 1);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.goo.isEmpty()) {
            this.remove();
            return;
        }

        // let the server handle motion and updates
        // also don't tell the server what the goo amount is, it knows.
        if (world.isRemote()) {
            goo.setAmount(this.dataManager.get(GOO_AMOUNT));
            return;
        }
        handleMaterialCollisionChecks();
        this.isCollidingEntity = this.checkForEntityCollision();
        approachAttachmentPoint();
        doFreeMovement();
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
                .add(0.51d * sideWeLiveOn.getXOffset(),
                        0.51d * sideWeLiveOn.getYOffset(),
                        0.51d * sideWeLiveOn.getZOffset())
                .add((PUDDLE_DEPTH / 2d) * sideWeLiveOn.getXOffset(),
                        (PUDDLE_DEPTH / 2d) * sideWeLiveOn.getYOffset(),
                        (PUDDLE_DEPTH / 2d) * sideWeLiveOn.getZOffset());
        return attachmentPoint;
    }

    protected void doFreeMovement()
    {
        Vector3d motion = this.getMotion();
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);
        this.setPosition(projection.x, projection.y, projection.z);
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

    /**
     * Gets the EntityRayTraceResult representing the entity hit
     */
    protected EntityRayTraceResult rayTraceEntities(Vector3d startVec, Vector3d endVec) {
        return ProjectileHelper.rayTraceEntities(this.world, this, startVec, endVec,
                this.getBoundingBox().expand(this.getMotion()).grow(1.0D), this::canHitEntityAndNotAlready);
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
        deserializeShape(tag);
        if (tag.hasUniqueId("owner")) {
            this.owner = world.getPlayerByUuid(tag.getUniqueId("owner"));
        }
        this.isCollidingEntity = tag.getBoolean("LeftOwner");
    }

    private void deserializeAttachment(CompoundNBT tag)
    {
        CompoundNBT at = tag.getCompound("attachment");
        this.blockAttached =
                new BlockPos(at.getInt("x"), at.getInt("y"), at.getInt("z"));
        this.sideWeLiveOn = Direction.byIndex(at.getInt("side"));
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
        serializeAttachment(tag);
        tag.putFloat("cubicSize", cubicSize);
        serializeShape(tag);
        if (this.owner != null) { tag.putUniqueId("owner", owner.getUniqueID()); }
        if (this.isCollidingEntity) { tag.putBoolean("isDepartedOwner", true); }
        return tag;
    }

    private void serializeAttachment(CompoundNBT tag)
    {
        CompoundNBT at = new CompoundNBT();
        at.putInt("x", blockAttached.getX());
        at.putInt("y", blockAttached.getY());
        at.putInt("z", blockAttached.getZ());
        at.putInt("side", sideWeLiveOn.getIndex());
        tag.put("attachment", at);
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

    protected void setSize() {
        this.dataManager.set(GOO_AMOUNT, goo.getAmount());
        this.recalculateSize();
    }

    @Override
    public ActionResultType processInitialInteract(PlayerEntity player, Hand hand)
    {
        return super.processInitialInteract(player, hand);
    }

    @Override
    public ActionResultType applyPlayerInteraction(PlayerEntity player, Vector3d vec, Hand hand)
    {
        return super.applyPlayerInteraction(player, vec, hand);
    }

    public void notifyDataManagerChange(DataParameter<?> key) {
        if (GOO_AMOUNT.equals(key)) {
            this.recalculateSize();
        }

        super.notifyDataManagerChange(key);
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
        return true;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBox(Entity entityIn)
    {
        return null;
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
        FluidStack attemptTransfer = this.drain(1, FluidAction.SIMULATE);
        int allowed = fh.fill(attemptTransfer, FluidAction.SIMULATE);
        if (allowed == 0) {
            return;
        }
        fh.fill(this.drain(1, FluidAction.EXECUTE), FluidAction.EXECUTE);
    }

    private boolean isCollidingEntity;

    private boolean checkForEntityCollision() {
        if (!this.isAlive()) {
            return false;
        }
        Collection<Entity> collidedEntities =
                this.world.getEntitiesInAABBexcluding(this,
                        // grow bb
                        this.getBoundingBox().expand(this.getMotion()).grow(0.25d, 0.25d, 0.25d),
                        // filter
                        (eInBB) -> isGooThing(eInBB) || isValidCollisionEntity(eInBB));
        for(Entity e : collidedEntities) {
            if (e instanceof GooBlob) {
                if (isSameAttachment((GooBlob)e)) {
                    absorbBlob((GooBlob) e);
                }
            } else {
                // some other entity, do nasty stuff to it. Or good stuff, you know, whatever.
            }
        }

        return false;
    }

    private boolean isSameAttachment(GooBlob e)
    {
        if (e.blockAttached() == null || e.sideWeLiveOn() == null) {
            return false;
        }
        return e.blockAttached().equals(this.blockAttached) && e.sideWeLiveOn().equals(this.sideWeLiveOn);
    }

    private boolean isValidCollisionEntity(Entity eInBB)
    {
        return !eInBB.isSpectator() && eInBB.canBeCollidedWith();
    }

    private boolean isGooThing(Entity eInBB)
    {
        return eInBB instanceof GooSplat || eInBB instanceof GooBlob;
    }

    private void absorbBlob(GooBlob e)
    {
        if (this.world.isRemote()) {
            return;
        }
        FluidStack drained = e.drain(1, FluidAction.EXECUTE);
        fill(drained, FluidAction.EXECUTE);
    }

    protected void onImpact(RayTraceResult rayTraceResult) {
        RayTraceResult.Type resultType = rayTraceResult.getType();
        if (resultType == RayTraceResult.Type.ENTITY) {
            collideWithEntity(((EntityRayTraceResult)rayTraceResult).getEntity());
        }
    }

    protected void collideWithEntity(Entity entityHit)
    {
        if (this.world.isRemote()) {
            return;
        }
        if (entityHit == owner) {
            return;
        }
        if (entityHit instanceof LivingEntity && this.owner instanceof LivingEntity) {
            int intensity = Math.max(1, (int)Math.ceil(Math.sqrt(this.goo.getAmount()) - 1));
            GooChopEffects.doChopEffect(this.goo, intensity, (LivingEntity)this.owner, (LivingEntity)entityHit);
            this.drain(1, FluidAction.EXECUTE);
        } else if (entityHit instanceof GooBlob && ((GooBlob) entityHit).goo().isFluidEqual(this.goo)) {
            this.fill(((GooBlob) entityHit).drain(1, FluidAction.EXECUTE), FluidAction.EXECUTE);
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
            setSize();
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
        if (this.world.isRemote()) {
            return FluidStack.EMPTY;
        }
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

    public BlockPos blockAttached()
    {
        return this.blockAttached;
    }

    public Direction sideWeLiveOn() {
        return this.sideWeLiveOn;
    }

    public FluidStack goo()
    {
        return this.goo;
    }
}