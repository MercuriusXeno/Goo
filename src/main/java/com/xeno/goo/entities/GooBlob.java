package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.interactions.GooInteractions;
import com.xeno.goo.interactions.IPassThroughPredicate;
import com.xeno.goo.interactions.SplatContext;
import com.xeno.goo.items.Basin;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.util.GooTank;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.fluid.Fluids;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

public class GooBlob extends Entity implements IEntityAdditionalSpawnData, IGooContainingEntity
{
    private static final DataParameter<Integer> GOO_AMOUNT = EntityDataManager.createKey(GooBlob.class, DataSerializers.VARINT);
    private static final double GENERAL_FRICTION = 0.98d;
    private static final int QUIVER_TIMER_INITIALIZED_VALUE = 100;
    private static final int QUIVER_TIMER_ONE_CYCLE_DOWN = 75;
    private static final double GOO_GRAVITY = 0.06d;

    private int quiverTimer;

    private GooTank goo = new GooTank(() -> 1000).setFilter(f -> f.getFluid() instanceof GooFluid).setChangeCallback(this::contentsChanged);
    private final LazyOptional<IFluidHandler> lazyHandler = LazyOptional.of(() -> goo);

    private Entity owner;
    private Direction sideWeLiveOn;
    private BlockPos blockAttached = null;
    private GooSplat attachedSplat = null;
    private boolean isAttachedToBlock;
    private BlockState insideBlockState = Blocks.AIR.getDefaultState();
    private int ticksInGround = 0;

    public GooBlob(EntityType<GooBlob> type, World worldIn) {
        super(type, worldIn);
    }

    public GooBlob(EntityType<GooBlob> type, World worldIn, Entity sender, FluidStack stack) {
        super(type, worldIn);
        isAttachedToBlock = false;
        if (!(stack.getFluid() instanceof GooFluid)) {
            this.remove();
        } else {
            Vector3d pos = initialPosition(sender);
            this.setPositionAndRotation(pos.x, pos.y, pos.z, sender.rotationYaw, sender.rotationPitch);
            this.owner = sender;
            goo.fill(stack, FluidAction.EXECUTE);
            this.shoot();
        }
    }

    public static GooBlob createLobbedBlob(GooSplat splat) {
        return new GooBlob(splat, splat.getPositionVec(), GooSplat.getGoo(splat).getFluidInTankInternal(0));
    }

    public static GooBlob createLobbedBlob(SplatContext context, Vector3d dropPosition, FluidStack stackReturned) {
        return new GooBlob(context, dropPosition, stackReturned);
    }

    public static GooBlob createLobbedBlob(World world, FluidStack result, Vector3d spawnPos) {
        return new GooBlob(Registry.GOO_BLOB.get(), world, null, result, spawnPos);
    }

    public static GooBlob createSplattedBlob(PlayerEntity player, GooSplat splat, FluidStack blobStack) {
        GooBlob blob = new GooBlob(Registry.GOO_BLOB.get(), player.world, player, blobStack);
        blob.setPositionAndRotation(splat.getPosX(), splat.getPosY(), splat.getPosZ(), splat.rotationYaw, splat.rotationPitch);
        blob.attachToBlock(splat.blockAttached(), splat.sideWeLiveOn(), splat);
        return blob;
    }

    // constructor for splats that no longer have a block to sit on and convert back to blobs.
    private GooBlob(GooSplat splat, Vector3d dropPosition, FluidStack stackReturned) {
        this(Registry.GOO_BLOB.get(), splat.world, splat.owner(), stackReturned, dropPosition);
    }

    // constructor for blobs that break blocks and return some amount of themselves.
    private GooBlob(SplatContext context, Vector3d dropPosition, FluidStack stackReturned) {
        this(Registry.GOO_BLOB.get(), context.world(), context.splat().owner(), stackReturned, dropPosition);
    }

