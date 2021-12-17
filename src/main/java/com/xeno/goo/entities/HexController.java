package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.interactions.GooInteractions;
import com.xeno.goo.interactions.IPassThroughPredicate;
import com.xeno.goo.items.Vessel;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.util.GooTank;
import com.xeno.goo.util.IGooTank;
import net.minecraft.block.BlockState;
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

public class HexController extends Entity implements IEntityAdditionalSpawnData, IGooContainingEntity
{
    private static final DataParameter<Integer> GOO_AMOUNT = EntityDataManager.createKey(HexController.class, DataSerializers.VARINT);
    private static final double airFriction = 0.98d;
    private static final double generalFriction = 0.98d;
    private static final int quiverTimerInitializedValue = 60;
    private static final int quiverTimerCycle = 15;
    private static final double gooGravity = 0.06d;
    private static int mergePulseTimerMax = 5;
    private static float MERGE_PULSE_FACTOR_MAX = 1.1f;
    private int pulseTimer = 0;
    private int squishTimer = 0;

    // no not the chopping kind. Each axis for squishing.
    private Direction[] squishAxes = new Direction[2];

    // "default" of up for falling things executing, didn't see a "none" or directionless, and generally default to gravity.
    private Direction sideHit = Direction.UP;

    // whether this blob is mashed on something.
    private boolean isAttachedToBlock = false;

    // the direction the goo was going when processing for a bounce. It stays updated until it hits something.
    private Vector3d initialBounceVector = Vector3d.ZERO;

    // how long the goo has spent wobblin'
    private int quiverTimer;

    // where our goo goes, this is our reservoir's behavior.
    private final GooTank goo = new GooTank(() -> 1000).setFilter(GooFluid.IS_GOO_FLUID).setChangeCallback(this::contentsChanged);

    // our capability for fluid
    private final LazyOptional<IFluidHandler> lazyHandler = LazyOptional.of(() -> goo);

    private Entity owner;
    private BlockPos blockAttached = null;

    /***
     * factory for a splat that was hand placed by a player. Typically has some more structure than other placements because it has a specific hit vec.
     * @param player who sent you
     * @param pos where
     * @param side which side of a block position are you on
     * @param hit the specific 3d vec of the hit
     * @param splatStack what fluid are you
     * @return
     */
    public static HexController createPlacedSplat(PlayerEntity player, BlockPos pos, Direction side, Vector3d hit, FluidStack splatStack) {
        return new HexController(Registry.GOO_BLOB.get(), player.world, player, splatStack, pos, side, hit);
	}

    /***
     * constructor for the controller factory above
     * @param type what kind of blob are you
     * @param world what world you're in
     * @param sender who sent you
     * @param splatStack what fluid are you
     * @param pos where
     * @param side which side of a block position are you on
     * @param hit the specific 3d vec of the hit
     */
    public HexController(EntityType<HexController> type, World world, PlayerEntity sender, FluidStack splatStack, BlockPos pos, Direction side, Vector3d hit) {
        super(type, world);
        isAttachedToBlock = true;
        if (sender != null) {
            this.owner = sender;
        }
        if (this.goo.isFluidValid(0, splatStack) && this.goo.getRemainingCapacity() > 0) {
            int maxFill = Math.min(this.goo.getRemainingCapacity(), splatStack.getAmount());
            this.goo.fill(splatStack, FluidAction.EXECUTE);
            splatStack.setAmount(splatStack.getAmount() - maxFill);
        }
        this.sideHit = side;
        this.setPositionAndRotation(pos.getX(), pos.getY(), pos.getZ(), 0, 0);

    }

	public List<GooBlobShape> shapes() {
        return null;
    }

    public HexController(EntityType<HexController> type, World worldIn) {
        super(type, worldIn);
    }

    /***
     *  special constructor for the thrown goo event, has its own shoot protocol.
     * @param type expects a goo type from the registry class
     * @param worldIn the world you're in
     * @param sender who shot this blob
     * @param stack the fluid stack blob representation
     */
    public HexController(EntityType<HexController> type, World worldIn, Entity sender, FluidStack stack) {
        super(type, worldIn);
        if (!(stack.getFluid() instanceof GooFluid)) {
            this.remove();
        } else {
            Vector3d pos = initialPosition(sender);
            this.setPositionAndRotation(pos.x, pos.y, pos.z, 0, 0);
            this.owner = sender;
            goo.fill(stack, FluidAction.EXECUTE);
            this.shoot();
        }
    }

