package com.xeno.goo.tiles;

import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.Equivalencies;
import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.datagen.GooBlockTags;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.Compare;
import com.xeno.goo.network.CrucibleCurrentItemPacket;
import com.xeno.goo.network.CrucibleMeltProgressPacket;
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
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.particles.ItemParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CrucibleTile extends GooContainerAbstraction implements ITickableTileEntity,
																	 FluidUpdatePacket.IFluidPacketReceiver {

	private static final int MAX_MELT_SPEED = 20;
	// the itemstack currently being "held" by the crucible. If meltRate > 0, this item is also melting.
	private ItemStack currentItem;

	private int meltRate;

	// the fluidstacks created when an item begins melting. These fluidstacks deplete into the crucible until empty,
	// at which point melting is complete (melted item will become air, and this list will be empty)
	private List<FluidStack> meltedProgress;

	private float simplifiedMeltProgress;

	public CrucibleTile() {

		super(Registry.CRUCIBLE_TILE.get());
	}

	public void setSimplifiedMeltProgress(float f) {
		simplifiedMeltProgress = f;
	}

	private void setSimplifiedMeltProgress() {
		float f = getMeltProgressForItemAndMelting();
		if (f != simplifiedMeltProgress) {
			setSimplifiedMeltProgress(f);
			sendMeltProgressUpdatePacket();
		}
	}

	private void sendMeltProgressUpdatePacket() {
		Networking.sendToClientsAround(new CrucibleMeltProgressPacket(this.world, this.pos, this.simplifiedMeltProgress, this.meltRate), (ServerWorld)this.world, this.pos);
	}

	private float getMeltProgressForItemAndMelting() {
		if (currentItem == null || currentItem.isEmpty()) {
			return 0f;
		}

		if (meltedProgress.size() == 0) {
			return 0f;
		}

		GooEntry entry = Equivalencies.getEntry(this.world, currentItem.getItem());
		if (entry.isUnusable()) {
			return 0f;
		}

		List<FluidStack> stacks = entry.inputsAsFluidStacks();
		int sumGoo = stacks.stream().mapToInt(FluidStack::getAmount).sum();
		int sumProgress = meltedProgress.stream().mapToInt(FluidStack::getAmount).sum();

		if (sumGoo == 0f) {
			return 0f;
		}

		return 1f - ((float)sumProgress / sumGoo);
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
	private static final float FLUID_VERTICAL_OFFSET = 0.376f;
	private static final float FLUID_VERTICAL_MAX = (1f - 0.075f);
	public static final float HEIGHT_SCALE = FLUID_VERTICAL_MAX - FLUID_VERTICAL_OFFSET;
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

	private AxisAlignedBB getSpaceInCrucible() {
		return new AxisAlignedBB(this.pos.getX() + 0.1875d, this.pos.getY() + 0.125d, this.pos.getZ() + 0.1875d,
				this.pos.getX() + 0.8125d, this.pos.getY() + 1d, this.pos.getZ() + 0.8125d);
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

	public List<FluidStack> getAllGooContentsFromTile() {
		return goo.getFluidAsList();
	}

	@Override
	public IFluidHandler getCapabilityFromRayTraceResult(Vector3d hitVec, Direction face, RayTraceTargetSource targetSource) {

		return goo;
	}

	@Override
	public void tick() {
		if (world.isRemote()) {
			if (hasHeatSource() && !currentItem().isEmpty() && meltRate > 0) {
				spawnItemParticles();
			}
			return;
		}

		// try to grab an item from within the cauldron's inner hitbox if one exists.
		if (currentItem().isEmpty()) {
			tryGrabbingItem();
		}

		// try to turn goo + items into something first.
		trySubstrateTransmutation();

		// if there's no substrate transmuting to do, melt the item, if one exists
		tryMeltingItemForGoo();
	}

	private void tryMeltingItemForGoo() {
		if (!hasHeatSource() || currentItem().isEmpty()) {
			meltRate = 0;
			sendMeltProgressUpdatePacket();
			return;
		}

		boolean hasMeltedProgressChanged = false;
		if (meltRate < MAX_MELT_SPEED && world.getGameTime() % 5 == 0) {
			meltRate++;
			hasMeltedProgressChanged = true;
		}

		if (meltRate > 0) {
			if (meltedProgress == null || meltedProgress.size() == 0) {
				List<FluidStack> values = Equivalencies.getEntry(this.world, currentItem().getItem()).inputsAsFluidStacks();
				meltedProgress = values;
				hasMeltedProgressChanged = true;
			}

			int maxTransfer = meltRate;
			for(FluidStack stack : meltedProgress) {
				if (maxTransfer <= 0) {
					break;
				}
				int minTransfer = Math.min(stack.getAmount(), maxTransfer);
				if (stack.isEmpty()) {
					continue;
				}
				FluidStack fillStack = stack.copy();
				fillStack.setAmount(minTransfer);
				int simulatedFill = goo.fill(fillStack, FluidAction.SIMULATE);
				if (simulatedFill == 0) {
					break;
				} else {
					if (simulatedFill < minTransfer) {
						minTransfer = simulatedFill;
					}
				}
				fillStack = stack.copy();
				fillStack.setAmount(minTransfer);
				goo.fill(fillStack, FluidAction.EXECUTE);
				stack.shrink(minTransfer);
				hasMeltedProgressChanged = true;
			}
			int previousSize = meltedProgress.size();
			meltedProgress.removeIf(FluidStack::isEmpty);
			if (previousSize != meltedProgress.size()) {
				hasMeltedProgressChanged = true;
			}
			if (meltedProgress.size() == 0) {
				currentItem.shrink(1);
				sendCurrentItemUpdatedPacket();
			}

			if (hasMeltedProgressChanged) {
				setSimplifiedMeltProgress();
			}
		}
	}

	private boolean trySubstrateTransmutation() {
		if (goo.isEmpty()) {
			return false;
		}

		if (currentItem == null || currentItem.isEmpty()) {
			return false;
		}

		// don't try substrate transmutations if items are melting.
		// since substrate tries first, we return true if substrate succeeds.
		// if it does, melting doesn't fire until next tick (which gives substrate a chance to fire again)
		if (meltRate > 0) {
			return false;
		}

		return false;
	}

	private void tryGrabbingItem() {
		AxisAlignedBB bb = getSpaceInCrucible();
		List<ItemEntity> itemsInBox = world.getEntitiesWithinAABB(ItemEntity.class, bb);
		if (itemsInBox.isEmpty()) {
			return;
		}
		ItemStack item = itemsInBox.get(0).getItem();
		if (Equivalencies.getEntry(this.world, item.getItem()).isUnusable()) {
			return;
		}
		setCurrentItem(item);
		sendCurrentItemUpdatedPacket();
		itemsInBox.get(0).remove();
	}

	private void sendCurrentItemUpdatedPacket() {
		Networking.sendToClientsAround(new CrucibleCurrentItemPacket(this.world, this.pos, this.currentItem), (ServerWorld)this.world, this.pos);
	}

	private void spawnItemParticles() {
		for(int i = 0; i < 4; ++i) {
			// try to determine the item position, vaguely
			// this is based on the fluid height, on top of the offset
			float fluidHeight = calculateFluidHeight() + FLUID_VERTICAL_OFFSET;
			float dx = (world.rand.nextFloat() - 0.5f) / 8f;
			float dy = (world.rand.nextFloat() - 0.5f) / 16f;
			float dz = (world.rand.nextFloat() - 0.5f) / 8f;
			Vector3d center = Vector3d.copyCentered(this.pos).add(dx, dy + fluidHeight, dz);
			if (this.world instanceof ServerWorld)
				((ServerWorld)this.world).spawnParticle(new ItemParticleData(ParticleTypes.ITEM, currentItem()), center.x, center.y, center.z, 1, 0d, 0d, 0d, 0.0D);
			else
				this.world.addParticle(new ItemParticleData(ParticleTypes.ITEM, currentItem()), center.x, center.y, center.z, 0d, 0d, 0d);
		}

	}

	private ItemStack currentItem() {
		if (currentItem != null && !currentItem.isEmpty()) {
			return currentItem;
		}

		return ItemStack.EMPTY;
	}

	private boolean hasHeatSource() {
		BlockState stateBelow = world.getBlockState(this.pos.offset(Direction.DOWN));
		if (stateBelow.getBlock().isIn(GooBlockTags.HEAT_SOURCES_FOR_CRUCIBLE)) {
			return true;
		}
		return false;
	}

	public void setCurrentItem(ItemStack currentItem) {
		this.currentItem = currentItem;
	}

	public void setMeltRate(int meltRate) {
		this.meltRate = meltRate;
	}
}
