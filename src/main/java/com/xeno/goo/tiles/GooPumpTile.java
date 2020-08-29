package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.network.BulbVerticalFillPacket;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.setup.Registry;
import net.minecraft.fluid.Fluid;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;

public class GooPumpTile extends TileEntity implements ITickableTileEntity, FluidUpdatePacket.IFluidPacketReceiver, BulbVerticalFillPacket.IVerticalFillReceiver
{
    private FluidStack goo;
    private float intensity;
    private int cooldown;
    private boolean isPumping;

    public GooPumpTile()
    {
        super(Registry.GOO_PUMP_TILE.get());
        this.goo = FluidStack.EMPTY;
        this.cooldown = 0;
        this.isPumping = false;
    }

    @Override
    public void updateVerticalFill(Fluid f, float intensity)
    {
        this.intensity = intensity;
    }

    @Override
    public void updateFluidsTo(List<FluidStack> fluids)
    {
        // we only have one slot, so hopefully this is all there is to this packet or we ignore the rest.
        if (fluids.size() > 0) {
            this.goo = fluids.get(0);
        }
    }

    @Override
    public void tick()
    {
        if (world == null || world.isRemote()) {
            return;
        }

        if (isPumping()) {
            if (tryPushingFluid()) {
                return;
            }
        } else {
            if (this.isCoolingDown()) {
                this.cooldown--;
            }
        }

        if (this.goo.isEmpty()) {
            this.isPumping = false;
        }

        if (prepareToPump()) {
            this.startCooldown();
        }
    }

    private boolean tryPushingFluid()
    {
        GooBulbTile target = tryGettingBulbInTargetDirection();

        if (target == null) {
            return false;
        }

        BulbFluidHandler targetHandler = (BulbFluidHandler)BulbFluidHandler.bulbCapability(target, targetDirection());
        if (targetHandler == null) {
            return false;
        }

        int filled = targetHandler.fill(this.goo, IFluidHandler.FluidAction.SIMULATE);
        if (filled == 0) {
            return false;
        }

        FluidStack result = this.goo.copy();
        result.setAmount(filled);
        targetHandler.fill(result, IFluidHandler.FluidAction.EXECUTE);
        this.goo.setAmount(this.goo.getAmount() - filled);
        if (this.goo.getAmount() == 0) {
            this.goo = FluidStack.EMPTY;
        }

        return true;
    }

    private boolean isPumping()
    {
        return this.isPumping;
    }

    private boolean prepareToPump()
    {
        GooBulbTile source = tryGettingBulbInSourceDirection();
        GooBulbTile target = tryGettingBulbInTargetDirection();

        if (source == null || target == null) {
            return false;
        }

        BulbFluidHandler sourceHandler = (BulbFluidHandler)BulbFluidHandler.bulbCapability(source, sourceDirection());
        BulbFluidHandler targetHandler = (BulbFluidHandler)BulbFluidHandler.bulbCapability(target, targetDirection());
        if (sourceHandler == null || targetHandler == null) {
            return false;
        }

        FluidStack simulatedDrain = sourceHandler.drain(getMaxDrain(), IFluidHandler.FluidAction.SIMULATE);
        if (simulatedDrain.isEmpty()) {
            return false;
        } else {
            if (!this.goo.isEmpty() && !simulatedDrain.isFluidEqual(this.goo)) {
                return false;
            }
        }

        int filled = targetHandler.fill(simulatedDrain, IFluidHandler.FluidAction.SIMULATE);
        if (filled == 0) {
            return false;
        }

        if (this.goo.isEmpty()) {
            this.goo = sourceHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        } else {
            this.goo.setAmount(this.goo.getAmount() + sourceHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE).getAmount());
        }

        this.isPumping = true;
        return true;
    }

    private int getMaxDrain()
    {
        return GooMod.config.pumpAmountPerCycle() - this.goo.getAmount();
    }

    private Direction getFacing()
    {
        return this.getBlockState().get(BlockStateProperties.FACING);
    }

    private Direction sourceDirection() {
        return this.getFacing().getOpposite();
    }

    private Direction targetDirection() {
        return this.getFacing();
    }

    private GooBulbTile tryGettingBulbInDirection(Direction d)
    {

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

    private boolean isCoolingDown()
    {
        return this.cooldownRemaining() > 0;
    }

    private int cooldownRemaining()
    {
        return this.cooldown;
    }

    private void startCooldown() {
        this.cooldown = GooMod.config.pumpCycleCooldown();
    }

    public float verticalFillIntensity()
    {
        return intensity;
    }

    public FluidStack goo() {
        return this.goo;
    }

    public boolean isVerticallyFilled()
    {
        return this.intensity > 0;
    }

    public FluidStack verticalFillFluid()
    {
        return goo();
    }
}