    // special constructor for goo blobs that don't shoot but were simply dropped from a specific point in space; this is important.
    private HexController(EntityType<HexController> type, World worldIn, Entity sender, FluidStack stack, Vector3d pos) {
        super(type, worldIn);
        isAttachedToBlock = false;
        if (sender != null) {
            this.owner = sender;
        }
        this.setPositionAndRotation(pos.x, pos.y, pos.z, 0f, 0f);
        goo.fill(stack, FluidAction.EXECUTE);
    }

    // special constructor for goo blobs created by a drain.
    public HexController(EntityType<HexController> type, World worldIn, Entity proxySender, FluidStack stack, BlockPos blockPos) {
        super(type, worldIn);
        // isAttachedToBlock = false;
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

        // let the server handle motion and updates
        if (world.isRemote()) {
            int dataManagerGooSize = this.dataManager.get(GOO_AMOUNT);
            if (!goo.isEmpty() && dataManagerGooSize != goo.getTotalContents()) {
                goo.getFluidInTankInternal(0).setAmount(this.dataManager.get(GOO_AMOUNT));
            }
        }

        if (goo.isEmpty()) {
            this.remove();
            return;
        }

        // quiver timer is really just a client side thing
        if (this.quiverTimer > 0) {
            quiverTimer--;
        }

        // NOOP TODO
//        if (this.isOnGround()) {
//            this.ticksInGround++;
//        }
//
//        if (!isAttachedToBlock) {
//            if (handleMovement()) {
//                return;
//            }
//            doFreeMovement();
//            this.isCollidingEntity = this.checkForEntityCollision();
//        }

        // NOOP TODO
//        // feed the splat we belong to - at this point it's possible we're collided with an entity and still moving,
//        // so we do a living check before this tick is over - but it's not needed any earlier than this.
//        if (this.isAlive() && goo.getTotalContents() > 0 && attachedSplat != null) {
//            approachSplatOffset();
//            if (!world.isRemote()) {
//                // we attempt to reach equilibrium with the splat we're attached to.
//                int amountInSplat = attachedSplat.goo().getAmount();
//                int amountToDrain = (int)Math.floor((double)(amountInSplat - goo.getTotalContents()) / 2d);
//                if (amountToDrain != 0) {
//                    if (amountToDrain < 0) {
//                        GooBlobController.getGoo(attachedSplat).fill(goo.drain(-amountToDrain, FluidAction.EXECUTE), FluidAction.EXECUTE);
//                    } else {
//                        goo.fill(GooBlobController.getGoo(attachedSplat).drain(amountToDrain, FluidAction.EXECUTE), FluidAction.EXECUTE);
//                    }
//                }
//
//            }
//        }
    }

    // NOOP TODO
//    private void approachSplatOffset() {
//        Vector3d splatPosition = attachedSplat.getPositionVec();
//        Vector3d offsetPosition = Vector3d.copy(attachedSplat.sideWeLiveOn().getDirectionVec()).scale(cubicSize() / 2f);
//        Vector3d trueOffset = splatPosition.add(offsetPosition.getX(), offsetPosition.getY(), offsetPosition.getZ());
//        setPositionAndRotation(trueOffset.x, trueOffset.y, trueOffset.z, this.rotationYaw, this.rotationPitch);
//    }

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

            if (!this.world.isRemote() && isValidForPassThrough(world.getBlockState(blockResult.getPos()))) {
                GooInteractions.tryResolving(blockResult.getPos(), this);
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
            this.isAirBorne = true;
            return onImpact(entityResult);
        }

        handleLiquidCollisions(motion);

        this.doBlockCollisions();

