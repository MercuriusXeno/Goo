package com.xeno.goo.entities;

import com.xeno.goo.blocks.Drain;
import com.xeno.goo.interactions.GooInteractions;
import com.xeno.goo.items.Basin;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.DrainTile;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.TileEntity;
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
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Optional;

public class GooSplat extends Entity implements IEntityAdditionalSpawnData, IFluidHandler
{
    private static final DataParameter<Integer> GOO_AMOUNT = EntityDataManager.createKey(GooSplat.class, DataSerializers.VARINT);
    private static final DataParameter<Boolean> IS_AT_REST = EntityDataManager.createKey(GooSplat.class, DataSerializers.BOOLEAN);

    // vars that help determine the dimensions of the splat
    // the first Single-Tile-Liquid-Covering amount is what it takes to get
    // the entire block covered, after which the depth increases instead.
    private static final double PUDDLE_DEPTH = 0.1d;
    private static final double SINGLE_TILE_LIQUID_COVERING_RATIO = 16d; // 16 units is expected to cover 1x1
    // these ratios are "fixed" ratios that get used up until the amount of liquid is greater than the covering ratio.
    private static final double LIQUID_CUBIC_RATIO = 1000d;
    private static final double LIQUID_CUBIC_TILE_COVERAGE_VOLUME = SINGLE_TILE_LIQUID_COVERING_RATIO / LIQUID_CUBIC_RATIO;
    private static final double LIQUID_CUBIC_SIDE_LENGTH_DERIVED = Math.sqrt(LIQUID_CUBIC_TILE_COVERAGE_VOLUME / PUDDLE_DEPTH);
    private static final double PUDDLE_EXPANSION_RATIO = 1d / LIQUID_CUBIC_SIDE_LENGTH_DERIVED;


    // actual properties of the splat
    private FluidStack goo;
    private Entity owner;
    private Vector3d shape;
    private AxisAlignedBB box;
    private Direction sideWeLiveOn;
    private BlockPos blockAttached = null;
    private int cooldown = 0;
    private int lastGooAmount = 0;
    private boolean isAtRest;
    private float imperceptibleOffset = 0f;

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

    public GooSplat(EntityType<GooSplat> type, Entity sender, World world, FluidStack traceGoo, Vector3d hitVec,
                    BlockPos pos, Direction face, boolean playSound, float imperceptibleOffset) {
        super(type, world);
        this.goo = traceGoo;
        this.owner = sender;
        this.sideWeLiveOn = face;
        this.blockAttached = pos;
        this.isAtRest = false;
        this.imperceptibleOffset = imperceptibleOffset;
        // send clients the updated size, it defaults to 1
        setSize();
        Vector3d findCenter = findCenter(hitVec);
        this.setPosition(findCenter.x, findCenter.y, findCenter.z);
        if (playSound) {
            AudioHelper.entityAudioEvent(this, Registry.GOO_SPLAT_SOUND.get(), SoundCategory.AMBIENT,
                    1.0f, AudioHelper.PitchFormulas.HalfToOne);
        }
    }

    public static GooSplat createPlacedSplat(PlayerEntity player, BlockPos pos, Direction side, Vector3d hit,
                                             FluidStack thrownStack, boolean playSound, float imperceptibleOffset) {
        return new GooSplat(Registry.GOO_SPLAT.get(), player,  player.world, thrownStack, hit, pos, side, playSound, imperceptibleOffset);
    }

    private void updateSplatState()
    {
        double cubicArea = goo.getAmount() / LIQUID_CUBIC_RATIO;
        double targetSurfaceArea = cubicArea / PUDDLE_DEPTH;
        double sideLength = Math.min(0.999d, Math.sqrt(targetSurfaceArea) * PUDDLE_EXPANSION_RATIO);
        // this will be puddle depth const until sidelength gets throttled.
        double actualDepth = cubicArea / (sideLength * sideLength);
        switch (depthAxis()) {
            case X:
                shape =  new Vector3d(actualDepth, sideLength, sideLength);
                break;
            case Y:
                shape =  new Vector3d(sideLength, actualDepth, sideLength);
                break;
            case Z:
                shape =  new Vector3d(sideLength, sideLength, actualDepth);
                break;
            default:
                shape = Vector3d.ZERO;
                break;
        }

        this.box = new AxisAlignedBB(shape.scale(-0.5d), shape.scale(0.5d));
        resetBox();
    }

