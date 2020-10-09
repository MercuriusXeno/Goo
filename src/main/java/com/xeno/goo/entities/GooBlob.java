package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.interactions.GooInteractions;
import com.xeno.goo.interactions.IPassThroughPredicate;
import com.xeno.goo.items.BasinAbstraction;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.items.GauntletAbstraction;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.item.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
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
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

import java.util.Collection;

public class GooBlob extends Entity implements IEntityAdditionalSpawnData, IFluidHandler
{
    private static final DataParameter<Integer> GOO_AMOUNT = EntityDataManager.createKey(GooBlob.class, DataSerializers.VARINT);
    private static final double GENERAL_FRICTION = 0.98d;
    private static final int QUIVER_TIMER_INITIALIZED_VALUE = 100;
    private static final int QUIVER_TIMER_ONE_CYCLE_DOWN = 75;
    private static final double GOO_GRAVITY = 0.06d;
    private int quiverTimer;
    private FluidStack goo;
    private Entity owner;
    private float cubicSize;
    private EntitySize size;
    private Direction sideWeLiveOn;
    private BlockPos blockAttached = null;
    private GooSplat attachedSplat = null;
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

    // special constructor for goo blobs that doesn't shoot; this is important.
    public GooBlob(EntityType<GooBlob> type, World worldIn, Entity sender, FluidStack stack, Vector3d pos) {
        super(type, worldIn);
        goo = stack;
        this.setPositionAndRotation(pos.x, pos.y, pos.z, sender.rotationYaw, sender.rotationPitch);
        this.owner = sender;
        this.setSize();
    }

    // special constructor for goo blobs created by a drain.
    public GooBlob(EntityType<GooBlob> type, World worldIn, Entity proxySender, FluidStack stack, BlockPos blockPos) {
        super(type, worldIn);
        goo = stack;
        float offset = cubicSize() / 2f;
        // neutral offset "below" the drain
        Vector3d pos = Vector3d.copy(blockPos)
                .add(0.5d, 0.74d - offset, 0.5d);
        this.setPositionAndRotation(pos.x, pos.y, pos.z, 0f, 0f);
        this.owner = proxySender;
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
        AudioHelper.entityAudioEvent(this, Registry.GOO_LOB_SOUND.get(), SoundCategory.AMBIENT,
                1.0f, AudioHelper.PitchFormulas.HalfToOne);
        startQuivering();
        Vector3d velVec = owner.getLookVec();
        velVec = (velVec).normalize().scale(1.0f);
        this.setMotion(velVec);
    }

    public AxisAlignedBB getCollisionBoundingBox() {
        return null;
    }

    @Override
    public void recalculateSize() {
        this.cubicSize = (float)Math.cbrt(goo.getAmount() / 1000f);
        this.size = new EntitySize(cubicSize, cubicSize, false);
        realignBoundingBox(this.getPositionVec());
    }

    // 32 blocks.
    private double A_REASONABLE_RENDER_DISTANCE_SQUARED = 1024;
    @Override
    public boolean isInRangeToRenderDist(double distance)
    {
        return distance < A_REASONABLE_RENDER_DISTANCE_SQUARED;
    }

    @Override
    public void removeTrackingPlayer(ServerPlayerEntity player)
    {
        super.removeTrackingPlayer(player);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (goo.isEmpty()) {
            this.remove();
            return;
        }

        // quiver timer is really just a client side thing
        if (this.quiverTimer > 0) {
            quiverTimer--;
        }

        // let the server handle motion and updates
        if (world.isRemote()) {
            goo.setAmount(this.dataManager.get(GOO_AMOUNT));
            return;
        }

        if (!isAttachedToBlock) {
            if (handleMovement()) {
                return;
            }
            doFreeMovement();
            this.isCollidingEntity = this.checkForEntityCollision();
        }

        // feed the splat we belong to
        if (attachedSplat != null) {
            approachSplatOffset();
            attachedSplat.fill(this.drain(1, FluidAction.EXECUTE), FluidAction.EXECUTE);
        }
    }

    private void approachSplatOffset() {
        Vector3d splatPosition = attachedSplat.getPositionVec();
        Vector3f offsetPosition = attachedSplat.sideWeLiveOn().toVector3f();
        offsetPosition.mul(cubicSize() / 2f);
        Vector3d trueOffset = splatPosition.add(offsetPosition.getX(), offsetPosition.getY(), offsetPosition.getZ());
        setPositionAndRotation(trueOffset.x, trueOffset.y, trueOffset.z, this.rotationYaw, this.rotationPitch);
    }

    private boolean handleMovement() {
        handleGravity();
        handleFriction();
        return handleMaterialCollisionChecks();
    }

    // return true to halt movement processing for whatever reason
    private boolean handleMaterialCollisionChecks()
    {
        Vector3d motion = this.getMotion();
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);

