package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.elements.ElementEnum;
import com.xeno.goo.blobs.WeaponizedBlobHitContext;
import com.xeno.goo.entities.AbstractThrownBlob;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BlobOfGooItem extends Item {

	private final ElementEnum element;
	public BlobOfGooItem(ElementEnum element) {
		super(new Item.Properties()
				.tab(GooMod.ITEM_GROUP)
				.stacksTo(64)
		);
		this.element = element;
	}

	@Override
	public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
		if (entity instanceof LivingEntity living) {
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			this.element.getWeaponizedEffect().resolve(new WeaponizedBlobHitContext(living, player, this.element));
			return true;
		}
		return super.onLeftClickEntity(stack, player, entity);
	}

	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack itemstack = player.getItemInHand(hand);
		level.playSound((Player)null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
		if (!level.isClientSide) {
			AbstractThrownBlob thrownBlob = element.createThrownBlob(level, player);
			if (thrownBlob == null) {
				return InteractionResultHolder.fail(itemstack);
			}
			thrownBlob.setItem(itemstack);
			thrownBlob.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
			level.addFreshEntity(thrownBlob);
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.getAbilities().instabuild) {
			itemstack.shrink(1);
		}

		return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
	}
}
