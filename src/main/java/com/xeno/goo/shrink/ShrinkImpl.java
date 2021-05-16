package com.xeno.goo.shrink;
import com.google.common.collect.ImmutableMap;
import com.xeno.goo.GooMod;
import com.xeno.goo.network.Networking;
import com.xeno.goo.network.ShrinkPacket;
import com.xeno.goo.shrink.api.IShrinkProvider;
import com.xeno.goo.shrink.api.ShrinkAPI;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public final class ShrinkImpl
{
	public static final float defaultEyeHeight = 1.62F;
	public static final EntitySize defaultSize = new EntitySize(0.6F, 1.8F, false);

	public static final Map<Pose, EntitySize> defaultSizes = ImmutableMap.<Pose, EntitySize>builder().put(Pose.STANDING, PlayerEntity.STANDING_SIZE).put(Pose.SLEEPING, EntitySize.flexible(0.2F, 0.2F)).put(Pose.FALL_FLYING, EntitySize.flexible(0.6F, 0.6F)).put(Pose.SWIMMING, EntitySize.flexible(0.6F, 0.6F)).put(Pose.SPIN_ATTACK, EntitySize.flexible(0.6F, 0.6F)).put(Pose.CROUCHING, EntitySize.flexible(0.6F, 1.5F)).put(Pose.DYING, EntitySize.fixed(0.2F, 0.2F)).build();

	public static void init()
	{
		CapabilityManager.INSTANCE.register(IShrinkProvider.class, new Capability.IStorage<IShrinkProvider>() {
			@Override
			public CompoundNBT writeNBT(Capability<IShrinkProvider> capability, IShrinkProvider instance, Direction side) {
				return instance.serializeNBT();
			}

			@Override
			public void readNBT(Capability<IShrinkProvider> capability, IShrinkProvider instance, Direction side, INBT nbt) {
				if (nbt instanceof CompoundNBT) {
					instance.deserializeNBT((CompoundNBT) nbt);
				}
			}
		}, () -> new DefaultImpl(null));
	}

	private static class DefaultImpl implements IShrinkProvider
	{
		@Nullable
		private final LivingEntity entity;
		private boolean isShrunk = false;
		private boolean isShrinking = false;
		private EntitySize defaultEntitySize;
		private float defaultEyeHeight;
		private float scale = 1F;

		private DefaultImpl(@Nullable LivingEntity entity)
		{
			this.entity = entity;
			this.defaultEntitySize = entity.size;
			this.defaultEyeHeight = entity.eyeHeight;
		}

		@Override
		public boolean isShrunk()
		{
			return isShrunk;
		}

		@Override
		public void setShrunk(boolean isShrunk)
		{
			if(this.isShrunk != isShrunk)
			{
				this.isShrunk = isShrunk;
				sync(entity);
			}
		}

		@Override
		public boolean isShrinking()
		{
			return this.isShrinking;
		}

		@Override
		public void setShrinking(boolean shrinking)
		{
			this.isShrinking = shrinking;
		}

		@Override
		public void sync(@Nonnull LivingEntity entity)
		{
			if (entity.world.isRemote) {
				return;
			}
			Networking.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> this.entity),
					new ShrinkPacket(this.entity.getEntityId(), serializeNBT()));
		}

		@Override
		public void shrink(@Nonnull LivingEntity entity)
		{
			setShrunk(true);
			setShrinking(true);
			entity.size = new EntitySize(0.1F, 0.2F, true);
			PlayerEntity.STANDING_SIZE = EntitySize.flexible(0.1F, 0.2F);
			PlayerEntity.SIZE_BY_POSE = ImmutableMap.<Pose, EntitySize>builder()
					.put(Pose.STANDING, PlayerEntity.STANDING_SIZE)
					.put(Pose.SLEEPING, EntitySize.fixed(0.2F, 0.2F))
					.put(Pose.FALL_FLYING, EntitySize.flexible(0.6F, 0.6F))
					.put(Pose.SWIMMING, EntitySize.flexible(0.6F, 0.6F))
					.put(Pose.SPIN_ATTACK, EntitySize.flexible(0.6F, 0.6F))
					.put(Pose.CROUCHING, EntitySize.flexible(0.5F, 0.5F))
					.put(Pose.DYING, EntitySize.fixed(0.2F, 0.2F)).build();
			entity.eyeHeight = 0.16F;
			entity.recalculateSize();
			sync(entity);
		}

		@Override
		public void deShrink(@Nonnull LivingEntity entity)
		{
			setShrunk(false);
			setShrinking(false);
			entity.eyeHeight = defaultEyeHeight;
			entity.size = defaultSize;
			PlayerEntity.SIZE_BY_POSE = defaultSizes;
			entity.recalculateSize();
			sync(entity);
		}

		@Override
		public EntitySize defaultEntitySize() {

			return defaultSize;
		}

		@Override
		public float defaultEyeHeight() {

			return defaultEyeHeight;
		}

		@Override
		public float scale()
		{
			return scale;
		}

		@Override
		public void setScale(float scale)
		{
			if(this.scale != scale)
			{
				this.scale = scale;
				sync(entity);
			}
		}

		@Override
		public CompoundNBT serializeNBT()
		{
			CompoundNBT properties = new CompoundNBT();
			properties.putBoolean("isshrunk", isShrunk);
			properties.putBoolean("isshrinking", isShrinking);
			return properties;
		}

		@Override
		public void deserializeNBT(CompoundNBT properties)
		{
			isShrunk = properties.getBoolean("isshrunk");
			isShrinking = properties.getBoolean("isshrinking");
		}
	}

	public static class Provider implements ICapabilitySerializable<CompoundNBT>
	{
		public static final ResourceLocation NAME = new ResourceLocation(GooMod.MOD_ID, "shrunk");

		private final DefaultImpl impl;
		private final LazyOptional<IShrinkProvider> cap;

		public Provider(LivingEntity entity)
		{
			impl = new DefaultImpl(entity);
			cap = LazyOptional.of(() -> impl);
		}

		@Nonnull
		@Override
		public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, Direction facing)
		{
			if (capability == ShrinkAPI.SHRINK_CAPABILITY)
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