        BlockRayTraceResult blockResult = this.world.rayTraceBlocks(new RayTraceContext(position, projection, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.ANY, this));
        if (blockResult.getType() != RayTraceResult.Type.MISS) {
            // try colliding with the block for some tank interaction, this entity
            // wants to enter receptacles first if it can, but splat otherwise
            if (tryFluidHandlerInteraction(blockResult.getPos(), blockResult.getFace())) {
                return true;
            }

            if (isValidForPassThrough(blockResult)) {
                GooInteractions.tryResolving(blockResult, this);
                return false;
            }

            // check to see if the block is a solid cube.
            // goo bounces off of anything that isn't.
            if (!isValidForSplat(blockResult)) {
                bounceBlob(blockResult.getFace());
                return false;
            }

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

    private boolean isValidForPassThrough(BlockRayTraceResult blockResult) {
        BlockState state = world.getBlockState(blockResult.getPos());
        if (!GooInteractions.materialPassThroughPredicateRegistry.containsKey(goo.getFluid())) {
            return isPassableMaterial(state);
        }
        IPassThroughPredicate funk = GooInteractions.materialPassThroughPredicateRegistry.get(goo.getFluid());
        return funk.blobPassThroughPredicate(blockResult, this);
    }

    private boolean isPassableMaterial(BlockState state) {
        return (!state.getMaterial().blocksMovement() || state.getBlock() instanceof LeavesBlock) && !isFullFluidBlock(state);
    }

    private boolean isValidForSplat(BlockRayTraceResult result)
    {
        BlockState state = world.getBlockState(result.getPos());
        return state.isSolidSide(world, result.getPos(), result.getFace()) || isFullFluidBlock(state);
    }

    private boolean isFullFluidBlock(BlockState state) {
        return !state.getFluidState().getFluid().equals(Fluids.EMPTY) && state.getFluidState().isSource();
    }

    private boolean isGlass(BlockState state)
    {
        return state.getBlock() instanceof StainedGlassBlock || state.getBlock() instanceof GlassBlock;
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
        // don't spawn particles unless we're moving kinda fast. blobs just kinda sit there
        // if they don't have a surface to splat on.
        if (this.getMotion().lengthSquared() >= 1d) {
            // the state we're interested in observing is the state of the hit block, not the offset.
            GooInteractions.spawnParticles(this);
        }
        if (world.isRemote()) {
            return;
        }
        // nerf motion on impact.
        this.setMotion(this.getMotion().scale(0.02d));
        // create a goo splat
        FluidStack traceGoo = drain(1, FluidAction.EXECUTE);
        GooSplat splatToAdd = new GooSplat(Registry.GOO_SPLAT.get(), this.owner, world, traceGoo, hitVec, pos, face);
        world.addEntity(splatToAdd);
        attachToBlock(pos, face, splatToAdd);
    }

    private static final double BOUNCE_DECAY = 0.65d;

    public void bounceBlob(Direction face)
    {
        switch(face.getAxis()) {
            case Y:
                setMotion(getMotion().mul(BOUNCE_DECAY, -BOUNCE_DECAY, BOUNCE_DECAY));
                return;
            case X:
                setMotion(getMotion().mul(-BOUNCE_DECAY, BOUNCE_DECAY, BOUNCE_DECAY));
                return;
            case Z:
                setMotion(getMotion().mul(BOUNCE_DECAY, BOUNCE_DECAY, -BOUNCE_DECAY));
        }
    }

    private void attachToBlock(BlockPos pos, Direction face, GooSplat splat)
    {
        this.blockAttached = pos;
        this.sideWeLiveOn = face;
        this.isAttachedToBlock = true;
        this.attachedSplat = splat;
    }

    protected void doFreeMovement() {
        Vector3d motion = this.getMotion();
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);

        this.setPositionAndRotation(projection.x, projection.y, projection.z, this.rotationYaw, this.rotationPitch);
        this.realignBoundingBox(projection);
    }