    private Vector3d findCenter(Vector3d hitVec)
    {
        switch(sideWeLiveOn) {
            case NORTH:
                return new Vector3d(hitVec.x, hitVec.y, hitVec.z + (box.minZ - (0.01d + imperceptibleOffset)));
            case SOUTH:
                return new Vector3d(hitVec.x, hitVec.y, hitVec.z + ((0.01d + imperceptibleOffset) + box.maxZ));
            case DOWN:
                return new Vector3d(hitVec.x, hitVec.y + (box.minY - (0.01d + imperceptibleOffset)), hitVec.z);
            case UP:
                return new Vector3d(hitVec.x, hitVec.y + ((0.01d + imperceptibleOffset) + box.maxY), hitVec.z);
            case WEST:
                return new Vector3d(hitVec.x + (box.minX - (0.01d + imperceptibleOffset)), hitVec.y, hitVec.z);
            case EAST:
                return new Vector3d(hitVec.x + ((0.01d + imperceptibleOffset) + box.maxX), hitVec.y, hitVec.z);
        }
        // something weird happened that wasn't supposed to.
        return hitVec;
    }

    /**
     * Sets the x,y,z of the entity from the given parameters. Also seems to set up a bounding box.
     */
    @Override
    public void setPosition(double x, double y, double z) {
        this.setRawPosition(x, y, z);
        if (this.isAddedToWorld() && !this.world.isRemote && world instanceof ServerWorld)
            ((ServerWorld)this.world).chunkCheck(this); // Forge - Process chunk registration after moving.
        resetBox();
    }

    private void resetBox() {
        // on initialization this box doesn't exist
        // but almost immediately afterwards we should always have a shape
        if (box != null && this.goo.getAmount() > 0) {
            this.setBoundingBox(box.offset(this.getPositionVec()));
        }
    }

    public AxisAlignedBB getCollisionBoundingBox() {
        return null;
    }

    @Override
    public void recalculateSize() {
        this.updateSplatState();
    }

    @Override
    protected void registerData() {
        this.dataManager.register(GOO_AMOUNT, 1);
        this.dataManager.register(IS_AT_REST, false);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.goo.isEmpty()) {
            this.remove();
            return;
        }

        if (sideWeLiveOn == Direction.DOWN) {
            // the state we're interested in observing is the state of the hit block, not the offset.
            GooInteractions.spawnParticles(this);
        }
        spawnParticles();

        // let the server handle motion and updates
        // also don't tell the server what the goo amount is, it knows.
        if (world.isRemote()) {
            int dataManagerGooSize = this.dataManager.get(GOO_AMOUNT);
            isAtRest = this.dataManager.get(IS_AT_REST);
            if (dataManagerGooSize != goo.getAmount()) {
                goo.setAmount(dataManagerGooSize);
                updateSplatState();
            }
            return;
        }

        boolean wasAtRest = this.isAtRest;
        this.isAtRest = lastGooAmount == goo.getAmount();

        if (wasAtRest != isAtRest) {
            this.dataManager.set(IS_AT_REST, this.isAtRest);
        }

        if (this.isAtRest) {
            // first we try to drain into a drain if we're vertical and it's below
            if (sideWeLiveOn == Direction.UP && isDrainBelow()) {
                drainIntoDrain();
            } else {
                if (cooldown == 0) {
                    GooInteractions.tryResolving(this);
                } else {
                    cooldown--;
                }
            }
        }

