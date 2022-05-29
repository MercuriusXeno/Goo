package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.blobs.GooElement;
import com.xeno.goo.blobs.WeaponizedBlobHitContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public class TestGooItem extends Item {

	private final GooElement element;
	public TestGooItem(GooElement element) {
		super(new Item.Properties()
				.tab(GooMod.ITEM_GROUP)
				.stacksTo(64)
		);
		this.element = element;
	}

	@Override
	public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
		if (entity instanceof LivingEntity living) {
			return this.element.getWeaponizedEffect().resolve(new WeaponizedBlobHitContext(living, player, this.element));
		}
		return super.onLeftClickEntity(stack, player, entity);
	}

	public InteractionResult useOn(UseOnContext context) {
		return InteractionResult.FAIL;
	}
}