    private void realignBoundingBox(Vector3d projection)
    {
        Vector3d halfSize = new Vector3d(cubicSize / 2d, cubicSize / 2d, cubicSize / 2d);
        this.setBoundingBox(new AxisAlignedBB(projection.subtract(halfSize), projection.add(halfSize)));
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
        return ProjectileHelper.rayTraceEntities(this.world, this, startVec, endVec, this.getBoundingBox().expand(this.getMotion()), this::canHitEntityAndNotAlready);
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

    public void startQuivering()
    {
        if (this.quiverTimer < QUIVER_TIMER_ONE_CYCLE_DOWN) {
            this.quiverTimer = QUIVER_TIMER_INITIALIZED_VALUE;
        }
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
    public ActionResultType applyPlayerInteraction(PlayerEntity player, Vector3d vec, Hand hand)
    {
        ItemStack stack = player.getHeldItem(hand);
        if (!isValidInteractionStack(stack)) {
            return ActionResultType.PASS;
        }
        boolean[] didStuff = {false};

        LazyOptional<IFluidHandlerItem> cap = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
        cap.ifPresent((c) -> didStuff[0] = tryExtractingGooFromEntity(c, this));
        if (didStuff[0]) {
            AudioHelper.playerAudioEvent(player, Registry.GOO_WITHDRAW_SOUND.get(), 1.0f);
            return ActionResultType.SUCCESS;
        }
        return ActionResultType.CONSUME;
    }

    private boolean isValidInteractionStack(ItemStack stack)
    {
        return stack.getItem() instanceof GauntletAbstraction || stack.getItem() instanceof BasinAbstraction;
    }

    private static boolean tryExtractingGooFromEntity(IFluidHandlerItem item, GooBlob entity)
    {
        FluidStack heldGoo = item.getFluidInTank(0);
        if (!item.getFluidInTank(0).isEmpty()) {
            if (!heldGoo.isFluidEqual(entity.getFluidInTank(0)) || entity.getFluidInTank(0).isEmpty()) {
                return false;
            }
        }

        int spaceRemaining = item.getTankCapacity(0) - item.getFluidInTank(0).getAmount();
        FluidStack tryDrain = entity.drain(spaceRemaining, IFluidHandler.FluidAction.SIMULATE);
        if (tryDrain.isEmpty()) {
            return false;
        }
        item.fill(entity.drain(tryDrain, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);

        return true;
    }

    public boolean tryFluidHandlerInteraction(BlockPos blockPos, Direction sideHit)
    {
        if (world.isRemote()) {
            return false;
        }

        boolean[] result = {false};
        LazyOptional<IFluidHandler> fh = FluidHandlerHelper.capability(this.world, blockPos, sideHit);
        fh.ifPresent((c) -> result[0] = enterTank(c));
        return result[0];
    }

    // return true if 100% of the goo is drained, which chains up as "stop processing movement"
    // return false if we need to keep processing movement because some or all of the goo is still there
    private boolean enterTank(IFluidHandler fh)
    {
        int attemptTransfer = fh.fill(goo, IFluidHandler.FluidAction.SIMULATE);
        if (attemptTransfer == 0) {
            return false;
        }
        if (attemptTransfer > 0) {
            GooInteractions.spawnParticles(this);
            AudioHelper.entityAudioEvent(this, Registry.GOO_DEPOSIT_SOUND.get(), SoundCategory.PLAYERS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
        }
        if (attemptTransfer >= goo.getAmount()) {
            fh.fill(goo, IFluidHandler.FluidAction.EXECUTE);
            goo.setAmount(0);
            return true;
        } else {
            fh.fill(new FluidStack(goo.getFluid(), attemptTransfer), IFluidHandler.FluidAction.EXECUTE);
            goo.setAmount(goo.getAmount() - attemptTransfer);
        }

        if (goo.getAmount() > 0) {
            return false;
        }

        return true;
    }


    protected void setSize() {
        this.dataManager.set(GOO_AMOUNT, goo.getAmount());
        this.recalculateSize();
    }

    @Override
    protected void registerData() {
        this.dataManager.register(GOO_AMOUNT, 1);
    }

    public void notifyDataManagerChange(DataParameter<?> key) {
        if (GOO_AMOUNT.equals(key)) {
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
                            this.getBoundingBox().expand(this.getMotion()),
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
            // only try catching the goos flagged to bounce/return goo
            // at the time of writing, hard coded.
            if (!isAutoGrabbedGoo()) {
                return;
            }
            // try catching  it!
            if (owner instanceof PlayerEntity) {
                // check if the player has a gauntlet either empty or with the same goo as me
                ItemStack heldItem = ((PlayerEntity) owner).getHeldItem(Hand.MAIN_HAND);
                if (heldItem.getItem() instanceof Gauntlet) {
                    LazyOptional<IFluidHandlerItem> lazyCap = heldItem.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
                    lazyCap.ifPresent((c) -> {
                        int drain = c.fill(this.goo(), FluidAction.SIMULATE);
                        if (drain > 0) {
                            c.fill(this.drain(drain, FluidAction.EXECUTE), FluidAction.EXECUTE);
                        }
                        if (this.goo.isEmpty()) {
                            this.remove();
                        }
                    });
                }
            }
            return;
        }
        // collisions with entities cause a dead drop but nothing else
        if (entityHit instanceof LivingEntity && this.owner instanceof LivingEntity) {
            this.setMotion(this.getMotion().mul(0d, -GOO_GRAVITY, 0d));
        }
    }

    private boolean isAutoGrabbedGoo() {
        return goo.getFluid().equals(Registry.CRYSTAL_GOO.get())
                || goo.getFluid().equals(Registry.METAL_GOO.get())
                || goo.getFluid().equals(Registry.REGAL_GOO.get());
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
