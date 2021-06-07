package com.xeno.goo.fertilize;

import com.xeno.goo.GooMod;
import com.xeno.goo.effects.FloralEffect;
import com.xeno.goo.network.FertilizePacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.block.IGrowable;
import net.minecraft.block.MushroomBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BoneMealItem;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FertilizeImpl {

	public static void init()
	{
		CapabilityManager.INSTANCE.register(IFertilizeProvider.class, new Capability.IStorage<IFertilizeProvider>() {
			@Override
			public CompoundNBT writeNBT(Capability<IFertilizeProvider> capability, IFertilizeProvider instance, Direction side) {
				return instance.serializeNBT();
			}

			@Override
			public void readNBT(Capability<IFertilizeProvider> capability, IFertilizeProvider instance, Direction side, INBT nbt) {
				if (nbt instanceof CompoundNBT) {
					instance.deserializeNBT((CompoundNBT) nbt);
				}
			}
		}, () -> new DefaultImpl(null));
	}

	private static class DefaultImpl implements IFertilizeProvider {

		@Nullable
		private final LivingEntity entity;
		private BlockPos previousBlockPosition;

		private DefaultImpl(@Nullable LivingEntity entity)
		{
			this.entity = entity;
			if (entity != null) {
				this.setPrevBlockPos(entity.getPosition());
			}
		}

		@Override
		public BlockPos prevBlockPos() {
			return this.previousBlockPosition;
		}

		@Override
		public void setPrevBlockPos(BlockPos pos) {
			// for some reason intellisense thinks entity.getActivePotionMap() can never be null, and this is simply not true.
			if (entity == null || entity.getActivePotionMap() == null || entity.getActivePotionEffect(Registry.FLORAL_EFFECT.get()) == null) {
				return;
			}

			if (this.previousBlockPosition != null && this.previousBlockPosition.equals(pos)) {
				return;
			}
			tryFloralEffectOnTile(entity.world, pos);
			tryFloralEffectOnTile(entity.world, pos.offset(Direction.UP));

			this.previousBlockPosition = pos;

			this.sync(entity);
		}

		private void tryFloralEffectOnTile(World world, BlockPos position) {
			BlockState state = world.getBlockState(position);
			if (!(state.getBlock() instanceof IGrowable)) {
				return;
			}
			IGrowable growable = (IGrowable)state.getBlock();
			if (!growable.canGrow(world, position, state, world.isRemote())) {
				return;
			}
			if (state.getBlock() instanceof MushroomBlock) {
				return;
			}
			if (!(world instanceof ServerWorld)) {
				BoneMealItem.spawnBonemealParticles(world, position, 4);
				return;
			}
			growable.grow((ServerWorld) world, world.rand, position, state);
		}

		@Override
		public void sync(LivingEntity e) {
			if (entity.world.isRemote) {
				return;
			}
			Networking.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
					new FertilizePacket(entity.getEntityId(), serializeNBT()));
		}

		@Override
		public CompoundNBT serializeNBT() {

			CompoundNBT properties = new CompoundNBT();
			if (this.prevBlockPos() != null) {
				properties.putInt("prevBlockPosX", this.prevBlockPos().getX());
				properties.putInt("prevBlockPosY", this.prevBlockPos().getY());
				properties.putInt("prevBlockPosZ", this.prevBlockPos().getZ());
			}
			return properties;
		}

		@Override
		public void deserializeNBT(CompoundNBT nbt) {
			this.setPrevBlockPos(new BlockPos(nbt.getInt("prevBlockPosX"), nbt.getInt("prevBlockPosY"), nbt.getInt("prevBlockPosZ")));
		}
	}

	public static class Provider implements ICapabilitySerializable<CompoundNBT>
	{
		public static final ResourceLocation NAME = new ResourceLocation(GooMod.MOD_ID, "fertilize");

		private final FertilizeImpl.DefaultImpl impl;
		private final LazyOptional<IFertilizeProvider> cap;

		public Provider(LivingEntity entity)
		{
			impl = new FertilizeImpl.DefaultImpl(entity);
			cap = LazyOptional.of(() -> impl);
		}

		@Nonnull
		@Override
		public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, Direction facing)
		{
			if (capability == FertilizeCapability.FERTILIZE_CAPABILITY)
			{
				return cap.cast();
			}
			return LazyOptional.empty();
		}

		@Override
		public CompoundNBT serializeNBT()
		{
			return impl.serializeNBT();
		}

		@Override
		public void deserializeNBT(CompoundNBT nbt)
		{
			impl.deserializeNBT(nbt);
		}
	}
}
