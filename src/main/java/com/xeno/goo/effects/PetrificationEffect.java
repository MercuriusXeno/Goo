package com.xeno.goo.effects;

import com.xeno.goo.Registry;
import com.xeno.goo.blobs.WeaponizedBlobHitContext;
import com.xeno.goo.entities.EntityNbtHelper;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.List;

public class PetrificationEffect extends AbstractGooEffect {
	private static final int SHATTER_PARTICLE_COUNT = 120;
	public static final String PETRIFICATION_PROGRESS = "petrification_progress";
	public static final int MAX_PROGRESS = 99;
	private static final String ALREADY_NO_AI = "already_no_ai";

	//	private static final String ALREADY_NO_AI = "already_no_ai";
	public PetrificationEffect() {
		super();
	}

	@Override
	public List<ItemStack> getCurativeItems() {
		return List.of();
	}

	public static void handlePetrificationTicks(LivingUpdateEvent event) {
		var e = event.getEntityLiving();
		if (e.getActiveEffectsMap().containsKey(Registry.PETRIFICATION_EFFECT.get())) {
			var initialAmp = e.getActiveEffectsMap().get(Registry.PETRIFICATION_EFFECT.get()).getAmplifier();
			var nbt = e.getPersistentData();
			EntityNbtHelper.ensureIntContents(nbt, PETRIFICATION_PROGRESS, initialAmp);

			var petrificationProgress = EntityNbtHelper.incrementIntContents(nbt, PETRIFICATION_PROGRESS, initialAmp, MAX_PROGRESS);
			if (petrificationProgress >= MAX_PROGRESS) {
				// prevent the entity from doing anything
				event.setCanceled(true);
			}
			e.removeEffect(Registry.PETRIFICATION_EFFECT.get());
			e.addEffect(new MobEffectInstance(Registry.PETRIFICATION_EFFECT.get(), MAX_PROGRESS,
					petrificationProgress, false, false, false));
			if (e instanceof Mob m) {
				if (petrificationProgress < MAX_PROGRESS) {
					EntityNbtHelper.ensureBoolContents(nbt, ALREADY_NO_AI, m.isNoAi());
				}
				if (petrificationProgress >= MAX_PROGRESS) {
					m.setNoAi(true);
				} else {
					EntityNbtHelper.ensureBoolContents(nbt, ALREADY_NO_AI, false);
					m.setNoAi(nbt.getBoolean(ALREADY_NO_AI));
				}
			}
		}
	}

	public static void handlePetrificationResistance(LivingAttackEvent event) {
		var e = event.getEntityLiving();
		if (e.getActiveEffectsMap().containsKey(Registry.PETRIFICATION_EFFECT.get())) {
			var initialAmp = e.getActiveEffectsMap().get(Registry.PETRIFICATION_EFFECT.get()).getAmplifier();
			var nbt = e.getPersistentData();
			EntityNbtHelper.ensureIntContents(nbt, PETRIFICATION_PROGRESS, 1);
			var progress = e.getPersistentData().getInt(PETRIFICATION_PROGRESS);
			var isLethalAnyway = false;
			if (progress >= MAX_PROGRESS) {
				// prevent the entity from taking ANY damage.. unless
				isLethalAnyway = event.getSource().isExplosion() || event.getSource().isFall(); // pickaxe/tool handling is handled elsewhere, somehow...
				event.setCanceled(true);

			}
			if (isLethalAnyway) {
				shatterEffect(e);
				return;
			}

			var petrificationProgress = EntityNbtHelper.incrementIntContents(nbt, PETRIFICATION_PROGRESS, initialAmp, MAX_PROGRESS);
			e.removeEffect(Registry.PETRIFICATION_EFFECT.get());
			e.addEffect(new MobEffectInstance(Registry.PETRIFICATION_EFFECT.get(), MAX_PROGRESS,
					petrificationProgress, false, false, false));
			if (e instanceof Mob m) {
				if (petrificationProgress < MAX_PROGRESS) {
					EntityNbtHelper.ensureBoolContents(nbt, ALREADY_NO_AI, m.isNoAi());
				}
				if (petrificationProgress >= MAX_PROGRESS) {
					handleEntitySpecificDataCorrections(e);
					m.setNoAi(true);
				} else {
					EntityNbtHelper.ensureBoolContents(nbt, ALREADY_NO_AI, false);
					m.setNoAi(nbt.getBoolean(ALREADY_NO_AI));
				}
			}
		}
	}

	private static void handleEntitySpecificDataCorrections(LivingEntity e) {
		e.setOldPosAndRot();

		if (e.yHeadRot != e.yHeadRotO) {
			e.yHeadRotO = e.yHeadRot;
		}

		if (e.yBodyRot != e.yBodyRotO) {
			e.yBodyRotO = e.yBodyRot;
		}

		float f = (float)(e.getYRot() * 360) / 256.0F;
		float f1 = (float)(e.getXRot() * 360) / 256.0F;
		e.lerpTo(e.getX(), e.getY(), e.getZ(), f, f1, 0, true);

		float f2 = (float)(e.getYHeadRot() * 360) / 256.0F;
		e.lerpHeadTo(f2, 0);

		if (e instanceof AbstractHorse ah) {
			// tail, eat, standing, mouth
			ah.tailCounter = 0;
//			ah.eatingCounter = 0;
//			ah.standCounter = 0;
		}

		if (e instanceof Sheep s) {
			// eating
		}
	}

	public static void shatterEffect(LivingEntity e) {
		if (e.getPersistentData().getInt(PetrificationEffect.PETRIFICATION_PROGRESS) >= PetrificationEffect.MAX_PROGRESS) {
			// "shatter" the mob
			if (e.level.isClientSide) {
				var ab = e.getBoundingBox();
				var xa = ab.minX;
				var xz = ab.maxX;
				var ya = ab.minY;
				var yz = ab.maxY;
				var za = ab.minZ;
				var zz = ab.maxZ;
				var x = e.level.random.doubles(xa, xz).iterator();
				var y = e.level.random.doubles(ya, yz).iterator();
				var z = e.level.random.doubles(za, zz).iterator();
				for (var i = 0; i < SHATTER_PARTICLE_COUNT; i++) {
					e.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()),
							x.nextDouble(), y.nextDouble(), z.nextDouble(), 0d, 0d, 0d);
				}
			} else {
				e.level.addFreshEntity(new ExperienceOrb(e.level, e.getX(), e.getY(), e.getZ(), 6));
				e.remove(RemovalReason.DISCARDED);
			}
		}
	}
}