        return false;
    }

    @Override
    protected void onInsideBlock(BlockState state) {
        super.onInsideBlock(state);
        boolean isAir = state.getBlock().isAir(state, world, this.getPosition()) || isValidForPassThrough(state);
        this.setOnGround(!isAir);
    }

    private void stickBlob(Vector3d hitVec) {

        this.setPositionAndRotation(hitVec.x, hitVec.y, hitVec.z, this.rotationYaw, this.rotationPitch);
        this.setMotion(this.getMotion().scale(0.06d));
        GooInteractions.spawnParticles(this);
    }

    @Override
    public ActionResultType applyPlayerInteraction(PlayerEntity player, Vector3d vec, Hand hand) {
        if (player.getHeldItem(hand).getItem() instanceof Vessel
                || player.getHeldItem(hand).getItem() instanceof Gauntlet) {
                if (this.ticksExisted > 10) {
                    tryPlayerInteraction(player, hand);
                    return ActionResultType.CONSUME;
                }

        }
        return super.applyPlayerInteraction(player, vec, hand);
    }

    private void tryPlayerInteraction(PlayerEntity player, Hand hand)
    {
        LazyOptional<IFluidHandlerItem> cap = player.getHeldItem(hand).getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
        cap.ifPresent((c) -> {
            if (tryExtractingGooFromEntity(c)) {
                AudioHelper.playerAudioEvent(player, Registry.GOO_WITHDRAW_SOUND.get(), 1.0f);
            }
        });
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

    private boolean isValidForPassThrough(BlockState state) {
        if (!GooInteractions.materialPassThroughPredicateRegistry.containsKey(goo.getFluidInTankInternal(0).getFluid())) {
            return isPassableMaterial(state);
        }
        IPassThroughPredicate funk = GooInteractions.materialPassThroughPredicateRegistry.get(goo.getFluidInTankInternal(0).getFluid());
        // isPassable and the blob's passthrough predicate are cooperative, any combination of their conditions will pass.
        return isPassableMaterial(state) || funk.blobPassThroughPredicate(state, this);
    }

    private boolean isPassableMaterial(BlockState state) {
        return !state.getMaterial().blocksMovement() && !isFullFluidBlock(state);
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
        this.setMotion(this.getMotion().add(0d, -gooGravity, 0d));
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

        // NOOP TODO
//        List<GooBlobController> splats = world.getEntitiesWithinAABB(Registry.GOO_SPLAT.get(), this.getBoundingBox(),
//                (s) -> GooBlobController.getGoo(s).getFluidInTankInternal(0).getFluid().equals(goo.getFluidInTankInternal(0).getFluid()));
//        if (splats.size() > 0) {
//            attachToBlock(pos, face, splats.get(0));
//        } else {
//            // create a goo splat
//            FluidStack traceGoo = goo.drain(1, FluidAction.EXECUTE);
//            GooBlobController splatToAdd = new GooBlobController(Registry.GOO_SPLAT.get(), this.owner, world, traceGoo, hitVec, pos, face, true, 0f, false);
//            attachToBlock(pos, face, splatToAdd);
//            world.addEntity(splatToAdd);
//        }
    }

    // NOOP TODO
//    private void attachToBlock(BlockPos pos, Direction face, GooBlobController splat)
//    {
//        this.blockAttached = pos;
//        this.sideWeLiveOn = face;
//        this.isAttachedToBlock = true;
//        this.attachedSplat = splat;
//        AudioHelper.entityAudioEvent(this, Registry.GOO_SPLAT_SOUND.get(), SoundCategory.AMBIENT,
//                1.0f, AudioHelper.PitchFormulas.HalfToOne);
//    }

    protected void doFreeMovement() {
        Vector3d motion = this.getMotion();
        Vector3d position = this.getPositionVec();
        Vector3d projection = position.add(motion);
        this.lastTickPosX = position.x;
        this.lastTickPosY = position.y;
        this.lastTickPosZ = position.z;
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

        // NOOP TODO
        //deserializeAttachment(tag);
        goo.readFromNBT(tag.getCompound("goo"));
        if (tag.hasUniqueId("owner")) {
            this.owner = world.getPlayerByUuid(tag.getUniqueId("owner"));
        }
        this.isCollidingEntity = tag.getBoolean("LeftOwner");
    }

    // NOOP TODO
//    private void deserializeAttachment(CompoundNBT tag)
//    {
//        if (!tag.contains("attachment")) {
//            this.isAttachedToBlock = false;
//            return;
//        }
//        CompoundNBT at = tag.getCompound("attachment");
//        this.blockAttached =
//                new BlockPos(at.getInt("x"), at.getInt("y"), at.getInt("z"));
//        this.sideWeLiveOn = Direction.byIndex(at.getInt("side"));
//        this.isAttachedToBlock = true;
//    }

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
//        if (!isAttachedToBlock) {
//            return;
//        }
        CompoundNBT at = new CompoundNBT();
        at.putInt("x", blockAttached.getX());
        at.putInt("y", blockAttached.getY());
        at.putInt("z", blockAttached.getZ());

        // NOOP TODO
        //at.putInt("side", sideWeLiveOn.getIndex());
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
        if (tag != null) {
            readAdditional(tag);
        }
    }

    public void startQuivering()
    {
        if (this.quiverTimer < quiverTimerCycle) {
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
                            (eInBB) -> isBlobToMergeWith(eInBB) || !eInBB.isSpectator());
            for(Entity e : collidedEntities) {
                if (isBlobToMergeWith(e)) {
                    HexController target = (HexController)e;
                    double mergeSigma = (double) goo.getTotalContents() + target.goo.getTotalContents();
                    double sourceDominance = (double) goo.getTotalContents() / mergeSigma;
                    Vector3d positionDelta = target.getPositionVec().subtract(this.getPositionVec()).scale(1d - sourceDominance);
                    Vector3d motionDelta = target.getMotion().subtract(this.getMotion()).scale(1d - sourceDominance);
                    Vector3d newPos = this.getPositionVec().add(positionDelta);
                    Vector3d newMotion = this.getMotion().add(motionDelta);
                    this.setPositionAndRotation(newPos.x, newPos.y, newPos.z, rotationYaw, rotationPitch);
                    this.setMotion(newMotion.x, newMotion.y, newMotion.z);

                    goo.fill(((HexController)e).goo.drain(goo.getRemainingCapacity(), FluidAction.EXECUTE), FluidAction.EXECUTE);
                    return true;
                } else if (e instanceof LivingEntity) {
                    // do stuff if the entity is a living entity
                    // sometimes the ray trace collision doesn't work and it passes through entities.
                    // this is a motion projection collision instead of a here and there collision, which seems to catch what the other misses.
                    if (collideWithEntity(e)) {
                        return true;
                    }
                }
                // skip riders unless we're hitting the lowest
                if (owner != null) {
                    if (e.getLowestRidingEntity() == owner.getLowestRidingEntity()) {
                        return false;
                    }
                }
            }

        return false;
    }

    private boolean isBlobToMergeWith(Entity eInBB) {
        return eInBB instanceof HexController && eInBB.isAlive()
                && ((HexController)eInBB).goo.getFluidInTankInternal(0).isFluidEqual(goo.getFluidInTankInternal(0))
                && ((HexController)eInBB).goo.getTotalContents() <= goo.getTotalContents();
    }

    protected boolean onImpact(RayTraceResult rayTraceResult) {
        RayTraceResult.Type resultType = rayTraceResult.getType();
        if (resultType == RayTraceResult.Type.ENTITY) {
            return collideWithEntity(((EntityRayTraceResult)rayTraceResult).getEntity());
        }
        return false;
    }

    protected boolean collideWithEntity(Entity entityHit)
    {
        if (entityHit == owner) {
            return false;
        }
        // collisions with entities cause a dead drop and attempt to resolve their blob hit effect
        LivingEntity blobSender = null;
        if (this.owner instanceof LivingEntity) {
            blobSender = (LivingEntity)owner;
        }
        if (entityHit instanceof LivingEntity) {
            if (!world.isRemote()) {
                GooInteractions.tryResolving((LivingEntity) entityHit, blobSender, this);
            }
            this.setMotion(this.getMotion().mul(0d, -gooGravity, 0d));
            return true;
        }
        return false;
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

    @Override
    public FluidStack goo() {

        return goo.getFluidInTank(0);
    }

    public static IGooTank getGoo(HexController blob) {

        return blob.goo;
    }
}