    // special constructor for goo blobs that don't shoot; this is important.
    private GooBlob(EntityType<GooBlob> type, World worldIn, Entity sender, FluidStack stack, Vector3d pos) {
        super(type, worldIn);
        isAttachedToBlock = false;
        float yaw = 0f;
        float pitch = 0f;
        if (sender != null) {
            yaw = sender.rotationYaw;
            pitch = sender.rotationPitch;
            this.owner = sender;
        }
        this.setPositionAndRotation(pos.x, pos.y, pos.z, yaw, pitch);
        goo.fill(stack, FluidAction.EXECUTE);
    }

    // special constructor for goo blobs created by a drain.
    public GooBlob(EntityType<GooBlob> type, World worldIn, Entity proxySender, FluidStack stack, BlockPos blockPos) {
        super(type, worldIn);
        isAttachedToBlock = false;
        float offset = cubicSize() / 2f;
        // neutral offset "below" the drain
        Vector3d pos = Vector3d.copy(blockPos)
                .add(0.5d, 0.74d - offset, 0.5d);
        this.setPositionAndRotation(pos.x, pos.y, pos.z, 0f, 0f);
        this.owner = proxySender;
        goo.fill(stack, FluidAction.EXECUTE);
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
        realignBoundingBox(this.getPositionVec());
    }