        handleMaterialCollisionChecks();
        this.checkForEntityCollision();
        approachAttachmentPoint();
        doFreeMovement();

        // check if we're floating lol
        if (world.getBlockState(blockAttached).isAir(world, blockAttached)) {
            world.addEntity(GooBlob.createLobbedBlob(this));
            this.remove();
        }

        lastGooAmount = goo.getAmount();
    }

    private void spawnParticles() {
        if (world instanceof ServerWorld) {
            BasicParticleType type = Registry.vaporParticleFromFluid(goo.getFluid());
            if (type == null) {
                return;
            }
            AxisAlignedBB box = getBoundingBox();
            for (int i = 0; i < 4; i++) {
                float scale = world.rand.nextFloat() / 6f + 0.25f;
                Vector3d lowerBounds = new Vector3d(box.minX, box.minY, box.minZ);
                Vector3d upperBounds = new Vector3d(box.maxX, box.maxY, box.maxZ);
                Vector3d threshHoldMax = upperBounds.subtract(lowerBounds);
                Vector3d centeredBounds = threshHoldMax.scale(0.5f);
                Vector3d center = lowerBounds.add(centeredBounds);
                Vector3d randomOffset = threshHoldMax.mul(world.rand.nextFloat() - 0.5f,
                        world.rand.nextFloat() - 0.5f,
                        world.rand.nextFloat() - 0.5f).scale(scale * 2f);
                Vector3d spawnVec = center.add(randomOffset);
                // make sure the spawn area is offset in a way that puts the particle outside of the block side we live on
                // Vector3d offsetVec = Vector3d.copy(sideWeLiveOn().getDirectionVec()).mul(threshHoldMax.x, threshHoldMax.y, threshHoldMax.z);

                Vector3d spawnOutwardVec = Vector3d.copy(sideWeLiveOn().getDirectionVec()).scale(scale / 2f);
                spawnVec = spawnVec.add(spawnOutwardVec);
                ((ServerWorld) world).spawnParticle(type, spawnVec.x, spawnVec.y, spawnVec.z,
                        1, 0d, 0d, 0d, scale);
            }
        }
    }

    @Override
    public ActionResultType applyPlayerInteraction(PlayerEntity player, Vector3d vec, Hand hand) {
        if (player.getHeldItem(hand).getItem() instanceof Basin ||
                player.getHeldItem(hand).getItem() instanceof Gauntlet) {
            if (isAtRest()) {
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
            if (!heldGoo.isFluidEqual(getFluidInTank(0)) || getFluidInTank(0).isEmpty()) {
                return false;
            }
        }

        int spaceRemaining = item.getTankCapacity(0) - item.getFluidInTank(0).getAmount();
        FluidStack tryDrain = drain(spaceRemaining, IFluidHandler.FluidAction.SIMULATE);
        if (tryDrain.isEmpty()) {
            return false;
        }

        item.fill(drain(tryDrain, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    private void drainIntoDrain() {
        BlockPos below = this.getPosition().offset(Direction.DOWN);
        TileEntity e = world.getTileEntity(below);
        if (e instanceof DrainTile) {
            if (((DrainTile) e).canFill(this.goo)) {
                ((DrainTile)e).fill(this.drain(1, FluidAction.EXECUTE), this.owner);
            }
        }
    }

    private boolean isDrainBelow() {
        BlockPos below = this.getPosition().offset(Direction.DOWN);
        BlockState state = world.getBlockState(below);
        if (state.getBlock() instanceof Drain) {
            return true;
        }
        return false;
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
        Vector3d attachmentPoint = Vector3d.copy(blockAttached)
                .add(0.5d, 0.5d, 0.5d)

                .add((0.51d + imperceptibleOffset) * sideWeLiveOn.getXOffset(),
                        (0.51d + imperceptibleOffset) * sideWeLiveOn.getYOffset(),
                        (0.51d + imperceptibleOffset) * sideWeLiveOn.getZOffset())
                .add((box.maxX) * sideWeLiveOn.getXOffset(),
                        (box.maxY) * sideWeLiveOn.getYOffset(),
                        (box.maxZ) * sideWeLiveOn.getZOffset());
        return attachmentPoint;
    }

    protected void doFreeMovement()
    {
        Vector3d projection = getPositionVec().add(getMotion());
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

    @Override
    public void read(CompoundNBT tag)
    {
        super.read(tag);
        goo = FluidStack.loadFluidStackFromNBT(tag);
        lastGooAmount = tag.getInt("lastGooAmount");
        isAtRest = tag.getBoolean("isAtRest");
        deserializeAttachment(tag);
        if (tag.hasUniqueId("owner")) {
            this.owner = world.getPlayerByUuid(tag.getUniqueId("owner"));
        }
        updateSplatState();
    }

    private void deserializeAttachment(CompoundNBT tag)
    {
        CompoundNBT at = tag.getCompound("attachment");
        this.blockAttached =
                new BlockPos(at.getInt("x"), at.getInt("y"), at.getInt("z"));
        this.sideWeLiveOn = Direction.byIndex(at.getInt("side"));
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT tag = super.serializeNBT();
        goo.writeToNBT(tag);
        tag.putInt("lastGooAmount", lastGooAmount);
        tag.putBoolean("isAtRest", isAtRest);
        serializeAttachment(tag);
        if (this.owner != null) { tag.putUniqueId("owner", owner.getUniqueID()); }
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

    // 32 blocks.
    private double A_REASONABLE_RENDER_DISTANCE_SQUARED = 1024;
    @Override
    public boolean isInRangeToRenderDist(double distance)
    {
        return distance < A_REASONABLE_RENDER_DISTANCE_SQUARED;
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

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return this.getBoundingBox();
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

    private boolean checkForEntityCollision() {
        if (!this.isAlive()) {
            return false;
        }
        Collection<Entity> collidedEntities =
                this.world.getEntitiesInAABBexcluding(this,
                        // grow bb
                        this.getBoundingBox(),
                        // filter
                        (eInBB) -> eInBB.equals(owner) || eInBB instanceof GooSplat || isValidCollisionEntity(eInBB));
        for(Entity e : collidedEntities) {
            if (e instanceof GooSplat && ((GooSplat) e).goo().isFluidEqual(this.goo)) {
                // must be dominant
                if (this.goo.getAmount() < ((GooSplat)e).goo().getAmount()) {
                    continue;
                }
                // must be resting
                if (!((GooSplat)e).isAtRest() || !this.isAtRest()) {
                    continue;
                }
                // must be living on the same side as us or it's just annoying
                if (((GooSplat)e).sideWeLiveOn() != this.sideWeLiveOn || !((GooSplat)e).blockAttached().equals(this.blockAttached)) {
                    continue;
                }
                int amountToDrain = (int)Math.ceil(Math.sqrt(((GooSplat)e).goo().getAmount()) / 2d);
                this.fill(((GooSplat) e).drain(amountToDrain, FluidAction.EXECUTE), FluidAction.EXECUTE);
            } else if (e.equals(owner)) {
                if (e == owner) {
                    // only try catching the goos flagged to bounce/return goo
                    // at the time of writing, hard coded.
                    if (!isAutoGrabbedGoo()) {
                        continue;
                    }
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isAtRest() {
        return this.isAtRest;
    }

    private boolean isAutoGrabbedGoo() {
        return goo.getFluid().equals(Registry.CRYSTAL_GOO.get())
                || goo.getFluid().equals(Registry.METAL_GOO.get())
                || goo.getFluid().equals(Registry.REGAL_GOO.get())
                || goo.getFluid().equals(Registry.OBSIDIAN_GOO.get());
    }

    private boolean isValidCollisionEntity(Entity eInBB)
    {
        return !eInBB.isSpectator() && eInBB.canBeCollidedWith();
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

    public Entity owner()
    {
        return this.owner;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }
}
