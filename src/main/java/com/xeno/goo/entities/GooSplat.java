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

public class GooSplat extends GooEntity implements IEntityAdditionalSpawnData
{
    private static final int INITIAL_GRACE_TICKS = 10;
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
        updateSplatState();
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
        double cubicArea = goo.getAmount() / LIQUID_CUBIC_RATIO;
        double targetSurfaceArea = cubicArea / depth;
        double sideLength = Math.sqrt(targetSurfaceArea);
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

    private void updateSplatState()
    {
        setDepth(defaultDepth());
        setShape();
        makeBox();
    }

    private double defaultDepth()
    {
        return Math.cbrt(goo.getAmount() / LIQUID_CUBIC_RATIO) * 0.3d;
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
        return this.box.offset(this.getPositionVec());
    }

    public void recalculateSize() {
        double d0 = this.getPosX();
        double d1 = this.getPosY();
        double d2 = this.getPosZ();
        super.recalculateSize();
        this.setPosition(d0, d1, d2);
    }

    @Override
    protected void registerData() {
        super.registerData();
    }

    @Override
    public void tick()
    {
        super.tick();
        handleMaterialCollisionChecks();
        handleGravity();
        handleResolving();
        this.isCollidingEntity = this.checkForEntityCollision();
        doFreeMovement();
    }

    protected void doFreeMovement()
    {
        Vector3d motion = this.getMotion();
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);
        this.setPosition(projection.x, projection.y, projection.z);
    }
    private void handleGravity()
    {
        if (this.depthAxis() == Direction.Axis.Y) {
            return;
        }

        this.setMotion(0d, -0.01d, 0d);
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

    private void handleResolving()
    {
        if (this.graceTicks > 0) {
            graceTicks--;
            return;
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
        return true;
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
            if (e instanceof GooBlob) {
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

    private void absorbBlob(GooBlob e)
    {
        // perform a weighted average of locations
        int volumeWeight = goo.getAmount() + e.goo.getAmount();
        double ratio = volumeWeight / (double)goo.getAmount();
        this.goo.setAmount(volumeWeight);
        updateSplatState();
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
}
