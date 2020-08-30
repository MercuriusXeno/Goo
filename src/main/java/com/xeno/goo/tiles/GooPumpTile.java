package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.network.GooFlowPacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.setup.Registry;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.Objects;

public class GooPumpTile extends TileEntity implements ITickableTileEntity, GooFlowPacket.IGooFlowReceiver
{
    private static final int DEFAULT_ANIMATION_FRAMES = 20;
    private Fluid pumpFluid;
    private float flowIntensity;
    private int animationFrames;

    @Override
    public void updateVerticalFill(Fluid f, float intensity)
    {
        this.pumpFluid = f;
        this.flowIntensity = intensity;
        if (this.animationFrames == 0) {
            this.animationFrames = DEFAULT_ANIMATION_FRAMES;
        }
    }

    public int animationFrames() {
        return this.animationFrames;
    }

    public float verticalFillIntensity()
    {
        return this.flowIntensity;
    }

    public FluidStack verticalFillFluid()
    {
        return new FluidStack(pumpFluid, 1);
    }

    private float verticalFillDecay() {
        // throttle the intensity decay so it doesn't look so jittery. This will cause the first few frames to be slow
        // changing, but later frames will be proportionately somewhat faster.
        if (flowIntensity > 0.9f) {
            return 0.01f;
        }
        float decayRate = 0.2f;
        return Math.min(flowIntensity * decayRate, 0.125f);
    }

    public void decayVerticalFillVisuals() {
        if (this.animationFrames > 0) {
            this.animationFrames--;
        }
        if (!isVerticallyFilled()) {
            return;
        }
        flowIntensity -= verticalFillDecay(); // flow reduces each frame work tick until there's nothing left.
        float cutoffThreshold = 0.05f;
        if (flowIntensity <= cutoffThreshold) {
            disableVerticalFillVisuals();
        }
    }

    public void disableVerticalFillVisuals() {
        pumpFluid = Fluids.EMPTY;
        flowIntensity = 0f;
    }

    public boolean isVerticallyFilled() {
        return !pumpFluid.equals(Fluids.EMPTY) && flowIntensity > 0f;
    }

    public void toggleVerticalFillVisuals(Fluid f)
    {
        pumpFluid = f;
        flowIntensity = 1f; // default fill intensity is just "on", essentially
        if (world == null) {
            return;
        }
        if (this.animationFrames == 0) {
            animationFrames = DEFAULT_ANIMATION_FRAMES;
        }
        Networking.sendToClientsAround(new GooFlowPacket(world.func_234923_W_(), pos, pumpFluid, flowIntensity), Objects.requireNonNull(Objects.requireNonNull(world.getServer()).getWorld(world.func_234923_W_())), pos);
    }

    public GooPumpTile()
    {
        super(Registry.GOO_PUMP_TILE.get());
        this.pumpFluid = Fluids.EMPTY;
    }

    @Override
    public void tick()
    {
        if (world == null) {
            return;
        }

        if (world.isRemote) {
            // vertical fill visuals are client-sided, for a reason. We get sent activity from server but
            // the decay is local because that's needless packets otherwise. It's deterministic.
            decayVerticalFillVisuals();
            return;
        }

        tryPushingFluid();
    }

    private void tryPushingFluid()
    {
        GooBulbTile source = tryGettingBulbInSourceDirection();
        GooBulbTile target = tryGettingBulbInTargetDirection();

        if (source == null || target == null) {
            return;
        }

        BulbFluidHandler sourceHandler = (BulbFluidHandler)BulbFluidHandler.bulbCapability(source, sourceDirection());
        BulbFluidHandler targetHandler = (BulbFluidHandler)BulbFluidHandler.bulbCapability(target, targetDirection());
        if (sourceHandler == null || targetHandler == null) {
            return;
        }

        FluidStack simulatedDrain = sourceHandler.drain(getMaxDrain(), IFluidHandler.FluidAction.SIMULATE);
        if (simulatedDrain.isEmpty()) {
            return;
        }

        int filled = targetHandler.fill(simulatedDrain, IFluidHandler.FluidAction.SIMULATE);
        if (filled == 0) {
            return;
        }

        FluidStack result = sourceHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        toggleVerticalFillVisuals(result.getFluid());

        // pump for real though
        targetHandler.fill(result, IFluidHandler.FluidAction.EXECUTE);

    }

    private int getMaxDrain()
    {
        return GooMod.config.pumpAmountPerCycle();
    }

    public Direction facing()
    {
        return this.getBlockState().get(BlockStateProperties.FACING);
    }

    private Direction sourceDirection() {
        return this.facing().getOpposite();
    }

    private Direction targetDirection() {
        return this.facing();
    }

    private GooBulbTile tryGettingBulbInDirection(Direction d)
    {
        if (world == null) {
            return null;
        }
        TileEntity t = world.getTileEntity(pos.offset(d));
        if (t instanceof GooBulbTile) {
            return (GooBulbTile)t;
        }
        return null;
    }

    private GooBulbTile tryGettingBulbInTargetDirection()
    {
        return tryGettingBulbInDirection(targetDirection());
    }

    private GooBulbTile tryGettingBulbInSourceDirection()
    {
        return tryGettingBulbInDirection(sourceDirection());
    }
}