    // 32 blocks.
    private static final double A_REASONABLE_RENDER_DISTANCE_SQUARED = 1024;
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
            int dataManagerGooSize = this.dataManager.get(GOO_AMOUNT);
            if (!goo.isEmpty() && dataManagerGooSize != goo.getTotalContents()) {
                goo.getFluidInTankInternal(0).setAmount(this.dataManager.get(GOO_AMOUNT));
            }
        }

        if (this.isOnGround()) {
            this.ticksInGround++;
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
            if (!world.isRemote()) {
                int amountToDrain = (int) Math.ceil(Math.sqrt(goo.getTotalContents()) / 2d);
                GooSplat.getGoo(attachedSplat).fill(goo.drain(amountToDrain, FluidAction.EXECUTE), FluidAction.EXECUTE);
            }
        }
    }

    private void approachSplatOffset() {
        Vector3d splatPosition = attachedSplat.getPositionVec();
        Vector3d offsetPosition = Vector3d.copy(attachedSplat.sideWeLiveOn().getDirectionVec()).scale(cubicSize() / 2f);
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

        BlockRayTraceResult blockResult = this.world.rayTraceBlocks(new RayTraceContext(position, projection, RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.ANY, this));
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
            if (isValidForSplat(blockResult) && goo.getTotalContents() > 0) {
                splat(blockResult.getPos(), blockResult.getFace(), blockResult.getHitVec());
                return true;
            } else {
                stickBlob(blockResult.getHitVec());
            }
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

    @Override
    protected void onInsideBlock(BlockState state) {
        super.onInsideBlock(state);
        boolean wasOnGround = this.isOnGround();
        boolean isAir = state.getBlock().isAir(state, world, this.getPosition());
        this.setOnGround(!isAir);
        if (!wasOnGround) {
            this.ticksInGround = 0;
        }
    }

    private void stickBlob(Vector3d hitVec) {

        this.setPositionAndRotation(hitVec.x, hitVec.y, hitVec.z, this.rotationYaw, this.rotationPitch);
        this.setMotion(this.getMotion().scale(0.02d));
        if (this.ticksInGround > 0) {
             return;
        }
        GooInteractions.spawnParticles(this);
    }

    @Override
    public ActionResultType applyPlayerInteraction(PlayerEntity player, Vector3d vec, Hand hand) {
        if (player.getHeldItem(hand).getItem() instanceof Basin ||
                player.getHeldItem(hand).getItem() instanceof Gauntlet) {
            if (isOnGround()) {
                tryPlayerInteraction(player, hand);
                return ActionResultType.CONSUME;
            }
        }
        return super.applyPlayerInteraction(player, vec, hand);
    }

    private boolean tryPlayerInteraction(PlayerEntity player, Hand hand)
    {
        boolean[] didStuff = {false};
        LazyOptional<IFluidHandlerItem> cap = player.getHeldItem(hand).getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
        cap.ifPresent((c) -> didStuff[0] = tryExtractingGooFromEntity(c));
        if (didStuff[0]) {
            AudioHelper.playerAudioEvent(player, Registry.GOO_WITHDRAW_SOUND.get(), 1.0f);
        }
        return didStuff[0];
    }

    private boolean tryExtractingGooFromEntity(IFluidHandlerItem item)
    {
        FluidStack heldGoo = item.getFluidInTank(0);
        if (!item.getFluidInTank(0).isEmpty()) {
            if (!heldGoo.isFluidEqual(goo.getFluidInTankInternal(0)) || goo.isEmpty()) {
                return false;
            }
        }

        int spaceRemaining = item.getTankCapacity(0) - item.getFluidInTank(0).getAmount();
        FluidStack tryDrain = goo.drain(spaceRemaining, IFluidHandler.FluidAction.SIMULATE);
        if (tryDrain.isEmpty()) {
            return false;
        }

        item.fill(goo.drain(tryDrain, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    private boolean isValidForPassThrough(BlockRayTraceResult blockResult) {
        BlockState state = world.getBlockState(blockResult.getPos());
        if (!GooInteractions.materialPassThroughPredicateRegistry.containsKey(goo.getFluidInTankInternal(0).getFluid())) {
            return isPassableMaterial(state);
        }
        IPassThroughPredicate funk = GooInteractions.materialPassThroughPredicateRegistry.get(goo.getFluidInTankInternal(0).getFluid());
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

    private void handleFriction()
    {
        this.setMotion(this.getMotion().scale(GENERAL_FRICTION));
    }

    private void handleGravity()
    {
        if (this.isOnGround()) {
            return;
        }
        this.setMotion(this.getMotion().add(0d, -GOO_GRAVITY, 0d));
    }

    protected void splat(BlockPos pos, Direction face, Vector3d hitVec) {
        // don't spawn particles unless we're moving kinda fast. blobs just kinda sit there
        // if they don't have a surface to splat on.
        if (this.getMotion().lengthSquared() >= 0.2d) {
            // the state we're interested in observing is the state of the hit block, not the offset.
            GooInteractions.spawnParticles(this);
        }
        if (world.isRemote()) {
            return;
        }
        // nerf motion on impact.
        this.setMotion(this.getMotion().scale(0.02d));

        // check if there isn't already a splat we can stick onto, if there is, attach to it instead of a new one

        List<GooSplat> splats = world.getEntitiesWithinAABB(Registry.GOO_SPLAT.get(), this.getBoundingBox(),
                (s) -> GooSplat.getGoo(s).getFluidInTankInternal(0).getFluid().equals(goo.getFluidInTankInternal(0).getFluid()));
        if (splats.size() > 0) {
            attachToBlock(pos, face, splats.get(0));
        } else {
            // create a goo splat
            FluidStack traceGoo = goo.drain(1, FluidAction.EXECUTE);
            GooSplat splatToAdd = new GooSplat(Registry.GOO_SPLAT.get(), this.owner, world, traceGoo, hitVec, pos, face, true, 0f, false);
            attachToBlock(pos, face, splatToAdd);
            world.addEntity(splatToAdd);
        }
    }

    private void attachToBlock(BlockPos pos, Direction face, GooSplat splat)
    {
        this.blockAttached = pos;
        this.sideWeLiveOn = face;
        this.isAttachedToBlock = true;
        this.attachedSplat = splat;
        AudioHelper.entityAudioEvent(this, Registry.GOO_SPLAT_SOUND.get(), SoundCategory.AMBIENT,
                1.0f, AudioHelper.PitchFormulas.HalfToOne);
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
        Vector3d halfSize = new Vector3d(cubicSize() / 2d,
                cubicSize() / 2d, cubicSize() / 2d);
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
        deserializeAttachment(tag);
        goo.readFromNBT(tag.getCompound("goo"));
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
    public CompoundNBT writeWithoutTypeId(CompoundNBT compound) {
        CompoundNBT tag = super.writeWithoutTypeId(compound);
        tag.put("goo", goo.writeToNBT(new CompoundNBT()));
        serializeAttachment(tag);
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
        CompoundNBT tag = writeWithoutTypeId(new CompoundNBT());
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
        int attemptTransfer = fh.fill(goo.getFluidInTank(0), IFluidHandler.FluidAction.SIMULATE);
        if (attemptTransfer == 0) {
            return false;
        }
        if (attemptTransfer > 0) {
            GooInteractions.spawnParticles(this);
            AudioHelper.entityAudioEvent(this, Registry.GOO_DEPOSIT_SOUND.get(), SoundCategory.PLAYERS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
        }
        fh.fill(goo.drain(attemptTransfer, FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        return goo.isEmpty();
    }


    protected void setSize() {
        this.dataManager.set(GOO_AMOUNT, goo.getTotalContents());
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
            Collection<Entity> collidedEntities =
                    this.world.getEntitiesInAABBexcluding(this,
                            // grow bb
                            this.getBoundingBox().expand(this.getMotion()),
                            // filter
                            (eInBB) -> isBlobToMergeWith(eInBB) || !eInBB.isSpectator() && eInBB.canBeCollidedWith());
            for(Entity e : collidedEntities) {
                if (isBlobToMergeWith(e)) {
                    GooBlob target = (GooBlob)e;
                    double mergeSigma = (double) goo.getTotalContents() + target.goo.getTotalContents();
                    double sourceDominance = (double) goo.getTotalContents() / mergeSigma;
                    Vector3d positionDelta = target.getPositionVec().subtract(this.getPositionVec()).scale(1d - sourceDominance);
                    Vector3d motionDelta = target.getMotion().subtract(this.getMotion()).scale(1d - sourceDominance);
                    Vector3d newPos = this.getPositionVec().add(positionDelta);
                    Vector3d newMotion = this.getMotion().add(motionDelta);
                    this.setPositionAndRotation(newPos.x, newPos.y, newPos.z, rotationYaw, rotationPitch);
                    this.setMotion(newMotion.x, newMotion.y, newMotion.z);

                    goo.fill(((GooBlob)e).goo.drain(goo.getRemainingCapacity(), FluidAction.EXECUTE), FluidAction.EXECUTE);
                }
                // skip riders unless we're hitting the lowest
                if (owner != null) {
                    if (e.getLowestRidingEntity() == owner.getLowestRidingEntity()) {
                        return false;
                    }
                }
            }

        return true;
    }

    private boolean isBlobToMergeWith(Entity eInBB) {
        return eInBB instanceof GooBlob && eInBB.isAlive()
                && ((GooBlob)eInBB).goo.getFluidInTankInternal(0).isFluidEqual(goo.getFluidInTankInternal(0))
                && ((GooBlob)eInBB).goo.getTotalContents() <= goo.getTotalContents();
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
        // collisions with entities cause a dead drop but nothing else
        if (entityHit instanceof LivingEntity && this.owner instanceof LivingEntity) {
            this.setMotion(this.getMotion().mul(0d, -GOO_GRAVITY, 0d));
        }
    }

    public float cubicSize()
    {
        return cubicSize(goo.getTotalContents());
    }

    public static float cubicSize(int amount) {
        return  (float)Math.cbrt(amount) / 10f;
    }

    public int quiverTimer()
    {
        return quiverTimer;
    }
    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) {

        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return lazyHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    protected void invalidateCaps() {

        super.invalidateCaps();
        lazyHandler.invalidate();
    }

    private void contentsChanged() {

        if (goo.isEmpty()) remove(true); // do not kill caps here, World will do this for us when it removes us at the end of the tick
        else setSize();
    }

    public Entity owner()
    {
        return this.owner;
    }

    public Direction sideWeLiveOn()
    {
        return sideWeLiveOn;
    }

    public BlockPos blockAttached()
    {
        return this.blockAttached;
    }

    @Override
    public FluidStack goo() {

        return goo.getFluidInTank(0);
    }
}
