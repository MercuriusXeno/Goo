package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.Compare;
import com.xeno.goo.network.FluidUpdatePacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.util.FluidHandlerWrapper;
import com.xeno.goo.util.IGooTank;
import com.xeno.goo.util.MultiGooTank;
import it.unimi.dsi.fastutil.ints.Int2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.fluid.Fluid;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CrucibleTile extends GooContainerAbstraction implements ITickableTileEntity,
																	 FluidUpdatePacket.IFluidPacketReceiver {

	public CrucibleTile() {

		super(Registry.CRUCIBLE_TILE.get());
	}


	private final LazyOptional<IFluidHandler> crucibleHandler = LazyOptional.of(() -> new FluidHandlerWrapper(goo) {

		@Override
		public int fill(FluidStack resource, FluidAction action) {

			return super.fill(resource, action);
		}
	});

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, Direction side) {

		if (side == null) {
			return crucibleHandler.cast();
		}
		// if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && side == Direction.UP) {
			// return topHandler.cast();
		// }
		return super.getCapability(cap, side);
	}

	@Override
	public void updateFluidsTo(PacketBuffer fluids) {

		this.goo.readFromPacket(fluids);
	}

	@Override
	protected IGooTank createGooTank() {
		return new MultiGooTank(this::getStorageCapacity)
				.setFilter(GooFluid.IS_GOO_FLUID)
				.setChangeCallback(this::onContentsChanged);
	}

	public void onContentsChanged() {

		if (world == null || world.isRemote) {
			return;
		}
		Networking.sendToClientsAround(new FluidUpdatePacket(world.getDimensionKey(), pos, goo), (ServerWorld) world, pos);
	}

	@Override
	public int getBaseCapacity() {
		return GooMod.config.crucibleCapacity();
	}

	public int getStorageMultiplier()
	{
		return storageMultiplier(getContainmentLevel());
	}

	public static int storageMultiplier(int enchantContainment)
	{
		return (int)Math.pow(GooMod.config.crucibleContainmentMultiplier(), enchantContainment);
	}

	public static int storageForDisplay(int containmentLevel)
	{
		return storageMultiplier(containmentLevel) * GooMod.config.crucibleCapacity();
	}

	// moved this from renderer to here so that both can utilize the same
	// offset logic (and also renderer is client code, not the same in reverse)
	private static final float FLUID_VERTICAL_OFFSET = 0.126f; // this offset puts it slightly below/above the 1px line to seal up an ugly seam
	private static final float FLUID_VERTICAL_MAX = 0.075f;
	public static final float HEIGHT_SCALE = (1f - FLUID_VERTICAL_MAX) - FLUID_VERTICAL_OFFSET;
	public static final float ARBITRARY_GOO_STACK_HEIGHT_MINIMUM = 1f / Registry.FluidSuppliers.size(); // percentile is a representation of all the fluid types in existence.

	public static final Int2DoubleLinkedOpenHashMap CAPACITY_LOGS = new Int2DoubleLinkedOpenHashMap();

	public static Object2FloatMap<Fluid> calculateFluidHeights(int capacity, IGooTank unsortedGoo) {
		if (!CAPACITY_LOGS.containsKey(capacity)) {
			CAPACITY_LOGS.put(capacity, calculateCapacityLog(capacity));
		}
		// start with the smallest stacks because those will influence the "minimum heights" first
		// and result in the larger stacks being diminished to compensate for their relative smallness.
		// shallow copy to avoid concurrent mods
		List<FluidStack> gooStacks = new ArrayList<>();
		for (int i = 0, e = unsortedGoo.getTanks(); i < e; ++i)
			// quick hacks
			if (unsortedGoo.getFluidInTankInternal(i).getAmount() > 0)
				gooStacks.add(unsortedGoo.getFluidInTankInternal(i));

		gooStacks.sort(Compare.fluidAmountComparator);
		float total = gooStacks.stream().mapToInt(FluidStack::getAmount).sum();
		if (total < 0) {
			return new Object2FloatOpenHashMap<>();
		}

		// how high the fluid is in the bulb, as a fraction of 1d
		// 1d is never really achievable since the bulb viewport is technically smaller than that
		float totalFluidHeight = (float)Math.pow(total, 1f / CAPACITY_LOGS.get(capacity)) / 16f; // scaled

		// when something achieves a mandatory minimum in this way, we have to deflate the value
		// proportionally to compensate, and this is also logarithmic.
		Object2FloatMap<Fluid> results = new Object2FloatOpenHashMap<>();
		for (FluidStack g : gooStacks) {
			int totalGooInThisStack = g.getAmount();
			results.put(g.getFluid(), totalGooInThisStack * totalFluidHeight / total);
		}

		// The purpose here is to scale the contents of the bulb so that amounts
		// less than a threshold are made larger than they should be, so you can see them and target
		// them easily even though there's not a lot.
		float scale = 1f;
		float remainder = 1f;
		float diminished = 0f;
		for (FluidStack g : gooStacks) {
			float v = results.getFloat(g.getFluid());
			if (v < ARBITRARY_GOO_STACK_HEIGHT_MINIMUM) {
				remainder -= ARBITRARY_GOO_STACK_HEIGHT_MINIMUM;
				diminished += v;
				scale = remainder / (1f - diminished);
				v = ARBITRARY_GOO_STACK_HEIGHT_MINIMUM;
			} else {
				v *= scale;
			}
			results.put(g.getFluid(), v);
		}
		return results;
	}

	private static double calculateCapacityLog(int capacity) {
		return Math.log(capacity) / Math.log(16d);
	}

	public Object2FloatMap<Fluid> calculateFluidHeights() {
		return calculateFluidHeights(getStorageCapacity(), goo);
	}

	public float calculateFluidHeight() {
		Map<Fluid, Float> fluidStackHeights = calculateFluidHeights();
		return (float)fluidStackHeights.values().stream().mapToDouble(v -> v).sum() * HEIGHT_SCALE;
	}

	private AxisAlignedBB getSpaceInBox() {
		float fluidLevels = calculateFluidHeight();
		return new AxisAlignedBB(this.pos.getX(), this.pos.getY() + fluidLevels, this.pos.getZ(),
				this.pos.getX() + 1d, this.pos.getY() + 1d, this.pos.getZ() + 1d);
	}

	@Override
	public FluidStack getGooFromTargetRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource) {

		if (goo.isEmpty()) {
			return FluidStack.EMPTY;
		}

		FluidStack last = FluidStack.EMPTY;
		Object2FloatMap<Fluid> fluidStacksHeights = calculateFluidHeights();
		float height = this.pos.getY() + FLUID_VERTICAL_OFFSET;
		float hitY = (float)hitVec.y;

		for (int i = 0, e = goo.getTanks(); i < e; ++i) {
			FluidStack g = goo.getFluidInTankInternal(i);
			if (g.getAmount() <= 0 || !fluidStacksHeights.containsKey(g.getFluid()))
				continue;

			last = g;

			float gooHeight = fluidStacksHeights.getFloat(g.getFluid()) * HEIGHT_SCALE;
			if (hitY >= height && hitY < height + gooHeight)
				return g;

			height += gooHeight;
		}

		// we couldn't find the stack which means we're *above* any stack. Return the last one.
		return last;
	}

	@Override
	public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource) {

		return goo;
	}

	@Override
	public void tick() {

	}
}
